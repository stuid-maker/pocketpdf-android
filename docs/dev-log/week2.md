# Week 2 · 2026-05-15 至 -

> 第 2 周：本地 RAG 数据管线（文本切块与本地向量化）。**当前进度：Day 1 计划完成，准备进入 Day 2**。

## 1. 本周目标（来自 ROADMAP）

- [ ] 分支与环境收尾：合并 W1，打 `v0.1.0-pdf-reader` 标签，开启 `feat/local-rag-indexing` 功能分支（**Day 1 目标** - 需手动执行）
- [x] Room 表扩展：`ChunkEntity` + 向量数据类型转换（TypeConverter），Schema 升至 v2（**Day 1 目标**）
- [x] 领域模型：`DocumentChunk` 实体与 Repository 接口扩展（**Day 1 目标**）
- [ ] 文本切块（Chunking）算法封装，支持基础规则与滑动窗口，编写边界单测
- [ ] 引入本地 Embedding 引擎（如 ONNX Runtime + 小型向量模型），完成模型初始化与推理调用
- [ ] WorkManager 编排：后台提取文本 -> 切块 -> 批量向量化 -> 落库持久化
- [ ] UI 状态流转闭环：观察并映射后台任务状态到列表文档徽章（INDEXING -> INDEXED）

## 2. 实际完成

> 进行中，每完成一项就移到这里。

### Day 1 计划（2026-05-15）

- [ ] **Phase 1 · 分支与基线**：
  - W1 的 `feat/library-document-import` 合入 `dev`。
  - 在 `dev` 上打基线 Tag `v0.1.0-pdf-reader`。
  - 从 `dev` 切出本周工作分支 `feat/local-rag-indexing`。
  - *注：请在终端手动执行相应的 git merge 和 tag 指令。*

- [x] **Phase 2 · Domain 层模型**：
  - 定义 `DocumentChunk` data class（`id`, `documentId`, `pageIndex`, `chunkIndex`, `text`, `embedding` FloatArray/List 等）。
- [x] **Phase 3 · data/local Room 升级**：
  - 新增 `ChunkEntity`，配置 `foreignKeys` 关联 `DocumentEntity`（`onDelete = CASCADE` 级联删除）。
  - 确定向量字段的存储方式：使用 Room `@TypeConverter` 将 `FloatArray` 转为 `ByteArray`（BLOB 类型），或者 JSON 字符串（需权衡性能与存储）。
  - 升级 `AppDatabase` `version = 2`，并在 KSP 产物中生成 `2.json` schema 验证 Diff。
- [x] **Phase 4 · DAO 与 Repository 扩展**：
  - 新增 `ChunkDao`：支持按 documentId 批量插入 `insertAll`，按 documentId 查询 `getChunksByDocumentId`。
  - 扩展 `DocumentRepository` 接口及 Impl，增加保存/读取 Chunk 的能力，或者单独抽离 `ChunkRepository`（视领域边界设计而定）。
- [x] **Phase 5 · 测试验证**：
  - 编写 `ChunkEntity` 外键约束测试（文档被删时 Chunk 必须自动被清空）。
  - 编写 `TypeConverter` 转换 Round-trip 无损测试（确保浮点精度与数组存取一致性）。
