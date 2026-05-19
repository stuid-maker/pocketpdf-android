# PocketPDF · Android 本地 PDF · RAG 阅读助手

> 一款让你在阅读 PDF 时**就近接入 AI** 的安卓应用：自动切块、向量检索、生成摘要并基于内容回答问题，全程可断网（仅依赖你 PC 上的本地 LLM 服务）。

[![status](https://img.shields.io/badge/status-W1%20done-brightgreen)](https://github.com/stuid-maker/pocketpdf-android)
[![Android](https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android)](#)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0-7F52FF?logo=kotlin)](#)
[![License](https://img.shields.io/badge/license-MIT-green)](#)

仓库：<https://github.com/stuid-maker/pocketpdf-android>

## 项目目标

一个**架构清晰、功能闭环、有完整开发日志**的 Android RAG 应用

## 核心功能（v1.0 范围）

- [x] 项目骨架与文档（W0）
- [x] 本地 PDF 导入、阅读、翻页（W1）
- [ ] 文本切块 + 向量化 + 索引入库（W2 · **进行中**）
- [ ] 检索 + 调用本地 LLM 生成摘要（流式输出）（W3）
- [ ] 基于文档内容的问答 + 引用回溯到原文页码（W4）
- [ ] 单元测试、集成测试、CI、Demo 视频（W5）

## 技术栈

| 层 | 选型 |
|---|---|
| 语言 / 平台 | Kotlin · minSdk 26（Android 8.0+） |
| UI | XML + Material Components + ViewBinding |
| 架构 | Clean Architecture 思想 · 单 Module 分层（data/domain/ui） |
| 模式 | MVVM + Repository |
| DI | Hilt |
| 异步 | Coroutines + Flow + StateFlow |
| 本地数据 | Room |
| 网络 | Retrofit + OkHttp + okhttp-sse |
| PDF 解析 | PdfBox-Android（文本） |
| PDF 渲染 | AndroidPdfViewer / PdfRenderer |
| Embedding | Sentence-Embeddings-Android（MiniLM-L6-v2） |
| LLM 后端 | **PC 端 LM Studio**（默认 `Gemma 3 4B-IT Q4_K_M`），**OpenAI 兼容协议**，通过 `adb reverse` 桥接 |
| 测试 | JUnit4 + MockK + Turbine + Espresso |
| CI | GitHub Actions |

## 架构总览

```
┌─────────────────┐
│   ui (XML+VM)   │  ←  Activity/Fragment/ViewModel/Adapter
└────────┬────────┘
         │ depends on
┌────────▼────────┐
│     domain      │  ←  Use Cases · Models · Repository Interfaces
└────────▲────────┘
         │ implements
┌────────┴────────┐
│      data       │  ←  Room · Retrofit(OpenAI-compat) · PdfBox · Embedder
└─────────────────┘
```

依赖方向严格：`ui → domain ← data`，`domain` 不依赖 any Android API。

详见 [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md)。

## 快速开始

> W1 已完结，本地阅读器核心功能可用。

PC 端准备：

```powershell
# 1. 安装 LM Studio: https://lmstudio.ai/
# 2. 在 GUI 里下载/选择模型（默认 Gemma 3 4B-IT Q4_K_M）
# 3. 进入 LM Studio → Developer / Local Server 页签 → Start Server（默认端口 1234）
# 4. 验证服务（PowerShell）
curl http://localhost:1234/v1/models
```

手机端（Android Studio 跑起后）：

```powershell
# 把手机的 localhost:1234 转发到 PC 的 LM Studio
adb reverse tcp:1234 tcp:1234
```

## 文档

- [`PLAN.md`](PLAN.md) — 项目总方案（技术选型、目录结构、风险）
- [`ROADMAP.md`](ROADMAP.md) — 5 周任务路线图（可勾选）
- [`CONTRIBUTING.md`](CONTRIBUTING.md) — 编码规范 + Git 提交规范 + AI 辅助约定
- [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) — 架构详解
- [`docs/dev-log/`](docs/dev-log/) — 每周开发日志

## 路线图状态

| 周 | 主题 | 状态 | Tag |
|---|---|---|---|
| W0 | 环境就绪 + 文档骨架 | ✅ Done | `v0.0.1-env-ready` |
| W1 | PDF 阅读器 Demo | ✅ Done | `v0.1.0-pdf-reader` |
| W2 | 切块 + 向量化 + 索引 | ⏳ In Progress | `v0.2.0-indexed` |
| W3 | 检索 + LLM 桥接 + 总结 | ⚪ Pending | `v0.3.0-summary` |
| W4 | 问答 + 引用回溯 + 抛光 | ⚪ Pending | `v0.4.0-qa` |
| W5 | 测试 + 文档 + Demo | ⚪ Pending | `v1.0.0-release` |

## License

MIT
