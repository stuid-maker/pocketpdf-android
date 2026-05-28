# PocketPDF · 简历项目描述

> 三个版本：一句话 / 短段落 / 详细描述。
> 写于 Week 5（2026-06-01），适配 Android 开发岗位。

---

## V1 — 一句话（约 30 字）

PocketPDF：一款离线 RAG PDF 阅读器 Android App，支持 AI 问答与带页码引用的摘要生成。

---

## V2 — 短段落（约 100 字）

PocketPDF 是一款基于 RAG（检索增强生成）架构的 Android PDF 阅读器。用户导入 PDF 后，App 自动完成文本提取、切块与端侧向量化（MediaPipe）。用户可提问并获取流式 AI 回答，答案附带可点击的页码引用，点击后跳转到阅读器的对应页面。LLM 推理部署在 PC 端（LM Studio），通过 OpenAI 兼容协议桥接。技术栈：Kotlin、Clean Architecture + MVVM、Hilt DI、Room、WorkManager、Compose（Chat UI）、PdfBox、MediaPipe。

---

## V3 — 详细描述（约 280 字）

**项目名称**：PocketPDF — 离线 RAG PDF 阅读器 for Android

**项目简介**：
PocketPDF 是一款 Android 端 PDF 阅读器，核心特色是内建 RAG（检索增强生成）问答系统。支持本地 PDF 导入、智能文本索引、基于自然语言的文档问答、以及带页码引用回溯的 AI 摘要。

**技术亮点**：
- **Clean Architecture + MVVM 分层**：domain 层纯 Kotlin、零 Android 依赖，保障业务逻辑的可测试性（覆盖率 > 70%）。单 Module 结构降低了新手配置复杂度。
- **端侧向量化**：使用 Google MediaPipe TextEmbedder 在本地完成文本切片向量化，无需网络连接，保护用户隐私。
- **RAG 问答闭环**：用户提问 → 端侧 embedding → 内存余弦相似度 Top-K 检索 → 构建带页码标记的上下文 → LLM 流式生成 → 引用解析 → 点击跳转阅读器对应页。
- **双 UI 范式**：阅读器与文档库使用 XML + ViewBinding（稳定可靠），聊天问答界面使用 Jetpack Compose（LazyColumn 消息列表 + 可点击 AnnotatedString 引用）。
- **离线优先**：LLM 通过 LM Studio（PC 端，OpenAI 兼容协议）+ adb reverse 桥接，Embedding 完全端侧，文档数据不出 App 内部存储。

**技术栈**：Kotlin、Hilt、Room、WorkManager、OkHttp、Retrofit、PdfBox-Android、MediaPipe、Jetpack Compose、Material 3、MockK、Turbine、Robolectric。

**项目亮点**：5 周单人从零交付，每周末交付可演示 Git tag。核心功能完整闭环：导入 → 阅读 → 索引 → 问答 → 引用跳转 → 历史持久化。
