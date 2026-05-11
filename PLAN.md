# PocketPDF · 项目总方案

> 本文是 5 周开发的**唯一参考方案**。所有技术选型、目录结构、模块边界、风险预案以此为准。每周日志若发现方案需要调整，**必须同步更新本文**并在日志里记录原因。

最近更新：Week 0 · 2026-05-11

---

## 1. 定位与边界

| 维度 | 决定 |
|---|---|
| 产品形态 | Android 原生应用，**本地 PDF 阅读器 + RAG 问答助手** |
| LLM 路线 | **阶段 A（W0–W4）**：HTTP 调用 PC 上的 LM Studio（OpenAI 兼容协议）；**阶段 B（W5 或之后）**：可选切云端 API（DeepSeek / OpenAI 等，同协议零改动） |
| 差异化 | 暂不做；先实现标准 RAG 跑通，跑通后再考虑（已与用户确认） |
| 目标用户 | 学生、自学者；典型场景：教辅、论文、电子书 |
| 工期 | **5 周硬 DDL**（2026-05-11 起） |
| 代码量目标 | **业务代码 5k–8k 行，仓库总行数 12k–18k 行**；不追求 3 万行 |

### 明确**不做**（v1 范围外）

- ❌ 扫描件 OCR（PdfBox 拿不到文本时直接提示用户）
- ❌ 多设备同步、云存储
- ❌ 跨 PDF 知识库（先单文档问答）
- ❌ 端侧 LiteRT-LM / Gemma 推理（开发期靠 LM Studio 桥接，端侧推理列为 v2）
- ❌ Kotlin Multiplatform、Compose（已选 XML 稳妥路线）

---

## 2. 技术栈（已锁定，不再变更）

### Android 端

| 层 | 选型 | 版本 / 备注 |
|---|---|---|
| 平台 | Android | minSdk 26 (8.0)，targetSdk 36，compileSdk 36 |
| 语言 | Kotlin | 2.2.20（Gradle 9.2.1 内嵌） |
| 构建 | Gradle 9.2.1 + AGP 9.0.1 + Kotlin DSL + Version Catalog | `gradle/libs.versions.toml`；AGP 9 内置 Kotlin Android 支持，无需单独 apply Kotlin plugin |
| JVM target | Java 17 | `compileOptions sourceCompatibility = VERSION_17` |
| UI | XML + Material Components + ViewBinding | 不用 Compose |
| 架构 | Clean Architecture（单 Module 分层）+ MVVM | 详见 §4 |
| DI | Hilt | 替代 Dagger 模板 |
| 异步 | Coroutines + Flow + StateFlow | |
| 本地存储 | Room | 实体见 §5 |
| 网络 | Retrofit + OkHttp + `okhttp-sse` | 流式响应 |
| PDF 文本 | PdfBox-Android（TomRoush） | 2.0.27.0+ |
| PDF 渲染 | AndroidPdfViewer (barteksc) | 备选 系统 PdfRenderer |
| Embedding | Sentence-Embeddings-Android (shubham0204) | MiniLM-L6-v2，384 维 |
| 向量检索 | 手写余弦相似度 + 内存索引 | 量小不上 FAISS |
| 后台任务 | WorkManager | PDF 索引异步化 |
| 日志 | Timber | |
| 测试 | JUnit4 + MockK + Turbine + Espresso | |
| 编辑器 | Android Studio Hedgehog 或更新 | |

### PC 端（LLM 服务）

| 项 | 决定 |
|---|---|
| OS | Windows |
| LLM 服务 | **LM Studio**（已装，CLI `lms.exe`） |
| 协议 | **OpenAI 兼容**（`/v1/chat/completions`、`/v1/embeddings`、`/v1/models`），事实标准 |
| 默认模型 | `lmstudio-community/gemma-3-4b-it` Q4_K_M（2.3 GB，已下载） |
| 备选模型 | `gemma-3n-e4b-it` Q8_0（已下载，质量更高但慢）/ 如中文不达预期再拉 `qwen2.5-3b-instruct` |
| 端口 | `localhost:1234` |
| 启动方式 | LM Studio GUI → Developer / Local Server → Start Server（或 `lms server start`） |
| 手机联调 | **`adb reverse tcp:1234 tcp:1234`**（不用同 WiFi、不用配 IP） |
| 未来切云端 | 同 OpenAI 兼容协议，只改 `BASE_URL` + API Key，**业务代码零改动** |

### 工程化

| 项 | 决定 |
|---|---|
| Git 工作流 | `main`（受保护）+ `dev` + `feat/xxx` |
| Commit 规范 | Conventional Commits（`feat:` / `fix:` / `docs:` / `test:` / `refactor:` / `chore:`） |
| CI | GitHub Actions（W5 加，跑 lint + unit test） |
| 包名 | `com.asuka.pocketpdf`（占位，建工程时定） |
| Application ID | 同包名 |
| 仓库名 | `pocketpdf-android` |

---

## 3. 项目目录结构

```
PocketPDF/
├─ app/
│  └─ src/
│     ├─ main/
│     │  ├─ AndroidManifest.xml
│     │  ├─ java/com/asuka/pocketpdf/
│     │  │  ├─ MainActivity.kt
│     │  │  ├─ PocketPdfApp.kt            # @HiltAndroidApp
│     │  │  │
│     │  │  ├─ core/                      # 跨层工具
│     │  │  │  ├─ Result.kt
│     │  │  │  ├─ DispatcherProvider.kt
│     │  │  │  └─ ext/                    # 扩展函数
│     │  │  │
│     │  │  ├─ data/                      # 数据层
│     │  │  │  ├─ local/
│     │  │  │  │  ├─ AppDatabase.kt
│     │  │  │  │  ├─ dao/
│     │  │  │  │  └─ entity/
│     │  │  │  ├─ pdf/                    # PdfBox / PdfRenderer 封装
│     │  │  │  │  ├─ PdfTextExtractor.kt
│     │  │  │  │  └─ PdfPageRenderer.kt
│     │  │  │  ├─ embedding/              # Sentence-Embeddings 封装
│     │  │  │  │  └─ Embedder.kt
│     │  │  │  ├─ remote/                 # Retrofit + OpenAI-compat
│     │  │  │  │  ├─ LlmApi.kt              # /v1/chat/completions, /v1/models, /v1/embeddings
│     │  │  │  │  ├─ LlmStreamClient.kt     # SSE 流式解析
│     │  │  │  │  └─ dto/                   # ChatCompletionRequestDto, ChoiceDto, …
│     │  │  │  └─ repository/             # 仓库实现
│     │  │  │
│     │  │  ├─ domain/                    # 纯 Kotlin，无 Android 依赖
│     │  │  │  ├─ model/                  # Document, Chunk, ChatMessage, …
│     │  │  │  ├─ repository/             # 接口
│     │  │  │  └─ usecase/                # IndexDocumentUseCase, AskUseCase, …
│     │  │  │
│     │  │  ├─ ui/
│     │  │  │  ├─ common/
│     │  │  │  ├─ library/                # 文档库
│     │  │  │  ├─ reader/                 # 阅读器
│     │  │  │  ├─ chat/                   # 聊天问答
│     │  │  │  └─ settings/
│     │  │  │
│     │  │  └─ di/
│     │  │     ├─ DatabaseModule.kt
│     │  │     ├─ NetworkModule.kt
│     │  │     └─ RepositoryModule.kt
│     │  │
│     │  └─ res/                          # XML 布局 / 资源
│     │
│     ├─ test/                            # 单元测试
│     └─ androidTest/                     # 仪器测试
│
├─ gradle/libs.versions.toml              # Version Catalog
├─ build.gradle.kts (root)
├─ settings.gradle.kts
├─ .github/workflows/android.yml          # W5 添加
│
├─ docs/
│  ├─ ARCHITECTURE.md
│  ├─ dev-log/
│  │  ├─ TEMPLATE.md
│  │  ├─ week0.md
│  │  └─ ...
│  └─ screenshots/
│
├─ PLAN.md                                # 本文
├─ ROADMAP.md
├─ CONTRIBUTING.md
├─ README.md
└─ .gitignore
```

**强制依赖方向**：`ui → domain ← data`，`domain` 不允许 `import android.*` 任何东西。

---

## 4. 架构原则

### 4.1 分层职责

- **`domain`**：纯 Kotlin，包含 **Use Case + 领域模型 + Repository 接口**。可单独 JVM 测试。
- **`data`**：实现 `domain` 的 Repository 接口；Room / Retrofit / PdfBox 等所有 Android/三方依赖都在这里。
- **`ui`**：Activity / Fragment / ViewModel；只调用 Use Case，不直接调用 Repository。
- **`di`**：Hilt Module，把 `data` 的实现绑到 `domain` 的接口。
- **`core`**：跨层通用工具（`Result<T>`、`DispatcherProvider`、扩展函数）。

### 4.2 数据流（单向）

```
View ── intent ──▶ ViewModel ── invoke ──▶ UseCase ── call ──▶ Repository
View ◀── state ── ViewModel ◀── result ── UseCase ◀── data ── Repository
```

- ViewModel 持有 `StateFlow<UiState>`，View 用 `repeatOnLifecycle(STARTED)` 收集
- UseCase 返回 `Flow<Result<T>>` 或 `suspend fun`
- 错误用 `Result<T>`（`core/Result.kt`）封装，不抛裸异常

### 4.3 命名规范

- Use Case：`动词 + 名词 + UseCase`，如 `IndexDocumentUseCase`、`AskDocumentUseCase`
- Repository 接口：`XxxRepository` 在 `domain`；实现：`XxxRepositoryImpl` 在 `data`
- DTO 后缀：`Dto`（如 `ChatCompletionRequestDto`），不和 domain model 混用
- Entity 后缀：`Entity`（Room）
- ViewModel：`XxxViewModel`
- 资源 ID：`{type}_{feature}_{name}`，如 `btn_library_import`

---

## 5. 数据模型（领域 + 持久化）

### Domain Models（`domain/model/`）

```kotlin
data class Document(
    val id: Long,
    val title: String,
    val uri: String,           // 内部存储路径
    val pageCount: Int,
    val indexStatus: IndexStatus,
    val importedAt: Long
)

enum class IndexStatus { NOT_INDEXED, INDEXING, INDEXED, FAILED }

data class Chunk(
    val id: Long,
    val documentId: Long,
    val pageStart: Int,
    val pageEnd: Int,
    val text: String,
    val embedding: FloatArray?  // 384 维
)

data class ChatMessage(
    val id: Long,
    val documentId: Long,
    val role: Role,            // USER / ASSISTANT
    val content: String,
    val citedChunks: List<Long>,
    val createdAt: Long
)
```

### Room Entities（`data/local/entity/`）

`DocumentEntity` / `PageEntity`（W1）→ `ChunkEntity`（W2，embedding 存 `ByteArray`）→ `ChatMessageEntity`（W4）

---

## 6. 风险清单与对策

| # | 风险 | 概率 | 影响 | 对策 |
|---|---|---|---|---|
| 1 | 工作目录中文 + 空格导致 Gradle 异常 | 中 | 高 | W0 创建 AS 工程时改放 `C:\dev\pocketpdf-android` |
| 2 | PdfBox-Android 对扫描件无效 | 高 | 中 | v1 明确只支持文本型 PDF，扫描件友好提示 |
| 3 | Sentence-Embeddings 模型下载慢 | 中 | 高 | 首次进入显示进度；备份方案：模型打包到 assets |
| 4 | LM Studio 调用慢（PC 上 Gemma 3 4B Q4 约 20–40 tokens/s） | 低 | 中 | 一律走 SSE 流式；prompt 控制在 2k tokens；必要时降级到 1.5B 模型 |
| 5 | Hilt 配置坑（kapt/ksp 冲突） | 高 | 中 | W0 第一个 commit 就把 Hilt 跑通，不拖延 |
| 6 | 5 周不够 | 中 | 高 | 每周末评估是否砍功能；砍序：深色模式 > 设置高级项 > 跳转高亮 |
| 7 | AI 生成代码不一致 | 高 | 中 | `CONTRIBUTING.md` 列出规范，每次给 AI 上下文时附带 |
| 8 | 新手不熟 Coroutines | 中 | 高 | W0 学习 1h；W1 写 `notes/coroutines-cheatsheet.md` |
| 9 | adb reverse 失效（USB 断开） | 低 | 低 | 文档化恢复命令；考虑 W3 加"健康检查"按钮 |
| 10 | Embedding 中文质量差 | 中 | 中 | 备选切换到 `bge-small-zh-v1.5`（同样 ONNX 格式可用） |

---

## 7. 验收标准（5 周末）

到 2026-06-15 前必须满足：

- [ ] APK 可在 minSdk 26 真机安装运行
- [ ] 能完成「导入 PDF → 阅读 → 提问 → 流式回答（带引用页码）→ 点击引用跳转高亮」完整闭环
- [ ] domain 层单元测试覆盖率 ≥ 70%
- [ ] 至少 2 个 Espresso 测试（导入流程、问答流程）
- [ ] GitHub Actions 跑通 lint + unit test，README 显示 ✅ 徽章
- [ ] `docs/dev-log/` 至少 6 篇日志（W0–W5）
- [ ] `docs/ARCHITECTURE.md` 完整，包含 Mermaid 模块图
- [ ] Demo 视频 60–90 秒，传 B 站，README 嵌入链接
- [ ] Git 至少 50 个有意义的 commit，6 个 tag（v0.0.1 → v1.0.0）

---

## 8. 面试时怎么讲（开发优先级反推）

每个模块至少要能回答下列问题中的一个：

1. 为什么分 data/domain/ui？依赖方向怎么保证？
2. PdfBox-Android 的局限？大文件 OOM 怎么处理？
3. Chunking 窗口怎么定？为什么用 overlap？
4. Embedding 选型？换中文模型要改什么？
5. 向量检索为什么没用 FAISS？数据量上去了怎么扩？
6. 为什么用 SSE 不用 WebSocket？OkHttp 超时怎么调？
7. 哪些任务用 Coroutine、哪些用 WorkManager？为什么？
8. Hilt Scope 怎么用？Singleton vs ActivityScoped？
9. 为什么 domain 覆盖率高、UI 覆盖率低？
10. CI 跑哪些？怎么保证主分支稳定？

→ 开发期边写边把"为什么"记到 `docs/dev-log/` 里，面试前直接复习。

---

## 9. 方案变更记录

| 日期 | 变更 | 原因 |
|---|---|---|
| 2026-05-11 | 初稿 | W0 启动 |
| 2026-05-11 | LLM 后端从 Ollama 改为 LM Studio（OpenAI 兼容协议），端口 11434 → 1234，默认模型 `qwen2.5:3b-instruct` → `gemma-3-4b-it Q4_K_M` | 检测到开发机已装 LM Studio 且已有 Gemma 3 4B / 3n E4B 两个模型；OpenAI 兼容协议比 Ollama 私有协议更通用，未来切云端零改动；详见 ADR-002 修订版 |
| 2026-05-11 | targetSdk/compileSdk 34 → 36；JVM target Java 11 → Java 17；启用 ViewBinding | AS New Project Wizard 默认生成 SDK 36 + Java 11 + AGP 9.0.1 + Gradle 9.2.1，比原计划更新；接受默认（API 36 = Android 16）；Hilt 与 AGP 9 都建议 Java 17+，提前对齐；ViewBinding 是 XML 路线下访问视图的现代写法，必启 |
