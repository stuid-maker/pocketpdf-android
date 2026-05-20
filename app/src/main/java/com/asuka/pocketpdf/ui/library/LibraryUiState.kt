package com.asuka.pocketpdf.ui.library

import com.asuka.pocketpdf.domain.model.Document

/**
 * 文档库界面的状态机。
 *
 * 设计对齐 PingUiState 的 4 态 sealed 风格（CONTRIBUTING §2 命名一致性）：
 * - [Empty]：documents 为空且没有正在进行中的 import；展示空状态视图
 * - [Loading]：StateFlow stateIn 的初始值；Room Flow 几乎立即推空列表把它替换掉
 * - [Loaded]：documents 非空 或 isImporting=true；展示列表 + 可选的顶部进度条
 * - [Error]：observeDocuments 上游抛异常时的兜底；实际 Room Flow 不会抛
 *
 * 注意：import / delete 操作的失败不切换为 [Error]，而是走一次性事件 [LibraryEvent]
 * 弹 Snackbar——避免把整个列表清空。Error 态仅用于"列表数据源本身坏了"的极端情况。
 */
sealed class LibraryUiState {
    data object Empty : LibraryUiState()
    data object Loading : LibraryUiState()
    data class Loaded(
        val documents: List<Document>,
        val isImporting: Boolean = false,
    ) : LibraryUiState()
    data class Error(val message: String) : LibraryUiState()
}

/**
 * 文档库一次性事件（Snackbar / 浮层等不该旋屏后重弹的提示）。
 *
 * 与 [LibraryUiState] 解耦的原因：sealed UiState 进 StateFlow 后旋屏会被回放，
 * 但 Snackbar 这类提示只想消费一次——走 Channel + receiveAsFlow 保证不重弹。
 */
sealed class LibraryEvent {
    /** 导入失败 → Snackbar 弹错误信息（来自 Result.Failure 的 Throwable.message） */
    data class ShowImportError(val message: String) : LibraryEvent()

    /**
     * 用户左滑触发删除 → View 弹 Snackbar 带 UNDO 按钮 + dismiss callback。
     * View 接到后需要：
     * 1. 显示 Snackbar，文案含 [title]
     * 2. UNDO 点击回调 → [LibraryViewModel.onUndoDelete]
     * 3. dismiss 回调（非 UNDO 触发） → [LibraryViewModel.onSnackbarDismissedWithoutUndo]
     *
     * ViewModel 内部还挂了 5s timer 兜底（旋屏 / Activity 重建时 Snackbar 没了
     * 但 ViewModel 还在，timer 保证删除最终生效，避免 dangling state）。
     */
    data class ShowDeleteUndo(val documentId: Long, val title: String) : LibraryEvent()

    /** DeleteDocumentUseCase 失败（DB 删失败 / 文件删失败）→ Snackbar 弹错误信息 */
    data class ShowDeleteError(val message: String) : LibraryEvent()
}
