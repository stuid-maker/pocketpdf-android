package com.asuka.pocketpdf.ui.ping

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asuka.pocketpdf.core.Result
import com.asuka.pocketpdf.domain.usecase.GetAvailableModelsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * Ping 自检界面的 ViewModel。
 *
 * 持有两条流：
 * - [uiState]：StateFlow，承载持久状态（按钮可点 / 列表内容），界面重建后还能拿到
 * - [oneShotEvents]：Channel，承载只想消费一次的事件（Toast / Snackbar），避免旋屏后重弹
 *
 * 这套"持久状态 + 一次性事件"双流模式是 Week 1 起聊天/阅读页的复用模板，
 * 因此 Week 0 demo 就提前打好。
 */
@HiltViewModel
class PingViewModel @Inject constructor(
    private val getAvailableModels: GetAvailableModelsUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow<PingUiState>(PingUiState.Idle)
    val uiState: StateFlow<PingUiState> = _uiState.asStateFlow()

    private val _oneShotEvents = Channel<PingEvent>(Channel.BUFFERED)
    val oneShotEvents = _oneShotEvents.receiveAsFlow()

    fun onPingClicked() {
        if (_uiState.value is PingUiState.Loading) return
        viewModelScope.launch {
            _uiState.value = PingUiState.Loading
            when (val result = getAvailableModels()) {
                is Result.Success -> {
                    _uiState.value = PingUiState.Success(result.data)
                    val firstId = result.data.firstOrNull()?.id ?: "(empty)"
                    Timber.tag(TAG).i("ping success, %d model(s), first=%s", result.data.size, firstId)
                    _oneShotEvents.send(PingEvent.ShowToast(firstId))
                }
                is Result.Failure -> {
                    val msg = result.error.message ?: result.error.javaClass.simpleName
                    Timber.tag(TAG).e(result.error, "ping failed: %s", msg)
                    _uiState.value = PingUiState.Error(msg)
                    _oneShotEvents.send(PingEvent.ShowToast("Ping failed: $msg"))
                }
            }
        }
    }

    private companion object {
        const val TAG = "PingViewModel"
    }
}

sealed class PingEvent {
    data class ShowToast(val message: String) : PingEvent()
}
