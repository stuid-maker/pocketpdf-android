# PocketPDF · 5 周路线图

> 每周末交付一个**可演示的 Demo + 一篇日志 + 一个 Git tag**。中途若进度不及预期，**立刻砍功能，不硬撑**。

起始：2026-05-11（周一）  
DDL：2026-06-15（周日，第 5 周末）

图例：⚪ Pending · 🟡 In Progress · ✅ Done · ⛔ Cut

---

## Week 0 · 环境就绪（2 天 / 实际 ~8.8h）｜✅ 完成

**目标**：把"动手前能阻塞 4 周后的事"全部解决。

- [x] 写 `PLAN.md` / `ROADMAP.md` / `README.md` / `CONTRIBUTING.md` / `.gitignore`
- [x] 写 `docs/dev-log/TEMPLATE.md` 和 `docs/dev-log/week0.md`
- [x] 创建本地 Git 仓库（在最终的工作目录），首次 commit `e4d1946`
- [x] 创建 GitHub 远端仓库 `pocketpdf-android`（公开），push
- [x] 安装 / 验证 Android Studio（`D:\AndroidStudio\`，JBR OpenJDK 21.0.9）
- [x] 用 AS 新建工程（路径 `c:\Users\33755\Desktop\pocketPDF`，纯英文无空格），配 Version Catalog
- [x] 加 Hilt 依赖，写 `PocketPdfApp.kt` 注解 `@HiltAndroidApp`，`./gradlew assembleDebug` 通过（AGP 8.7.3 黄金组合，详见 ADR-004）
- [x] 确认 LM Studio 已装（CLI `lms.exe` + 已下载 Gemma 3 4B-IT Q4_K_M）
- [x] LM Studio GUI → Developer / Local Server → Start Server（端口 1234）
- [x] PowerShell 跑 `curl http://localhost:1234/v1/models` 列出模型
- [x] 模拟器跑通 PingActivity（Medium_Phone_API_36.1 / Android 16）
- [x] `adb reverse tcp:1234 tcp:1234` 已配
- [x] App 里 PingActivity：按钮 → `LlmApi.listModels()` → Toast 显示 `google/gemma-3-4b`
- [x] 打 tag `v0.0.1-env-ready`，写 `week0.md` 收尾

**验收**：✅ 模拟器上点按钮，Toast 显示 `google/gemma-3-4b`，TextView 列出全部 3 个已加载模型；OkHttp 拦截器看到 `200 OK ... (14ms)`。截图 `docs/screenshots/w0-ping-success.png`。

---

## Week 1 · PDF 阅读器 Demo｜🟡

**目标**：导入本地 PDF、阅读、文本提取入库。

- [x] 文件选择器（SAF · `ACTION_OPEN_DOCUMENT`）
- [x] 把选中 PDF 复制到 App 内部存储（`filesDir/documents/`）
- [x] PdfBox-Android 集成，封装 `PdfTextExtractor`，按页提取文本
- [ ] AndroidPdfViewer 集成，阅读器界面（翻页、双指缩放）
- [x] Room 表：`DocumentEntity` + DAO（`PageEntity` 推迟至 W2 切块前，见 week1 决策）
- [x] 仓库 `DocumentRepository` + UseCase 四件套（含 `ImportDocumentUseCase`）
- [x] 文档库主页（RecyclerView 列表 + 空状态）
- [x] 文档卡片：标题、页数、导入时间、索引状态徽章
- [ ] 阅读器底部页码条
- [x] 单元测试：`PdfTextExtractor`（合成 PDF，Robolectric）

**验收**：导入一个 30 页 PDF → 在列表显示 → 点击进入阅读 → 翻页正常 → 重启 App 仍在。

**Tag**：`v0.1.0-pdf-reader`

---

## Week 2 · 切块 + 向量化 + 索引｜⚪

**目标**：导入后自动建向量库。

- [ ] Chunking 策略：500 tokens 窗口 + 100 overlap，按段落优先
- [ ] `ChunkDocumentUseCase`，保留 `pageStart/pageEnd` 元数据
- [ ] Sentence-Embeddings-Android 集成，模型下载到 `filesDir/models/`
- [ ] 封装 `Embedder`（批量 embed、Flow 进度）
- [ ] Room 表：`ChunkEntity`（含 `embedding ByteArray`）+ DAO
- [ ] `IndexDocumentUseCase`（chunk → embed → store）
- [ ] WorkManager `IndexWorker` + 前台通知
- [ ] UI：文档卡片显示索引进度
- [ ] 单元测试：chunking 边界（空文档、超长段、纯中文）

**验收**：导入 100 页 PDF → 通知栏可见进度 → 完成后数据库有 chunks → 重启 App 状态保留。

**Tag**：`v0.2.0-indexed`

---

## Week 3 · 检索 + LLM 桥接 + 总结｜⚪

**目标**：能让 LLM 基于文档片段生成内容。

- [ ] `RetrieveChunksUseCase`（余弦相似度 Top-K，K=5）
- [ ] Retrofit + OkHttp 配置，超时 60s，日志拦截器
- [ ] `LlmApi`：`/v1/chat/completions`、`/v1/models`（embedding 走端侧 ONNX，不走 HTTP）
- [ ] SSE / NDJSON 流式响应 → `Flow<String>`
- [ ] `SummarizeDocumentUseCase`（MapReduce：每 chunk 出小结 → 合并）
- [ ] 阅读器顶部按钮："总结本页" / "总结全文"
- [ ] 总结结果浮层 + 流式打字效果 + 复制按钮
- [ ] 设置页：LLM Base URL（默认 `http://localhost:1234/v1`）、模型名、可选 API Key（云端兼容）
- [ ] 单元测试：检索排序、MapReduce 合并

**验收**：阅读时点"总结本页" → 流式输出中文摘要，无明显卡顿。

**Tag**：`v0.3.0-summary`

---

## Week 4 · 问答 + 引用回溯 + 抛光｜⚪

**目标**：完整问答闭环，能拿出去演示。

- [ ] 聊天 UI（RecyclerView 双气泡 + 输入框 + 发送/停止按钮）
- [ ] `AskDocumentUseCase`：embed query → retrieve → 构造 prompt → 流式生成
- [ ] Prompt 模板：要求模型答复时引用 `[来源: 第N页]`
- [ ] 答案末尾解析引用，渲染为可点击 chip
- [ ] 点引用 → 跳回阅读器 → 滚动到该页 → **高亮关键句**
- [ ] 聊天历史持久化（Room `ChatMessageEntity`）
- [ ] 错误处理：LLM 服务离线 / 超时 / 解析失败 / PDF 损坏，统一 Snackbar
- [ ] 应用图标、启动页（Splash Screen API）
- [ ] 主题：浅色 + 深色（systemDefault）
- [ ] 重要 UX：长按问答消息 → 复制 / 分享 / 重新生成

**验收**：完整 Demo 流程：导入 → 阅读 → 提 3 个问题 → 流式回答带页码 → 点引用跳转 → 退出再进入历史还在。

**Tag**：`v0.4.0-qa`

---

## Week 5 · 测试 + 文档 + Demo + 简历素材｜⚪

**目标**：作品集级别的工程化收尾。

- [ ] domain 层单元测试，覆盖率 ≥ 70%
- [ ] 集成测试：`IndexWorker`、关键 Repository
- [ ] Espresso：导入流程、问答流程（用 Hilt test runner）
- [ ] `docs/ARCHITECTURE.md` 完整版（含 Mermaid 模块图、数据流图）
- [ ] `README.md`：功能截图、GIF、快速开始
- [ ] GitHub Actions：lint + unit test，README 加徽章
- [ ] R8 / ProGuard 规则（保留 PdfBox、Sentence-Embeddings 类）
- [ ] Release build 签名，`./gradlew assembleRelease`，APK 体积优化
- [ ] Demo 视频 60–90 秒，B 站上传
- [ ] `INTERVIEW_NOTES.md`（私有，不提交）：自答 20 个常问
- [ ] 简历项目段（一句话 / 一段话 / 详细描述 三个版本）

**验收**：仓库一眼能看懂 / Demo 流畅 / CI 绿 / 面试问答有底。

**Tag**：`v1.0.0-release`

---

## 砍功能优先级（如进度不及预期）

按砍掉的顺序（**从最先砍开始**）：

1. 深色模式（W4）
2. 长按消息的"分享"项（W4）
3. 设置页的"清除缓存"高级项（W4）
4. 跳转引用时的"高亮关键句"（W4）→ 只跳转不高亮
5. Espresso 测试（W5）→ 只保留 1 个
6. 总结的"MapReduce"分两步 → 直接全文 prompt（仅限短文档）
7. 主题切换 → 只做浅色

**绝不砍**：Clean Architecture 分层、domain 单元测试、引用页码（这是简历核心卖点）、Git 提交规范、开发日志。
