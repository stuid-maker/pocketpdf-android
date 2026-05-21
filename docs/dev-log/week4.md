# Week 4 · 2026-05-20 至 2026-05-27

> 第 4 周：问答 + 引用回溯 + 抛光。**完整问答闭环，能拿出去演示。**
>
> **D0–D1 完成**（2026-05-20）：Prompt 迁移 + ProGuard + Compose 环境 + `AskDocumentUseCase` + 3 测试全绿。

## 1. 本周目标（来自 ROADMAP）

- [ ] 聊天 UI（Compose 双气泡 + 输入框 + 发送/停止按钮）
- [ ] `AskDocumentUseCase`：embed query → retrieve → RAG prompt → 流式生成
- [ ] Prompt 模板：要求模型答复时引用 `[来源: 第N页]`
- [ ] 答案末尾解析引用，渲染为可点击 chip
- [ ] 点引用 → 跳回阅读器 → 滚动到该页
- [ ] 聊天历史持久化（Room `ChatMessageEntity`）
- [ ] 错误处理：LLM 服务离线 / 超时 / 解析失败
- [x] ProGuard/R8 规则（PdfBox + MediaPipe + Moshi keep）
- [ ] 引用解析容错：`[来源: 第N页]` / `[第N页]` / `(Page N)` 等多格式

---

## 2. Day 0 实际完成（2026-05-20）

### Prompt 模板迁移 ✅
- `SummarizeDocumentUseCase` 内联 prompt → `PromptTemplates.documentSummary(chunks: List<Pair<String, String>>)`
- 所有 prompt 统一在 `domain/prompt/PromptTemplates.kt`

### ProGuard/R8 规则 ✅
- 追加 PdfBox、MediaPipe、Moshi、Room、OkHttp keep 规则
- `isMinifyEnabled` 保持 false（W5 release 开）

### Compose 依赖 ✅
- `composeBom = "2024.09.00"` + UI/Material3/Activity + `kotlin-compose` 插件
- `compose = true` + BOM，编译通过（2m47s）
---

### Day 1 · AskDocumentUseCase ✅

**新增文件**：
- `domain/usecase/AskDocumentUseCase.kt` — 检索 Top-K → 拼上下文 + 页码 → RAG prompt → 流式生成
- `test/.../AskDocumentUseCaseTest.kt` — 3 case（正常流 / 空检索 fallback / LLM 异常）

**修改文件**：
- `domain/prompt/PromptTemplates.kt` — `ragQuery()` 加引用格式指令

**测试**：3/3 全绿，全量约 85 tests

---


- `AskDocumentUseCase`（`domain/usecase/`）：RetrieveChunksUseCase + RAG prompt + LLM stream → `Flow<String>`
- `PromptTemplates.ragQuery(context, question)` 填实：要求中文回答、引用 `[第N页]` 格式、不知道就说不知道
- 引用格式设计：标准 `[来源: 第3页]`，容错 `[第N页]` / `[Page N]` / `(P.3)` / `【来源3】`
- 单元测试：mock RetrieveChunksUseCase + LlmRepository

---

## 4. Day 2 计划（2026-05-22）— 聊天 UI（Compose）

- `ChatActivity`（`@AndroidEntryPoint` + `setContent`）+ `ChatViewModel`（`@HiltViewModel`）
- `LazyColumn` 双气泡（用户右蓝底 / AI 左灰底）
- 输入框 + 发送按钮 + 流式打字 + 停止按钮 + 自动滚动
- `ChatUiState`：消息列表 + 输入文字 + isStreaming + 错误
- LibraryActivity 加"提问"入口

---

## 5. Day 3 计划（2026-05-23）— 引用解析 + 跳转

- `CitationParser`：多格式正则 → `List<Int>`（0-based pageIndex）
- Compose 中引用渲染为可点击 chip（`AnnotatedString` + `ClickableText`）
- 点击 chip → Intent 跳转 ReaderActivity 指定页
- `ReaderActivity.newIntent(context, documentId, pageIndex?)`

---

## 6. Day 4 计划（2026-05-24）— 聊天历史持久化

- `ChatMessageEntity`（Room v3 migration）+ `ChatMessageDao`
- `ChatRepository` 接口 + Impl
- ChatViewModel 集成：init 加载历史 + 逐条落库

---

## 7. Day 5 计划（2026-05-25）— 错误处理 + 长按菜单 + 截图

- LLM 离线 → 错误提示 + 重试；超时 → 停止 + 提示
- 长按消息：复制 / 重新生成
- 截图：聊天界面、引用跳转、设置页、总结浮层

---

## 8. Day 6–7 计划（2026-05-26/27）— 缓冲 + 抛光

**按优先级**：修 bug → MapReduce 配置项 → 深色模式 → 补测试 → W4 验收 → 写日志

---

## 9. 依赖关系

```
D0 (快修+Compose) ──▶ D1 (AskUseCase) ──▶ D2 (聊天UI) ──▶ D3 (引用跳转)
                                                    ▶ D4 (持久化) ← D2 后即可开始
                                                    ▶ D5 (错误处理)
                                                    ▶ D6 (缓冲)
```

---

## 10. 风险管理

| 风险 | 影响 | 缓解 |
|------|------|------|
| Compose 学习曲线 | D2 延迟 | D0 已验证编译 |
| LLM 引用格式不稳定 | 解析失败 | 容错多格式正则 |
| Room migration v3 | D4 阻塞 | 单独测试 |
| W4 内容多 | 延期 | 长按菜单可砍 |

**逃生舱口**：Compose → 退 XML / 引用 chip → 纯文本 / 聊天历史 → 仅内存

---

## 11. 决策记录（待填充）

### 决策 24：聊天 UI 选 Compose（理由：面试展示 + 2026 生态；代价：学习曲线）

### 决策 25–27：待补充

---

## 12. W4 验收标准

- [ ] 导入已索引 PDF → 进入聊天 → 提 3 个问题 → 流式回答带页码引用
- [ ] 点引用 chip → 跳转阅读器 → 定位到该页
- [ ] 退出再进 → 聊天历史还在
- [ ] LLM 离线 → 明确错误提示 + 可重试
- [x] ProGuard 规则就绪
- [ ] 截图归档

**Tag**：`