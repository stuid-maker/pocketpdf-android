# Code Review Remediation Design — 审阅意见

> 审阅文档：`2026-06-12-code-review-remediation-design.md`
> 审阅日期：2026-06-12
> 审阅人：Hermes Agent (deepseek-v4-pro)

> 处理状态（2026-06-13）：审阅中的阻塞项已在实现前纳入设计，6 个 finding 均已完成并通过最终项目验收。本文保留为设计审阅历史，结果见 `docs/project-audit-2026-06-13.md`。

---

## 总体评价

文档结构清晰，6 个修复点的方案方向基本正确。但与现有代码的对照暴露了若干遗漏和需要细化的地方。

**总体评估：可实施，但建议在开工前解决以下阻塞问题：**
1. Finding 4 — 配置优先级顺序（阻塞级）
2. 补充各发现的当前行为基线（重要）
3. 明确 Finding 6 的 UI 修改路径和 `retry()` 行为变更（重要）

---

## 逐项分析

### Finding 1：HTTP 取消 (LlmRepositoryImpl)

**设计正确，但描述不够精确。**

#### 当前行为

现有 `LlmRepositoryImpl.chatCompletionStream()` 使用 `callbackFlow` + `awaitClose`：

```kotlin
// 当前代码结构（简化）
): Flow<String> = callbackFlow {
    try {
        response = withContext(Dispatchers.IO) { call!!.execute() }  // 同步阻塞
        // SSE 解析...
        channel.close()
    } catch (e: CancellationException) {
        response?.close()
        throw e
    }

    awaitClose { call?.cancel() }  // ← 这行在 execute() 阻塞期间永远不会到达
}
```

**Bug 根因：** `awaitClose` 位于 `callbackFlow` lambda 末尾。当 collector 取消时，协程在 `withContext(Dispatchers.IO)` 处抛出 `CancellationException`，被 line 121 的 catch 捕获并 re-throw，`awaitClose` 从未被到达——`call?.cancel()` 永远不会执行。结果：OkHttp 的 `execute()` 阻塞在 socket read 上，直到 OS 级别的 socket timeout 才断开。

#### 设计点评

设计文档提出的方案——将 HTTP 调用放入 `callbackFlow` 内的子协程——是正确的：

```kotlin
// 修复后结构
): Flow<String> = callbackFlow {
    val workerJob = launch(Dispatchers.IO) {
        // HTTP 调用 + SSE 解析
        channel.close()
    }
    awaitClose {
        call?.cancel()
        response?.close()
        workerJob.cancel()
    }
}
```

这样 `awaitClose` 在注册后立即可以被触发，不再等待 `execute()` 完成。

#### 需要补充

1. **catch 块语义：** 修复后，`awaitClose` 触发 → `call?.cancel()` → `execute()` 抛 `IOException`。内层 catch 应区分「正常取消（`awaitClose` 触发）」和「异常取消（协程被外部取消）」
2. **`CancellationException` 传播：** 确保子协程取消时正确关闭 channel，避免 `trySend` 失败导致静默丢 token

---

### Finding 2：嵌入模型供应

**方案合理，但缺实现细节。**

#### 当前状态

- 依赖 `mediapipe.tasks.text` 已引入（`build.gradle.kts` line 149）
- 模型文件当前如何管理、是否在 `.gitignore` 中——设计文档未说明现状基线

#### 设计点评

- Gradle task 做 SHA-256 校验 + 原子替换 → 方向正确
- "fails with a clear remediation message" → 好的 UX 设计
- "Android asset merge tasks will depend on model preparation" → 正确利用 Gradle 依赖链

#### 需要补充

1. **"pinned checksum" 存放在哪里？**
   - 建议：硬编码在 Gradle 脚本中（如 `val EMBEDDING_MODEL_SHA256 = "abc123..."`），并用注释标注来源 URL，方便审计和更新

2. **"atomically replaces the destination" 在 Windows 上如何实现？**
   - `File.renameTo()` 跨卷可能失败
   - 应明确使用 `java.nio.file.Files.move(src, dst, StandardCopyOption.ATOMIC_MOVE)` + 跨卷回退 `REPLACE_EXISTING`

3. **下载 URL 硬编码后如何维护？**
   - 模型更新 → 需要改代码 + 重新编译。是否应做成 Gradle property 可配置？
   - 权衡：可配置性 vs 可靠性（硬编码更可审计）

4. **缺少当前状态基线：**
   - 模型文件当前在哪个目录？
   - `.gitignore` 是否已排除？
   - CI 干净 checkout 目前是否真的会静默产生 AI-broken APK？

---

### Finding 3：对话历史中的重复问题

**设计正确。现有代码中的误导性注释必须一并修复。**

#### 当前行为

`ChatViewModel.sendMessage()` 的执行顺序是「先保存，后快照」：

```
line 131: chatRepository.saveMessage(...)         // (1) 保存用户问题
line 135: chatRepository.getHistorySnapshot(...)   // (2) 获取历史快照（包含 (1) 的消息）
line 140: askDocument(history=history, question=text)  // (3) history + 当前问题
```

在 `AskDocumentUseCase` 中 (line 65-77)：

```kotlin
val messages = buildList {
    add(systemPrompt)
    trimmedHistory.forEach { add(it) }    // history 包含刚保存的用户问题
    add(ChatMessage(ROLE_USER, prompt))   // 当前问题又被追加一次 → 重复！
}
```

**更具迷惑性的是**，line 128-129 的注释写道：

> "Save user message first, then snapshot — avoids race where current question appears twice in the context"

这个注释是错误的——当前顺序正是导致重复的原因，而非避免重复。

#### 设计点评

设计文档的「先快照后保存」是唯一正确的修复：

```kotlin
val history = chatRepository.getHistorySnapshot(documentId)  // 快照先于保存
chatRepository.saveMessage(documentId, ...)                    // 保存后于快照
```

#### 需要补充

1. **错误注释必须删除或重写**（line 128-129）
2. **并发场景验证：** 如果保存失败但 UI 已显示用户消息，用户体验如何？当前代码 `saveMessage` 在 try-catch 中，失败不阻塞生成——修复后逻辑不变，但需确认快照+保存的时序在失败路径上不会导致状态不一致
3. **`getHistorySnapshot` 返回顺序：** 确认消息按时间升序，`trimHistory` 的 `takeLast(8)` 能正确保留最近 N 轮对话

---

### Finding 4：Sentry 配置

**设计有缺陷——配置优先级顺序需要调整。**

#### 当前行为

`build.gradle.kts` line 23：

```kotlin
buildConfigField("String", "SENTRY_DSN", "\"${findProperty("SENTRY_DSN") ?: ""}\"")
```

只支持 Gradle 属性（`-PSENTRY_DSN=xxx` 或 `gradle.properties`）。环境变量和 `local.properties` 完全未被读取。

#### 设计点评

文档提出的优先级：**Gradle property > Environment variable > local.properties**

**这个顺序存在问题：**

- CI 通常通过环境变量注入 `SENTRY_DSN`
- 开发者可能在 `gradle.properties` 中有残留测试值
- 按此优先级，CI 的环境变量会被 `gradle.properties` 覆盖 → 生产构建使用错误的 DSN

**建议优先级：Environment variable > Gradle property > local.properties**

#### 需要补充

1. **优先级调整**（阻塞级）
2. **签名配置是否共用 loader？** 当前签名只读 `local.properties` (line 28-48)。如果统一成同一个 loader，签名也会受环境变量影响。CI 的 keystore 不应通过环境变量传递（安全风险）。建议签名保持仅读文件，Sentry 用统一 loader
3. **`local.properties` 的 key 命名约定：** `SENTRY_DSN`、`sentry.dsn` 还是 `sentryDsn`？需统一明确
4. **fallback 行为：** 所有来源均未配置时，`BuildConfig.SENTRY_DSN` 应为空字符串（当前如此，确认保持）

---

### Finding 5：超时语义

**设计正确。现有代码清晰展示了 bug。**

#### 当前行为

```kotlin
// collectWithTimeout (line 378-394)
private fun collectWithTimeout(...): Flow<String> {
    return flow {
        try {
            withTimeout(PER_CALL_TIMEOUT_SECONDS * 1000L) {
                stream.collect { emit(it) }
            }
        } catch (e: TimeoutCancellationException) {
            throw e  // ← 抛出 TimeoutCancellationException
        }
    }
}

// summarize (line 127-141)
try {
    withTimeout(OVERALL_TIMEOUT_SECONDS * 1000L) {
        // 内部调用 collectWithTimeout ...
    }
} catch (e: TimeoutCancellationException) {
    throw OverallTimeoutException(...)  // ← 错误：per-call timeout 也被包装
}
```

因为 `TimeoutCancellationException` 是 `CancellationException` 子类，per-call timeout 沿调用栈传播时被外层 `catch` 统一捕获并错误包装为 `OverallTimeoutException`。

#### 设计点评

创建专用 `PerCallTimeoutException`（继承 `Exception`，非 `CancellationException`）是正确的方案。外层只捕获自己 `withTimeout` 产生的 `TimeoutCancellationException`。

#### 需要补充

1. **Per-call timeout 后的资源清理：** `TimeoutCancellationException` 在 `collectWithTimeout` 内触发时会取消内部协程 → 触发 `LlmRepositoryImpl` 的 `awaitClose` → HTTP 资源被清理。转换为 `PerCallTimeoutException` 后，这个清理路径是否仍能正确执行？需要验证
2. **异常类型定义位置：** `PerCallTimeoutException` 应定义在 `FullDocumentSummarizer.kt` 中（与 `OverallTimeoutException` 并列），保持一致性
3. **injectable timeout 的实现路径：** 设计文档提到"injectable timeout durations for testing"。当前用 companion object 常量。可改为构造函数参数（默认值 = 原常量），Hilt 注入默认值，测试传入更小值

---

### Finding 6：重新生成

**设计正确，但缺 UI 层实现路径。**

#### 当前行为

```kotlin
// ChatViewModel
private var lastQuestion: String = ""  // 全局可变字段

fun retry() {
    if (lastQuestion.isBlank()) return
    val q = lastQuestion
    lastQuestion = ""
    _uiState.update { it.copy(inputText = q, error = null) }
    sendMessage()
}
```

如果用户对第 3 轮 AI 回复点「重新生成」，但 `lastQuestion` 已被第 5 轮问题覆盖，会用错误的问题重新生成。

#### 设计点评

方案路径：UI 回调携带选中消息 ID → ViewModel 通过 ID 找到前一个 USER 消息 → 用该消息的问题重新生成。方向正确。

#### 需要补充

1. **UI 层修改路径不明确：**
   - 哪个 Composable 持有菜单回调？需要具体文件名和修改点
   - `ChatDisplayMessage` 的 ID 字段是否在 Compose UI 层可访问？
   - 菜单回调如何传递 `messageId`？

2. **`retry()` 签名变更：**
   - 当前 `retry()` 无参数，修复后应为 `retry(assistantMessageId: Long)`
   - 需全局搜索所有调用点，确保无一遗漏

3. **行为语义澄清：**
   - 设计文档说 "preserves the existing history, appends that user question as a new turn"
   - 这是追加新回复（旧回复保留），而非替换旧回复？
   - 如果确认是追加，UI 需要显示两条针对同一问题的回复，是否符合同一轮对话的 UX 预期？建议与产品确认

4. **边界情况：**
   - 选中消息没有匹配的 USER 消息（已被删除/DB 异常）→ 设计文档说"ignored"，但 UI 是否需要 toast 提示用户？

---

## 遗漏和弱项

### 1. 缺少当前状态基线

设计文档假设读者完全了解现有代码，但没有在任何地方总结「当前代码在 6 个发现点上的实际行为」。建议在每个发现点下增加 **Current Behavior** 小节。

### 2. 缺少修复依赖关系

6 个修复点可能冲突：

| 修复点 | 涉及文件 | 可能冲突 |
|--------|---------|---------|
| 1. HTTP 取消 | `LlmRepositoryImpl` | 与 Finding 5 共用同一类的 `callbackFlow` |
| 3. 历史去重 | `ChatViewModel`, `AskDocumentUseCase` | 独立 |
| 5. 超时语义 | `FullDocumentSummarizer`, `LlmRepositoryImpl` | 与 Finding 1 共用 `LlmRepositoryImpl` |
| 6. 重新生成 | `ChatViewModel`, UI Composable | 独立 |

建议实施顺序：**1 → 5 → 3 → 6 → 4 → 2**，前两项涉及最深的协程/资源管理改动，优先处理可避免后续 rebase 冲突。

### 3. 缺少回归风险分析

- **Finding 3（先快照后保存）：** 如果保存失败，用户消息丢失但已进入 UI → 刷新后消息消失
- **Finding 6（regenerate 按 ID）：** 如果 DB 中找不到对应 USER message → fallback 到 `lastQuestion`？还是直接 no-op？文档说"ignored"但用户无感知

### 4. 测试策略偏乐观

> "Gradle task verification for download behavior **where feasible**"

"where feasible" 自由度太大。建议：
- 可行（单元测试）：校验 checksum 比对逻辑、temp file 原子替换逻辑
- 不可行（下载集成测试）：至少验证任务在无网络时的错误信息是否清晰

### 5. Embedding 模型的可维护性

模型 URL 和 SHA-256 硬编码后，模型更新需要改代码。建议设计时考虑：
- checksum 存储在单独的 `.sha256` 文件中（改文件不改代码）
- 或通过 Gradle property 覆盖默认 URL/checksum

### 6. 没有回滚方案

每个修复应为独立 commit，确保 `git revert` 可单独回滚。避免「一个大 commit 修 6 个问题」。

---

## 总结表

| 发现 | 设计质量 | 主要问题 | 严重度 |
|------|---------|--------|--------|
| **1. HTTP 取消** | 正确 | 缺少子协程与 catch 块交互细节 | 中 |
| **2. 嵌入模型** | 合理 | 缺 checksum 位置、Windows 原子替换、现状基线 | 中 |
| **3. 历史去重** | 正确 | 注释需同步修改；并发耐久性需验证 | 低 |
| **4. Sentry 配置** | **有缺陷** | **优先级顺序应改为 Env > Gradle > local.properties**；签名是否共用 loader | **高** |
| **5. 超时语义** | 正确 | 需确认 per-call timeout 后的 HTTP 清理路径 | 中 |
| **6. 重新生成** | 正确 | 缺 UI 层路径；retry 签名变更需全量搜索 | 中 |

| 维度 | 评价 |
|------|------|
| **整体结构** | 清晰，Goal → Architecture → Error Handling → Testing → Scope 层次分明 |
| **可行性** | 所有方案技术上可行，与现有代码结构兼容 |
| **完整性** | 缺少现状基线、依赖关系、回滚方案；Finding 4 优先级有误 |
| **测试覆盖** | JVM 测试 + MockWebServer + Gradle 验证 + APK 检查 + 设备 smoke test 五层覆盖 |
| **Scope 控制** | "不重新设计 chat storage、不加加密持久化、不加相邻页渲染"——边界清晰 |
