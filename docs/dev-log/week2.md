# Week 2 · 2026-05-15 至 -

> 第 2 周：本地 RAG 数据管线（文本切块与本地向量化）。**当前进度：Day 2 计划完成**。

## 1. 本周目标（来自 ROADMAP）

- [x] 分支与环境收尾：合并 W1，打 `v0.1.0-pdf-reader` 标签，开启 `feat/local-rag-indexing` 功能分支
- [x] Room 表扩展：`ChunkEntity` + 向量数据类型转换（TypeConverter），Schema 升至 v2
- [x] 领域模型：`DocumentChunk` 实体与 Repository 接口扩展
- [x] 文本切块（Chunking）算法封装，支持基础规则与滑动窗口，编写边界单测（**Day 2 目标**）
- [ ] 引入本地 Embedding 引擎（如 ONNX Runtime + 小型向量模型），完成模型初始化与推理调用（**Day 3 目标**）
- [ ] WorkManager 编排：后台提取文本 -> 切块 -> 批量向量化 -> 落库持久化
- [ ] UI 状态流转闭环：观察并映射后台任务状态到列表文档徽章（INDEXING -> INDEXED）

## 2. 实际完成

### Day 1 计划（2026-05-15）- 已完成

- [x] **Phase 1 · 分支与基线**：完成 W1 归档与 W2 分支切换。
- [x] **Phase 2 · Domain 层模型**：定义 `DocumentChunk`。
- [x] **Phase 3 · data/local Room 升级**：`ChunkEntity`落地，v2 Schema 导出。
- [x] **Phase 4 · DAO 与 Repository 扩展**：`ChunkDao` 与 `DocumentRepositoryImpl` 扩展完成。
- [x] **Phase 5 · 测试验证**：完成外键级联删除与向量转换器的单元测试。

### Day 2 计划（2026-05-16）- 已完成

- [x] **Phase 1 · 算法接口定义**：定义了 `TextChunker` 接口。
- [x] **Phase 2 · 实现滑动窗口切分算法**：实现了 `SlidingWindowChunker`，支持自定义 `chunkSize` 和 `chunkOverlap`。
- [x] **Phase 3 · 鲁棒性处理**：在切块前对 PDF 提取的原始文本进行清洗（去除多余空白符、换行符）。
- [x] **Phase 4 · 算法单元测试**：编写了 `SlidingWindowChunkerTest`，覆盖了短文本、长文本滑动窗口、多页页码保持、空白符清理等场景。单元测试全部通过。
- [x] **Phase 5 · 接入点预研**：完成了 Hilt `ChunkingModule` 的配置，将 `TextChunker` 注入到依赖图中。

---
**Day 3 预告**：引入 ONNX Runtime 并加载本地 Embedding 模型，将文本切片转化为实数向量。
