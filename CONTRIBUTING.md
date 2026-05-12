# 贡献与开发约定

> 本项目主要由 **AI 辅助 + 个人手写** 开发。本文是给「未来的我」和「AI 助手」的约定，确保整个仓库的风格统一、提交规范、命名一致。

---

## 1. Git 工作流

### 分支

- **`main`**：仅在每周末合并 `dev`，每次合并打 Git tag。受保护，禁止直接 push。
- **`dev`**：日常开发主分支。
- **`feat/<scope>-<short-desc>`**：每个功能开新分支，完成后合并回 `dev`。
  - 例：`feat/reader-pdf-rendering`、`feat/chat-streaming-output`

### 提交规范（Conventional Commits）

格式：`<type>(<scope>): <subject>`

| type | 用途 |
|---|---|
| `feat` | 新功能 |
| `fix` | Bug 修复 |
| `refactor` | 重构（不改变外部行为） |
| `test` | 新增 / 修改测试 |
| `docs` | 文档（README、PLAN、日志） |
| `chore` | 构建 / 依赖 / 配置（无业务影响） |
| `style` | 格式化（不影响代码逻辑） |
| `perf` | 性能优化 |

**scope** 推荐用模块名：`reader` / `library` / `chat` / `embedding` / `pdf` / `llm` / `di` / `core`。

**示例**：

```
feat(reader): add page navigation with pinch zoom
fix(embedding): handle empty chunk before encoding
docs(week2): write dev log for chunking
test(usecase): cover overlap boundary in ChunkDocumentUseCase
chore(deps): bump retrofit to 2.11.0
```

**反例**：
- ❌ `update code`
- ❌ `fix bug`
- ❌ `wip`（开发过程中允许用 wip，但合到 dev 前必须 rebase 整理）

### 频率与粒度

- 一个功能 = 3–8 个细粒度 commit（**不要一个超大 commit**）
- 每天至少 1 次 push
- 周末打 tag 前，dev 分支必须能编译通过、单元测试通过

---

## 2. 代码风格

### Kotlin

- 遵循 [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html)
- 缩进 4 空格，不用 Tab
- 单行长度 ≤ 120 字符
- 文件末尾保留空行
- 没用到的 import 必须删除

### 命名

| 类型 | 规范 | 示例 |
|---|---|---|
| 类 / 对象 | PascalCase | `PdfTextExtractor` |
| 函数 / 变量 | camelCase | `extractTextByPage` |
| 常量 | UPPER_SNAKE_CASE | `DEFAULT_CHUNK_SIZE` |
| 包 | 全小写 | `com.asuka.pocketpdf.data.pdf` |
| Use Case | 动词+名词+UseCase | `IndexDocumentUseCase` |
| Repository 接口 | XxxRepository | `DocumentRepository` |
| Repository 实现 | XxxRepositoryImpl | `DocumentRepositoryImpl` |
| ViewModel | XxxViewModel | `ReaderViewModel` |
| DTO | Xxx**Dto** | `ChatCompletionRequestDto` |
| Entity (Room) | Xxx**Entity** | `DocumentEntity` |
| Domain Model | 纯名词 | `Document`, `Chunk` |
| Resource ID | `{type}_{feature}_{name}` | `btn_library_import`, `rv_chat_messages` |
| 布局文件 | `{type}_{feature}_{name}.xml` | `activity_reader.xml`, `item_document.xml` |

### 注释

- **不写**"// 这是变量"这种废话注释
- **要写**：复杂算法的意图、非显然的权衡决策、未来的 TODO（标 `TODO(asuka):`）
- 关键的 Use Case 头部加 KDoc 说明输入输出和异常

### 包结构强制规则

```
ui    → 可以 import domain
ui    → 不能 import data（必须经 use case）
data  → 可以 import domain
domain → 不能 import data、ui、androidx.*、android.*
core  → 任何层都可 import
```

违反这条规则的 PR 一律打回。

---

## 3. AI 辅助使用约定

本项目用 AI 助手生成 / 修改代码时，**每次会话开始前** 必须给 AI 提供以下上下文：

1. 当前周（W0–W5）和正在做的任务
2. 涉及的包路径（参考 `PLAN.md` §3 目录结构）
3. 命名规范（本文 §2）
4. 依赖方向规则（本文 §2 末尾）

**禁止**：
- ❌ 让 AI 一次生成整个功能模块（容易跑偏）
- ❌ 直接复制 AI 生成的代码不读就提交
- ❌ AI 写的代码不经过编译就 commit

**推荐做法**：
- ✅ 每次让 AI 生成单文件 / 单函数粒度
- ✅ 生成后人工 review 包路径、命名、注释
- ✅ 生成后立刻编译，编译不过让 AI 修复，**不要自己改半天**
- ✅ 关键决策（如选择某个库版本、设计某个接口）问 AI 列出 2–3 个候选 + 优缺点，自己做最终判断
- ✅ 每周末做一次"我自己能不能讲清楚这段代码"的自查，讲不清的部分重读 / 重写

### 决策原则：技术评估只用工程论据

本项目是个人学习 + 面试导向，但**"为了演示 / 为了简历好看 / 为了讲面试故事"绝对不能作为往方案里塞复杂度的论据**。

技术选型只接受以下论据：
- 当前是否有**真实的**功能 / 性能 / 测试 / 维护需求
- 现有代码是否已经膨胀到该重构（用具体行数、文件数说话，不靠"感觉")
- 新方案是否解决了一个**已存在**的问题（不是"未来可能出现的问题"——YAGNI）

**反例（一律不接受）**：
- ❌ "你是面试导向，加 androidx.startup 演示一下更显工程化"
- ❌ "未来可能要扩展，先抽个接口"（没有 2 个以上实际实现就不抽）
- ❌ "这样写更高级 / 更现代"

学习收益 / 简历价值是技术决策**正确之后**的副产品，不是决策**前**的论据。AI 助手若在决策对话里端出"面试 / 简历"论据，**用户应直接打回**让其重新基于工程必要性评估。

---

## 4. 测试策略

### 谁要写测试

| 层 | 单测覆盖率目标 | 必须测的内容 |
|---|---|---|
| `domain` (use case) | ≥ 70% | 所有公开方法、边界条件、错误路径 |
| `data` (repository) | ≥ 40% | 关键转换逻辑（Entity ↔ Model、DTO ↔ Model） |
| `data` (pdf/embedding) | 关键路径 | chunking 边界、embed 维度 |
| `ui` (ViewModel) | ≥ 30% | 状态转换、关键 intent 处理 |
| `ui` (View) | Espresso 2 个流程 | 导入、问答 |

### 测试命名

```
fun `extractTextByPage returns empty list when pdf has no pages`()
fun `chunk document with overlap keeps source page metadata`()
```

反引号 + 自然语言描述，**比驼峰更易读**。

---

## 5. 开发日志

每周末必写一篇 `docs/dev-log/weekN.md`，模板见 `docs/dev-log/TEMPLATE.md`。

**不允许**：交付 Git tag 时没写日志。

---

## 6. 文档同步

修改 `PLAN.md` / `ROADMAP.md` 必须在该次 commit 的 message 里说明原因。所有变更记录在 `PLAN.md` §9 的变更表里。

---

## 7. 谁来 review

个人项目，**review 自己**：

- 每次推到 `dev` 前：自己 diff 一遍，问 3 个问题
  1. 命名 / 包路径符合规范吗？
  2. 这个改动能不能在面试时讲清楚？
  3. 有没有引入新的依赖 / 复杂度而没在日志里记录？
- 每周末合并到 `main` 前：完整 review 整周 diff，整理日志

---

## 8. 环境快照（W0 末填好）

- OS：Windows 11
- JDK：17.0.13 (Microsoft OpenJDK)
- Android Studio：装在 `D:\AndroidStudio\`（自带 JBR OpenJDK 21.0.9）
- Gradle：**8.10.2**（wrapper 锁定）
- AGP：**8.7.3**
- Kotlin：**2.0.21**（显式通过 `kotlin("android")` plugin 引入，AGP 8 必需）
- KSP：**2.0.21-1.0.28**
- Hilt：**2.52**
- JVM target：Java 17（`compileOptions` + `kotlinOptions` 双配）
- 重要细节：AGP 8 默认禁用 BuildConfig 生成，已通过 `buildFeatures { buildConfig = true }` 显式启用
- 工程曾尝试 AGP 9.0.1 默认配置，因 KSP/Hilt 生态适配不足主动降级，详见 ADR-004
- LLM Runtime：LM Studio（CLI `lms.exe`，commit `0b2a176`），监听 `http://localhost:1234/v1`
- 默认 chat 模型 ID：`google/gemma-3-4b`（API 返回 ID，磁盘命名 `lmstudio-community/gemma-3-4b-it` Q4_K_M）
- 备选 chat 模型 ID：`google/gemma-4-e4b`（磁盘命名 `lmstudio-community/gemma-3n-e4b-it` Q8_0）
- Embedding 模型：端侧 ONNX `all-MiniLM-L6-v2`（384 维，走 Sentence-Embeddings-Android，不走 LM Studio）；W0 已确认 LM Studio 同时挂载了 `text-embedding-nomic-embed-text-v1.5`，云端切换时作为 HTTP embed 备选
- 测试设备：Android 模拟器 `Medium_Phone_API_36.1`（Android 16 / SDK 36 / 1080×2400），W0 PingActivity smoke test 通过；W1 起接入真机时在此追加型号
