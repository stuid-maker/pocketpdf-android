package com.asuka.pocketpdf.ui.diagnostics

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.view.WindowCompat
import com.asuka.pocketpdf.ui.theme.PocketPDFTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class DiagnosticsActivity : ComponentActivity() {

    private val viewModel: DiagnosticsViewModel by viewModels()

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
                LaunchedEffect(Unit) { viewModel.runDiagnostics() }
                DiagnosticsScreen(
                    state = state,
                    onBack = ::finish,
                    onRetry = viewModel::runDiagnostics,
                    onOpenSettings = ::finish,
                )
            }
        }
    }
}
