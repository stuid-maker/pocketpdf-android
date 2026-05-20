package com.asuka.pocketpdf.ui.library

import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.text.format.DateUtils
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.content.Intent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.asuka.pocketpdf.R
import com.asuka.pocketpdf.databinding.ActivityLibraryBinding
import com.asuka.pocketpdf.ui.reader.ReaderActivity
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * 文档库主界面。W1 起作为 LAUNCHER 入口（替代 W0 的 PingActivity）。
 *
 * UI 元素：
 * - FAB：调起 SAF `ACTION_OPEN_DOCUMENT` 限 `application/pdf`
 * - RecyclerView：DiffUtil 驱动的文档列表；左滑触发删除（带 UNDO Snackbar）
 * - 顶部 LinearProgressIndicator：仅在 isImporting=true 时显示
 * - 空状态视图：documents 空且 isImporting=false 时显示
 *
 * SAF DISPLAY_NAME 查询（决策 8）：
 * 选完文件后 `contentResolver.query(uri, [DISPLAY_NAME], ...)` 取原始文件名；
 * 拿不到时 fallback `"未命名 PDF · ${相对时间}"`。绝不从 URI 字符串解析末段——
 * SAF content URI 末段是 docId（编码后的），不同 Provider 格式不一致。
 *
 * 删除 UNDO 协作（与 [LibraryViewModel] 配合，参见 [LibraryEvent.ShowDeleteUndo] KDoc）：
 * - swipe → vm.onSwipeDelete → 收到 ShowDeleteUndo → 弹 Snackbar
 * - Snackbar UNDO action → vm.onUndoDelete
 * - Snackbar 非 ACTION dismiss → vm.onSnackbarDismissedWithoutUndo（被超时 / 手动 dismiss / 旋屏触发）
 * - ViewModel 内部还有 5s timer 兜底处理旋屏 case
 */
@AndroidEntryPoint
class LibraryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLibraryBinding
    private val viewModel: LibraryViewModel by viewModels()

    private val openDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri == null) {
            Timber.tag(TAG).i("SAF cancelled by user")
            return@registerForActivityResult
        }
        val displayName = resolveDisplayName(uri)
        Timber.tag(TAG).i("SAF picked uri=%s displayName=%s", uri, displayName)
        viewModel.onImportRequested(uri.toString(), displayName)
    }

    private val adapter = DocumentListAdapter(
        onClick = { document ->
            startActivity(ReaderActivity.newIntent(this, document.id))
        },
        onRetryIndex = { documentId ->
            viewModel.onRetryIndexing(documentId)
        },
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityLibraryBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyWindowInsets()

        setSupportActionBar(binding.toolbarLibrary)

        binding.rvLibraryDocuments.layoutManager = LinearLayoutManager(this)
        binding.rvLibraryDocuments.adapter = adapter
        attachSwipeToDelete(binding.rvLibraryDocuments)

        binding.fabLibraryImport.setOnClickListener { launchSafPicker() }
        binding.emptyLibrary.btnEmptyLibraryImport.setOnClickListener { launchSafPicker() }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { collectUiState() }
                launch { collectOneShotEvents() }
            }
        }
    }

    private fun launchSafPicker() {
        openDocumentLauncher.launch(arrayOf(MIME_PDF))
    }

    private fun resolveDisplayName(uri: Uri): String {
        val resolved = runCatching {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) cursor.getString(idx) else null
                } else null
            }
        }.onFailure {
            Timber.tag(TAG).w(it, "DISPLAY_NAME query failed for %s", uri)
        }.getOrNull()
        return resolved?.takeIf { it.isNotBlank() } ?: fallbackDisplayName()
    }

    private fun fallbackDisplayName(): String {
        val now = System.currentTimeMillis()
        val relative = DateUtils.getRelativeTimeSpanString(
            now,
            now,
            DateUtils.MINUTE_IN_MILLIS,
        )
        return getString(R.string.library_display_name_fallback, relative)
    }

    private fun attachSwipeToDelete(recyclerView: RecyclerView) {
        val callback = object : ItemTouchHelper.SimpleCallback(
            0,
            ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT,
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder,
            ): Boolean = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val document = adapter.documentAt(viewHolder.bindingAdapterPosition) ?: return
                viewModel.onSwipeDelete(document.id, document.title)
            }
        }
        ItemTouchHelper(callback).attachToRecyclerView(recyclerView)
    }

    private suspend fun collectUiState() {
        viewModel.uiState.collect { state -> render(state) }
    }

    private suspend fun collectOneShotEvents() {
        viewModel.oneShotEvents.collect { event ->
            when (event) {
                is LibraryEvent.ShowImportError -> showLongSnackbar(
                    getString(R.string.library_import_failed, event.message),
                )
                is LibraryEvent.ShowDeleteError -> showLongSnackbar(
                    getString(R.string.library_delete_failed, event.message),
                )
                is LibraryEvent.ShowDeleteUndo -> showDeleteUndoSnackbar(event.documentId, event.title)
            }
        }
    }

    private fun showDeleteUndoSnackbar(documentId: Long, title: String) {
        val snackbar = Snackbar.make(
            binding.root,
            getString(R.string.library_delete_undo_message, title),
            Snackbar.LENGTH_LONG,
        )
        snackbar.setAction(R.string.library_delete_undo_action) {
            viewModel.onUndoDelete(documentId)
        }
        snackbar.addCallback(object : BaseTransientBottomBar.BaseCallback<Snackbar>() {
            override fun onDismissed(transientBottomBar: Snackbar?, event: Int) {
                if (event != DISMISS_EVENT_ACTION) {
                    viewModel.onSnackbarDismissedWithoutUndo(documentId)
                }
            }
        })
        snackbar.show()
    }

    private fun showLongSnackbar(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
    }

    private fun render(state: LibraryUiState) {
        when (state) {
            LibraryUiState.Loading -> {
                binding.progressLibraryImport.visibility = View.GONE
                binding.emptyLibrary.root.visibility = View.GONE
                binding.rvLibraryDocuments.visibility = View.GONE
            }
            LibraryUiState.Empty -> {
                binding.progressLibraryImport.visibility = View.GONE
                binding.emptyLibrary.root.visibility = View.VISIBLE
                binding.rvLibraryDocuments.visibility = View.GONE
                adapter.submitList(emptyList())
            }
            is LibraryUiState.Loaded -> {
                binding.progressLibraryImport.visibility =
                    if (state.isImporting) View.VISIBLE else View.GONE
                binding.fabLibraryImport.isEnabled = !state.isImporting
                binding.emptyLibrary.root.visibility = View.GONE
                binding.rvLibraryDocuments.visibility = View.VISIBLE
                adapter.submitList(state.documents)
            }
            is LibraryUiState.Error -> {
                binding.progressLibraryImport.visibility = View.GONE
                binding.emptyLibrary.root.visibility = View.GONE
                binding.rvLibraryDocuments.visibility = View.GONE
                showLongSnackbar(getString(R.string.library_load_failed, state.message))
            }
        }
    }

    private fun applyWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_library, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                startActivity(Intent(this, com.asuka.pocketpdf.ui.settings.SettingsActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private companion object {
        const val TAG = "LibraryActivity"
        const val MIME_PDF = "application/pdf"
    }
}
