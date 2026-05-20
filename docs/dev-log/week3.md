# Week 3 · 2026-05-20 至 2026-05-26

> 第 3 周：检索 + LLM 桥接 + 总结。**让 LLM 基于文档片段生成内容。**
>
> **调整说明**（2026-05-20）：原计划 4 天（Thu–Sun）节奏偏紧。修订为 6 天（含今天前置 + Day 5 缓冲），Day 1 拆分为检索和 API 两天，设置页顺序后移。
>
> **Day 0-5 全部完成**（2026-05-20）。W3 核心功能闭环：检索 → LLM 流式 API → 单步摘要生成 → 阅读器 UI（浮层打字/复制/停止/错误提示）→ 设置页（地址/模型下拉/API Key/测试连接/恢复默认）。真机 USB + adb reverse 验证通过。Tag: `v0.3.0-summary`。

## 1. 本周目标（来自 ROADMAP）

- [x] `RetrieveChunksUseCase`（余弦相似度 Top-K，K=5）
- [x] `/v1/chat/completions` 端点 + 流式请求/响应 DTO
- [x] SSE / NDJSON 流式解析 → `Flow<String>`
- [x] `SummarizeDocumentUseCase`（单步 prompt，全文/分页两种模式）
- [x] 阅读器顶部按钮："总结本页" / "总结全文"
- [x] 总结结果浮层 + 流式打字效果 + 复制按钮 + 停止 + 错误提示
- [x] 设置页：LLM Base URL、模型名（下拉选择）、可选 API Key、测试连接
- [x] 单元测试：检索排序、摘要生成

---

## 2. Day 0–3 实际完成（2026-05-20）

> D0 前置检查通过后，D1-D3 代码在同一天完成，核心 UseCase 全部就绪。

### Day 0 · 前置检查 ✅

| 检查项 | 结果 |
|--------|------|
| `universal_sentence_encoder.tflite` | ✅ 6.1 MB |
| 基线编译 `assembleDebug` | ✅ 57s |
| 基线测试（42 个 W2） | ✅ 31s |
| LM Studio gemma-3-4b 已加载 | ✅ 端口 1234 |

### Day 1 · 检索引擎 ✅

**新增文件**：
- `domain/model/RetrievalResult.kt` — `data class RetrievalResult(val chunk, val score)`
- `domain/usecase/RetrieveChunksUseCase.kt` — `@Inject`，余弦相似度 Top-K，防御 NaN/零向量/维度不一致
- `test/.../RetrieveChunksUseCaseTest.kt` — 10 case，全绿

**关键实现**：
- `cosineSimilarity(a, b)`：dot / (|a|×|b|)，零向量 → 0，NaN → 0，维度不一致 → 抛异常
- 自动过滤 `embedding == null || isEmpty()` 的 chunk
- Hilt 零改动：`DocumentRepository` + `EmbeddingEngine` 已在图中

### Day 2 · LLM 流式 API ✅

**新增文件**：
- `data/remote/dto/ChatCompletionRequestDto.kt` — 请求 + Message + Chunk + Choice + Delta，5 个 DTO 合一
- `domain/model/ChatMessage.kt` — 纯 Kotlin data class，零 import
- `data/remote/SseStreamParser.kt` — `channelFlow`，SSE → `Flow<String>`，不持有资源
- `test/.../SseStreamParserTest.kt` — 9 case，全绿

**修改文件**：
- `domain/repository/LlmRepository.kt` — 新增 `chatCompletionStream(): Flow<String>`
- `data/remote/repository/LlmRepositoryImpl.kt` — 用原生 OkHttp（非 Retrofit）做流式；注入 `OkHttpClient` + `Moshi`；`callbackFlow + awaitClose` 管理生命周期

**端到端验证**：写临时 `LlmStreamSmokeTest`，调 `localhost:1234/v1/chat/completions`，gemma-3-4b 流式返回 token → 验证通过后删除

**踩坑**：
- `flow { withContext(IO) { emit() } }` → SafeCollector 抛异常（不能跨 context emit）→ 改用 `channelFlow`
- `assertTrue("msg: ${response.body?.string()}", ...)` → body 被消费 → 改用 `if (!isSuccessful)` 分支
- `awaitClose` 需要 `import kotlinx.coroutines.channels.awaitClose`

### Day 3 · MapReduce 摘要 ✅

**新增文件**：
- `domain/model/SummaryScope.kt` — `sealed class { Full, Page(pageIndex) }`
- `domain/prompt/PromptTemplates.kt` — `chunkSummary()` / `mergeSummaries()` / W4 `ragQuery()`
- `domain/usecase/SummarizeDocumentUseCase.kt` — Map（逐 chunk 小结，非流式）+ Reduce（合并，流式 emit）
- `test/.../SummarizeDocumentUseCaseTest.kt` — 6 case，全绿

**Map 阶段**：每个 chunk 调 LLM → `flow.toList().joinToString("")` 收集完整小结；单个失败 → 跳过继续
**Reduce 阶段**：合并 prompt → 直接流式 emit，不经收集

### Day 4 · 阅读器摘要 UI ✅

**修改文件**：
- `ui/reader/ReaderUiState.kt` — `Loaded` 加 `summaryState`，新增 `SummaryState` sealed class（Idle/Loading/Streaming/Done/Error）
- `ui/reader/ReaderViewModel.kt` — 注入 `SummarizeDocumentUseCase`，`summarizePage()` / `summarizeFullDocument()` / `stopSummarizing()`，Job 管理取消
- `ui/reader/ReaderActivity.kt` — 按钮接线 + `BottomSheetDialog` 浮层 + 流式打字 + 复制/停止 + 错误居中提示
- `res/layout/activity_reader.xml` — 底部新增 `reader_summary_bar`（"总结本页" / "总结全文" 两个 TonalButton）
- `res/layout/dialog_summary.xml` — 新建浮层布局（拖拽条 + 标题 + 进度条 + 内容区 + 停止/复制按钮）
- `res/values/strings.xml` — 新增 8 个 reader_summary_* 字符串
- `test/.../ReaderViewModelTest.kt` — 注入 mock `SummarizeDocumentUseCase` 适配新构造函数

**设计决策**：
- 页面总结：直接用 `DocumentRepository.getChunks()` 按 `pageIndex` 取该页所有 chunk，不走语义检索（语义检索"第N页"会从其他页拿结果）
- 单步 prompt（非 MapReduce）：MapReduce 需要 K+1 次 LLM 调用（10-30s），对 gemma-3-4b 延迟过高。改为一次 prompt 把所有 chunk 文本发给 LLM
- 错误提示居中在浮层内，不弹 Toast 关浮层

**真机验证**：USB 连手机 → `adb reverse tcp:1234 tcp:1234` → 全文总结流式输出正常 → 本页总结按页取 chunk 正常 → 无内容时浮层居中提示

### Day 5 · 设置页 ✅

**新增/修改文件**：
- `data/local/SettingsDataStore.kt` — DataStore 持久化 baseUrl / modelName / apiKey
- `ui/settings/SettingsUiState.kt` — 含 availableModels / connectionTesting
- `ui/settings/SettingsViewModel.kt` — 保存/重置/测试连接，测试成功填下拉列表
- `ui/settings/SettingsActivity.kt` — TextWatcher 实时同步输入，下拉框绑定
- `res/layout/activity_settings.xml` — 地址输入（带测试图标）+ 模型下拉（ExposedDropdownMenu）+ API Key + 测试连接/恢复按钮
- `data/remote/repository/LlmRepositoryImpl.kt` — 动态读 baseUrl；新增 `testConnection()`；错误 `throw e` 替代 `channel.close(e)`
- `domain/repository/LlmRepository.kt` — 新增 `testConnection(baseUrl)` 接口
- `ui/reader/ReaderViewModel.kt` — 模型名从 DataStore 动态读取
- `res/layout/activity_library.xml` — 加 MaterialToolbar 承载溢出菜单
- `res/menu/menu_library.xml` — 新建，设置入口
- `AndroidManifest.xml` — 注册 SettingsActivity
- `gradle/libs.versions.toml` + `app/build.gradle.kts` — 新增 datastore-preferences 1.1.1

**设计决策**：
- 模型下拉：测试连接成功后从 `/v1/models` 拿到可用模型列表，填 AutoCompleteTextView 下拉
- 动态 baseUrl：`LlmRepositoryImpl.chatCompletionStream()` 每次从 DataStore 读 baseUrl，不重建 Retrofit
- 错误传播：`callbackFlow` 内 `channel.close(e)` 吞异常 → 改为 `throw e`，确保错误进入浮层

---

---
## 3. Day 4 计划（2026-05-24，周日）— 阅读器摘要 UI

**目标**：在阅读器中能触发摘要并在浮层中实时看到流式打字。

### Phase 1 · 阅读器工具栏加按钮

- **布局改动**：`activity_reader.xml` 的 Toolbar 增加两个按钮（放在现有翻页按钮上方或 menu 中）：
  - `btn_summarize_page` — "总结本页"
  - `btn_summarize_full` — "总结全文"
- **交互约束**：
  - 文档 `IndexStatus != INDEXED` → "总结全文"置灰 + Toast "请等待文档索引完成"
  - 正在生成摘要 → 两个按钮都置灰，停止按钮出现
  - "总结本页"在 pageIndex 有效时始终可用（即使全文未索引完）

### Phase 2 · ReaderViewModel 集成

- **新增依赖**：`SummarizeDocumentUseCase`、`DocumentRepository.observeDocuments()`
- **新增 UiState 字段**：
  ```kotlin
  data class ReaderUiState(
      val document: Document? = null,
      val isLoading: Boolean = true,
      val error: String? = null,
      // W3 新增
      val summaryState: SummaryState = SummaryState.Idle,
      val currentModel: String = "gemma-3-4b",  // Day 6 设置页后切 DataStore
  )
  
  sealed class SummaryState {
      object Idle : SummaryState()
      object Loading : SummaryState()               // Map 阶段（等待中，无 token）
      data class Streaming(val tokens: String) : SummaryState()  // Reduce 阶段（流式输出）
      data class Done(val fullText: String) : SummaryState()
      data class Error(val message: String) : SummaryState()
  }
  ```
- **新增方法**：
  - `summarizePage(pageIndex: Int)` → launch coroutine → 收集 Flow → 更新 `summaryState`
  - `summarizeFullDocument()` → 同上
  - `stopSummarizing()` → 取消当前摘要协程 Job
  - `copySummary()` → ClipboardManager 复制 `fullText`

### Phase 3 · 总结浮层 UI

- **容器**：`BottomSheetDialog`（比 `BottomSheetDialogFragment` 轻量，ViewModel 共用宿主 Activity）
  - 顶部拖拽条 + 标题（"页面摘要" / "全文摘要"）
  - 右上角：停止按钮（Streaming/Loading 态）或关闭按钮（Idle/Done/Error 态）
  - 内容区：`ScrollView` + `TextView`，monospace 字体，动态追加 token
  - 底部：`Button` "复制摘要"（仅在 Done 态可见，`app:icon="@drawable/ic_copy"`）
- **打字效果**：每次 `Flow.emit(token)` → `textView.append(token)` + `scrollView.fullScroll(DOWN)`
- **停止按钮**：`viewModel.stopSummarizing()` → 取消协程 → summaryState 切回 Idle → 浮层保留已生成的部分文字

### Phase 4 · 手动测试流程

1. 导入已索引 PDF → 进入阅读器
2. 点击"总结全文" → 底部浮层滑出
3. Loading 态短暂出现 → Streaming 态流式文字追加
4. 完成后复制按钮出现
5. 点击"总结本页" → 仅当前页摘要
6. 在 Streaming 态点停止 → 摘要中断，浮层保留已生成文字

---

## 4. Day 5 计划（2026-05-25，周一）— 设置页

**目标**：让用户可配置 LLM 地址和模型名，为 W4 问答铺路。

### Phase 1 · DataStore 依赖与实现

- **依赖检查**：`libs.versions.toml` 是否有 `datastore-preferences`，没有则追加
  ```toml
  datastore-preferences = "1.1.1"
  ```
- **`SettingsDataStore`**（`data/local/SettingsDataStore.kt`）：
  ```kotlin
  @Singleton
  class SettingsDataStore @Inject constructor(
      @ApplicationContext private val context: Context
  ) {
      private val Context.dataStore by preferencesDataStore(name = "settings")
      
      val baseUrl: Flow<String>     // 默认 "http://localhost:1234/v1"
      val modelName: Flow<String>   // 默认 "gemma-3-4b"
      val apiKey: Flow<String?>     // 默认 null
      
      suspend fun setBaseUrl(url: String)
      suspend fun setModelName(name: String)
      suspend fun setApiKey(key: String?)
  }
  ```

### Phase 2 · 设置页 UI

- **`SettingsActivity`** + **`SettingsViewModel`**：遵循现有 MVVM 模板
- **布局**：`activity_settings.xml`
  - LLM 服务器地址：`TextInputEditText` + `TextInputLayout`，hint "http://localhost:1234/v1"
  - 模型名称：`TextInputEditText` + 下拉候选（调用 `GetAvailableModelsUseCase` 填充）
  - API Key（可选）：`TextInputEditText` + 密码可见切换
  - 保存按钮：Toolbar menu item
  - 恢复默认：Toolbar menu item
- **入口**：`LibraryActivity` 的 Toolbar overflow menu 加"设置"

### Phase 3 · NetworkModule 适配（方案 A）

- 问题：Retrofit 实例创建后 baseUrl 不可变
- **方案 A**：`@Singleton` 级别重建
  - `SettingsDataStore.baseUrl.first()` 作为 Retrofit 构建参数
  - 设置保存后：废弃旧 Retrofit 实例，新建
  - 风险：保存瞬间有进行中的请求 → 极低（设置几乎不动）
- **不做方案 B**（包装类动态创建）：W3 时间优先，方案 A 先闭环

### Phase 4 · 回溯适配

- `ReaderViewModel.currentModel` 从硬编码改为 `SettingsDataStore.modelName.first()`
- 设置保存后，下次进入阅读器自动使用新模型名

### Phase 5 · 手动验证

- 设置页启动 → 修改 Base URL 为错误地址 → 保存 → 进入阅读器 → 点总结 → Error 态提示连接失败
- 恢复正确地址 → 再次总结 → 正常

---

## 5. Day 6 计划（2026-05-26，周二）— 缓冲日

**目标**：吸收前 5 天的溢出，不强塞新功能。

### 按优先级使用缓冲时间

1. **修 bug**：前两天发现的问题
2. **补测试**：覆盖率不足的模块
3. **W3 验收标准逐条走通**：
   - [ ] 导入已索引 PDF → 进入阅读器 → 点击"总结全文" → 底部浮层滑出 → 流式输出中文摘要
   - [ ] "总结本页"按钮对当前页生成摘要
   - [ ] 复制按钮可用，Snackbar 提示"已复制"
   - [ ] 停止按钮中途打断摘要生成
   - [ ] 设置页修改 Base URL 和模型名 → 摘要使用新配置
   - [ ] `RetrieveChunksUseCase` 单元测试通过
   - [ ] `SummarizeDocumentUseCase` MapReduce 合并单元测试通过
4. **如果全部顺利**：写 `week3.md` 日志（实际完成、踩坑、决策记录）
5. **不打 tag 不合并**：W3 验收走通后再打 `v0.3.0-summary`

---

## 6. 依赖关系

```
Day 0 (前置) ──▶ Day 1 (检索) ──▶ Day 2 (API) ──▶ Day 3 (MapReduce) ──▶ Day 4 (UI)
                                                                           ▶ Day 5 (设置页)
                                                                           ▶ Day 6 (缓冲)
```

- Day 1 的 `RetrieveChunksUseCase` 是 Day 3 的硬依赖
- Day 2 的 `chatCompletionStream()` 是 Day 3 的硬依赖
- Day 3 的 `SummarizeDocumentUseCase` 是 Day 4 的硬依赖
- Day 5 设置页技术上独立（可在 Day 4 之前做），但建议在 UI 闭环后再做——先让功能跑通再加配置

---

## 7. 风险管理

| 风险 | 概率 | 影响 | 缓解措施 |
|------|------|------|----------|
| MediaPipe 模型文件缺失 | 中 | Day 1 阻塞 | Day 0 前置检查第一项 |
| SSE 解析器 `callbackFlow` 生命周期 bug | 高 | Day 2-3 延迟 | Day 2 Phase 4 写足单测覆盖取消/异常场景 |
| MapReduce prompt 质量差 | 中 | 验收体验差 | Day 3 用真实 PDF 快速验证 prompt，必要时 Day 6 调优 |
| Retrofit baseUrl 重建并发问题 | 低 | Day 5 | 设置几乎不动，影响面极小 |
| LM Studio 响应慢或超时 | 中 | 整体体验 | 60s 超时已配置；若首 token > 10s 考虑切更小模型 |
| 某天超时未完成 | 中 | 后面积压 | Day 6 缓冲日吸收；极端情况设置页砍到仅 UI 壳 |

### 逃生舱口（如进度严重不及预期）

1. **Day 5 设置页砍到最小**：只做 DataStore 读写 + 硬编码默认值，不做 UI（W4 补）
2. **MapReduce 简化为单步**：不先逐 chunk 小结，直接把 Top-5 chunk 文本 + "请总结" 发给 LLM（效果差但能跑）
3. **停止按钮砍掉**：W4 聊天 UI 再做（当前只做完成态复制）

---

## 8. 决策记录（待填充）

### 决策 20：（Day 1 时补充：为什么 RetrievalResult 放 domain/model 而非 data/dto）

### 决策 21：（Day 2 时补充：为什么流式不走 Retrofit 而用原生 OkHttp）

### 决策 22：（Day 3 时补充：MapReduce 的 Map 阶段是否流式——以及为什么不是）

### 决策 23：（Day 5 时补充：Retrofit baseUrl 重建选方案 A 还是 B）

---

## 9. W3 验收标准

- [ ] 导入已索引 PDF → 进入阅读器 → 点击"总结全文" → 底部浮层滑出 → 流式输出中文摘要
- [ ] "总结本页"按钮对当前页生成摘要
- [ ] 复制按钮可用，Snackbar 提示"已复制"
- [ ] 停止按钮中途打断摘要生成
- [ ] 设置页修改 Base URL 和模型名 → 摘要使用新配置
- [ ] `RetrieveChunksUseCase` 单元测试通过
- [ ] `SummarizeDocumentUseCase` MapReduce 合并单元测试通过
- [ ] 所有 W2 测试仍绿（42 个）

**Tag**：`v0.3.0-summary`
