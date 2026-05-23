package com.asuka.pocketpdf.ui.settings

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.asuka.pocketpdf.databinding.ActivitySettingsBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private val viewModel: SettingsViewModel by viewModels()
    private var fieldsPopulated = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyWindowInsets()

        binding.toolbarSettings.setNavigationOnClickListener { finish() }
        binding.btnSettingsSave.setOnClickListener { viewModel.save() }
        binding.btnResetDefaults.setOnClickListener { viewModel.resetDefaults() }
        binding.btnTestConnection.setOnClickListener { viewModel.testConnection() }

        addTextWatchers()
        setupPresetDropdown()
        setupChunkingDropdown()

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.uiState.collect { render(it) } }
            }
        }
    }

    private fun addTextWatchers() {
        binding.etBaseUrl.addTextChangedListener(SimpleWatcher { viewModel.onBaseUrlChanged(it) })
        binding.etModelName.addTextChangedListener(SimpleWatcher { viewModel.onModelNameChanged(it) })
        binding.etApiKey.addTextChangedListener(SimpleWatcher { viewModel.onApiKeyChanged(it) })
        binding.etSystemPrompt.addTextChangedListener(SimpleWatcher { viewModel.onSystemPromptChanged(it) })
    }

    private fun setupPresetDropdown() {
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, MODEL_PRESETS.map { it.label })
        (binding.etPreset as? android.widget.AutoCompleteTextView)?.apply {
            setAdapter(adapter)
            setOnItemClickListener { _, _, pos, _ ->
                viewModel.onPresetSelected(MODEL_PRESETS[pos].id)
            }
        }
    }

    private fun setupChunkingDropdown() {
        val strategies = listOf("滑动窗口（默认）" to "sliding_window", "按段落" to "paragraph")
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, strategies.map { it.first })
        (binding.etChunkingStrategy as? android.widget.AutoCompleteTextView)?.apply {
            setAdapter(adapter)
            setOnItemClickListener { _, _, pos, _ ->
                viewModel.onChunkingStrategyChanged(strategies[pos].second)
            }
        }
    }

    private fun render(state: SettingsUiState) {
        if (!fieldsPopulated && state.baseUrl.isNotEmpty()) {
            binding.etBaseUrl.setText(state.baseUrl)
            binding.etModelName.setText(state.modelName)
            binding.etApiKey.setText(state.apiKey)
            binding.etSystemPrompt.setText(state.systemPrompt)
            val p = MODEL_PRESETS.find { it.baseUrl == state.baseUrl && it.modelName == state.modelName }
            binding.etPreset.setText(p?.label ?: "自定义")
            binding.etChunkingStrategy.setText(if (state.chunkingStrategy == "paragraph") "按段落" else "滑动窗口（默认）")
            fieldsPopulated = true
        }
        binding.btnTestConnection.isEnabled = !state.connectionTesting
        binding.btnSettingsSave.isEnabled = !state.isSaving
        state.error?.let { Toast.makeText(this, it, Toast.LENGTH_LONG).show() }
        if (state.saveSuccess) Toast.makeText(this, "已保存", Toast.LENGTH_SHORT).show()
        state.connectionTestResult?.let {
            Toast.makeText(this, it, Toast.LENGTH_LONG).show()
            binding.btnTestConnection.text = if (it.startsWith("✅")) "✅ 已连接" else "❌ 失败"
        }
        if (state.availableModels.isNotEmpty()) {
            val a = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, state.availableModels)
            (binding.etModelName as? android.widget.AutoCompleteTextView)?.setAdapter(a)
        }
    }

    private fun applyWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
    }

    private class SimpleWatcher(private val onChanged: (String) -> Unit) : TextWatcher {
        override fun afterTextChanged(s: Editable?) { onChanged(s?.toString() ?: "") }
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
    }
}
