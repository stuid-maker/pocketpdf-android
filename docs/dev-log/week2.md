# Week 2 · 2026-05-15 至 -

> 第 2 周：本地 RAG 数据管线（文本切块与本地向量化）。**当前进度：W2 全部完成**。

## 1. 本周目标（来自 ROADMAP）

- [x] 分支与环境收尾：合并 W1，打 `v0.1.0-pdf-reader` 标签，开启 `feat/local-rag-indexing` 功能分支
- [x] Room 表扩展：`ChunkEntity` + 向量数据类型转换（TypeConverter），Schema 升至 v2
- [x] 领域模型：`DocumentChunk` 实体与 Repository 接口扩展
- [x] 文本切块（Chunking）算法封装，支持基础规则与滑动窗口，编写边界单测
- [x] 引入本地 Embedding 引擎（MediaPipe Text Embedder），完成模型初始化与推理调用（**Day 3 目标**）
- [x] WorkManager 编排：后台提取文本 -> 切块 -> 批量向量化 -> 落库持久化（**Day 4 目标**）
- [x] UI 状态流转闭环：观察并映射后台任务状态到列表文档徽章（INDEXING -> INDEXED）

## 2. 实际完成

### Day 1 计划（2026-05-15）- 已完成
... (略)

### Day 2 计划（2026-05-16）- 已完成
... (略)

### Day 3 计划（2026-05-17）- 已完成

- [x] **Phase 1 · 引擎选型与集成**：放弃了不稳定的 JitPack 第三方库，转向更成熟的 **Google MediaPipe Text Embedder**。
- [x] **Phase 2 · 依赖配置**：添加了 `com.google.mediapipe:tasks-text:0.10.14` 依赖，并配置了 JitPack 仓库（备用）。
- [x] **Phase 3 · 接口与实现**：
  - 定义了 `EmbeddingEngine` 接口。
  - 实现了 `MediaPipeEmbeddingEngine`，支持从 Assets 加载 `.tflite` 模型并进行文本向量化。
- [x] **Phase 4 · DI 装配**：创建了 `EmbeddingModule`，完成 Hilt 注入。
- [x] **Phase 5 · 编译验证**：成功编译并通过 Hilt 注入检查。

---
### 💡 重要提示：模型文件准备
由于模型文件较大，未直接提交到代码库。请手动执行以下步骤：
1. 下载 [Universal Sentence Encoder (tflite)](https://storage.googleapis.com/mediapipe-models/text_embedder/universal_sentence_encoder/float32/1/universal_sentence_encoder.tflite)。
2. 在项目中创建目录：`app/src/main/assets/models/`。
3. 将下载的文件重命名为 `universal_sentence_encoder.tflite` 并放入该目录。

**Day 4 预告**：编写 `IndexWorker`，将切块算法与 Embedding 引擎串联起来，实现自动化索引。

---

### Day 4 计划（2026-05-19）- 已完成

- [x] **Phase 1 · 依赖配置**：添加 WorkManager (`androidx.work:work-runtime-ktx:2.10.0`) + Hilt Work 集成 (`androidx.hilt:hilt-work:1.2.0`) 依赖。
- [x] **Phase 2 · Hilt WorkManager 集成**：
  - `PocketPdfApp` 实现 `Configuration.Provider`，注入 `HiltWorkerFactory`
  - `AndroidManifest.xml` 禁用默认 `WorkManagerInitializer`，添加 `FOREGROUND_SERVICE` + `POST_NOTIFICATIONS` 权限
- [x] **Phase 3 · IndexWorker 实现** (`data/indexing/IndexWorker.kt`)：
  - `@HiltWorker` + `CoroutineWorker`，注入 `DocumentRepository` / `PdfTextExtractor` / `TextChunker` / `EmbeddingEngine`
  - 编排流程：读取文档 → 标记 INDEXING → 提取 PDF 文本 → 切块 → 批量向量化 → 回填 embedding → 落库 → 标记 INDEXED
  - 异常处理：任何步骤失败 → 标记 FAILED（二次异常静默记录）
  - 前台服务通知（含取消 Action），避免后台被系统杀死
  - 空文本 PDF（如扫描件）直接标记 INDEXED，不执行向量化
- [x] **Phase 4 · 触发时机**：
  - 新增 `IndexingScheduler` 接口 + `WorkManagerIndexingScheduler` 实现，抽象 WorkManager 依赖以便单测
  - `LibraryViewModel.onImportRequested()` 导入成功后自动调用 `indexingScheduler.schedule(documentId)`
  - Hilt 绑定 `IndexingScheduler → WorkManagerIndexingScheduler`
- [x] **Phase 5 · 编译与测试**：编译通过，12 个 LibraryViewModel 测试全部通过。

#### 决策 17：IndexingScheduler 接口抽象

**背景**：导入成功后 LibraryViewModel 需要触发 IndexWorker。直接在 ViewModel 中调用 `WorkManager.getInstance(context).enqueue()` 有两个问题：1) 需要注入 `@ApplicationContext`，让 ViewModel 引入 Android 依赖；2) 单测无法轻松 mock 静态方法 `WorkManager.getInstance()`。

**决策**：抽象一个 `IndexingScheduler` 接口（仅 `fun schedule(documentId: Long)`），`WorkManagerIndexingScheduler` 持有 `@ApplicationContext` 做真实现，单测用 `mockk(relaxed = true)` 注入。

#### 决策 18：IndexWorker 前台服务策略

**背景**：IndexWorker 执行 CPU + IO 混合任务（PDF 解析 + MediaPipe 推理），时长可能 30s~2min。Android 8+ 后台服务限制可能杀死进程。

**决策**：使用 `CoroutineWorker.setForeground()` 挂前台服务通知，展示"Indexing document..."文案 + 取消按钮。通知 channel 设为 `IMPORTANCE_LOW` 避免打扰。取消按钮使用 `WorkManager.createCancelPendingIntent(id)` 确保原子性。

---

**Day 5 预告**：UI 状态流转闭环——通过 `observeDocuments()` 的 Flow 自动映射 `IndexStatus` 到列表文档卡片的索引徽章（NOT_INDEXED / INDEXING / INDEXED / FAILED），无需手动刷新。

---

### Day 5 计划（2026-05-19）- 已完成

- [x] **Phase 1 · 徽章视觉设计**：在 `colors.xml` 添加 4 组状态色板（灰/橙/绿/红），`item_document.xml` 徽章区新增 `ProgressBar`（INDEXING 态可见）+ 外层 `LinearLayout` 用作圆角背景容器。
- [x] **Phase 2 · Adapter 绑定**：`DocumentListAdapter.bindBadge()` 根据 `IndexStatus` 动态切换背景色、文字色、ProgressBar 可见性；FAILED 态徽章设为可点击。
- [x] **Phase 3 · 重试机制**：
  - `LibraryViewModel.onRetryIndexing(documentId)` 调用 `indexingScheduler.schedule()`
  - `LibraryActivity` 通过 `onRetryIndex` lambda 连接 ViewModel
  - Adapter 仅 FAILED 态响应点击；其他状态 `isClickable = false`
- [x] **Phase 4 · 编译与测试**：编译通过，42 个单元测试通过。

#### 决策 19：徽章状态由 Room Flow 自动驱动

**背景**：IndexWorker 写入 `Document.indexStatus` 到 DB 后，UI 需要感知变更。两种方案：1) 手动观察 `WorkInfo` 并轮询刷新；2) 依赖现有 `observeDocuments()` Flow（Room 自动推送 UPDATE）。

**决策**：选方案 2。Room DAO 的 `observeAll()` 返回 `Flow<List<Document>>`，任何 `UPDATE` 语句都会自动重发。IndexWorker 写 `INDEXING` → `INDEXED` 时，UI 自动收到新列表并调用 `adapter.submitList()`，DiffUtil 按 `Document.equals()` 比对发现 `indexStatus` 变化，局部刷新对应卡片。**零额外代码**。

#### W2 总结

| Day | 内容 | 状态 |
|-----|------|------|
| D1-D2 | 分支合并、ChunkEntity Schema、DocumentChunk 领域模型 | Done |
| D3 | MediaPipe Embedding 引擎集成 | Done |
| D4 | IndexWorker 后台索引编排 | Done |
| D5 | UI 索引状态徽章 + 失败重试 | Done |

**W2 成果**：完整的本地 RAG 数据管线——PDF 导入 → 后台文本提取 → 滑动窗口切块 → MediaPipe 向量化 → Room 持久化 → UI 自动感知索引状态。**W3 进入 LLM 检索与对话阶段**。
