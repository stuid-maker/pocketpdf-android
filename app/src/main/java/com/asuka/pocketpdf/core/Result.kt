package com.asuka.pocketpdf.core

import kotlinx.coroutines.CancellationException

/**
 * 全工程统一的结果包装器。
 *
 * 选自定义 sealed class 而非 kotlin.Result 的原因：
 * 1. 在 suspend / Flow 场景下 kotlin.Result 作为返回值有 inline class 限制，写起来别扭
 * 2. 自定义类型能在 when 分支里强制穷尽（无 else 分支），编译期保证错误处理
 * 3. 后续可平滑扩展（如加 Loading 态），不破坏调用方
 *
 * 约定：data / domain / ui 层之间传递异常路径一律用 [Result]，不抛裸异常。
 */
sealed class Result<out T> {
    data class Success<T>(val data: T) : Result<T>()
    data class Failure(val error: Throwable) : Result<Nothing>()
}

/** 把一段可能抛异常的代码块包成 [Result]。常用于 Repository 实现里包网络/磁盘调用。 */
inline fun <T> resultOf(block: () -> T): Result<T> = try {
    Result.Success(block())
} catch (t: CancellationException) {
    // Cancellation is coroutine control flow; callers upstream must see it.
    throw t
} catch (t: Throwable) {
    Result.Failure(t)
}
