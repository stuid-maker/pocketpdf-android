# Week 0 · 2026-05-11 至 2026-05-12

> 第 0 周：环境就绪 + 文档骨架。"动手前能阻塞 4 周后的事"全部解决。

## 1. 本周目标（来自 ROADMAP）

- [x] 写 `PLAN.md` / `ROADMAP.md` / `README.md` / `CONTRIBUTING.md` / `.gitignore`
- [x] 写 `docs/dev-log/TEMPLATE.md` 和本日志
- [x] 写 `docs/ARCHITECTURE.md` 占位 + ADR 模板
- [ ] 创建本地 Git 仓库（在最终的工作目录），首次 commit
- [ ] 创建 GitHub 远端仓库 `pocketpdf-android`，push
- [ ] 决定工作目录是否迁出 `PDF小助手app`（中文+空格路径风险）
- [ ] 验证 Android Studio 安装情况
- [ ] 用 AS 新建工程，配 Version Catalog + Hilt + Room + Retrofit 依赖
- [ ] 验证 `./gradlew assembleDebug` 通过
- [ ] 安装 Ollama，`ollama pull qwen2.5:3b-instruct`
- [ ] PowerShell 跑通 `curl http://localhost:11434/api/tags`
- [ ] 模拟器或真机跑通空 App
- [ ] `adb reverse tcp:11434 tcp:11434` 设置
- [ ] App 内最小 Demo：按钮 → 调 `/api/tags` → Toast 模型名
- [ ] 打 tag `v0.0.1-env-ready`

## 2. 实际完成

> 进行中，每完成一项就移到这里。

- ✅ 项目方案落地：`PLAN.md` 完整版，含技术选型、目录结构、风险表、面试问答清单
- ✅ 5 周路线图：`ROADMAP.md`，每周任务可勾选，"砍功能优先级"明确
- ✅ 编码规范与 Git 规范：`CONTRIBUTING.md`，含 AI 辅助使用约定
- ✅ 架构占位：`docs/ARCHITECTURE.md`，含 3 条初始 ADR（XML、Ollama 桥接、单 Module）
- ✅ 日志模板：`docs/dev-log/TEMPLATE.md`
- ✅ `.gitignore`（Android 标准 + 个人补充）
- ✅ README 项目首页（带状态徽章、路线图状态表）

## 3. 关键决策与权衡

### ADR-001: 选 XML 而非 Compose

- **背景**：开发者 Android 新手，5 周硬 DDL，主要靠 AI 辅助编程
- **候选方案**：
  - A. Jetpack Compose（2026 主流）
  - B. XML + ViewBinding + Material Components
- **最终决策**：B
- **理由**：XML 在 AI 训练数据中占比远高于 Compose；新手 + AI 辅助场景下，XML 出错率低、可参考代码多；ViewModel + LiveData/StateFlow 模式在两者之间通用
- **代价**：与 2026 主流稍有距离 → 面试时主动解释选型权衡，反而成为加分项（体现工程思维）

### ADR-002: 选 Ollama 桥接而非端侧推理

- **背景**：原方案考虑端侧 LiteRT-LM + Gemma；调研发现 5 周内难稳定集成
- **候选方案**：
  - A. 端侧推理（LiteRT-LM + Gemma 3 1B/3n）
  - B. PC 端 Ollama + adb reverse 桥接
  - C. 云端 API（OpenAI / DeepSeek / 通义）
- **最终决策**：B（开发期），C 列为 v2 选项
- **理由**：
  - B 开发效率最高，模型选择最灵活（PC 上可跑 3B/7B/13B 任意）
  - 业务代码完全不耦合后端，未来切 A 或 C 只动 `data/remote/`
  - 调用接口与云端 OpenAI 兼容（Ollama 提供 OpenAI-compat 端点）
- **代价**：演示必须 PC 在线；不可脱机；面试时要解释为什么不上端侧

### ADR-003: 单 Module 而非多 Module

- **背景**：Clean Architecture 经典做法是多 Gradle module（`:domain`、`:data`、`:feature-xxx`）
- **决策**：单 `app` module + 严格分包 + 包路径规则
- **理由**：新手在多 module Gradle 配置上容易卡 1–2 天；单 module 同样能保证 Clean Architecture 的核心精神（分层、依赖方向）
- **代价**：无法用 Gradle 编译期强制依赖方向，要靠 review 和 lint 规则

### 决策 4: 不追求 3 万行代码量

- **背景**：用户初始预期 30k+ LOC
- **决策**：目标改为 5k–8k 业务代码 + 测试 + 文档 = 仓库总 12k–18k 行
- **理由**：5 周新手 + AI 辅助下 3w 行只可能是模板代码堆砌，简历反而扣分。质量 > 数量
- **代价**：需要在简历里**主动用工程化亮点替代代码量描述**（如"70% 测试覆盖、CI、ADR、6 个 milestone tag"）

## 4. 踩坑记录

| 问题 | 原因 | 解决 | 用时 |
|---|---|---|---|
| PowerShell `where ollama` 报错 | `where` 是 `Where-Object` 别名 | 用 `where.exe ollama` | 1 min |
| 工作目录 `PDF小助手app` 含中文+空格 | 历史习惯 | （待决定）迁到 `C:\dev\pocketpdf-android` 或保留 | TBD |

## 5. 关键代码片段

W0 暂无业务代码，仅文档与配置。

## 6. 性能数据

不适用。

## 7. 测试

不适用（W0 无业务代码）。

## 8. Git 数据

- commit 数：（W0 末填）
- 标签：`v0.0.1-env-ready`（待打）

## 9. 自查问题

- [ ] 我能清楚说明 ui/domain/data 分层的意义吗？
- [ ] 我能说出为什么开发期用 Ollama 而不是端侧推理吗？
- [ ] 我能解释 Conventional Commits 的好处吗？

## 10. 下周计划（W1 · PDF 阅读器 Demo）

- 文件选择器 + 内部存储复制
- PdfBox-Android 文本提取
- AndroidPdfViewer 渲染
- Room `DocumentEntity` + `PageEntity`
- 文档库主页 + 阅读器基础 UI

## 11. 时间分配（W0 完成后填）

| 类型 | 小时 |
|---|---|
| 调研（市场 + 技术） |   |
| 写方案文档 |   |
| 环境安装与验证 |   |
| AS 工程骨架 |   |
| Ollama 联调 Demo |   |
