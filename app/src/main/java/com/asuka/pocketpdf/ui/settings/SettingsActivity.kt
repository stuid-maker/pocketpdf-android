package com.asuka.pocketpdf.ui.settings

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.asuka.pocketpdf.R
import com.asuka.pocketpdf.databinding.ActivitySettingsBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private val viewModel: SettingsViewModel by viewModels()
    private var fieldsPopulated = false
    private var presetConfirmDialog: AlertDialog? = null
    private var modelsDropdownShown = false

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
        setupSpinners()

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.uiState.collect { render(it) } }
            }
        }
    }

    private fun addTextWatchers() {
        val w = { action: (String) -> Unit -> SimpleWatcher(action) }
        binding.etBaseUrl.addTextChangedListener(w(viewModel::onBaseUrlChanged))
        binding.etModelName.addTextChangedListener(w(viewModel::onModelNameChanged))
        binding.etApiKey.addTextChangedListener(w(viewModel::onApiKeyChanged))
        binding.etSystemPrompt.addTextChangedListener(w(viewModel::onSystemPromptChanged))
    }

    private fun setupSpinners() {
        val presetAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, MODEL_PRESETS.map { it.label })
        presetAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerPreset.adapter = presetAdapter
        binding.spinnerPreset.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                if (fieldsPopulated) viewModel.onPresetSelected(MODEL_PRESETS[pos].id)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        val strategies = listOf(getString(R.string.settings_chunking_sliding_window) to "sliding_window", getString(R.string.settings_chunking_paragraph) to "paragraph")
        val chunkAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, strategies.map { it.first })
        chunkAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerChunking.adapter = chunkAdapter
        binding.spinnerChunking.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                if (fieldsPopulated) viewModel.onChunkingStrategyChanged(strategies[pos].second)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun render(state: SettingsUiState) {
        if (!fieldsPopulated && state.baseUrl.isNotEmpty()) {
            binding.etBaseUrl.setText(state.baseUrl)
            binding.etModelName.setText(state.modelName)
            binding.etApiKey.setText(state.apiKey)
            binding.etSystemPrompt.setText(state.systemPrompt)
            binding.spinnerPreset.setSelection(MODEL_PRESETS.indexOfFirst { it.id == state.selectedPreset }.coerceAtLeast(0))
            binding.spinnerChunking.setSelection(if (state.chunkingStrategy == "paragraph") 1 else 0)
            fieldsPopulated = true
        }

        binding.btnTestConnection.isEnabled = !state.connectionTesting
        binding.btnSettingsSave.isEnabled = !state.isSaving

        state.error?.let { Toast.makeText(this, it, Toast.LENGTH_LONG).show() }
        if (state.saveSuccess) Toast.makeText(this, getString(R.string.settings_saved), Toast.LENGTH_SHORT).show()

        // Connection test result → update button text
        state.connectionTestResult?.let {
            Toast.makeText(this, it, Toast.LENGTH_LONG).show()
            binding.btnTestConnection.text = if (it.startsWith("✅")) getString(R.string.settings_btn_connected) else getString(R.string.settings_btn_retry)
        }

        // Per-preset hints
        val preset = MODEL_PRESETS.find { it.id == state.selectedPreset }
        binding.tvBaseUrlHint.text = preset?.baseUrlHint ?: ""
        binding.tvBaseUrlHint.visibility = if (binding.tvBaseUrlHint.text.isNotEmpty()) View.VISIBLE else View.GONE

        binding.tvApiKeyHint.text = preset?.apiKeyHint ?: ""
        binding.tvApiKeyHint.visibility = if (binding.tvApiKeyHint.text.isNotEmpty()) View.VISIBLE else View.GONE

        // Preset override confirmation dialog
        if (state.confirmPresetId != null && presetConfirmDialog == null) {
            val presetLabel = MODEL_PRESETS.find { it.id == state.confirmPresetId }?.label ?: "此预设"
            presetConfirmDialog = AlertDialog.Builder(this)
                .setTitle(getString(R.string.settings_confirm_switch_title))
                .setMessage(getString(R.string.settings_confirm_switch_message, presetLabel))
                .setPositiveButton(getString(R.string.settings_confirm_switch_positive)) { _, _ ->
                    viewModel.confirmPresetOverride()
                    presetConfirmDialog = null
                }
                .setNegativeButton(getString(R.string.settings_confirm_switch_negative)) { _, _ ->
                    viewModel.cancelPresetOverride()
                    presetConfirmDialog = null
                }
                .setOnDismissListener { presetConfirmDialog = null }
                .show()
        } else if (state.confirmPresetId == null) {
            presetConfirmDialog?.dismiss()
            presetConfirmDialog = null
        }

        // After test success, populate model name dropdown with available models
        if (state.availableModels.isNotEmpty()) {
            if (binding.etModelName.adapter == null) {
                val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, state.availableModels)
                binding.etModelName.setAdapter(adapter)
                binding.etModelName.threshold = 1
                binding.etModelName.setOnItemClickListener { parent, _, position, _ ->
                    val selected = parent.getItemAtPosition(position) as String
                    viewModel.onModelNameChanged(selected)
                }
            }
            // 测试刚成功时自动弹出一次下拉，避免反复弹出
            if (!modelsDropdownShown && state.connectionTestResult?.startsWith("✅") == true) {
                binding.etModelName.showDropDown()
                modelsDropdownShown = true
            }
        } else {
            modelsDropdownShown = false
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
