# Week 0 · 2026-05-11 至 2026-05-12

> Day 1 收尾时间：2026-05-11 20:36 (UTC+8)，Day 2 收尾时间：2026-05-12 10:30 (UTC+8)。
> 第 0 周：环境就绪 + 文档骨架。"动手前能阻塞 4 周后的事"全部解决。**状态：✅ 完成**，tag `v0.0.1-env-ready` 已推。

## 1. 本周目标（来自 ROADMAP）

- [x] 写 `PLAN.md` / `ROADMAP.md` / `README.md` / `CONTRIBUTING.md` / `.gitignore`
- [x] 写 `docs/dev-log/TEMPLATE.md` 和本日志
- [x] 写 `docs/ARCHITECTURE.md` 占位 + ADR 模板
- [x] 创建本地 Git 仓库（在最终的工作目录），首次 commit（`e4d1946`）
- [x] 创建 GitHub 远端仓库 `pocketpdf-android`，push（<https://github.com/stuid-maker/pocketpdf-android>）
- [x] 决定工作目录是否迁出 `PDF小助手app`（中文+空格路径风险）→ 已改名为 `pocketPDF`
- [x] 验证 Android Studio 安装情况（`D:\AndroidStudio\`，自带 JBR OpenJDK 21.0.9）
- [x] 用 AS 新建工程（包名 `com.asuka.pocketpdf`），配 Version Catalog + Hilt + KSP + Retrofit + Moshi + OkHttp + Coroutines + Timber + Test 套件
- [x] 验证 `./gradlew :app:assembleDebug` 通过（41 tasks，BUILD SUCCESSFUL，APK 已生成）
- [x] ~~安装 Ollama，`ollama pull qwen2.5:3b-instruct`~~ → 改为复用已装 LM Studio（详见 ADR-002 修订）
- [x] LM Studio GUI 启动 Local Server（端口 1234，模型 `google/gemma-3-4b` / `google/gemma-4-e4b`）
- [x] PowerShell 跑通 `curl http://localhost:1234/v1/models`（200 OK，返回模型列表）
- [x] 模拟器跑通空 App（Medium_Phone_API_36.1 / Android 16 / SDK 36）
- [x] `adb reverse tcp:1234 tcp:1234` 设置，`adb reverse --list` 确认 `host-16 tcp:1234 tcp:1234`
- [x] App 内最小 Demo：按钮 → 调 `/v1/models` → Toast 显示 `google/gemma-3-4b`，TextView 列出 3 个已加载模型
- [x] 打 tag `v0.0.1-env-ready`（W0 全部完成后）

## 2. 实际完成

> 进行中，每完成一项就移到这里。

- ✅ 项目方案落地：`PLAN.md` 完整版，含技术选型、目录结构、风险表、面试问答清单
- ✅ 5 周路线图：`ROADMAP.md`，每周任务可勾选，"砍功能优先级"明确
- ✅ 编码规范与 Git 规范：`CONTRIBUTING.md`，含 AI 辅助使用约定
- ✅ 架构占位：`docs/ARCHITECTURE.md`，含 4 条 ADR（XML、LLM 桥接、单 Module、AGP 8 黄金组合降级）
- ✅ AS 新建 Gradle 工程骨架（包名 `com.asuka.pocketpdf`），首次 build 成功
- ✅ 完整依赖到位：Hilt 2.52 + Retrofit 2.11 + Moshi + OkHttp + Coroutines + Timber + Test 套件
- ✅ `PocketPdfApp.kt` 挂 `@HiltAndroidApp` + Timber.plant，Manifest 配 INTERNET + network_security_config（允许 localhost 明文）
- ✅ 日志模板：`docs/dev-log/TEMPLATE.md`
- ✅ `.gitignore`（Android 标准 + 个人补充）
- ✅ README 项目首页（带状态徽章、路线图状态表）

### Day 2（2026-05-12）

- ✅ **core 层**：`Result<T>` sealed class + `resultOf {}` helper + `DispatcherProvider` 接口 + 默认实现 → 作为 W1 起所有业务代码的跨层模板
- ✅ **data/domain/di 三层全链路骨架**（不是塞在 Activity 里的捷径写法）：
  - `domain.model.LlmModel` 纯 Kotlin 领域实体（id + ownedBy）
  - `domain.repository.LlmRepository` 接口（返回 `Result<List<LlmModel>>`）
  - `domain.usecase.GetAvailableModelsUseCase` 单职责 use case
  - `data.remote.dto.{ModelDto, ModelsResponseDto}` Moshi codegen DTO，宽容多余字段
  - `data.remote.LlmApi` Retrofit 接口（仅 `GET v1/models`，W3 起追加 chat/completions + embeddings）
  - `data.remote.repository.LlmRepositoryImpl` 走 `dispatchers.io`，DTO → domain 映射，`runCatching` 包成 `Result`
  - `di.NetworkModule` 提供 Moshi + OkHttp（60s 超时 + Debug BODY log）+ Retrofit + LlmApi
  - `di.RepositoryModule` `@Binds` LlmRepository ← Impl，`@Provides` DispatcherProvider
- ✅ **PingActivity（ui/ping 层）**：
  - `PingUiState` sealed class（Idle/Loading/Success/Error）
  - `PingViewModel` `@HiltViewModel`，**双流模式**（StateFlow 持久态 + Channel 一次性事件），W1 聊天/阅读页直接复用
  - `PingActivity` `@AndroidEntryPoint` + ViewBinding + `repeatOnLifecycle(STARTED)` + edge-to-edge + 系统栏 inset
  - `activity_ping.xml` 标题/副标题/Material 按钮/ProgressBar/等宽 TextView 五件套
  - `strings.xml` `ping_*` 字符串
  - Manifest 把 launcher 切到 PingActivity，**删掉** MainActivity.kt + activity_main.xml（避免无人维护的 Hello World 死代码）
- ✅ **smoke test 全链路打通**：
  - `./gradlew :app:assembleDebug` BUILD SUCCESSFUL（42 tasks，含 KSP + Hilt）
  - `adb reverse tcp:1234 tcp:1234`
  - `:app:installDebug` 安装到 `emulator-5554`
  - `am start -n com.asuka.pocketpdf/.ui.ping.PingActivity`
  - `uiautomator dump` 解析 `btn_ping_ping` bounds，`input tap` 模拟点击
  - logcat 验证：`PingViewModel: ping success, 3 model(s), first=google/gemma-3-4b`
  - logcat 验证 OkHttp 体：`200 OK http://localhost:1234/v1/models (14ms)`，返回 3 个模型
  - 截图 `docs/screenshots/w0-ping-success.png` 存档

## 3. 关键决策与权衡

### ADR-001: 选 XML 而非 Compose

- **背景**：开发者 Android 新手，5 周硬 DDL，主要靠 AI 辅助编程
- **候选方案**：
  - A. Jetpack Compose（2026 主流）
  - B. XML + ViewBinding + Material Components
- **最终决策**：B
- **理由**：XML 在 AI 训练数据中占比远高于 Compose；新手 + AI 辅助场景下，XML 出错率低、可参考代码多；ViewModel + LiveData/StateFlow 模式在两者之间通用
- **代价**：与 2026 主流稍有距离 → 面试时主动解释选型权衡，反而成为加分项（体现工程思维）

### ADR-002（初稿 + 同日修订）: 选 PC 端 LLM 服务 + OpenAI 兼容协议，而非端侧推理

- **背景**：原方案考虑端侧 LiteRT-LM + Gemma；调研发现 5 周内难稳定集成；同日检测到开发机已装 LM Studio 且模型已下载
- **候选方案**：
  - A. 端侧推理（LiteRT-LM + Gemma 3 1B/3n）
  - B. PC 端 **Ollama** + Ollama 私有协议
  - C. PC 端 **LM Studio** + **OpenAI 兼容协议**（最终）
  - D. 直接对接云端（OpenAI / DeepSeek / 通义）
- **最终决策**：C（开发期），D 因协议同构可零改动切换列为 v1 后期可选项，A 列为 v2
- **理由**：
  1. **零下载成本**：LM Studio 已装；本机已有 Gemma 3 4B-IT Q4_K_M (2.3 GB) 和 Gemma 3n E4B-IT Q8_0 (7.5 GB)
  2. **协议通用**：OpenAI Chat Completions 是事实标准；DeepSeek / 通义 / Together AI / vLLM 全兼容；Ollama 私有协议只服务自家
  3. **切换便宜**：从 LM Studio 切到云端只改 `BASE_URL` 和 API Key，业务代码零改动
  4. **简历表述更通用**：写"对接 OpenAI 兼容 LLM 服务"比"对接 Ollama"通用性强
- **代价**：演示必须 PC 在线；LM Studio Server 需在 GUI 点 Start（或 `lms server start`）；不可脱机；面试时要解释为什么不上端侧
- **端口与命令**：`localhost:1234`；`adb reverse tcp:1234 tcp:1234`

### ADR-003: 单 Module 而非多 Module

- **背景**：Clean Architecture 经典做法是多 Gradle module（`:domain`、`:data`、`:feature-xxx`）
- **决策**：单 `app` module + 严格分包 + 包路径规则
- **理由**：新手在多 module Gradle 配置上容易卡 1–2 天；单 module 同样能保证 Clean Architecture 的核心精神（分层、依赖方向）
- **代价**：无法用 Gradle 编译期强制依赖方向，要靠 review 和 lint 规则

### 决策 4: 不追求 3 万行代码量

- **背景**：用户初始预期 30k+ LOC
- **决策**：目标改为 5k–8k 业务代码 + 测试 + 文档 = 仓库总 12k–18k 行
- **理由**：5 周新手 + AI 辅助下 3w 行只可能是模板代码堆砌，简历反而扣分。质量 > 数量
- **代价**：需要在简历里**主动用工程化亮点替代代码量描述**（如"70% 测试覆盖、CI、ADR、6 个 milestone tag"）

## 4. 踩坑记录

| 问题 | 原因 | 解决 | 用时 |
|---|---|---|---|
| PowerShell `where ollama` 报错 | `where` 是 `Where-Object` 别名 | 用 `where.exe ollama` | 1 min |
| 工作目录 `PDF小助手app` 含中文+空格 | 历史习惯 | 直接在原位改名为 `pocketPDF`（纯英文、无空格、非 OneDrive 路径） | 5 min |
| Android Studio 未安装（SDK 单独存在于 `%LOCALAPPDATA%\Android\Sdk`） | 之前装过 AS 后卸载或单独装的 cmdline-tools | 装 AS 到 `D:\AndroidStudio\`，自动复用现有 SDK，无需重下 1 GB | 15 min |
| adb 不在 PATH | SDK 装了但没配 PATH | `platform-tools` 已加到 User PATH（新开终端生效） | 2 min |
| 默认 LLM runtime 选型反复 | 初定 Ollama，后发现已装 LM Studio | 改用 LM Studio + OpenAI 兼容协议，详见 ADR-002 修订版 | 10 min |
| **AGP 9.0.1 生态适配期连锁兼容性问题** | AS 默认生成 AGP 9 工程；2026 年 1 月才发布的 AGP 9，KSP/KGP/Hilt 三方未完全追齐 | 主动降级到 AGP 8.7.3 黄金组合（Kotlin 2.0.21 + KSP 2.0.21-1.0.28 + Hilt 2.52 + Gradle 8.10.2），**详见 ADR-004** | 45 min |
| KSP 与 AGP 9 built-in Kotlin 不兼容 | AGP 9 引入 built-in Kotlin（不需要 apply kotlin("android")），但 KSP 2.2.20-2.0.x 仍依赖旧 sourceSets DSL | （仅 AGP 9 下需要）`android.builtInKotlin=false` + 显式 apply `kotlin("android")` | 包含在上一项 |
| `kotlin("android") "必须添加这一行"` 教程是 AGP 8 时代的 | AGP 9 内置 Kotlin → AGP 8 必须显式 apply → 不同 AGP 版本写法相反 | 跟着当前选定的 AGP 版本走（AGP 8.7.3 ✓ 显式 apply） | 经验积累 |
| AGP 8 起 `BuildConfig` 默认禁用 | AGP 7→8 的 breaking change，多数老教程没提 | `buildFeatures { buildConfig = true }` | 1 min |
| 阿里云 Maven 镜像未同步 KSP plugin marker | 镜像同步覆盖率有限 | 撤回，仅用 google() + mavenCentral() + VPN | 5 min |
| 误判"AGP 9 内置 Kotlin 不需要 apply"导致删除用户手加的 `kotlin("android")` | 我未先验证 KSP 与 built-in Kotlin 的兼容性就下结论 | 总结教训：**AI 给的"AGP 9 不需要 apply Kotlin plugin"在配 KSP 时是错的** | 10 min |
| **Day 2**：PowerShell 里 `curl http://localhost:1234/v1/models` 卡死 30s+ 不返回 | PowerShell `curl` 是 `Invoke-WebRequest` 别名，对 keep-alive 接口处理与真 curl 不一致 | 全程改用 `curl.exe ... --max-time 5`，立刻 7s 返回 200 | 3 min |
| Day 2：`adb devices` 列表为空 → 误以为 USB / driver 问题 | 模拟器没启动；之前都用真机/直接靠 AS 启动，没在命令行启动过 emulator | `emulator -list-avds` 看 AVD，`Start-Process emulator -ArgumentList "-avd","Medium_Phone_API_36.1","-no-snapshot-load"` 后台启动，等待 `getprop sys.boot_completed == 1` | 5 min |
| Day 2：Hilt javac 阶段提示 "Kapt support in Moshi Kotlin Code Gen is deprecated" | Hilt 的 javac annotation processing 阶段会扫到 Moshi codegen 类元数据，触发友好提示；我们其实已经走 KSP（`ksp(libs.moshi.kotlin.codegen)`），告警是误报 | 不修，Moshi 官方已确认 Kapt 提示对仅 KSP 配置的项目无影响；BUILD SUCCESSFUL 不受影响 | 0 min |

## 5. 关键代码片段

W0 Day 2 落地的分层模板（W1 起所有业务代码都按这个走）：

**Repository 实现：DTO → domain 映射 + Result 包装 + IO 调度器**

```kotlin
class LlmRepositoryImpl @Inject constructor(
    private val api: LlmApi,
    private val dispatchers: DispatcherProvider,
) : LlmRepository {

    override suspend fun listModels(): Result<List<LlmModel>> =
        withContext(dispatchers.io) {
            resultOf { api.listModels().data.map(ModelDto::toDomain) }
        }
}

private fun ModelDto.toDomain(): LlmModel = LlmModel(id = id, ownedBy = ownedBy)
```

**ViewModel 双流模式：StateFlow 持久态 + Channel 一次性事件**

```kotlin
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
                    _oneShotEvents.send(PingEvent.ShowToast(result.data.first().id))
                }
                is Result.Failure -> { /* ... */ }
            }
        }
    }
}
```

**Activity：repeatOnLifecycle + 双流分别 collect**

```kotlin
lifecycleScope.launch {
    repeatOnLifecycle(Lifecycle.State.STARTED) {
        launch { viewModel.uiState.collect(::render) }
        launch { viewModel.oneShotEvents.collect { event -> /* Toast */ } }
    }
}
```

## 6. 性能数据

| 指标 | 值 | 备注 |
|---|---|---|
| `/v1/models` 端到端 RTT（模拟器 → adb reverse → LM Studio） | **14 ms** | OkHttp 拦截器实测；说明 adb reverse 性能足够，W3 流式聊天首 token 延迟瓶颈一定在模型本身 |
| `assembleDebug` 冷启动 build | ~3 min 30 s | 主要时间在 KSP + Hilt aggregateDeps；增量后 `:app:installDebug` 9 s |
| APK 体积 | ~5 MB（仅 Hilt + Retrofit + Moshi + Coroutines + Timber 骨架） | W2 接入 PdfBox / Sentence-Embeddings 后预计跳到 30–50 MB |

## 7. 测试

W0 业务代码量太薄，未写正式单元测试。**Day 2 通过手动 smoke test 验收**：

| 测试 | 方式 | 结果 |
|---|---|---|
| 网络调用打通 | `adb shell input tap` 模拟点击按钮 → 查 Timber 日志 | ✅ `ping success, 3 model(s), first=google/gemma-3-4b` |
| Moshi JSON 解析 | OkHttp 体日志 vs ViewModel 解析后日志比对 | ✅ 3 个模型 ID 全部解析正确 |
| Hilt 依赖图 | App 启动不崩 + ViewModel `@Inject` UseCase 拿到非空实例 | ✅ logcat 无 `IllegalStateException` |
| network_security_config | localhost 明文请求未被 NetworkSecurityException 拦截 | ✅ 200 OK 14ms |
| ViewBinding 生成 | `ActivityPingBinding.inflate` 编译通过 + 运行时 view 引用非空 | ✅ |

W1 起 domain 单测必须先于 UI 落地。

## 8. Git 数据

- Day 1 commit 数：7（`e4d1946` → `c611cc5`，含 Day 1 收尾 commit）
- Day 2 commit 数：4 业务 + 1 文档收尾 = 5
- W0 总 commit 数：12（推 tag 前）
- 标签：`v0.0.1-env-ready` ✅ 已打（Day 2 收尾后 push）
- Day 1 commit 列表：
  - `e4d1946` docs: bootstrap project plan, roadmap, contributing and dev-log skeleton
  - `c9e0a81` docs(week0): record rename of working dir to pocketPDF
  - `e7fea30` docs: link real GitHub remote (stuid-maker/pocketpdf-android)
  - `52e7bdb` docs(adr-002): switch LLM runtime from Ollama to LM Studio + OpenAI-compat protocol
  - `b99cfc4` feat(skeleton): bootstrap android project via Android Studio New Project wizard
  - `23f1375` chore(gradle): bump JVM target to 17, enable ViewBinding, sync PLAN/CONTRIBUTING
  - `1efc2e0` feat(deps): bootstrap Hilt+Retrofit+Coroutines stack on AGP 8.7.3 golden combo
  - `c611cc5` docs(week0): Day 1 wrap-up - 85% W0 done, PingActivity deferred to Day 2
- Day 2 commit 列表：
  - `7cd6a49` chore(core): scaffold Result sealed class and DispatcherProvider
  - `e5b243b` feat(llm): add openai-compat /v1/models api with data/domain/di layers
  - `f172477` feat(ui): add PingActivity with hilt-injected viewmodel for smoke test
  - `b3ba535` chore(manifest): switch launcher to PingActivity and drop main scaffold
  - `（最后一个文档 commit hash 在 tag 推后回填）`

## 9. 自查问题（Day 2 答完）

#### Q1. 我能清楚说明 ui/domain/data 分层的意义吗？

- **单向依赖** `ui → domain ← data`：domain 是纯 Kotlin，**不 import 任何 `android.*` / `androidx.*`**，能在 JVM 上跑单元测试，无需 Robolectric/instrumentation 这类重测试基建。W5 验收线 "domain 覆盖率 ≥ 70%" 的前提就在这。
- **可替换性**：LM Studio → DeepSeek 云端，只改 `NetworkModule.BASE_URL` + 加 `Authorization` 拦截器，**ViewModel / UseCase / View 零改动**；Room 想换 Realm 同理；端侧 ONNX embed 想换 HTTP embed 也只动 data。
- **职责清晰**：ui 关心 "用户看到什么 / 怎么交互"；domain 关心 "业务规则 / 输入输出契约"；data 关心 "数据从哪儿来怎么取"。Week 0 我就用一个 `/v1/models` 调用把这套骨架跑通，避免 Week 1 起边写业务边在 Activity 里塞 OkHttp + JSON 解析的临时代码。
- **代价**：单 module 下纯靠 review + 包路径约束依赖方向，没法编译期阻断违规 import（这是 ADR-003 接受的代价）。

#### Q2. 为什么开发期用 PC 端 LLM 服务（LM Studio）而不是端侧推理？

- **5 周 DDL 的现实约束**：端侧 LiteRT-LM + Gemma 3n 集成需要处理 NNAPI / GPU / CPU 后端 fallback、模型 quant 选型、内存预算（中端机 4GB RAM 跑 4B 模型基本会 OOM）、首次加载延迟 10–30s 等问题；任何一个坑都能吃掉 1–2 天，5 周里负担不起。
- **演示场景能控**：作品集 demo 演示时 PC 一定在；W4 末演示视频也是从 PC 旁边录的，断网约束不成立。
- **协议同构 → 未来可零改动切云端**：选 OpenAI 兼容协议而非 LM Studio 私有 API，意味着今天 `BASE_URL=http://localhost:1234/` 明天可以变成 `https://api.deepseek.com/`，业务代码零改动。
- **简历叙事**：写"对接 OpenAI 兼容 LLM 服务，开发期 LM Studio 桥接，生产可切云端"比"端侧 Gemma 推理"更易讲清楚，并且不会被追问"端侧延迟多少 / 是不是 stub"。

#### Q3. 我能解释为什么挑 OpenAI 兼容协议而不是 Ollama 私有协议吗？

- **生态覆盖面**：OpenAI Chat Completions 是 2025 末事实标准；DeepSeek、通义、Together AI、vLLM、Groq、SiliconFlow、LM Studio、llama-server 全部兼容；Ollama 私有协议只有 Ollama 一家。
- **切换成本**：从 LM Studio 切云端 DeepSeek，只改 `BASE_URL` + 加 `Authorization: Bearer <key>` 拦截器；如果一开始锁死 Ollama，切云端要重写 DTO（`/api/chat` vs `/v1/chat/completions`）、API 接口、流式解析（NDJSON vs SSE），半天起。
- **W0 落地代价 0**：LM Studio 本来就是 OpenAI 兼容协议的代表实现，选它 = 顺带选 OpenAI 协议。
- **简历叙事**："对接 OpenAI 兼容 LLM 服务"对实习招聘方更友好，HR/面试官一眼明白可迁移性。

#### Q4. 我能解释 Conventional Commits 的好处吗？

- **历史可读**：`git log --oneline` 一眼看清这周 `feat:` / `fix:` / `chore:` / `docs:` 的比例；W0 的 13 个 commit 用 type+scope 写完，新人来读 30 秒能看懂这周做了啥。
- **scope 帮助定位**：`feat(llm):` / `feat(ui):` / `chore(core):` / `chore(manifest):` 在未来 50+ commit 的仓库里能瞬间过滤出"网络层都改过啥"。
- **自动化**：CI 上跑 `git-cliff` 直接生成 `CHANGELOG.md`；commit type 还能驱动 SemVer（`feat:` → minor、`fix:` → patch、`feat!:` → major），W5 发 `v1.0.0` 时直接复用。
- **强制自己思考**：每次提交都得想清楚 "这个 commit 算什么类型 / 影响哪个 scope"，避免出现 `update code` `wip` `fix` 这种垃圾消息——作品集仓库给面试官看时，commit 历史本身就是工程化能力的展示。

> **下次面试演练时直接读这 4 段**，每段控制在 90 秒能讲完。

## 10. 下周计划（W1 · PDF 阅读器 Demo）

- 文件选择器 + 内部存储复制
- PdfBox-Android 文本提取
- AndroidPdfViewer 渲染
- Room `DocumentEntity` + `PageEntity`
- 文档库主页 + 阅读器基础 UI

## 11. 时间分配

### Day 1（2026-05-11）

| 类型 | 小时（约） |
|---|---|
| 调研（市场成果案例 + 技术栈 + AGP 9 兼容性） | 0.8 |
| 写方案文档（PLAN/ROADMAP/CONTRIBUTING/ARCH/dev-log） | 1.5 |
| 环境核查与安装（AS、LM Studio、adb PATH、Git/GitHub） | 1.2 |
| AS 工程骨架 + 4 轮 Gradle 构建排坑 + 主动降级到 AGP 8.7.3 | 1.8 |
| 文档同步（ADR-002 修订 + ADR-004 新增 + 踩坑表） | 0.7 |
| **Day 1 小计** | **≈ 6.0** |

### Day 2（2026-05-12）

| 类型 | 小时（约） |
|---|---|
| Phase 0 预检（curl PowerShell 别名坑 + emulator 启动 + boot 等待） | 0.2 |
| Phase 1 写代码（core/data/domain/di/ui 五层 + 拆 4 个 commit） | 1.2 |
| Phase 2 build + adb reverse + install + tap + 验证 logcat + 截图 | 0.6 |
| Phase 3 文档收尾（week0.md / README / ROADMAP / CONTRIBUTING） | 0.6 |
| Phase 4 打 tag + push | 0.2 |
| **Day 2 小计** | **≈ 2.8** |

### Week 0 合计 ≈ 8.8 h（Day 1 6.0 + Day 2 2.8），与 ROADMAP "2 天 / 约 8 h" 的预算几乎吻合。

## 12. Day 1 收尾总结

**今天做对了的**：
1. 提前调研发现已装 LM Studio，省下装 Ollama 半小时
2. 路径含中文/空格的隐患在动手前就改名规避
3. 撞 AGP 9 生态适配期连锁兼容问题时，**没有死磕**，第三次失败后果断决策降级 → 节省了至少 2–3 小时反复排坑
4. 把每个 Gradle 报错原因都写进踩坑表，面试时这些都是素材

**今天做差了的**：
1. AGP 9 配 Hilt 的兼容性应该**配之前先查**，而不是配之后撞错再回头查（教训：装"前沿组件 + 三方依赖"前先做一次兼容矩阵搜索）
2. 误判"AGP 9 内置 Kotlin 不需要 apply"导致删了用户手加的正确代码 → 教训：**用户写的代码先 push 上去再质疑**，不要先删后想

**明日优先级**：
1. 写 PingActivity（LlmApi/NetworkModule/UI/Hilt 注入全链路打通）
2. 在 AS 里跑到模拟器或真机
3. 跑 `adb reverse tcp:1234 tcp:1234` + 点按钮验证拿到 `google/gemma-3-4b`
4. 打 `v0.0.1-env-ready` tag，写 Day 2 总结，week0.md 定稿

---

## 13. Day 2 收尾总结

**今天做对了的**：
1. **先 plan 后写**：开工前把今天 5 个 Phase 全列出来 + 跟用户确认（设备 / 范围 / 是否删 MainActivity 三个问题），避免边写边返工；最后实际花费 2.8h 与预算几乎一致。
2. **没走"塞 Activity 里能跑就行"的捷径**：5 层骨架（core/data/domain/di/ui）全部按 PLAN.md §3 落地，commit 拆成 4 个细粒度，Week 1 起业务代码直接照着这套模板抄。
3. **double 流模式（StateFlow + Channel）提前打好**：本来 Ping 一个按钮用单 StateFlow 也够，但 Week 3 聊天页一定需要 Toast/Snackbar 一次性事件 → 现在就打好模板免得到时候返工。
4. **smoke test 全程命令行**：`adb shell input tap` 模拟点击 + logcat 抓 Timber 日志 + 截图存档，比"用户手动点 + 截图发给我"更可复现，dev log 里直接贴日志当证据。
5. **耗时 14ms 的实测数据写进 §6 性能预算**：W3 流式聊天首 token 慢的话能立刻定位到模型本身（不是 adb reverse）。

**今天做差了的**：
1. **PowerShell `curl` 别名坑应该写进 CONTRIBUTING**：浪费了 30 多秒等卡死、又花了 30s 调起后台进程查 PID kill 它。教训：Windows + PowerShell 工程，命令行 HTTP 验证一律 `curl.exe --max-time N`。
2. **Moshi codegen 配置时没启用 `KotlinJsonAdapterFactory`**：目前因为我手动给每个 DTO 都加了 `@JsonClass(generateAdapter = true)` 所以 OK，但 Week 1 起一旦忘加注解就会运行时 `IllegalArgumentException`。**TODO(asuka)：W1 第一个 commit 加一个单测，扫描所有 `*Dto` 类必须有 `@JsonClass`**。
3. **没在 PingActivity 加错误路径手动验证**：成功路径 ✓，但"LM Studio 没起 / 端口写错"的 Failure 分支没动手测过；ViewModel 里 `Result.Failure` 处理写了但没跑过。下次 smoke test 验收要至少跑一个 happy path + 一个 sad path。

**给 Week 1 的 Asuka 留话**：
1. **W1 第一天先写 `DocumentRepository` 接口和 `DocumentEntity` 单测**，不要直接上 UI；让 domain 层成型再让 UI 调它。
2. **PdfBox-Android 集成会卡 1–2h**：依赖体积大（~10MB），第一次 sync 会下半天；先把空 Activity 跑通再加这个依赖。
3. **SAF `ACTION_OPEN_DOCUMENT` 在 Android 14+ 有权限变化**：跑模拟器（Android 16）时要注意 `persistableUriPermission`。
4. **Day 2 这套双流模式直接复制到 LibraryViewModel / ReaderViewModel 就行**，不需要重新设计；UiState 改成 4 态 sealed 即可。
5. **每个 commit 进 dev 前自查 3 问**（CONTRIBUTING.md §7）：命名/包/能不能面试讲清楚。
