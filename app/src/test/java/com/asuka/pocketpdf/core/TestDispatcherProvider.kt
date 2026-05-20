package com.asuka.pocketpdf.core

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher

/**
 * 单测专用 [DispatcherProvider]：三个 dispatcher 全指向 [UnconfinedTestDispatcher]，
 * 让 `withContext(dispatchers.io)` 在测试线程同步执行，不需 advance scheduler。
 *
 * 放在 `test/core/` 而非 `main/core/`：仅测试代码可见，主代码不会误依赖。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TestDispatcherProvider : DispatcherProvider {
    private val dispatcher = UnconfinedTestDispatcher()
    override val main = dispatcher
    override val io = dispatcher
    override val default = dispatcher
}
