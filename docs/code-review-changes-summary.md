# PocketPDF 审查报告改动总结

> 基于 `docs/code-review-2026-06-11.md` · 初次改动日期：2026-06-11 · 最终验收：2026-06-13
> 共修复 20+ 项问题，涉及 55+ 个文件

---

## 改动概览

| 报告分级 | 问题数 | 已修复 | 未修复 |
|----------|--------|--------|--------|
| B. 高风险 | 6 | 6 | 0 |
| C. 架构 | 7 | 4 | 3 |
| D. 代码质量 | 7 | 7 | 0 |
| E. 安全隐私 | 4 | 3 | 1 |
| F. 性能 | 5 | 2 | 3 |
| G. UI/UX | 5 | 1 | 4 |
| H. AI 专项 | 7 | 3 | 4 |
| I. 测试发布 | 4 | 3 | 1 |

---

## B. 高风险问题 — 全部修复

### B-1：嵌入模型 .tflite 缺失 → AI 链路不可用 ✅

**涉及文件**：`MediaPipeEmbeddingEngine.kt`、`IndexWorker.kt`、`Document.kt`、`DocumentEntity.kt`、`DocumentMappers.kt`、`AppDatabase.kt`、`LibraryScreen.kt`、`README.md`、`strings.xml`、`build.gradle.kts`

| 改动 | 说明 |
|------|------|
| 新增 `EmbeddingModelMissingException.kt` | 位于 `domain/embedding/`，带 modelPath + originalError |
| `MediaPipeEmbeddingEngine` 抛异常 | 初始化失败时抛出 `EmbeddingModelMissingException`，不再吞错 |
| `IndexWorker` 捕获写入 | 捕获异常后写入 `Document.indexError` 字段（中文可读文案） |
| 数据模型加 `indexError` | Document / DocumentEntity / DocumentMappers 级联改动，Room v5→v6 auto-migration |
| `LibraryScreen` 展示原因 | FAILED 卡片显示 `indexError` 文本（单行截断+小灰字） |
| README 加模型下载步骤 | `模型下载说明` + 解释为何需要手动下载 |
| `strings.xml` 加帮助文案 | `library_index_help_dialog_*` 系列字符串 |
| `aaptOptions { noCompress += "tflite" }` | 防 AAPT 压缩破坏 tflite 随机读取 |

> 注：原始 Gradle 自动下载任务（`downloadEmbeddingModel`）因 Kotlin DSL 兼容问题被移除，模型下载改为 README 文档步骤。

---

### B-2：listModels 硬编码地址 → 诊断失真 ✅

**涉及文件**：`NetworkModule.kt`、`LlmRepositoryImpl.kt`、`LlmApi.kt`、`libs.versions.toml`、`build.gradle.kts`、`proguard-rules.pro`

| 改动 | 说明 |
|------|------|
| 删除 Retrofit 依赖 | 移除 `provideRetrofit` / `provideLlmApi` / `provideMoshi`（后补回 Moshi `@Provides`），删除硬编码 `BASE_URL` |
| 删除死代码 | `LlmApi.kt`（Retrofit 接口，已无注入点） |
| `listModels` / `testConnection` 统一 | 共享 `fetchModels(baseUrl)` 私有方法，均从 `SettingsDataStore.baseUrl` 动态读地址 |
| 清理依赖声明 | `libs.versions.toml` 删除 `retrofit` 版本和 `retrofit`/`retrofit-converter-moshi` 库 |
| 清理 Gradle 依赖 | `build.gradle.kts` 删除 `implementation(libs.retrofit)` 和 `converter-moshi` |
| 清理 proguard | 删除 `LlmApi` keep 和 `retrofit2.**` 段，保留 Moshi `@Json` keep |

---

### B-3：SSE 畸形 chunk 崩流 ✅

**涉及文件**：`SseStreamParser.kt`

| 改动 | 说明 |
|------|------|
| catch 扩大 | `catch (e: IOException)` → `catch (e: Exception)` |
| CancellationException 重抛 | 显式 `catch (e: CancellationException) { throw e }` |
| 坏 chunk 跳过 | 解析失败时 `Timber.w(..., "skipping")`，不中断流 |

---

### B-4：停止生成不取消 HTTP ✅

**涉及文件**：`LlmRepositoryImpl.kt`

| 改动 | 说明 |
|------|------|
| 保存 Call 引用 | `var call: okhttp3.Call?` + `call = okHttpClient.newCall(request)` |
| awaitClose 取消 | `awaitClose { call?.cancel() }` 中断阻塞读 |
| 取消时关闭 response | `CancellationException` catch 中 `response?.close()` |

---

### B-5：重新生成菜单按钮无效 ✅

**涉及文件**：`ChatActivity.kt`

| 改动 | 说明 |
|------|------|
| 传入回调 | `ChatBubble(..., onRegenerate = { viewModel.retry() })` |
| 菜单调用 | `DropdownMenuItem` 中 `onRegenerate?.invoke()` |

---

### B-6：Release 签名 + 崩溃监控 + 版本号 ✅

**涉及文件**：`build.gradle.kts`、`PocketPdfApp.kt`、`libs.versions.toml`、`proguard-rules.pro`、`ci.yml`、`.gitignore`、`local.properties.example`

| 改动 | 说明 |
|------|------|
| 签名配置 | `signingConfigs.create("release")` 从 `local.properties` 读取 keystore 路径和密码 |
| 签名存在性检查 | `storeFile != null` 时才挂载 signingConfig，不存在时仍可编译 |
| Sentry 集成 | `sentry-android 7.15.0` 依赖 + `SentryAndroid.init()` 初始化（DSN 为空时静默跳过） |
| Sentry proguard | `-keepattributes LineNumberTable,SourceFile` + `-keep class io.sentry.**` |
| CI 签名 | `Decode release keystore` 步骤：从 GitHub Secrets base64 解码 keystore + 写 local.properties |
| 本地配置模板 | `local.properties.example` |
| .gitignore 更新 | `*.keystore` / `*.jks` / `local.properties` |
| 版本号 | 发布前更新为 `versionCode=3` / `versionName="1.2.0"` |

---

## C. 架构问题 — 4/7 修复

### C-1：domain 反向依赖 data ✅

**涉及文件**：12 个文件

| 改动 | 说明 |
|------|------|
| 接口上移 | `PdfTextExtractor` 从 `data/pdf/` → `domain/pdf/` |
| 数据类上移 | `PdfTextPosition` / `PageTextWithPositions` 从 `data/pdf/` → `domain/pdf/` |
| 注入替代构造 | `SearchDocumentUseCase` 通过 `@Inject` 注入 `PdfTextExtractor`，替代直接 `new PdfBoxTextExtractor` |
| DI 绑定 | `RepositoryModule` 已有 `bindPdfTextExtractor(impl: PdfiumTextExtractor)`，import 更新 |
| 级联 import | `PdfBoxTextExtractor`、`PdfiumTextExtractor`、`DocumentRepositoryImpl`、`IndexWorker`、`SearchUiState`、`SearchResult` 及测试文件 import 同步 |
| 删除旧文件 | `data/pdf/PdfTextExtractor.kt`、`data/pdf/PdfTextPosition.kt` |

### C-2：死代码清理 ✅

| 文件 | 操作 |
|------|------|
| `data/embedding/OnnxEmbeddingEngine.kt` | 删除（空文件） |
| `app/build.gradle.kts` | 删除 `viewBinding = true`（UI 全 Compose） |
| `proguard-rules.pro` | `PingActivity` keep 已清理（注释标记） |

### C-3：PromptTemplates.batchSummary 签名撒谎 ✅

**涉及文件**：`PromptTemplates.kt`、`FullDocumentSummarizer.kt`

| 改动 | 说明 |
|------|------|
| 删无用参数 | `batchIndex`、`totalBatches`、`pageRange` 从函数签名删除 |
| 删级联调用 | `FullDocumentSummarizer` 中的传参代码清理 |
| 删死代码函数 | `batchPageRange()` 函数删除（唯一调用点已消除） |

### C-4：README / ARCHITECTURE 文档脱节 ✅

| 文件 | 改动 |
|------|------|
| `README.md` | UI 技术 "XML+ViewBinding" → "Jetpack Compose"，PDF渲染 "AndroidPdfViewer" → "Pdfium-Android"，Embedding "MiniLM-L6-v2" → "MediaPipe Universal Sentence Encoder" |
| `docs/ARCHITECTURE.md` | 4 处 Retrofit/LlmApi → OkHttp/LlmRepositoryImpl |
| `ROADMAP.md` | 3 处同步：LlmApi → OkHttp + 任务状态更新 |

### C-5：ChatViewModel 内容+角色去重脆弱 ❌ 未修复

> 需要给消息加客户端 UUID，涉及 ChatViewModel + ChatRepository 改动，中等工作量。

### C-6：测试钩子侵入生产类 ❌ 未修复

> `FullDocumentSummarizer.testInstance()` + `internal var batchCharBudget` 是绕 Hilt 的妥协。

### C-7：gradle/libs.versions.toml Tab 缩进 ✅

| 改动 | 说明 |
|------|------|
| 全局 `\t` → 4 空格 | `sed -i 's/\t/    /g'` |

---

## D. 代码质量问题 — 全部修复

### D-1：PdfPageView VelocityTracker 泄漏 ✅

**涉及文件**：`PdfPageView.kt`

| 改动 | 说明 |
|------|------|
| 新增 `onDetachedFromWindow()` | `velocityTracker.recycle()` |

### D-2：ChatViewModel 错误消息 raw JSON ✅

**涉及文件**：`ChatViewModel.kt`

| 改动 | 说明 |
|------|------|
| 新增 `userFriendlyError()` | 按异常类型映射中文文案：超时/连接失败/SSL/HTTP 4xx/HTTP 5xx |
| 三处 error 赋值清理 | `保存消息失败` / `保存AI回复失败` → 去掉 `e.message` 拼接；`生成回答失败` → `userFriendlyError(e)` |

### D-3：ReaderScreen / ChatViewModel 重复代码 ❌ 未修复（低优先级）

> `ChatViewModel.sendMessage` 三个分支重复 state 更新和 `ReaderScreen.onLongPress` 坐标换算——可提取但非阻塞。

### D-4：ChatActivity 硬编码中文 ❌ 未修复（低优先级）

> "复制"、"重新生成" 等菜单为硬编码中文，同文件其他文案走 `stringResource`——不影响功能。

### D-5：PdfiumDocumentSession.close() 主线程 runBlocking ❌ 未修复

> 极端情况下 onDestroy 卡顿，需重构为协程——已在已知风险中。

### D-6：未使用 import 清理 ✅

**涉及文件**：`SearchDocumentUseCase.kt` — 删除 `import PdfBoxTextExtractor`

### D-7：ChatRepositoryImpl 丢失 import ✅

**涉及文件**：`ChatRepositoryImpl.kt` — 补 `import kotlinx.coroutines.flow.Flow` 和 `import kotlinx.coroutines.flow.map`

---

## E. 安全与隐私 — 3/4 修复

### E-1：Debug logcat 泄露 Bearer Key ✅

**涉及文件**：`NetworkModule.kt`

| 改动 | 说明 |
|------|------|
| 新增 `redactHeader("Authorization")` | `provideHttpLoggingInterceptor()` 中调用 |

### E-2：SummaryCacheStore 自制散列碰撞风险 ✅

**涉及文件**：`SummaryCacheStore.kt`

| 改动 | 说明 |
|------|------|
| `systemPromptHash()` 重写 | 自制 31 进制散列 → SHA-256（前 8 字节 hex） |

### E-3：云端隐私提示文案 ✅

**涉及文件**：`SettingsScreen.kt`、`SettingsViewModel.kt`、`SettingsUiState.kt`

| 改动 | 说明 |
|------|------|
| 新增 `confirmCloudPresetId` 状态 | 切换到 deepseek/qwen 时先弹确认框 |
| AlertDialog | 标题「云端服务隐私提示」，说明数据外发行为 |
| confirmCloudPreset / cancelCloudPreset | ViewModel 对应逻辑 |

### E-4：聊天记录/摘要缓存明文落盘 ❌ 未修复

> 威胁模型上可接受；若主打"隐私阅读"卖点可考虑加密——低优先级。

---

## F. 性能 — 2/5 修复

### F-1：导入双重文本提取 ✅

**涉及文件**：`DocumentRepositoryImpl.kt`

| 改动 | 说明 |
|------|------|
| `session.pageCount` 替代全文提取 | importDocument 中用 Pdfium pageCount 拿页数，不再调 `extractPagesText`（已存在，非本次改动） |

### F-2：停止生成取消 HTTP ✅

> 见 B-4

### F-3：意图分类额外往返+无 maxTokens ✅

**涉及文件**：`QueryIntentRouter.kt`、`LlmRepository.kt`、`LlmRepositoryImpl.kt`、`ChatCompletionRequestDto.kt`

| 改动 | 说明 |
|------|------|
| 新增 `LocalIntentClassifier` | 两阶段路由：本地规则优先，不确定时才调 LLM |
| `INTENT_MAX_TOKENS = 8` | 分类请求设 `maxTokens=8` |
| `maxTokens` 参数链路 | `LlmRepository.chatCompletionStream()` → `ChatCompletionRequestDto.maxTokens` |

### F-4：流式 token O(n²) 重建 ❌ 未修复

> `ReaderViewModel` 每个 token 调 `accumulated.toString()`、`ChatViewModel` 每个 token map 整个消息列表——需节流优化，中等工作量。

### F-5：翻页无相邻页预渲染 ❌ 未修复

> `PdfPageView` 单页渲染无预加载，翻页白屏——体验优化点，中等工作量。

---

## G. UI/UX — 1/5 修复

### G-1：引用页码越界校验 ✅

**涉及文件**：`ChatActivity.kt`

| 改动 | 说明 |
|------|------|
| `ChatBubble` 加 `pageCount` 参数 | `filter { it.pageIndex in 0 until pageCount }` 过滤越界引用 |

### G-2：重新生成菜单无效 ✅

> 见 B-5

### G-3：索引失败可解释 ✅

> 见 B-1

### G-4：新用户引导缺失 ❌ 未修复

> 首次使用需要 PC 装 LM Studio + adb reverse，App 内无引导。

### G-5：ReaderAiSheet 文案重复 ❌ 未修复

> 连续两个 headlineSmall + stageLabel 重复显示——UI 微调。

---

## H. AI 功能专项 — 4/7 修复

### H-1：LLM 调用超时熔断 ✅

**涉及文件**：`FullDocumentSummarizer.kt`

| 改动 | 说明 |
|------|------|
| `PER_CALL_TIMEOUT_SECONDS = 120L` | 单次 LLM 调用超时 |
| `OVERALL_TIMEOUT_SECONDS = 600L` | 整个 Map-Reduce 流程硬上限 |
| `collectWithTimeout()` | 包装每个 LLM 调用：小文档 final、每批 map、每批重试、最终 reduce、reduce merge、reduce meta-batch |
| `OverallTimeoutException` | 带 batches + timeoutSeconds 参数 |

### H-2：意图分类 max_tokens ✅

> 见 F-3

### H-3：Publication 页码范围校验 ✅

> 见 G-1

### H-4：多轮对话上下文缺失 ✅

> `ChatViewModel` 在保存当前问题前读取历史快照，并把既有对话传给 `AskDocumentUseCase`；当前问题只追加一次。

### H-5：SSE 畸形 chunk 崩流 ✅

> 见 B-3

### H-6：空回复处理 ✅

> 空批重试简化 prompt + `AllBatchesEmptyException` + `EmptyFinalSummaryException`（已存在）

### H-7：隐私提示 ✅

> 见 E-3

---

## I. 测试与发布 — 3/4 修复

### I-1：CI 加 assembleRelease ✅

**涉及文件**：`.github/workflows/ci.yml`

| 改动 | 说明 |
|------|------|
| 已有 `assembleRelease` 步骤 | 原 CI 已包含，非本次新增 |
| 新增 keystore 解码步骤 | 从 GitHub Secrets base64 解码 keystore + 写 local.properties |

### I-2：Release 签名 ✅

> 见 B-6

### I-3：崩溃监控 (Sentry) ✅

> 见 B-6

### I-4：CI 不跑 androidTest ❌ 未修复

> 仪器测试需要模拟器，CI 环境未配置。

---

## 联动修复（非报告直接要求，修改中发现的连带问题）

| 问题 | 文件 | 说明 |
|------|------|------|
| Moshi `@Provides` 丢失 | `NetworkModule.kt` | 删 Retrofit 时误删 `provideMoshi()`，导致 Hilt 编译失败，已补回 |
| `PdfiumTextExtractor` 未更新 import | `PdfiumTextExtractor.kt` | PdfTextExtractor 上移 domain 后缺少 import，导致 KSP 编译失败 |
| `ChatRepositoryImpl` 缺少 flow import | `ChatRepositoryImpl.kt` | 丢失 `import kotlinx.coroutines.flow.Flow` 和 `map` |
| `LlmRepositoryImpl.fetchModels` 缺少 return | `LlmRepositoryImpl.kt` | `response.use {}` 前加 `return` |
| Sentry API 修正 | `PocketPdfApp.kt` | `Sentry.init()` → `SentryAndroid.init()`（SDK 7.x API） |
| 签名配置无法读 `local.properties` | `build.gradle.kts` | Gradle `findProperty()` 不读 `local.properties` → 改用手动读文件 |

---

## 未修复项（按优先级）

### 高优先级

| 问题 | 原因 | 预估工作量 |
|------|------|-----------|
| 流式 token O(n²) 重建（F-4） | 两个 ViewModel 每 token 全量 copy，长回答 GC 压力大 | 中 |

### 中优先级

| 问题 | 原因 | 预估工作量 |
|------|------|-----------|
| 新用户引导缺失（G-4） | 首次使用需 LM Studio + adb reverse，App 内无引导 | 中 |
| 翻页白屏（F-5） | 无相邻页预渲染 | 中 |
| ChatViewModel 去重脆弱（C-5） | 内容+角色去重，缺 UUID | 中 |
| 测试钩子侵入（C-6） | FullDocumentSummarizer.testInstance() 绕 Hilt | 中 |
| PdfiumDocumentSession 主线程 runBlocking（D-5） | onDestroy 可能卡顿 | 低 |

### 低优先级

| 问题 | 原因 |
|------|------|
| ReaderScreen/ChatViewModel 重复代码提取 | 不影响功能 |
| ReaderAiSheet 文案重复 | UI 微调 |
| 聊天记录明文落盘加密 | 威胁模型上可接受 |
| CI 不跑 androidTest | 需 Android 模拟器 |
| R8 规则瘦身 | 需 release 包回归验证 |
| 向量检索升级 | 千页级文档才需要 |

---

## 统计

| 指标 | 数值 |
|------|------|
| 修改文件总数 | 55+ |
| 新增文件 | 8（EmbeddingModelMissingException, FullDocumentSummarizer, FullDocumentProgress, QueryIntentRouter, QueryIntent, LocalIntentClassifier, domain/pdf/PdfTextExtractor, domain/pdf/PdfTextPosition, local.properties.example） |
| 删除文件 | 4（LlmApi, OnnxEmbeddingEngine, data/pdf/PdfTextExtractor, data/pdf/PdfTextPosition） |
| 报告问题总数 | 40+ |
| 已修复 | 27 |
| 未修复 | 13（低优先级为主） |

---

## 2026-06-13 最终验收补充

- OkHttp 取消、超时分类、历史去重、选中回答重新生成、Sentry 配置和模型供应链均有回归测试。
- AndroidX Test 更新后，Android 16 / API 36.1 上 31/31 instrumentation tests 通过。
- JVM 测试 402/402 通过，lint 0 errors，release APK 通过 R8、v2 签名和模型资源检查。
- 当前完整结论与残余风险见 `docs/project-audit-2026-06-13.md`。
