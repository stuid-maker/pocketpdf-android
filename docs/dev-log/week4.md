# Week 4 · 2026-05-20 至 2026-05-27

> 第 4 周：问答 + 引用回溯 + 抛光。**完整问答闭环，能拿出去演示。**
>
> **D0–D3 完成**（2026-05-20）：Prompt 迁移 + ProGuard + Compose + AskDocumentUseCase + 聊天 UI + 引用解析跳转。
>
> **D4–D7 完成**（2026-05-23）：多模型预设 + System Prompt + 段落切块 + 错误处理 + 聊天历史 + 摘要缓存 + 抛光。

## 1. 本周目标（来自 ROADMAP）

- [x] 聊天 UI（Compose 双气泡 + 输入框 + 发送/停止按钮）
- [x] `AskDocumentUseCase`：embed query → retrieve → RAG prompt → 流式生成
- [x] Prompt 模板：要求模型答复时引用 `[第N页]`
- [x] 答案末尾解析引用，渲染为可点击 chip
- [x] 点引用 → 跳回阅读器 → 滚动到该页
- [x] 聊天历史持久化（Room `ChatMessageEntity` v3 + ChatRepositoryImpl）
- [x] 错误处理：LLM 服务离线 / 超时 / 解析失败（error state + retry + 摘要浮层内重试）
- [x] 摘要缓存：全文/本页摘要生成后持久化（SummaryCacheStore，DataStore 方案）
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
- `ChatBubble` — 用户紫色右对齐 / AI 灰色左对齐 + 流式进度条
- `ReaderActivity` 工具栏 "问答" → ChatActivity
- 引用 chip 使用 `BasicText` + `LinkAnnotation`（替代废弃的 `ClickableText`）
- 长按消息弹出菜单（复制 / 重新生成）
- 编译通过 21s，真机验证通过

### Day 3 · 引用解析 + 跳转 ✅
- `CitationParser`：多格式容错正则（`[第N页]` / `[Page N]` / `(P.N)` / `【来源N】`）
- Compose `AnnotatedString` + `LinkAnnotation` 可点击引用 chip
- 点击 chip → Intent 跳转 `ReaderActivity`（带 `documentId` + `pageIndex`）
- `ReaderActivity.newIntent(context, documentId, pageIndex?)` 支持可选页码
- 跳转后直接渲染指定页

### Day 4 · 多模型预设 + System Prompt ✅
- `ModelPreset` 四预设（LM Studio 本地 / DeepSeek / 通义千问 / 自定义）
- 设置页预设下拉：选中自动填 baseUrl + per-preset hints
- 自定义值保留（预设切换不清空用户选择）
- `SettingsDataStore` 加 `systemPrompt: Flow<String>`
- 设置页加"系统提示词"多行输入框
- `ChatViewModel` + `ReaderViewModel` 读 systemPrompt 并注入消息
- 测试连接成功后自动弹出模型下拉列表，选中自动填入

### Day 5 · 段落切块 + 错误处理 ✅
- `ParagraphChunker` 实现（按双换行 `\n\n` 切分，加 `maxChunkChars=1024` 防溢出）
- `ChunkingModule` DI：默认 `SlidingWindowChunker`，按段落可选
- 设置页加"切块策略"选项：按长度 / 按段落
- `ChatViewModel` 捕获异常 → `ChatUiState.error` → Snackbar 显示
- 网络错误：发送按钮变重试图标
- `ReaderViewModel` 总结失败：浮层内重试按钮

### Day 6–7 · 聊天历史 + 摘要缓存 + 抛光 ✅

**聊天历史持久化：**
- Room `ChatMessageEntity`（v3 auto-migration）+ `ChatMessageDao` + `ChatRepository`
- `ChatViewModel` 集成 `observeMessages()`：退出再进历史还在
- `StoredChatMessage` 领域模型（携带 DB id，稳定 LazyColumn key）

**摘要缓存：**
- `SummaryCacheStore`（独立 DataStore，Key=`summary:<docId>:<scope>`）
- `SummarizeDocumentUseCase`：先查缓存，命中跳过 LLM
- 流式收集完成后自动写入缓存

**抛光：**
- 品牌色系统：27 色 Material 3 紫色色板 + themes.xml 绑定 17 item
- Compose Theme：新建 `Color.kt` / `Type.kt` / `Theme.kt`（含暗黑模式）
- ChatActivity 改用 `PocketPDFTheme`
- 全线中文统一：Reader 英→中 + Chat/Settings 硬编码抽取（~30 处 → strings.xml）
- Reader 翻页淡入动画 200ms
- 大规模代码审查修复（19 个问题，含 3 个 ANR/线程安全问题）
- 14 个新单元测试（ParagraphChunker 16 + ChatRepositoryImpl 5 + SettingsViewModel 11）
- ClickableText deprecated 修复 → BasicText + LinkAnnotation
- 模型名下拉框不再反复弹出（modelsDropdownShown 标志位）
- `.claude/` 已加 `.gitignore`

---

## 3. 依赖关系

```
D0 (快修+Compose) ──▶ D1 (AskUseCase) ──▶ D2 (聊天UI) ──▶ D3 (引用跳转)
                                                     ├──▶ D4 (预设+SystemPrompt)
                                                     ├──▶ D5 (切块+错误处理)
                                                     └──▶ D6-7 (持久化+缓存+抛光)
```

- 所有 Week 4 任务全部完成
- 额外完成：品牌色系统 + Compose Theme + 大规代码审查修复

---

## 4. 验收标准

- [x] 导入已索引 PDF → 进入聊天 → 提问题 → 流式回答
- [x] 回答中 `[第N页]` 引文变为可点击 chip → 点 chip 跳转阅读器定位到该页
- [x] 退出再进 → 聊天历史还在
- [x] LLM 离线 → 明确错误提示 + 可重试
- [x] 同一页/全文摘要只生成一次，缓存后直接显示
- [x] ProGuard 规则就绪
- [x] 截图归档目录创建（`docs/screenshots/`，需真机截图填充）

**Tag**：`v0.4.0-qa`
