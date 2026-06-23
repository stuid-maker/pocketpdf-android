package com.asuka.pocketpdf.ui.onboarding

import android.content.Intent
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import com.asuka.pocketpdf.ui.library.LibraryActivity
import com.asuka.pocketpdf.ui.settings.SettingsActivity
import com.asuka.pocketpdf.ui.theme.PocketPDFTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

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
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    finishOnboardingAndOpenLibrary()
                }
            },
        )
        setContent {
            PocketPDFTheme {
                OnboardingScreen(
                    onOpenSettings = {
                        settingsLauncher.launch(
                            Intent(this, SettingsActivity::class.java),
                        )
                    },
                    onFinish = {
                        finishOnboardingAndOpenLibrary()
                    },
                )
            }
        }
    }

    private fun finishOnboardingAndOpenLibrary() {
        lifecycleScope.launch {
            viewModel.markOnboardingCompleted()
            startActivity(Intent(this@OnboardingActivity, LibraryActivity::class.java))
            finish()
        }
    }
}
