# PocketPDF · 架构说明

> 本文随项目演进同步更新。W0 仅占位；W5 完整版。

最近更新：Week 0 · 2026-05-11

---

## 1. 概览

PocketPDF 采用 **Clean Architecture 思想 + 单 Module 分层** 的方式实现。设计核心目标：

1. **领域逻辑可测试**：`domain` 层是纯 Kotlin，不依赖任何 Android API
2. **依赖方向单一**：`ui → domain ← data`
3. **可替换性**：LLM 后端在 LM Studio / Ollama / 云端 OpenAI 兼容 API（DeepSeek、通义、Together AI…）之间切换不影响 `domain` 和 `ui`
4. **新手友好**：单 Gradle Module，避免多模块 Gradle 配置陷阱

## 2. 模块边界

```mermaid
graph TD
    UI[ui<br/>Activity/Fragment/ViewModel] --> DOMAIN
    DI[di<br/>Hilt Modules] -.binds.-> DOMAIN
    DI -.provides.-> DATA
    DATA[data<br/>Room/Retrofit/PdfBox] --> DOMAIN
    DOMAIN[domain<br/>UseCase/Model/RepoInterface]
    UI --> CORE
    DATA --> CORE
    DOMAIN --> CORE
    CORE[core<br/>Result/DispatcherProvider/ext]
```

## 3. 数据流（单向）

```
View ── intent ──▶ ViewModel ── invoke ──▶ UseCase ── call ──▶ Repository
View ◀── state ── ViewModel ◀── result ── UseCase ◀── data ── Repository
```

- ViewModel 持有 `StateFlow<UiState>`
- View 用 `repeatOnLifecycle(STARTED)` 收集
- UseCase 返回 `Flow<Result<T>>` 或 `suspend fun`
- 错误统一 `Result<T>` 封装

## 4. 关键子系统（W1–W4 填充）

### 4.1 PDF 解析与渲染（W1）

待补。

### 4.2 切块与向量化（W2）

待补。

### 4.3 检索与 LLM 调用（W3）

待补。

### 4.4 RAG 问答（W4）

待补。

## 5. 关键决策记录（ADR）

每个重要的技术决策记录在此，参考 [Architecture Decision Records](https://adr.github.io/)。

### ADR-001: 用 XML 而非 Compose

- **背景**：开发者是 Android 新手，5 周硬 DDL
- **决策**：用 XML + ViewBinding + Material Components
- **理由**：XML 资料和 AI 训练数据更充足，新手 + AI 辅助路径更稳；Compose 状态管理对新手是额外认知负担
- **代价**：与 2026 主流（Compose）有距离，面试时要解释清楚选择理由
- **日期**：2026-05-11

### ADR-002: 用「PC 端 LLM 服务 + OpenAI 兼容协议」桥接，而非端侧推理

- **背景**：5 周内无法稳定集成 LiteRT-LM 端侧推理；开发机已装 LM Studio，模型现成
- **候选**：
  - A. 端侧推理（LiteRT-LM + Gemma 3n）：开发周期长、设备适配复杂
  - B. PC 端 **Ollama** + Ollama 私有协议（原计划）
  - C. PC 端 **LM Studio** + **OpenAI 兼容协议**（最终选）
  - D. 直接对接云端 OpenAI / DeepSeek API
- **决策**：开发期采用 **C**；契约层固定为 OpenAI Chat Completions 协议；端侧推理列为 v2；云端 API 因协议同构，未来可零改动切换
- **理由**：
  1. **零下载成本**：LM Studio 已装，Gemma 3 4B-IT Q4_K_M / Gemma 3n E4B-IT Q8_0 已下载
  2. **协议通用**：OpenAI Chat Completions 是事实标准，DeepSeek / 通义 / Together AI / vLLM 全兼容；Ollama 私有协议只服务 Ollama
  3. **未来扩展便宜**：从 LM Studio 切到云端 DeepSeek 只需改 `BASE_URL` 和 API Key
  4. **简历叙事更通用**：写"对接 OpenAI 兼容 LLM 服务"比"对接 Ollama"通用性更强
- **代价**：演示需要 PC 在线；LM Studio Server 需要手动在 GUI 里点 Start（或 `lms server start`，可写脚本）；不能脱机使用
- **端口**：`localhost:1234`（adb reverse `tcp:1234 tcp:1234`）
- **日期**：2026-05-11（初稿）/ 2026-05-11（修订：Ollama → LM Studio + OpenAI 兼容）

### ADR-004: 主动降级到 AGP 8.7.3 黄金组合，放弃 AGP 9.0.1

- **背景**：AS 默认生成 AGP 9.0.1 + Gradle 9.2.1 + Kotlin 2.2.20 工程；Week 0 配 Hilt + KSP 时连续撞 3 类兼容性问题：
  1. KSP 2.2.20-2.0.x 与 AGP 9 built-in Kotlin 互不兼容（KSP 明确抛错）
  2. KGP `kotlin("android")` 2.2.20 跟 AGP 9 ApplicationExtensionImpl 出现 ClassCastException
  3. Hilt 2.59 在 AGP 9 上有 ComponentTreeDeps 缺失问题（2.59.2 才修），生态稳定性不足
- **候选**：
  - A. 继续在 AGP 9 上死磕（升 Kotlin 到 2.3.x + KSP 2.3.x + Hilt 2.59.2）
  - B. 降到 AGP 8.13（2025 末），Kotlin 2.2.20 + Hilt 2.58
  - C. 降到 **AGP 8.7.3 黄金组合**：Kotlin 2.0.21 + KSP 2.0.21-1.0.28 + Hilt 2.52 + Gradle 8.10.2
- **决策**：C
- **理由**：
  1. **生态成熟**：2024 末 - 2025 中的事实主流组合，全网文档、AI 训练数据、Stack Overflow 答案密度最高
  2. **零兼容性意外**：Hilt 2.52、KSP 2.0.21、AGP 8.7 三方半年以上的实战互验
  3. **代价小**：仅放弃 API 36 预览（实际改用 API 35 = Android 15），项目层零影响
  4. **节省 Week 0 预算**：撞兼容性坑会把 Week 0 拖到 W1，影响后续节奏
- **代价**：
  - targetSdk/compileSdk 35（不是 36）
  - Gradle 8.10.2（不是 9.2.1）
  - 简历无法标"AGP 9 早鸟"——但 AGP 9 在 2026 年还不是招聘要求
- **可逆性**：v2 可升级到 AGP 9 稳定版（预计 2026 年中），仅需更新 `libs.versions.toml` 和处理 `BuildConfig`/built-in Kotlin 几个点
- **关键避坑总结**：
  - AGP 8 起 `BuildConfig` 默认禁用，需要 `buildFeatures { buildConfig = true }`
  - KSP + AGP 9 built-in Kotlin 暂不兼容，必须 `android.builtInKotlin=false` 并显式 apply `kotlin("android")`
  - `kotlin("android")` 是否 apply 取决于 AGP 版本：AGP 9 内置不要 apply，AGP 8 必须 apply
- **日期**：2026-05-11

### ADR-003: 单 Module 而非多 Module

- **背景**：Clean Architecture 通常多模块化
- **决策**：单 `app` module + 严格分包
- **理由**：避免新手在多模块 Gradle 配置上浪费 1–2 天；分包 + 包路径规则也能保证依赖方向
- **代价**：编译时无法强制依赖方向（靠人工 review + 包路径规则）
- **日期**：2026-05-11

## 6. 性能预算

| 操作 | 目标 | 备注 |
|---|---|---|
| 冷启动 | < 2s | minSdk 26 中端机 |
| 100 页 PDF 文本提取 | < 5s | PdfBox-Android |
| 100 页 PDF 索引（embed） | < 30s | 后台 WorkManager |
| 检索 Top-5 | < 200ms | 内存余弦相似度 |
| LLM 首 token | < 3s | LM Studio + Gemma 3 4B-IT Q4_K_M |
| 流式 token 速度 | > 15 tokens/s | PC 端 |

## 7. 安全与隐私

- 文档复制到 App 内部存储（`filesDir`），其他应用无法访问
- 文档不上云
- LLM 调用走 `adb reverse` 到 localhost:1234（LM Studio），**数据不出 PC**
- 不收集任何遥测数据
