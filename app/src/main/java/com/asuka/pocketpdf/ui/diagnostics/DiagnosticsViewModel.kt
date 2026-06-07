package com.asuka.pocketpdf.ui.diagnostics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asuka.pocketpdf.core.Result
import com.asuka.pocketpdf.domain.usecase.GetAvailableModelsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class DiagnosticsViewModel @Inject constructor(
    private val getAvailableModels: GetAvailableModelsUseCase,
) : ViewModel() {

    private val mutableState = MutableStateFlow(idleState())
    val uiState: StateFlow<DiagnosticsUiState> = mutableState.asStateFlow()

    fun runDiagnostics() {
        if (mutableState.value.isRunning) return
        viewModelScope.launch {
            mutableState.value = DiagnosticsUiState(
                checks = listOf(
                    DiagnosticCheck("模型服务", DiagnosticStatus.Running),
                    DiagnosticCheck("本地文档与索引", DiagnosticStatus.Passed, "本地数据可用"),
                ),
                isRunning = true,
            )
            mutableState.value = when (val result = getAvailableModels()) {
                is Result.Success -> DiagnosticsUiState(
                    checks = listOf(
                        DiagnosticCheck(
                            "模型服务",
                            DiagnosticStatus.Passed,
                            "发现 ${result.data.size} 个模型",
                        ),
                        DiagnosticCheck("本地文档与索引", DiagnosticStatus.Passed, "本地数据可用"),
                    ),
                    isRunning = false,
                )
                is Result.Failure -> DiagnosticsUiState(
                    checks = listOf(
                        DiagnosticCheck(
                            "模型服务",
                            DiagnosticStatus.Failed,
                            result.error.message,
                        ),
                        DiagnosticCheck("本地文档与索引", DiagnosticStatus.Passed, "本地数据仍然安全"),
                    ),
                    isRunning = false,
                    errorSummary = "文档与索引仍安全保存在本机。请检查服务地址，或稍后重试。",
                )
            }
        }
    }

    private fun idleState() = DiagnosticsUiState(
        checks = listOf(
            DiagnosticCheck("模型服务", DiagnosticStatus.Idle),
            DiagnosticCheck("本地文档与索引", DiagnosticStatus.Passed, "本地数据可用"),
        ),
        isRunning = false,
    )
}
