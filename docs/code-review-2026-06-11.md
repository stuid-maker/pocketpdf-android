# PocketPDF 项目审查报告

> 审查日期：2026-06-11 · 审查方式：只读源码审查（未运行构建）
> 审查范围：构建配置、应用入口、核心页面、数据/状态管理、网络与 AI 调用、权限与安全、性能、测试与发布

---

## A. 项目概览

* **项目类型**：Android 原生应用（单模块 `app`）
* **技术栈**：Kotlin 2.0 + Jetpack Compose (M3) + Hilt + Room + WorkManager + DataStore + Retrofit/OkHttp(SSE) + Moshi + PdfBox-Android + Pdfium + MediaPipe TextEmbedder + Timber
* **主要功能**：导入 PDF → 切块 + 端侧向量化（RAG 索引）→ Pdfium 渲染阅读 → 连接 OpenAI 兼容 LLM（默认本地 LM Studio，可切 DeepSeek/通义）做问答、页面/全文 Map-Reduce 摘要、引用页码跳转
* **当前成熟度判断**：工程化程度较高（分层清晰、约 70 个测试文件、CI、Room schema 导出、API Key Keystore 加密），处于 W4「问答+打磨」阶段；但存在一个会让 AI 主链路整体不可用的关键缺陷（见 B-1），距离可发布还有距离
* **实际查看过的关键文件**：
  * 构建/配置：`app/build.gradle.kts`、`gradle/libs.versions.toml`、`app/proguard-rules.pro`、`AndroidManifest.xml`、`network_security_config.xml`、`backup_rules.xml`、`.github/workflows/ci.yml`、`README.md`
  * 入口/UI：`PocketPdfApp.kt`、`ReaderActivity.kt`、`ReaderScreen.kt`、`ReaderController.kt`、`ReaderViewModel.kt`、`PdfPageView.kt`、`ChatActivity.kt`、`ChatViewModel.kt`、`SettingsViewModel.kt`、`SettingsUiState.kt`、`DiagnosticsViewModel.kt`、`GenerationProgressEstimator.kt`
  * 数据/网络/AI：`NetworkModule.kt`、`LlmApi.kt`、`LlmRepositoryImpl.kt`、`SseStreamParser.kt`、`ChatCompletionRequestDto.kt`、`SettingsDataStore.kt`、`ApiKeyCipher.kt`、`SummaryCacheStore.kt`、`DocumentRepositoryImpl.kt`、`InternalFileStorage.kt`、`AppDatabase.kt`、`IndexWorker.kt`、`MediaPipeEmbeddingEngine.kt`、`PdfiumDocumentEngine.kt`、`PdfiumDocumentSession.kt`
  * Domain：`PromptTemplates.kt`、`AskDocumentUseCase.kt`、`SummarizeDocumentUseCase.kt`、`FullDocumentSummarizer.kt`、`QueryIntentRouter.kt`、`RetrieveChunksUseCase.kt`、`ImportDocumentUseCase.kt`

---

## B. 高风险问题

### 问题 1：嵌入模型 `.tflite` 文件缺失，索引→问答→摘要整条 AI 链路不可用

* **风险等级**：Critical
* **涉及文件**：`app/src/main/java/com/asuka/pocketpdf/data/embedding/MediaPipeEmbeddingEngine.kt`、`.gitignore`、`data/indexing/IndexWorker.kt`、`README.md`
* **具体位置**：`MediaPipeEmbeddingEngine.kt` 第 34 行 `setModelAssetPath("models/universal_sentence_encoder.tflite")`；初始化失败被第 43-47 行的 catch 吞掉
* **问题说明**：`.gitignore` 第 85 行忽略了 `*.tflite`，当前工作区 `app/src/main/assets/` 为空（已确认全仓库无任何 `.tflite`）。模型缺失时初始化异常被吞掉，`getEmbeddings()` 抛 `IllegalStateException("TextEmbedder not initialized")` → `IndexWorker.doWork()` 第 99 行失败 → 文档永远标记 `FAILED`，且 `replaceChunks` 从未执行 → 数据库无 chunk → TOP_K 问答返回"未找到内容"、全文摘要抛 `NoChunksException`。下载步骤只写在 `docs/dev-log/week2.md`，README 的 Quick Start 完全没提。
* **可能后果**：任何按 README 克隆构建的人（包括 CI 出的包）拿到的是 AI 功能全废的 App，且错误表现为"文档未索引"，极难排查。
* **修改建议**：① README 增加模型下载步骤；② 增加 Gradle 任务校验/自动下载模型（带 SHA 校验，挂 `preBuild`），缺失时构建期报错；③ `MediaPipeEmbeddingEngine` 初始化失败时向上抛出带明确文案的异常，并让 Library 页对 `FAILED` 状态给出可读原因。
* **是否需要确认后再改**：是（涉及构建脚本与产品文案）

### 问题 2：`/v1/models` 永远请求硬编码的 `localhost:1234`，无视用户设置的服务器地址

* **风险等级**：High
* **涉及文件**：`di/NetworkModule.kt`、`data/remote/LlmApi.kt`、`ui/diagnostics/DiagnosticsViewModel.kt`
* **具体位置**：`NetworkModule.kt` 第 30 行 `BASE_URL = "http://localhost:1234/"`（Retrofit baseUrl 固定）；`LlmApi.kt` 第 18 行 `@GET("v1/models")`
* **问题说明**：`LlmRepositoryImpl.listModels()` 走 Retrofit，baseUrl 硬编码。「连接诊断」页（`DiagnosticsViewModel.runDiagnostics()` → `GetAvailableModelsUseCase`）因此永远只诊断本机端口。用户切到 DeepSeek/通义预设后，诊断页会误报"模型服务失败"（或在 adb reverse 存在时误报成功）。设置页的 `testConnection(baseUrl)`（`LlmRepositoryImpl.kt` 第 136-161 行）倒是正确地手动拼了 URL——同一仓库里两套行为不一致。
* **可能后果**：云端用户诊断结果完全失真；误导用户反复检查网络。
* **修改建议**：删除 Retrofit 的 `listModels`，统一改为像 `testConnection` 一样从 `SettingsDataStore.baseUrl` 动态构造请求（或用 Interceptor 动态改写 host）。
* **是否需要确认后再改**：否

### 问题 3：SSE 解析只捕获 `IOException`，畸形 chunk 会让整条回答流崩溃

* **风险等级**：High
* **涉及文件**：`data/remote/SseStreamParser.kt`
* **具体位置**：第 64-76 行 `try { chunkAdapter.fromJson(json) ... } catch (e: IOException)`
* **问题说明**：Moshi 对类型不匹配抛的是 `JsonDataException`（`RuntimeException` 子类），不会被 `catch (IOException)` 捕获。LM Studio / 各类兼容后端的 SSE 偶发非标准 chunk（如 error 事件、keep-alive 变体）时，异常直穿 Flow，用户看到整次生成失败。Map-Reduce 中某一批失败会让整个全文摘要作废。
* **可能后果**：AI 输出明显不稳定，尤其换后端时。
* **修改建议**：catch 扩为 `Exception`（保留 `CancellationException` 重抛），单 chunk 解析失败仅记日志跳过，并补一条畸形 chunk 单测。
* **是否需要确认后再改**：否

### 问题 4：「停止生成」不会取消底层 HTTP 流，本地/云端模型继续空转

* **风险等级**：Medium
* **涉及文件**：`data/remote/repository/LlmRepositoryImpl.kt`
* **具体位置**：第 95-133 行：`okHttpClient.newCall(request).execute()` 的 `Call` 句柄没有保存；`awaitClose` 里只打日志
* **问题说明**：`source.readUtf8Line()` 是阻塞 IO，协程取消无法中断它，只能等下一个 token 到达或 60s read timeout。用户点「停止」后，服务端依然在完整生成（云端按 token 计费照扣；本地 LM Studio 被占用，下一次请求排队）。
* **可能后果**：成本浪费、取消后立即重问会变慢甚至超时。
* **修改建议**：保存 `Call` 引用，在 `awaitClose { call.cancel() }` 中显式取消（OkHttp 的 cancel 可中断阻塞读）。
* **是否需要确认后再改**：否

### 问题 5：自定义局域网地址（http://192.168.x.x）会被网络安全配置静默拦截

* **风险等级**：Medium
* **涉及文件**：`res/xml/network_security_config.xml`、`ui/settings/SettingsUiState.kt`
* **具体位置**：`network_security_config.xml` 仅放行 `localhost / 127.0.0.1 / 10.0.2.2` 的明文流量；`SettingsUiState.kt` 第 47 行 custom 预设提示「请输入 LLM 服务器地址（如 http://xxx:1234/v1）」
* **问题说明**：用户按提示填 PC 的局域网 IP（不走 adb reverse 的最常见场景）会得到 `CLEARTEXT communication not permitted`，错误文案对普通用户不可理解，且与 hint 自相矛盾。
* **可能后果**：核心配置流程对一大类用户不可用。
* **修改建议**：要么对用户输入的私有网段地址给出明确报错引导（推荐），要么有限度放行 RFC1918 网段明文（需权衡上架审核），并修正 hint 文案。
* **是否需要确认后再改**：是（涉及安全策略取舍）

### 问题 6：Release 无签名配置、无崩溃监控、版本号未管理

* **风险等级**：Medium（发布阻塞项）
* **涉及文件**：`app/build.gradle.kts`（第 13-31 行：`versionCode 1`、`buildTypes.release` 无 `signingConfig`）；全仓库无 Crashlytics/Sentry 依赖
* **问题说明/后果**：当前 release 包无法直接对外分发；线上崩溃无法感知。
* **修改建议**：补 signingConfig（密钥走 `local.properties`/环境变量）、引入崩溃监控、把 versionCode/Name 接入 CI tag。
* **是否需要确认后再改**：是（需要签名密钥与监控平台选择）

---

## C. 架构问题

* **分层总体清晰**：ui → domain ← data，UseCase 粒度合理，Repository 接口在 domain、实现挂 Hilt，符合 README 的声明。
* **分层违规一处**：`domain/usecase/SearchDocumentUseCase.kt` 第 5 行直接 `import com.asuka.pocketpdf.data.pdf.PdfTextExtractor`，domain 反向依赖 data，破坏了"domain 可纯 JVM 单测"的承诺。`PdfTextExtractor` 接口应上移到 domain。
* **测试钩子侵入生产类**：`FullDocumentSummarizer` 用 `internal var batchCharBudget` + `companion.testInstance()`（第 49-79 行）替代构造注入，是为绕 Hilt 而做的妥协，建议改为 `@Provides` 提供配置对象。
* **ChatViewModel 本地消息与 DB 流合并逻辑脆弱**（`ChatViewModel.kt` 第 74-81 行）：靠「内容+角色相等」去重，用户连续发两条相同文本时会出现短暂吞消息/闪烁；建议给消息加客户端生成的 UUID 并入库。
* **死代码**：`OnnxEmbeddingEngine.kt` 是空文件；`proguard-rules.pro` 第 238 行还在 keep 已不存在的 `ui.ping.PingActivity`。
* **文档与实现脱节**：README 声称 UI 为 "XML + ViewBinding"、embedding 为 "MiniLM-L6-v2"，实际是 Compose + Universal Sentence Encoder；`viewBinding = true` 在 `app/build.gradle.kts` 仍开着但 UI 已全 Compose。
* **可扩展性**：单模块下包结构良好，继续扩展没问题；向量检索是全表线性扫描（见 F），文档量大后需要引入向量索引方案。

## D. 代码质量问题

* **命名/职责**：整体良好，KDoc 注释质量高（中文注释解释了决策动机，难得）。
* **未使用参数**：`PromptTemplates.batchSummary(batchIndex, totalBatches, pageRange, ...)`（第 111-122 行）三个参数完全没用，签名撒谎。
* **重复代码**：`ChatViewModel.sendMessage()` 的 `CancellationException` / `Exception` / 正常完成三个分支几乎相同的 state 更新（第 156-190 行）可提取；`ReaderScreen.onLongPress` 内两段坐标换算逻辑高度重复（第 148-210 行，60+ 行 lambda 应提取成函数）。
* **资源释放**：`PdfPageView` 的 `velocityTracker = VelocityTracker.obtain()`（第 57 行）从不 `recycle()`，View 销毁即泄漏一个 native tracker。`PdfiumDocumentSession.close()` 用 `runBlocking` 在主线程等待渲染锁（第 143-152 行），极端情况下 `onDestroy` 会卡顿。
* **硬编码**：`ChatActivity.kt` 第 288、296 行 `"复制"`、`"重新生成"` 为硬编码中文（同文件其他文案都走 `stringResource`）；Manifest 中 `android:label="连接诊断"` 等同样硬编码（无国际化打算则可接受）。
* **失效 UI 入口**：`ChatBubble` 的「重新生成」菜单项调用 `onRegenerate`，但 `ChatScreen` 调用 `ChatBubble(message, documentId)` 时从未传入（默认 null）→ 点击无任何反应。
* **格式**：`gradle/libs.versions.toml` 第 87-99、134-136 行混入 Tab 缩进，TOML 虽合法但很容易在合并时出错。
* **错误处理**：总体规范（`CancellationException` 全链路正确重抛，值得肯定）；`ChatViewModel` 把 `e.message` 直接展示给用户（第 182 行），HTTP 错误时会是整段 JSON。

## E. 安全与隐私问题

**做得好的部分（已明确确认）**：

* API Key 用 Android Keystore AES/GCM 加密存储（`ApiKeyCipher.kt`），旧明文自动迁移加密（`SettingsDataStore.kt` 第 36-39 行）；输入框有 `PasswordVisualTransformation` 掩码；全仓库无硬编码密钥（已 grep 验证，测试里的 `sk-my-secret-key` 是假值）。
* `allowBackup=false` + `backup_rules.xml`/`data_extraction_rules.xml` 全量排除，云备份不会带走文档和密钥。
* 权限最小化：仅 `INTERNET`，文件导入走 SAF，无存储权限。
* 明文流量仅放行 localhost（反面影响见 B-5）。

**需要改进**：

* `NetworkModule.provideHttpLoggingInterceptor` 未调用 `redactHeader("Authorization")`——debug 包 logcat 会打印完整 Bearer Key 和文档内容请求体。虽限 debug，建议加 redact。
* 文档内容会被发送到用户配置的云端 LLM（DeepSeek/通义预设），App 内没有任何隐私提示文案；上架需要隐私政策与数据出境说明。
* 聊天记录、摘要缓存（含文档内容衍生物）明文落盘（Room / DataStore），威胁模型上可接受，但若主打"隐私阅读"卖点可考虑加密。
* `SummaryCacheStore.systemPromptHash` 是自制 31 进制散列截 32 位（第 105-113 行），碰撞会导致不同 system prompt 命中同一条缓存——概率低、后果轻（错误摘要），可换 SHA-256。

## F. 性能问题

* **取消不释放连接**：见 B-4，是当前最实际的性能/成本问题。
* **向量检索线性扫描**：`RetrieveChunksUseCase` 每次问答把该文档所有 chunk（含 embedding blob）从 Room 全量读入内存再算余弦（第 38-52 行）。百页文档可接受，千页级会显著拖慢首 token 时间。中期可上 sqlite-vec 或量化+分片。
* **意图分类额外往返**：模糊问题先打一次 LLM 分类（`QueryIntentRouter` Stage 2），且请求未设 `maxTokens`（`ChatCompletionRequestDto.maxTokens` 从未被赋值）——分类这种单词级输出应限制 `max_tokens≈8`，否则碰到啰嗦模型既慢又费钱。
* **每 token 全量重建字符串/列表**：`ReaderViewModel` 每个 token 调 `accumulated.toString()`（O(n²) 拷贝，第 122-130 行）；`ChatViewModel` 每个 token `map` 整个消息列表（第 147-155 行）。长回答（几千 token）时会带来可观 GC 压力，建议节流（如 50ms 批量刷 UI）。
* **导入时双重解析**：导入即用 `extractPagesText` 全文提取只为拿页数（`DocumentRepositoryImpl.kt` 第 68-70 行），IndexWorker 又提取一遍。拿页数用 Pdfium `pageCount` 即可，大 PDF 导入可快一个数量级。
* **渲染**：单页按需渲染 2x 宽 ARGB_8888（最大 2400px，单页位图约 20-30MB），旧页正确 `recycle`，无泄漏；但无相邻页预渲染，翻页必有白屏等待，体验型优化点。
* **启动**：`PDFBoxResourceLoader.init` 在 `Application.onCreate` 主线程执行，PdfBox 资源加载不算轻，可延迟到首次使用旧标注路径时。

## G. UI/UX 问题

* **做得好**：Map-Reduce 进度条 + 阶段文案 + ETA（`GenerationProgressEstimator`）、停止按钮、错误条带重试、空聊天提示、摘要可后台收起（`SummaryStatusBar`）——这部分超出平均水平。
* **「重新生成」菜单无效**：见 D，用户长按 AI 消息点重新生成毫无反应，比没有更糟。
* **索引失败不可解释**：模型缺失/嵌入失败时文档卡在 `FAILED`，需要确认 Library 页是否向用户解释原因与重试入口（未读 `LibraryScreen.kt` 全文，需要更多上下文确认）。
* **新用户引导缺失**：首次使用需要 PC 装 LM Studio + `adb reverse`，App 内没有任何引导（README 才有）；诊断页存在但藏在设置里。「本地文档与索引」检查项是写死的 `Passed`（`DiagnosticsViewModel.idleState()`），并非真实检查——这是假诊断，建议要么实现要么删掉。
* **ReaderAiSheet 文案重复**：`ReaderScreen.kt` 第 624-638 行连续两个 `headlineSmall`（"理解这一页" + 状态文案），Generating 时 stageLabel 又在第 651 行再显示一次。
* **多轮对话名不符实**：聊天页历史会显示，但发给模型的只有当前问题（见 H）。用户追问"它呢？"会得到答非所问。

## H. AI 功能专项审查

* **Prompt 管理**：集中在 `PromptTemplates`，可维护性好；系统 Prompt 用户可配置且全链路传递，分层正确。
* **上下文长度**：12,000 字符/批的保守预算 + 递归 Reduce + 不收敛检测（`NoConvergenceException`），设计扎实。
* **多轮上下文缺失**：`AskDocumentUseCase` 只发 system + 当前问题，从不携带对话历史（`ChatRepository` 存了历史却不用）。这是功能性缺陷。
* **超时**：仅靠 OkHttp 60s read timeout；Map-Reduce 数十次调用无整体超时/熔断，最坏情况用户盯进度条几十分钟。建议每次调用包 `withTimeout` 并给全流程上限。
* **空回复**：处理完善（空批重试简化 prompt、`AllBatchesEmptyException`、`EmptyFinalSummaryException`），好评。
* **格式错误**：意图分类对非法输出回退 TOP_K，安全；但 SSE 畸形 chunk 会崩流（B-3）。
* **幻觉控制**：ragQuery 明确"仅根据文档内容回答 + 如实说明缺失"，并要求 `[第N页]` 引用；但引用页码未对 `pageCount` 做范围校验，模型编造 `[第999页]` 会生成跳转到越界页的链接（`ReaderController.renderNow` 有 coerceIn 兜底，不会崩，但体验怪异）。
* **流式/取消/重试**：流式 ✔；取消 UI 层 ✔ 但底层连接不断（B-4）；错误后一键重试 ✔。
* **隐私**：本地模型默认值合理；切云端无提示（见 E）。
* **成本控制**：`maxTokens` 字段存在但从未使用；缓存（版本化 key，含 model/sysPrompt/算法版本）做得很好，是有效的成本控制。

## I. 测试与发布风险

* **单元测试**：约 60 个 JVM 测试文件，覆盖 domain/usecase/parser/cache/ViewModel，质量投入明显。
* **UI/仪器测试**：11 个 androidTest（Compose UI、Room 迁移、Pdfium parity），但 CI 不跑仪器测试。
* **CI**：`ci.yml` 跑 lint + unit test，无 release 构建验证——意味着 **R8 打包从未在 CI 验证过**，而 `proguard-rules.pro` 是几乎"keep 一切"的规则（keep 了 okhttp/coroutines/compose/kotlin.reflect 全量），混淆和瘦身基本失效，但至少不易崩。建议 CI 加 `assembleRelease`。
* **发布阻塞**：无签名配置、无崩溃监控、`versionCode=1` 无管理（B-6）；无隐私政策（云端 LLM 数据传输必须声明，否则商店审核风险真实存在）。
* **日志**：Timber 仅 debug 种树，release 静默，正确。

## J. 修复路线图

### 第一阶段：立即修复

| # | 任务 | 原因 | 涉及文件 | 难度 | 建议做法 |
|---|------|------|---------|------|---------|
| 1 | 恢复嵌入模型供给链 | B-1，AI 主链路对新环境 100% 不可用 | `README.md`、`app/build.gradle.kts`、`MediaPipeEmbeddingEngine.kt`、`IndexWorker.kt` | 中 | Gradle `downloadEmbeddingModel` 任务（带 SHA 校验）挂 `preBuild`；引擎初始化失败抛带用户文案的专用异常 |
| 2 | `listModels` 改用设置中的 baseUrl | B-2，诊断结果失真 | `LlmRepositoryImpl.kt`、`NetworkModule.kt`、`LlmApi.kt` | 低 | 复用 `testConnection` 的手动 OkHttp 构造，删掉 Retrofit 接口或加动态 host Interceptor |
| 3 | SSE 解析容错扩展到 `Exception` | B-3，畸形 chunk 崩整条回答 | `SseStreamParser.kt`（+畸形 chunk 单测） | 低 | catch `Exception`，重抛 `CancellationException`，跳过坏 chunk |
| 4 | `awaitClose { call.cancel() }` 真正取消流 | B-4，成本与本地模型占用 | `LlmRepositoryImpl.kt` | 低 | 保存 `Call` 引用并在 awaitClose 取消 |
| 5 | 修复「重新生成」菜单（接通或暂时移除） | 可见的死按钮，比缺功能更伤信任 | `ChatActivity.kt`、`ChatViewModel.kt` | 低 | 接 `viewModel.retry()` 或先移除菜单项 |

### 第二阶段：上线前修复

| 任务 | 原因 | 涉及文件 | 难度 | 建议做法 |
|------|------|---------|------|---------|
| 云端隐私确认弹窗 + 隐私政策链接 | 合规/审核 | `SettingsScreen.kt`、新增文案 | 低 | 首次切云端预设时弹窗确认数据外发 |
| release 签名 + 崩溃监控 + CI 加 `assembleRelease` | 发布工程 | `app/build.gradle.kts`、`ci.yml` | 中 | 密钥走环境变量；Crashlytics 或 Sentry |
| LAN 地址明文策略 | B-5 | `network_security_config.xml`、`SettingsUiState.kt` | 低 | 私有网段输入给明确引导或受控放行，修正 hint |
| LLM 调用超时与整体熔断 | 防无限等待 | `FullDocumentSummarizer.kt` | 中 | 每次调用 `withTimeout` + 全程上限 |
| 索引失败可解释 + 重试入口 | FAILED/NEEDS_OCR 用户不可理解 | Library 相关文件 | 中 | 状态 badge 加原因文案与重试按钮 |
| 意图分类设 `max_tokens`；logging 加 `redactHeader("Authorization")` | 成本/安全小项 | `QueryIntentRouter.kt`、`NetworkModule.kt` | 低 | — |

### 第三阶段：后续优化

| 任务 | 原因 | 涉及文件 | 难度 | 建议做法 |
|------|------|---------|------|---------|
| 多轮对话上下文 | 追问答非所问 | `AskDocumentUseCase.kt`、`ChatViewModel.kt` | 中 | 最近 N 轮历史纳入 messages 并做长度裁剪 |
| 架构清理 | 分层违规/死代码/文档脱节 | `SearchDocumentUseCase.kt`、`OnnxEmbeddingEngine.kt`、`proguard-rules.pro`、`README.md` | 低 | `PdfTextExtractor` 接口上移 domain；删空文件与 PingActivity keep；同步文档 |
| 性能优化包 | 导入速度/流式 UI/翻页体验 | `DocumentRepositoryImpl.kt`、两个 ViewModel、`ReaderController.kt`、`PdfPageView.kt` | 中 | Pdfium pageCount 替代全文提取；token 节流刷新；相邻页预渲染；`VelocityTracker.recycle()` |
| R8 规则瘦身 + CI 验证 release 包 | 包体与混淆失效 | `proguard-rules.pro`、`ci.yml` | 中 | 逐组删 keep 并用 release 包回归 |
| 向量检索升级 | 千页级文档检索慢 | `RetrieveChunksUseCase.kt`、`ChunkDao.kt` | 高 | sqlite-vec / 量化 + 分片余弦 |

---

*报告基于对上述列出文件的逐行阅读，未运行构建或设备测试；行号对应审查时的工作区状态（含未提交改动）。*
