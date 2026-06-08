package com.asuka.pocketpdf.ui.diagnostics

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.asuka.pocketpdf.ui.components.PocketCompactButton
import com.asuka.pocketpdf.ui.theme.LocalPocketColors
import com.asuka.pocketpdf.ui.theme.PocketRadii
import com.asuka.pocketpdf.ui.theme.PocketSpacing

enum class DiagnosticStatus { Idle, Running, Passed, Failed }

data class DiagnosticCheck(
    val label: String,
    val status: DiagnosticStatus,
    val detail: String? = null,
)

data class DiagnosticsUiState(
    val checks: List<DiagnosticCheck>,
    val isRunning: Boolean,
    val errorSummary: String? = null,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(
    state: DiagnosticsUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val colors = LocalPocketColors.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        colors.paper,
                        colors.workspace,
                        colors.workspace,
                    ),
                ),
            ),
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                DiagnosticsHeader(onBack)
            },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .navigationBarsPadding()
                    .padding(PocketSpacing.Xl),
                verticalArrangement = Arrangement.spacedBy(PocketSpacing.Xxl),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(PocketSpacing.Lg)) {
                    Text(
                        text = if (state.errorSummary == null) "检查 PocketPDF 的连接状态" else "暂时无法连接模型服务",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.ink,
                    )
                    Text(
                        text = state.errorSummary
                            ?: "验证模型服务是否可用，并确认本地文档能力保持正常。",
                        color = colors.mutedInk,
                        style = MaterialTheme.typography.bodyMedium,
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
                                colors.paper,
                                RoundedCornerShape(PocketRadii.Card),
                            )
                            .border(
                                1.dp,
                                colors.crystalBorder,
                                RoundedCornerShape(PocketRadii.Card),
                            )
                            .padding(PocketSpacing.Lg),
                        verticalArrangement = Arrangement.spacedBy(PocketSpacing.Lg),
                    ) {
                        state.checks.forEach { check ->
                            DiagnosticRow(check)
                        }
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(PocketSpacing.Sm),
                ) {
                    PocketCompactButton(
                        text = if (state.isRunning) "检测中" else "重新检测",
                        onClick = onRetry,
                        enabled = !state.isRunning,
                    )
                    TextButton(onClick = onOpenSettings) {
                        Text("检查服务设置")
                    }
                }
            }
        }
    }
}

@Composable
private fun DiagnosticsHeader(onBack: () -> Unit) {
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
            shape = RoundedCornerShape(18.dp),
            color = colors.paper.copy(alpha = .58f),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                colors.crystalBorder,
            ),
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "返回",
                tint = colors.ink,
                modifier = Modifier.padding(8.dp),
            )
        }
        Text(
            text = "连接诊断",
            modifier = Modifier.weight(1f).padding(start = PocketSpacing.Md),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = colors.ink,
        )
    }
}

@Composable
private fun DiagnosticRow(check: DiagnosticCheck) {
    val colors = LocalPocketColors.current
    val statusText = when (check.status) {
        DiagnosticStatus.Idle -> "等待"
        DiagnosticStatus.Running -> "检查中"
        DiagnosticStatus.Passed -> "正常"
        DiagnosticStatus.Failed -> "无响应"
    }
    val statusColor = when (check.status) {
        DiagnosticStatus.Passed -> colors.success
        DiagnosticStatus.Failed -> MaterialTheme.colorScheme.error
        else -> colors.mutedInk
    }
    Row(Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            Text(check.label, style = MaterialTheme.typography.titleSmall)
            check.detail?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = colors.mutedInk)
            }
        }
        Text(statusText, color = statusColor, style = MaterialTheme.typography.labelMedium)
    }
}
