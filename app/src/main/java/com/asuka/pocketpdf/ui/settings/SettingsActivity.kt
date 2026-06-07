package com.asuka.pocketpdf.ui.settings

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.view.WindowCompat
import com.asuka.pocketpdf.ui.diagnostics.DiagnosticsActivity
import com.asuka.pocketpdf.ui.theme.PocketPDFTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class SettingsActivity : ComponentActivity() {

    private val viewModel: SettingsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
        setContent {
            PocketPDFTheme {
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                SettingsScreen(
                    state = state,
                    onBack = ::finish,
                    onSave = viewModel::save,
                    onOpenDiagnostics = {
                        startActivity(Intent(this, DiagnosticsActivity::class.java))
                    },
                    actions = SettingsActions(
                        onPresetSelected = viewModel::onPresetSelected,
                        onBaseUrlChanged = viewModel::onBaseUrlChanged,
                        onModelNameChanged = viewModel::onModelNameChanged,
                        onApiKeyChanged = viewModel::onApiKeyChanged,
                        onChunkingChanged = viewModel::onChunkingStrategyChanged,
                        onSystemPromptChanged = viewModel::onSystemPromptChanged,
                        onConfirmPreset = viewModel::confirmPresetOverride,
                        onCancelPreset = viewModel::cancelPresetOverride,
                        onResetDefaults = viewModel::resetDefaults,
                        onTestConnection = viewModel::testConnection,
                    ),
                )
            }
        }
    }
}
