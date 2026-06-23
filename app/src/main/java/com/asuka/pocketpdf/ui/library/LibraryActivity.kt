package com.asuka.pocketpdf.ui.library

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.text.format.DateUtils
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.asuka.pocketpdf.R
import com.asuka.pocketpdf.data.local.SettingsDataStore
import com.asuka.pocketpdf.ui.onboarding.OnboardingActivity
import com.asuka.pocketpdf.ui.reader.ReaderActivity
import com.asuka.pocketpdf.ui.settings.SettingsActivity
import com.asuka.pocketpdf.ui.theme.PocketPDFTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

@AndroidEntryPoint
class LibraryActivity : ComponentActivity() {

    private val viewModel: LibraryViewModel by viewModels()

    @Inject
    lateinit var coverLoader: DocumentCoverLoader

    @Inject
    lateinit var settingsDataStore: SettingsDataStore

    private val openDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult
        persistReadPermission(uri)
        lifecycleScope.launch {
            viewModel.onImportRequested(uri.toString(), resolveDisplayName(uri))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent { PocketPDFTheme { LibraryGateLoading() } }

        lifecycleScope.launch {
            val onboardingCompleted = runCatching {
                settingsDataStore.onboardingCompleted.first()
            }.getOrElse { error ->
                if (error is CancellationException) throw error
                Timber.tag(TAG).w(error, "Unable to read onboarding state")
                true
            }
            if (!onboardingCompleted) {
                startActivity(Intent(this@LibraryActivity, OnboardingActivity::class.java))
                finish()
                return@launch
            }
            showLibraryContent()
        }
    }

    private fun showLibraryContent() {
        setContent {
            PocketPDFTheme {
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                val snackbarHostState = remember { SnackbarHostState() }

                LaunchedEffect(snackbarHostState) {
                    viewModel.oneShotEvents.collect { event ->
                        when (event) {
                            is LibraryEvent.ShowImportError -> snackbarHostState.showSnackbar(
                                getString(R.string.library_import_failed, event.message),
                            )
                            is LibraryEvent.ShowDeleteError -> snackbarHostState.showSnackbar(
                                getString(R.string.library_delete_failed, event.message),
                            )
                            is LibraryEvent.ShowDeleteUndo -> {
                                val result = snackbarHostState.showSnackbar(
                                    message = getString(
                                        R.string.library_delete_undo_message,
                                        event.title,
                                    ),
                                    actionLabel = getString(R.string.library_delete_undo_action),
                                    duration = SnackbarDuration.Long,
                                )
                                if (result == SnackbarResult.ActionPerformed) {
                                    viewModel.onUndoDelete(event.documentId)
                                } else {
                                    viewModel.onSnackbarDismissedWithoutUndo(event.documentId)
                                }
                            }
                        }
                    }
                }

                LibraryScreen(
                    state = state,
                    onImport = ::launchSafPicker,
                    onOpenDocument = {
                        startActivity(ReaderActivity.newIntent(this, it))
                    },
                    onOpenSettings = {
                        startActivity(Intent(this, SettingsActivity::class.java))
                    },
                    onRetryIndexing = viewModel::onRetryIndexing,
                    onDeleteDocument = {
                        viewModel.onSwipeDelete(it.id, it.title)
                    },
                    coverLoader = coverLoader,
                    snackbarHostState = snackbarHostState,
                )
            }
        }
    }

    private fun launchSafPicker() {
        openDocumentLauncher.launch(arrayOf(MIME_PDF))
    }

    private fun persistReadPermission(uri: Uri) {
        runCatching {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }.onFailure {
            Timber.tag(TAG).w(it, "Persistable permission not granted for %s", uri)
        }
    }

    private suspend fun resolveDisplayName(uri: Uri): String {
        val resolved = withContext(Dispatchers.IO) {
            runCatching {
                contentResolver.query(
                    uri,
                    arrayOf(OpenableColumns.DISPLAY_NAME),
                    null,
                    null,
                    null,
                )?.use { cursor ->
                    if (!cursor.moveToFirst()) return@use null
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) cursor.getString(index) else null
                }
            }.onFailure {
                Timber.tag(TAG).w(it, "DISPLAY_NAME query failed for %s", uri)
            }.getOrNull()
        }
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

    private companion object {
        const val TAG = "LibraryActivity"
        const val MIME_PDF = "application/pdf"
    }
}

@Composable
private fun LibraryGateLoading() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}
