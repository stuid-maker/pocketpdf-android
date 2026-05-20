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
import androidx.core.view.isVisible
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

        binding.etBaseUrl.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { viewModel.onBaseUrlChanged(s?.toString() ?: "") }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
        binding.etModelName.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { viewModel.onModelNameChanged(s?.toString() ?: "") }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
        binding.etApiKey.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { viewModel.onApiKeyChanged(s?.toString() ?: "") }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.uiState.collect { render(it) } }
            }
        }
    }

    private var fieldsPopulated = false

    private fun render(state: SettingsUiState) {
        // Populate fields once on first load
        if (!fieldsPopulated && state.baseUrl.isNotEmpty()) {
            binding.etBaseUrl.setText(state.baseUrl)
            binding.etModelName.setText(state.modelName)
            binding.etApiKey.setText(state.apiKey)
            fieldsPopulated = true
        }

        binding.btnSettingsSave.isEnabled = !state.isSaving

        state.error?.let {
            Toast.makeText(this, it, Toast.LENGTH_LONG).show()
        }

        if (state.saveSuccess) {
            Toast.makeText(this, "已保存", Toast.LENGTH_SHORT).show()
        }

        binding.btnTestConnection.isEnabled = !state.connectionTesting
        binding.btnTestConnection.text = if (state.connectionTesting) "测试中…" else "测试连接"

        state.connectionTestResult?.let { result ->
            Toast.makeText(this, result, Toast.LENGTH_LONG).show()
            binding.btnTestConnection.text = if (result.startsWith("✅")) "✅ 已连接" else "❌ 失败"
        }

        // 可用模型列表更新后设置下拉
        if (state.availableModels.isNotEmpty()) {
            val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, state.availableModels)
            (binding.etModelName as? android.widget.AutoCompleteTextView)?.setAdapter(adapter)
        }
    }

    private fun applyWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
    }
}
