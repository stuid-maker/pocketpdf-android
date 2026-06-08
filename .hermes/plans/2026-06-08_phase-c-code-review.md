# Phase C: 代码审查 + 质量加固

> **For Hermes:** This plan covers 8 independent audit tasks. Use delegate_task to run them in parallel (batch of 3), then collect results.

**Goal:** 对 Purple Crystal Compose 迁移后的代码库做全面审查：安全、质量、可访问性、测试覆盖、性能。

**Architecture:** 8 个独立审查维度，用 3 个 subagent 并行执行（每批 3 个任务，共 3 批），最后汇总报告。

**Tech Stack:** Kotlin 2.0, Compose Material 3, Hilt, Room, DataStore, ProGuard/R8

**审查范围**（Purple Crystal 新增/修改的文件）：

| 文件 | 行数 | 审查重点 |
|------|------|---------|
| `ui/library/LibraryScreen.kt` | 591 | Compose 状态管理、搜索栏、SwipeToDismiss |
| `ui/reader/ReaderScreen.kt` | 356 | AndroidView 嵌入、动画、BottomSheet |
| `ui/settings/SettingsScreen.kt` | 414 | 输入框、对话框、API Key 展示 |
| `ui/diagnostics/DiagnosticsScreen.kt` | 205 | 系统信息展示 |
| `ui/components/PocketStates.kt` | 65 | 空状态/加载/错误组件 |
| `ui/components/PocketButtons.kt` | 42 | 按钮尺寸、颜色 |
| `ui/components/PocketBrandMark.kt` | 54 | Canvas 绘制 |
| `ui/theme/PocketTokens.kt` | 45 | 设计 Token |
| `ui/theme/Theme.kt` | 105 | CompositionLocal、暗色方案 |
| `ui/library/DocumentCoverLoader.kt` | 108 | Bitmap 加载、LRU 缓存 |
| `ui/reader/ReaderController.kt` | 155 | PdfRenderer 生命周期 |
| `data/local/ApiKeyCipher.kt` | — | 加密实现审计 |

---

## Task 1: 安全审查（Security Audit）

**Objective:** 审查 API Key 加密存储、DataStore 敏感数据、网络配置

**Checklist:**
- [ ] `ApiKeyCipher.kt` — 加密算法是否正确（AES/GCM? 有 IV? 密钥存储在哪里?）
- [ ] `SettingsDataStore.kt` — API Key 是否在内存中明文传递
- [ ] `SettingsScreen.kt` — API Key 输入框是否有 `PasswordVisualTransformation`，编辑时是否仍是明文
- [ ] `AndroidManifest.xml` — `android:allowBackup` 是否为 false
- [ ] `network_security_config.xml` — 是否有 `cleartextTrafficPermitted` 限制
- [ ] R8/ProGuard 规则 — 是否保留了加密相关类

**验证命令:**
```bash
grep -rn "apiKey\|ApiKey\|api_key" app/src/main/java/ --include="*.kt"
grep -rn "allowBackup\|networkSecurity\|cleartext" app/src/main/ --include="*.xml"
```

---

## Task 2: Compose 状态管理审查

**Objective:** 审查新 Compose Screen 的状态管理，检查重组陷阱、内存泄漏

**Checklist:**
- [ ] `LibraryScreen.kt` — `rememberSaveable` 用于 `searchQuery`，是否正确？cross-activity 回来后搜索词保留是否是预期行为？
- [ ] `ReaderScreen.kt` — `chromeVisible` 和 `summarySheetVisible` 用 `rememberSaveable`，是否正确？
- [ ] `SettingsScreen.kt` — `editor` 和 `presetDialogVisible` 用 `remember`（非 saveable），为什么？
- [ ] 所有 Screen 的 `LaunchedEffect` key 是否正确 —— 检查是否有 missing key 导致副作用重复执行
- [ ] `produceState` 使用 —— 在 `LibraryScreen` 中是否正确处理了取消
- [ ] 检查是否在 Composable 内直接调用 suspend 函数（应该在 LaunchedEffect 或 produceState 中）

**验证方法:** 逐文件审查 `@Composable` 函数，重点看 state hoisting 和 side-effect handlers

---

## Task 3: 可访问性审查（Accessibility）

**Objective:** 确保 Compose UI 对屏幕阅读器友好

**Checklist:**
- [ ] 所有 IconButton 是否有 `contentDescription`
- [ ] `ReaderScreen.kt` 中的 IconButton（返回、上一页、下一页、AI、刷新）— 检查 contentDescription
- [ ] `LibraryScreen.kt` 中的 FAB、搜索图标、文档卡片 — 检查 contentDescription
- [ ] `SettingsScreen.kt` 中的返回按钮、输入框标签
- [ ] 图片/Canvas 元素（PocketBrandMark）是否有 `contentDescription`（应设为 null 表示装饰性）
- [ ] `PdfPageView`（AndroidView）是否有合理的 contentDescription
- [ ] 触摸目标 — 所有可点击元素是否 ≥ 48dp（按钮已有 `defaultMinSize(minHeight=34.dp)` — 检查是否违规）

**验证命令:**
```bash
grep -rn "IconButton\|Icon(" app/src/main/java/com/asuka/pocketpdf/ui/ --include="*.kt" | grep -v "contentDescription"
grep -rn "clickable" app/src/main/java/com/asuka/pocketpdf/ui/ --include="*.kt"
```

---

## Task 4: 测试覆盖分析

**Objective:** 分析 Compose UI 测试覆盖缺口，列出需补充的测试

**当前测试覆盖：**

| Screen | 测试文件 | 测试数 | 缺口 |
|--------|---------|--------|------|
| Library | LibraryScreenTest.kt | 1 | 无 Loaded/Error/Loading 状态测试 |
| Reader | ReaderScreenTest.kt | 1 | 无 摘要展开/停止/错误 状态 |
| Settings | — | 0 | **完全无 Compose UI 测试** |
| Diagnostics | — | 0 | **无测试** |
| Components | PocketComponentsTest.kt | 2 | 无 BrandMark/更多状态变体 |

**输出:** 列出每个 Screen 需要补充的测试 case（至少 3 个/Screen），标注优先级

---

## Task 5: 硬编码颜色审查

**Objective:** 检查 Compose 代码中是否有硬编码颜色，应该使用 Purple Crystal Token

**Checklist:**
- [ ] 搜索 `Color(0x` 或 `Color.` 硬编码 — 确认哪些是合理的（Canvas 绘制），哪些应改为 Token
- [ ] `ReaderScreen.kt:83` — `Color(0xFF29252D)` 硬编码背景色
- [ ] `PocketBrandMark.kt` — Canvas 内多个硬编码颜色（合理，Canvas 绘制）
- [ ] `PocketButtons.kt` — `Color(0xFF302739)` 硬编码
- [ ] `DiagnosticsScreen.kt` — 检查硬编码颜色
- [ ] 渐变色的颜色值 — 检查是否可提取为 Token

**验证命令:**
```bash
grep -rn "Color(0x" app/src/main/java/com/asuka/pocketpdf/ui/ --include="*.kt" | grep -v "Color.kt\|PocketTokens.kt\|Theme.kt"
```

---

## Task 6: Reader 层重构质量审查

**Objective:** 审查 PdfPageView → ReaderController → ReaderScreen 的重构是否正确

**Checklist:**
- [ ] `ReaderController.kt` — `PdfReaderController` 是否正确实现了 `Closeable`，`close()` 是否释放 PdfRenderer
- [ ] `ReaderScreen.kt:196` — `AndroidView` 中创建 `PdfPageView`，factory 和 update 逻辑是否正确
- [ ] 生命周期 —— `DisposableEffect` 是否在 Composable 离开时清理资源
- [ ] 触控事件 —— `onTouchEvent` 是否正确地传递到 PdfPageView
- [ ] `ReaderActivity.kt` — 是否还有未清理的旧 XML 逻辑
- [ ] 页面渲染 Bitmap 是否有 LRU 缓存防止 OOM

**验证命令:**
```bash
grep -rn "PdfRenderer\|close()\|DisposableEffect\|AndroidView" app/src/main/java/com/asuka/pocketpdf/ui/reader/ --include="*.kt"
```

---

## Task 7: R8/ProGuard 规则审查

**Objective:** 检查 ProGuard 规则是否覆盖了新增的 Compose 和 Purple Crystal 相关类

**Checklist:**
- [ ] `proguard-rules.pro` — 是否需要保留 Compose 相关类
- [ ] MediaPipe TextEmbedder 类是否被保留
- [ ] Room Entity 的序列化是否被保留
- [ ] Retrofit/Moshi DTO 是否被保留
- [ ] Hilt 生成的类是否被保留

---

## Task 8: 汇总报告

**Objective:** 汇总 7 个审查任务的结果，生成一份质量报告

**输出格式:**
```markdown
# PocketPDF Phase C 质量审查报告

## 总分: X/10

## 安全 (权重 25%)
- 发现: ...
- 建议: ...

## Compose 状态管理 (权重 15%)
- ...

## 可访问性 (权重 15%)
- ...

## 测试覆盖 (权重 15%)
- 当前: 263 单元测试 + 4 Compose UI 测试
- 缺口: ...
- 建议新增: ...

## 硬编码审查 (权重 10%)
- ...

## Reader 重构 (权重 10%)
- ...

## R8/ProGuard (权重 10%)
- ...

## 优先修复清单 (P0 → P2)
1. ...
2. ...
```

---

## 执行方式

用 3 个 subagent 并行执行：

**Batch 1（并行）：**
- Subagent 1: Task 1 (安全) + Task 7 (R8)
- Subagent 2: Task 2 (状态管理) + Task 5 (硬编码)
- Subagent 3: Task 3 (可访问性) + Task 6 (Reader 重构)

**Batch 2（并行）：**
- Subagent 1: Task 4 (测试覆盖分析)
- Subagent 2: 修复 Batch 1 发现的高优先级问题
- Subagent 3: 修复 Batch 1 发现的高优先级问题

**Batch 3:**
- Subagent 1: Task 8 (汇总报告)

---

## 验证

- [ ] 安全审查无高危发现
- [ ] 所有 IconButton 有 contentDescription
- [ ] 硬编码颜色 ≤ 3 处合理例外
- [ ] 每个新 Screen 至少有 3 个 Compose UI 测试
- [ ] ReaderController 资源正确释放
- [ ] ProGuard 规则覆盖新组件
