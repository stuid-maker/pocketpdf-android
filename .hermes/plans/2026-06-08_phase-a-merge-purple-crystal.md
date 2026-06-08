# Phase A: 合并 Purple Crystal UI 并收尾 Compose 迁移

> **For Hermes:** Use subagent-driven-development skill to implement this plan task-by-task.

**Goal:** 将 `codex/purple-crystal-ui` 分支合并到 `main`，验证全量测试通过，清理遗留问题，关闭本次全 Compose 迁移。

**Architecture:** 先处理 main 上的未提交修改（commit），再 merge purple-crystal-ui 分支。合并后运行全量测试验证无回归，最后按 Purple Crystal 最终验收标准逐项检查。

**Tech Stack:** Kotlin 2.0, Gradle, Compose Material 3, Hilt, JUnit4, Robolectric

---

### Task 1: 提交 main 上的未提交修改

**Objective:** 将 IndexWorker 的安全缓存清理修复 + ROADMAP W5 状态更新提交

**Files:**
- `app/src/main/java/com/asuka/pocketpdf/data/indexing/IndexWorker.kt`
- `app/src/test/java/com/asuka/pocketpdf/data/indexing/IndexWorkerTest.kt`
- `ROADMAP.md`

**Step 1: 确认修改内容正确**

已确认：
- `IndexWorker.kt`: 新增 `invalidateSummaryCacheSafely()` 方法，用 try/catch 包裹缓存清理，防止缓存失败阻塞索引流程
- `ROADMAP.md`: W5 标记为 ✅ 完成，更新验收描述，注明 Demo 视频跳过

**Step 2: Commit**

```bash
git add app/src/main/java/com/asuka/pocketpdf/data/indexing/IndexWorker.kt \
        app/src/test/java/com/asuka/pocketpdf/data/indexing/IndexWorkerTest.kt \
        ROADMAP.md
git commit -m "fix(indexing): safe summary cache invalidation + update ROADMAP W5 status"
```

---

### Task 2: 合并 codex/purple-crystal-ui 到 main

**Objective:** 将 8 个 Compose 迁移 commit 合并到主线

**Step 1: Merge**

```bash
git merge codex/purple-crystal-ui --no-ff -m "merge: purple crystal UI — full Compose migration

- Foundation: PocketTokens, PocketBrandMark, PocketButtons, PocketStates
- Library: Compose LibraryScreen (591 lines), DocumentCoverLoader
- Reader: Compose ReaderScreen (356 lines), ReaderController extraction
- Settings: Compose SettingsScreen (414 lines), grouped + diagnostics
- Diagnostics: DiagnosticsActivity/Screen/ViewModel (replaces PingActivity)
- Removed: all XML layouts (library, reader, settings, ping, dialogs)"
```

**Step 2: 确认无冲突**

Expected: clean merge (purple-crystal 分支基于最近的 main，应无冲突)

---

### Task 3: 运行全量单元测试

**Objective:** 确保合并后所有 246 个测试仍然通过

**Step 1: Run unit tests**

```bash
./gradlew.bat testDebugUnitTest --no-daemon
```

Expected: BUILD SUCCESSFUL, 246 tests passed (或更多，如果 purple-crystal 分支新增了测试)

**Step 2: Run lint**

```bash
./gradlew.bat lintDebug --no-daemon
```

Expected: no new lint errors

**Step 3: Assemble debug**

```bash
./gradlew.bat assembleDebug --no-daemon
```

Expected: BUILD SUCCESSFUL

---

### Task 4: 验证 Purple Crystal 最终验收标准

**Objective:** 逐项检查 Purple Crystal UI Roadmap 的 Final Acceptance 标准

**Check 1: 统一设计系统**

验证 `PocketTokens.kt` 在所有 Screen 中一致使用（LibraryScreen, ReaderScreen, SettingsScreen, DiagnosticsScreen, ChatActivity）。

Run:
```bash
grep -r "PocketTokens" app/src/main/java/com/asuka/pocketpdf/ui/ --include="*.kt" -l
```

Expected: LibraryScreen.kt, ReaderScreen.kt, SettingsScreen.kt, DiagnosticsScreen.kt 等

**Check 2: PDF 缩放/平移 + 引用跳转保持**

确认 `PdfPageView` 和 `ReaderController` 的交互逻辑未被破坏。

Run:
```bash
grep -r "PdfPageView\|ReaderController\|CitationParser\|pageIndex" \
  app/src/main/java/com/asuka/pocketpdf/ui/reader/ --include="*.kt" -l
```

Expected: ReaderScreen.kt, ReaderActivity.kt, ReaderController.kt 中仍有引用

**Check 3: Edge-to-edge 无双重 insets**

检查各 Activity 和 Screen 是否正确处理系统栏 insets。

Run:
```bash
grep -r "enableEdgeToEdge\|WindowInsets\|systemBars" \
  app/src/main/java/com/asuka/pocketpdf/ui/ --include="*.kt"
```

**Check 4: 无 XML/ViewBinding 残留引用**

确认已删除的 XML 布局文件无残留 import/引用。

Run:
```bash
grep -r "activity_library\|activity_reader\|activity_settings\|activity_ping\|dialog_summary\|item_document\|view_empty_library\|menu_reader\|menu_library" \
  app/src/main/java/com/asuka/pocketpdf/ --include="*.kt" --include="*.xml"
```

Expected: NO matches (或在注释中)

---

### Task 5: 更新 ROADMAP v1.0 范围 checklist

**Objective:** 将 ROADMAP 中 v1.0 范围的实际完成项打勾

**Files:**
- `ROADMAP.md` — v1.0 范围 section (lines ~139-150)

**修改内容：**

```markdown
## v1.0 范围（2026-06-15 前）— 闭环可演示

确保核心流程完整可用：

- [x] PDF 导入 + 阅读 + 翻页（W1）
- [x] 文本切块 + MediaPipe 向量化 + 索引（W2）
- [x] 检索 + LLM 流式生成 + 摘要（W3）
- [x] 聊天 UI（Compose）+ 问答 + 引用跳转（W4）
- [x] 多模型预设 + System Prompt + 段落切块（W4 D4-D5）
- [x] 聊天历史持久化 + 摘要缓存（W4 D6）
- [x] 错误处理 + 长按菜单 + 截图（W4 D7）
- [x] 测试 + CI + Demo + 发布（W5）
```

**Step 1: Patch ROADMAP.md**

使用 patch 工具更新 v1.0 范围 checklist。

**Step 2: Commit**

```bash
git add ROADMAP.md
git commit -m "docs: sync ROADMAP v1.0 scope checklist with actual delivery"
```

---

### Task 6: Tag 并推送

**Objective:** 打 tag 标记 Compose 迁移完成

```bash
git tag -a v1.1.0-compose -m "v1.1.0: Purple Crystal full Compose migration"
git push origin main --tags
```

---

## Verification Checklist

- [ ] `git status` — clean working tree
- [ ] `./gradlew.bat testDebugUnitTest` — 所有测试通过
- [ ] `./gradlew.bat lintDebug` — 无新增 lint 错误
- [ ] `./gradlew.bat assembleDebug` — 编译成功
- [ ] 无 XML 布局残留引用
- [ ] PdfPageView + 引用跳转逻辑完整
- [ ] ROADMAP 与实际代码状态一致
- [ ] tag v1.1.0-compose 已打

---

## Risks

- **Purple crystal 分支缺少测试**：LibraryScreen/ReaderScreen/SettingsScreen 的 Compose UI 测试需要后续 Phase C 补充
- **依赖版本冲突**：构建配置有变更（`app/build.gradle.kts` +19/-1），需确认 Compose BOM 版本兼容
- **DiagnosticsActivity 需要 AndroidManifest 注册**：分支已改 `AndroidManifest.xml`，需确认无遗漏
