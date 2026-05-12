package com.asuka.pocketpdf.ui.ping

import com.asuka.pocketpdf.domain.model.LlmModel

/**
 * Ping 自检界面的状态机。
 *
 * 四态足够覆盖 Week 0 的演示：未触发 / 加载中 / 成功 / 失败。
 * Week 3 起会拆出更通用的 `LoadingUiState<T>` 在阅读/聊天页复用。
 */
sealed class PingUiState {
    data object Idle : PingUiState()
    data object Loading : PingUiState()
    data class Success(val models: List<LlmModel>) : PingUiState()
    data class Error(val message: String) : PingUiState()
}
