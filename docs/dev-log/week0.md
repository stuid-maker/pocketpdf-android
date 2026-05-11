# Week 0 · 2026-05-11 至 2026-05-12

> 第 0 周：环境就绪 + 文档骨架。"动手前能阻塞 4 周后的事"全部解决。

## 1. 本周目标（来自 ROADMAP）

- [x] 写 `PLAN.md` / `ROADMAP.md` / `README.md` / `CONTRIBUTING.md` / `.gitignore`
- [x] 写 `docs/dev-log/TEMPLATE.md` 和本日志
- [x] 写 `docs/ARCHITECTURE.md` 占位 + ADR 模板
- [x] 创建本地 Git 仓库（在最终的工作目录），首次 commit（`e4d1946`）
- [x] 创建 GitHub 远端仓库 `pocketpdf-android`，push（<https://github.com/stuid-maker/pocketpdf-android>）
- [x] 决定工作目录是否迁出 `PDF小助手app`（中文+空格路径风险）→ 已改名为 `pocketPDF`
- [ ] 验证 Android Studio 安装情况
- [ ] 用 AS 新建工程，配 Version Catalog + Hilt + Room + Retrofit 依赖
- [ ] 验证 `./gradlew assembleDebug` 通过
- [x] ~~安装 Ollama，`ollama pull qwen2.5:3b-instruct`~~ → 改为复用已装 LM Studio（详见 ADR-002 修订）
- [ ] LM Studio GUI 启动 Local Server（端口 1234）
- [ ] PowerShell 跑通 `curl http://localhost:1234/v1/models`
- [ ] 模拟器或真机跑通空 App
- [ ] `adb reverse tcp:1234 tcp:1234` 设置
- [ ] App 内最小 Demo：按钮 → 调 `/v1/models` → Toast 模型名
- [ ] 打 tag `v0.0.1-env-ready`

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

## 5. 关键代码片段

W0 暂无业务代码，仅文档与配置。

## 6. 性能数据

不适用。

## 7. 测试

不适用（W0 无业务代码）。

## 8. Git 数据

- commit 数：（W0 末填）
- 标签：`v0.0.1-env-ready`（待打）

## 9. 自查问题

- [ ] 我能清楚说明 ui/domain/data 分层的意义吗？
- [ ] 我能说出为什么开发期用 PC 端 LLM 服务（LM Studio）而不是端侧推理吗？
- [ ] 我能解释为什么挑 OpenAI 兼容协议而不是 Ollama 私有协议吗？
- [ ] 我能解释 Conventional Commits 的好处吗？

## 10. 下周计划（W1 · PDF 阅读器 Demo）

- 文件选择器 + 内部存储复制
- PdfBox-Android 文本提取
- AndroidPdfViewer 渲染
- Room `DocumentEntity` + `PageEntity`
- 文档库主页 + 阅读器基础 UI

## 11. 时间分配（W0 完成后填）

| 类型 | 小时 |
|---|---|
| 调研（市场 + 技术） |   |
| 写方案文档 |   |
| 环境安装与验证 |   |
| AS 工程骨架 |   |
| LM Studio 联调 Demo |   |
