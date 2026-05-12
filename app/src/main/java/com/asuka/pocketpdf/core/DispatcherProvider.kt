package com.asuka.pocketpdf.core

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * 协程调度器抽象。
 *
 * 不直接在业务代码里写 [Dispatchers.IO] / [Dispatchers.Default] 的原因：
 * 1. 单元测试时可以注入 TestDispatcher，无需 Main / IO 真实线程
 * 2. 一处统一切换调度策略（如未来引入限并发 Dispatcher）
 *
 * 通过 Hilt 注入。默认实现见 [DefaultDispatcherProvider]，测试用例可自行实现。
 */
interface DispatcherProvider {
    val main: CoroutineDispatcher
    val io: CoroutineDispatcher
    val default: CoroutineDispatcher
}

class DefaultDispatcherProvider : DispatcherProvider {
    override val main: CoroutineDispatcher = Dispatchers.Main
    override val io: CoroutineDispatcher = Dispatchers.IO
    override val default: CoroutineDispatcher = Dispatchers.Default
}
