# Week 4 · 2026-05-20 至 2026-05-27

> 第 4 周：问答 + 引用回溯 + 抛光。**完整问答闭环，能拿出去演示。**
>
> **D0–D3 完成**（2026-05-20）：Prompt 迁移 + ProGuard + Compose + AskDocumentUseCase + 聊天 UI + 引用解析跳转。

## 1. 本周目标（来自 ROADMAP）

- [x] 聊天 UI（Compose 双气泡 + 输入框 + 发送/停止按钮）
- [x] `AskDocumentUseCase`：embed query → retrieve → RAG prompt → 流式生成
- [x] Prompt 模板：要求模型答复时引用 `[第N页]`
- [x] 答案末尾解析引用，渲染为可点击 chip
- [x] 点引用 → 跳回阅读器 → 滚动到该页
- [ ] 聊天历史持久化（Room `ChatMessageEntity`）
- [ ] 错误处理：LLM 服务离线 / 超时 / 解析失败
- [ ] 摘要缓存：全文/本页摘要生成后持久化
- [x] ProGuard/R8 规则
- [x] 引用解析容错

---

## 2. 实际完成

### Day 0 · 快修 + Compose ✅
- Prompt 模板迁移 → `PromptTemplates.documentSummary()`
- ProGuard 规则：PdfBox、MediaPipe、Moshi、Room、OkHttp
- Compose BOM 2024.09 + Material3 + `kotlin-compose` 插件
- 编译通过 2m47s

### Day 1 · AskDocumentUseCase ✅
- `AskDocumentUseCase.kt` — 检索 Top-K → 拼上下文 + 页码 → RAG prompt → 流式
- `PromptTemplates.ragQuery` 更新：要求 `[第N页]` 格式标注页码
- 3 测试全绿（正常流 / 空检索 fallback / LLM 异常）

### Day 2 · 聊天 UI (Compose) ✅
- `ChatActivity.kt` — Compose LazyColumn 双气泡 + 输入栏 + 发送/停止
- `ChatViewModel.kt` — 消息管理 + 流式收集 + 取消
- `ChatInputBar` — 输入框 + 发送/停止按钮切换
- `ChatBubble` — 用户蓝色右对齐 / AI 灰色左对齐 + 流式进度条
- `ReaderActivity` 工具栏 "问答" → ChatActivity
- 编译通过 21s，真机验证通过

---

## 3. Day 3 计划（2026-05-21）— 引用解析 + 跳转

**目标**：AI 回复中的页码引用变成可点击 chip，点击跳回阅读器。

### Phase 1 · CitationParser 引用解析器

- **文件**：`core/CitationParser.kt`（纯 Kotlin，不依赖 Android）
- **输入**：AI 回复全文 `String`
- **输出**：解析出的页码列表 `List<Int>`（0-based pageIndex）
- **容错正则**：
  - `[第N页]` / `[第 N 页]`（标准格式）
  - `[来源: 第N页]`（备用格式）
  - `[Page N]`（英文）
  - `(P.N)` / `【来源N】`（其他变体）
- **去重**：同页码只返回一次
- **单元测试**：覆盖各种格式 + 边界（无引用/非法页码/重复）

### Phase 2 · 引用 Chip 渲染

- AI 消息正文中匹配到的 `[第N页]` 替换为可点击 chip
- Compose 实现：`AnnotatedString` + `ClickableText` 标记页码区间
- 点击 chip → Intent 跳转 `ReaderActivity`（带 `documentId` + `pageIndex`）
- 视觉：chip 用蓝色下划线或底色区分

### Phase 3 · ReaderActivity 接收外部跳转页

- `ReaderActivity.newIntent(context, documentId, pageIndex?)` — 支持可选页码
- 收到 pageIndex 后：打开 PDF → 直接渲染指定页（跳过 page 0）
- 在 Companion 保留旧签名兼容

---

## 4. Day 4 计划（2026-05-22）— 多模型预设 + System Prompt

**目标**：设置页加模型预设（一键切换 LM Studio/DeepSeek/自定义）和系统提示词自定义。改动最小，体验提升最大。

### Phase 1 · 模型预设

- [ ] `PresetModels` 定义预设列表：LM Studio 本地 / DeepSeek / 通义千问 / 自定义
- [ ] 每个预设含默认 baseUrl + modelName
- [ ] 设置页加"模型预设"下拉，选中自动填地址+模型名
- [ ] 已填写的自定义值保留

### Phase 2 · System Prompt

- [ ] `SettingsDataStore` 加 `systemPrompt: Flow<String>`（默认空）
- [ ] 设置页加"系统提示词"多行输入框
- [ ] `PromptTemplates` 接 DataStore：RAG/摘要 prompt 前拼接用户自定义 system prompt
- [ ] ChatViewModel + ReaderViewModel 读 systemPrompt 并注入消息

### Phase 3 · 编译验证

---

## 5. Day 5 计划（2026-05-23）— 段落切块 + 错误处理

**目标**：切块策略升级 + 聊天页错误反馈闭环。

### Phase 1 · 段落切块

- [ ] `ParagraphChunker` 实现（按双换行 `\n\n` 切分）
- [ ] `ChunkingModule` DI：默认 `SlidingWindowChunker`，可选切换
- [ ] 设置页加"切块策略"选项：按长度 / 按段落

### Phase 2 · 错误处理

- [ ] `ChatViewModel` 捕获异常 → `ChatUiState.error` → Snackbar 显示
- [ ] 网络错误：发送按钮变重试图标
- [ ] `ReaderViewModel` 总结失败：浮层内重试按钮
- [ ] 未索引 PDF 进聊天：提示 + 自动返回

---

## 6. Day 6–7 计划（2026-05-24/25）— 聊天历史 + 摘要缓存 + 抛光

**目标**：持久化闭环 + 用户体验完善。

### Phase 1 · 聊天历史持久化

- [ ] Room `ChatMessageEntity`（v3 migration）+ DAO + Repository
- [ ] ChatViewModel 集成：退出再进历史还在

### Phase 2 · 摘要缓存

- [ ] `SummaryCache`：`documentId + scope` → 文本（DataStore 或 Room）
- [ ] `SummarizeDocumentUseCase` 先查缓存，命中跳过 LLM

### Phase 3 · 抛光

- [ ] 长按消息菜单（复制/重新生成）
- [ ] 截图归档
- [ ] ClickableText deprecated 修掉
- [ ] `.claude/` 加 `.gitignore`

---

## 7. 依赖关系

```
D0 (快修+Compose) ──▶ D1 (AskUseCase) ──▶ D2 (聊天UI) ──▶ D3 (引用跳转) ──▶ D4 (持久化)
                                                                      ▶ D5 (错误处理)
                                                                      ▶ D6 (缓存+抛光)
```

- D3 引用跳转依赖 D2 聊天 UI
- D4 持久化 + D5 错误处理 + D6 缓冲可在 D2 后并行

---

## 8. 风险管理

| 风险 | 概率 | 影响 | 缓解 |
|------|------|------|------|
| Compose 引用渲染复杂 | 中 | D3 延迟 | 降级为纯文本高亮 |
| LLM 引用格式不稳定 | 高 | 解析失败 | 容错多格式正则 + 兜底纯文本显示 |
| Room migration v3 复杂 | 中 | D4 阻塞 | migration 单独测试 |
| W4 内容多，时间紧 | 中 | 延期 | D5 长按菜单/缓存可砍至 W5 |

### 逃生舱口（同前）

1. Compose 不行 → 退 XML
2. 引用 chip → 纯文本
3. 聊天历史 → 仅内存
4. 摘要缓存 → W5 再做

---

## 9. 决策记录

### 决策 24：聊天 UI 选 Compose
- **背景**：W4 需要聊天界面，用户建议用 Compose 展示技术能力
- **决策**：Compose（Material3），Activity 用 `setContent {}`
- **理由**：2026 面试大概率问 Compose；项目混合 XML + Compose 反而加分
- **代价**：学习曲线 + 依赖体积增加

### 决策 25–27：待补充

---

## 10. W4 验收标准

- [x] 导入已索引 PDF → 进入聊天 → 提问题 → 流式回答
- [ ] 回答中 `[第N页]` 引文变为可点击 chip → 点 chip 跳转阅读器定位到该页
- [ ] 退出再进 → 聊天历史还在
- [ ] LLM 离线 → 明确错误提示 + 可重试
- [ ] 同一页/全文摘要只生成一次，缓存后直接显示
- [x] ProGuard 规则就绪
- [ ] 截图归档

**Tag**：`v0.4.0-qa`
