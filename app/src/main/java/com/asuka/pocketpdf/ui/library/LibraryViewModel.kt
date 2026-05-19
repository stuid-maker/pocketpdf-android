package com.asuka.pocketpdf.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asuka.pocketpdf.core.Result
import com.asuka.pocketpdf.data.indexing.IndexingScheduler
import com.asuka.pocketpdf.domain.usecase.DeleteDocumentUseCase
import com.asuka.pocketpdf.domain.usecase.ImportDocumentUseCase
import com.asuka.pocketpdf.domain.usecase.ObserveDocumentsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * 文档库主页的 ViewModel。
 *
 * 三条职责：
 * 1. 把 [ObserveDocumentsUseCase] 的 Flow 转成 [LibraryUiState]，叠加 import / pending-delete 状态
 * 2. 接收 SAF 选完文件的回调，触发 [ImportDocumentUseCase]，结果通过状态 / 一次性事件回流 View
 * 3. 左滑删除 + UNDO 的状态机：5s 内可撤销，超时或主动 dismiss 后真删
 *
 * UNDO 的 ViewModel 侧设计（决策 3）：
 * - swipe → pendingDeleteIds += id（UI 立即看不到该 doc）+ 启动 5s 倒计时 Job + 发 ShowDeleteUndo
 * - 用户在 5s 内 UNDO → cancel timer + pendingDeleteIds -= id（UI 看到该 doc 复现）
 * - 5s 后 timer 触发 / View Snackbar dismiss callback → commitDelete → 调 DeleteDocumentUseCase
 *
 * timer + Snackbar callback 两条路径都能触发 commit 是刻意冗余：旋屏 / Activity 重建时
 * Snackbar 没了但 ViewModel 还在，timer 保证删除最终生效（避免"列表里看不到但 DB 还在"的 dangling state）。
 * commitDelete 自带 idempotent 检查（`id !in pendingDeleteIds`）所以双发也安全。
 *
 * 注：FAB disabled 由 [LibraryUiState.Loaded.isImporting] 驱动；View 不需要自己防抖。
 */
@HiltViewModel
class LibraryViewModel @Inject constructor(
    observeDocuments: ObserveDocumentsUseCase,
    private val importDocument: ImportDocumentUseCase,
    private val deleteDocument: DeleteDocumentUseCase,
    private val indexingScheduler: IndexingScheduler,
) : ViewModel() {

    private val pendingDeleteIds = MutableStateFlow<Set<Long>>(emptySet())
    private val isImporting = MutableStateFlow(false)

    private val pendingDeleteJobs = mutableMapOf<Long, Job>()

    private val _oneShotEvents = Channel<LibraryEvent>(Channel.BUFFERED)
    val oneShotEvents = _oneShotEvents.receiveAsFlow()

    val uiState: StateFlow<LibraryUiState> = combine(
        observeDocuments(),
        pendingDeleteIds,
        isImporting,
    ) { documents, pending, importing ->
        val visible = if (pending.isEmpty()) documents else documents.filterNot { it.id in pending }
        when {
            visible.isEmpty() && !importing -> LibraryUiState.Empty
            else -> LibraryUiState.Loaded(visible, isImporting = importing)
        }
    }.catch { t ->
        Timber.tag(TAG).e(t, "uiState upstream failed")
        emit(LibraryUiState.Error(t.message ?: t.javaClass.simpleName))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), LibraryUiState.Loading)

    fun onImportRequested(sourceUri: String, displayName: String) {
        if (isImporting.value) return
        viewModelScope.launch {
            isImporting.value = true
            val result = importDocument(sourceUri, displayName)
            isImporting.value = false
            when (result) {
                is Result.Success -> {
                    Timber.tag(TAG).i(
                        "import success: id=%d title=%s pages=%d",
                        result.data.id,
                        result.data.title,
                        result.data.pageCount,
                    )
                    // W2 Day 4: 导入成功后自动触发后台索引
                    enqueueIndexing(result.data.id)
                }
                is Result.Failure -> {
                    val msg = result.error.message ?: result.error.javaClass.simpleName
                    Timber.tag(TAG).e(result.error, "import failed: %s", msg)
                    _oneShotEvents.send(LibraryEvent.ShowImportError(msg))
                }
            }
        }
    }

    fun onSwipeDelete(documentId: Long, title: String) {
        if (documentId in pendingDeleteIds.value) return
        pendingDeleteIds.update { it + documentId }
        pendingDeleteJobs[documentId] = viewModelScope.launch {
            _oneShotEvents.send(LibraryEvent.ShowDeleteUndo(documentId, title))
            delay(UNDO_TIMEOUT_MS)
            commitDelete(documentId)
        }
    }

    fun onUndoDelete(documentId: Long) {
        pendingDeleteJobs.remove(documentId)?.cancel()
        pendingDeleteIds.update { it - documentId }
    }

    fun onSnackbarDismissedWithoutUndo(documentId: Long) {
        pendingDeleteJobs.remove(documentId)?.cancel()
        viewModelScope.launch { commitDelete(documentId) }
    }

    private fun enqueueIndexing(documentId: Long) {
        indexingScheduler.schedule(documentId)
        Timber.tag(TAG).d("IndexWorker enqueued for document #%d", documentId)
    }

    private suspend fun commitDelete(documentId: Long) {
        if (documentId !in pendingDeleteIds.value) return
        val result = deleteDocument(documentId)
        pendingDeleteIds.update { it - documentId }
        when (result) {
            is Result.Success -> Timber.tag(TAG).i("delete committed: id=%d", documentId)
            is Result.Failure -> {
                val msg = result.error.message ?: result.error.javaClass.simpleName
                Timber.tag(TAG).e(result.error, "delete failed: id=%d %s", documentId, msg)
                _oneShotEvents.send(LibraryEvent.ShowDeleteError(msg))
            }
        }
    }

    private companion object {
        const val TAG = "LibraryViewModel"
        const val UNDO_TIMEOUT_MS = 5_000L
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
