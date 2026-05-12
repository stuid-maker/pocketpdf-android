package com.asuka.pocketpdf.ui.ping

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.asuka.pocketpdf.R
import com.asuka.pocketpdf.databinding.ActivityPingBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Week 0 环境就绪自检界面。
 *
 * 流程：点按钮 → ViewModel 调 `/v1/models` → Toast 显示第一个模型 ID，
 *      并把全部模型 ID 渲染到下方 TextView 便于核对。
 *
 * 这个 Activity 在 Week 1 起会被替换为 LibraryActivity，但保留作为 Week 0 验收凭据。
 */
@AndroidEntryPoint
class PingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPingBinding
    private val viewModel: PingViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityPingBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyWindowInsets()

        binding.btnPingPing.setOnClickListener { viewModel.onPingClicked() }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { collectUiState() }
                launch { collectOneShotEvents() }
            }
        }
    }

    private suspend fun collectUiState() {
        viewModel.uiState.collect { state -> render(state) }
    }

    private suspend fun collectOneShotEvents() {
        viewModel.oneShotEvents.collect { event ->
            when (event) {
                is PingEvent.ShowToast -> Toast.makeText(this, event.message, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun render(state: PingUiState) {
        when (state) {
            PingUiState.Idle -> {
                binding.btnPingPing.isEnabled = true
                binding.progressPing.visibility = View.GONE
                binding.tvPingStatus.text = getString(R.string.ping_status_idle)
            }
            PingUiState.Loading -> {
                binding.btnPingPing.isEnabled = false
                binding.progressPing.visibility = View.VISIBLE
                binding.tvPingStatus.text = getString(R.string.ping_status_loading)
            }
            is PingUiState.Success -> {
                binding.btnPingPing.isEnabled = true
                binding.progressPing.visibility = View.GONE
                binding.tvPingStatus.text = state.models.joinToString(
                    separator = "\n",
                ) { "✓ ${it.id}" }
            }
            is PingUiState.Error -> {
                binding.btnPingPing.isEnabled = true
                binding.progressPing.visibility = View.GONE
                binding.tvPingStatus.text = getString(R.string.ping_status_error, state.message)
            }
        }
    }

    private fun applyWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
    }
}
