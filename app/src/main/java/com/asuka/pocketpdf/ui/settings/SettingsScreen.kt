package com.asuka.pocketpdf.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.asuka.pocketpdf.ui.components.PocketCompactButton
import com.asuka.pocketpdf.ui.theme.LocalPocketColors
import com.asuka.pocketpdf.ui.theme.PocketRadii
import com.asuka.pocketpdf.ui.theme.PocketSpacing

private enum class SettingsEditor { BaseUrl, Model, ApiKey, Prompt }

data class SettingsActions(
    val onPresetSelected: (String) -> Unit,
    val onBaseUrlChanged: (String) -> Unit,
    val onModelNameChanged: (String) -> Unit,
    val onApiKeyChanged: (String) -> Unit,
    val onChunkingChanged: (String) -> Unit,
    val onSystemPromptChanged: (String) -> Unit,
    val onConfirmPreset: () -> Unit,
    val onCancelPreset: () -> Unit,
    val onResetDefaults: () -> Unit,
    val onTestConnection: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    actions: SettingsActions,
) {
    val colors = LocalPocketColors.current
    var editor by remember { mutableStateOf<SettingsEditor?>(null) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFFFDFBFE),
                        colors.workspace,
                        Color(0xFFF0E8F5),
                    ),
                ),
            ),
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                SettingsHeader(
                    isSaving = state.isSaving,
                    onBack = onBack,
                    onSave = onSave,
                )
            },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(horizontal = PocketSpacing.Xl, vertical = PocketSpacing.Md),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                SettingsSection("AI 服务") {
                    SettingsRow(
                        title = "模型服务",
                        supporting = "DeepSeek / 通义千问 / LM Studio",
                        value = MODEL_PRESETS.find { it.id == state.selectedPreset }?.label ?: "自定义",
                        onClick = {},
                    )
                    SettingsRow("服务地址", state.baseUrl, "编辑") { editor = SettingsEditor.BaseUrl }
                    SettingsRow("模型名称", state.modelName.ifBlank { "尚未选择" }, "编辑") {
                        editor = SettingsEditor.Model
                    }
                    SettingsRow(
                        "API Key",
                        if (state.apiKey.isBlank()) "未设置" else "••••••••",
                        "编辑",
                    ) { editor = SettingsEditor.ApiKey }
                    SettingsRow(
                        title = "连接状态",
                        supporting = state.connectionTestResult ?: "尚未检测",
                        value = if (state.connectionTesting) "检测中" else "测试",
                        onClick = actions.onTestConnection,
                    )
                }
                SettingsSection("文档理解") {
                    SettingsRow(
                        title = "切块策略",
                        supporting = "影响索引粒度与问答上下文",
                        value = if (state.chunkingStrategy == "paragraph") "按段落" else "滑动窗口",
                        onClick = {
                            actions.onChunkingChanged(
                                if (state.chunkingStrategy == "paragraph") "sliding_window" else "paragraph",
                            )
                        },
                    )
                    SettingsRow(
                        title = "系统提示词",
                        supporting = state.systemPrompt.ifBlank { "默认" },
                        value = "编辑",
                        onClick = { editor = SettingsEditor.Prompt },
                    )
                }
                SettingsSection("维护") {
                    SettingsRow("连接诊断", "检查模型服务与本地状态", "运行检测") {
                        onOpenDiagnostics()
                    }
                    SettingsRow("恢复默认设置", "清除当前自定义连接配置", "恢复") {
                        actions.onResetDefaults()
                    }
                }
                state.error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
                if (state.saveSuccess) {
                    Text("设置已保存", color = colors.success)
                }
            }
        }
    }

    editor?.let { selected ->
        val (title, value, onChanged, password) = when (selected) {
            SettingsEditor.BaseUrl -> EditorSpec("服务地址", state.baseUrl, actions.onBaseUrlChanged)
            SettingsEditor.Model -> EditorSpec("模型名称", state.modelName, actions.onModelNameChanged)
            SettingsEditor.ApiKey -> EditorSpec("API Key", state.apiKey, actions.onApiKeyChanged, true)
            SettingsEditor.Prompt -> EditorSpec("系统提示词", state.systemPrompt, actions.onSystemPromptChanged)
        }
        SettingsTextDialog(
            title = title,
            initialValue = value,
            password = password,
            onConfirm = {
                onChanged(it)
                editor = null
            },
            onDismiss = { editor = null },
        )
    }

    if (state.confirmPresetId != null) {
        AlertDialog(
            onDismissRequest = actions.onCancelPreset,
            title = { Text("确认切换预设") },
            text = { Text("切换预设会覆盖当前服务地址。") },
            confirmButton = {
                TextButton(onClick = actions.onConfirmPreset) { Text("确认切换") }
            },
            dismissButton = {
                TextButton(onClick = actions.onCancelPreset) { Text("取消") }
            },
        )
    }
}

@Composable
private fun SettingsHeader(
    isSaving: Boolean,
    onBack: () -> Unit,
    onSave: () -> Unit,
) {
    val colors = LocalPocketColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = PocketSpacing.Xl)
            .padding(top = PocketSpacing.Sm, bottom = PocketSpacing.Md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            onClick = onBack,
            modifier = Modifier.size(36.dp),
            shape = RoundedCornerShape(18.dp),
            color = Color.White.copy(alpha = .58f),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = .74f)),
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "返回",
                tint = colors.ink,
                modifier = Modifier.padding(8.dp),
            )
        }
        Text(
            text = "设置",
            modifier = Modifier.weight(1f).padding(start = PocketSpacing.Md),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = colors.ink,
        )
        PocketCompactButton(
            text = if (isSaving) "保存中" else "保存",
            onClick = onSave,
            enabled = !isSaving,
        )
    }
}

private data class EditorSpec(
    val title: String,
    val value: String,
    val onChanged: (String) -> Unit,
    val password: Boolean = false,
)

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LocalPocketColors.current
    Column(verticalArrangement = Arrangement.spacedBy(PocketSpacing.Sm)) {
        Text(
            title,
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = PocketSpacing.Xs),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = 3.dp,
                    shape = RoundedCornerShape(PocketRadii.Card),
                    ambientColor = Color(0x10302739),
                    spotColor = Color(0x10302739),
                )
                .background(
                    Color(0xFFFEFCFF),
                    RoundedCornerShape(PocketRadii.Card),
                )
                .border(
                    1.dp,
                    Color.White.copy(alpha = .88f),
                    RoundedCornerShape(PocketRadii.Card),
                )
                .padding(horizontal = PocketSpacing.Lg),
            content = content,
        )
    }
}

@Composable
private fun SettingsRow(
    title: String,
    supporting: String,
    value: String,
    onClick: () -> Unit,
) {
    val colors = LocalPocketColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = colors.ink)
            Text(
                supporting,
                style = MaterialTheme.typography.bodySmall,
                color = colors.mutedInk,
                maxLines = 2,
            )
        }
        Text(value, style = MaterialTheme.typography.labelMedium, color = colors.mutedInk)
    }
}

@Composable
private fun SettingsTextDialog(
    title: String,
    initialValue: String,
    password: Boolean,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var value by remember(initialValue) { mutableStateOf(initialValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
                minLines = if (title == "系统提示词") 4 else 1,
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(value) }) { Text("完成") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
