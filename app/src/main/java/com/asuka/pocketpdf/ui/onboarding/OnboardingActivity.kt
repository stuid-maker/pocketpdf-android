package com.asuka.pocketpdf.ui.onboarding

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import com.asuka.pocketpdf.ui.library.LibraryActivity
import com.asuka.pocketpdf.ui.settings.SettingsActivity
import com.asuka.pocketpdf.ui.theme.PocketPDFTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class OnboardingActivity : ComponentActivity() {

    private val viewModel: OnboardingViewModel by viewModels()

    private val settingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        // User returned from settings — stay on the same onboarding step
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PocketPDFTheme {
                OnboardingScreen(
                    onOpenSettings = {
                        settingsLauncher.launch(
                            Intent(this, SettingsActivity::class.java),
                        )
                    },
                    onFinish = {
                        viewModel.completeOnboarding()
                        startActivity(
                            Intent(this, LibraryActivity::class.java),
                        )
                        finish()
                    },
                )
            }
        }
    }
}
