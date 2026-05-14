# Week 1 · 2026-05-12 至 -

> Day 1 收尾时间：2026-05-12 11:??（UTC+8）。Day 2 收尾时间：2026-05-12 12:??（UTC+8）。Day 3 收尾时间：2026-05-12 14:??（UTC+8）。Day 4 收尾时间：2026-05-14 15:??（UTC+8）。
> 第 1 周：PDF 阅读器 Demo。**当前进度：Day 4 完成（导入 → 文档库 → 原生 PdfRenderer 阅读器 → 翻页 / 双指缩放 / 页码条 + 40 业务测全绿）**；`feat/library-document-import` 仍不合 `dev`——等真机/模拟器 GUI 手验 SAF 导入 + 阅读 + 重启闭环后再合。

## 1. 本周目标（来自 ROADMAP）

- [x] 文件选择器（SAF · `ACTION_OPEN_DOCUMENT`）（**Day 3 完成**；`LibraryActivity` 调 `ActivityResultContracts.OpenDocument` 限 `application/pdf` + `OpenableColumns.DISPLAY_NAME` 查询 + fallback）
- [x] 把选中 PDF 复制到 App 内部存储（`filesDir/documents/`）（**Day 2 完成**；`InternalFileStorage.copyToInternal` 真实现 + 失败回滚）
- [x] PdfBox-Android 集成，封装 `PdfTextExtractor`，按页提取文本（**Day 2 完成**；`PdfBoxTextExtractor` 实现 + 3 个 Robolectric 测）
- [x] PDF 阅读器界面（**Day 4 完成**；使用 Android 原生 `PdfRenderer`，避免引入老旧 viewer 依赖；支持翻页、双指缩放、页码条）
- [x] Room 表：`DocumentEntity` + DAO（**Day 1 完成**；`PageEntity` 推迟到 W1 末或 W2，PDF 文本切块时再加，避免空表）
- [x] 仓库 `DocumentRepository` 接口 + UseCase 四件套（observe/get/import/delete）（**Day 1 完成**；`ImportDocumentUseCase` Day 2 真实现 + 失败回滚测）
- [x] 文档库主页（RecyclerView 列表 + 空状态）（**Day 3 完成**；`LibraryActivity` + `DocumentListAdapter` + `view_empty_library.xml`）
- [x] 文档卡片：标题、页数、导入时间、索引状态徽章（**Day 3 完成**；`item_document.xml` + `DateUtils.getRelativeTimeSpanString` + `bg_badge_index_status` shape；W1 阶段所有徽章都是"未索引"，W2 切多态）
- [x] 阅读器底部页码条（**Day 4 完成**；`当前页 / 总页数`）
- [x] 单元测试：`PdfTextExtractor`（用合成 PDF）（**Day 2 完成**；Robolectric + Standard 14 字体路径走通 → 3 case）

## 2. 实际完成

> 进行中，每完成一项就移到这里。

### Day 1（2026-05-12）

- ✅ **分支策略落地**：`main` → `dev`（W1 起所有日常开发挂这里）→ `feat/library-document-import`（功能分支，Day 2 完工后整组 review 合 dev）。3 个分支同步指向 `3951d59` (W0 收尾 commit)。
- ✅ **Phase 0 · Room 2.6.1 依赖**：`libs.versions.toml` + `app/build.gradle.kts`；ksp `room.schemaLocation = $projectDir/schemas`；commit `e48a807`。`app/schemas/` 进 Git，每次 schema 变更都能 diff。
- ✅ **Phase 1 · domain 层 7 文件**（commit `6d0eedb`）：
  - `Document` data class（id/title/uri/pageCount/indexStatus/importedAt）
  - `IndexStatus` 4 态 enum
  - `DocumentRepository` 接口（observe/get/import/delete）
  - 4 个 single-responsibility UseCase
  - **rg 自查 0 命中 `import android.*`**，依赖方向干净
- ✅ **Phase 2 · data/local Room**（commit `e2f05ee`）：
  - `DocumentEntity`（含 `importedAt` 索引）
  - `DocumentDao`（Flow `observeAll` + suspend `getById/insert/update/deleteById`）
  - `AppDatabase v1`，`exportSchema=true`
  - `DocumentMappers`：顶层 `internal` 双向 extension，`name`↔`valueOf` 处理 enum
  - `DatabaseModule` Hilt `@Singleton` Room.databaseBuilder
  - **`app/schemas/com.asuka.pocketpdf.data.local.AppDatabase/1.json` 已生成并入库**
- ✅ **Phase 3 · data/repository + storage**（commit `8382b49`）：
  - `DocumentRepositoryImpl`：`observe/get/delete` 真实现走 `dispatchers.io`；`importDocument` 留 `Result.Failure(NotImplementedError("DocumentRepositoryImpl.importDocument: ..."))` 沉降标记
  - `FileStorage` 接口 + `InternalFileStorage` 实现（`filesDir/documents/` 懒建目录；`delete()` 真实现；`copyToInternal()` 同样沉降）
  - `RepositoryModule` 多加 2 个 `@Binds`（DocumentRepository / FileStorage），与原 LlmRepository 并列
  - **`./gradlew :app:assembleDebug` BUILD SUCCESSFUL**，Hilt 依赖图编译期校验通过
- ✅ **Phase 4 · 单测 12 个**（commit `d18b68f`，6 个文件）：
  - `ObserveDocumentsUseCaseTest` × 2（Turbine）
  - `GetDocumentUseCaseTest` × 2
  - `DeleteDocumentUseCaseTest` × 2（mockk + coVerify）
  - `ImportDocumentUseCaseTest` × 1 ← **沉降测试**：直接构造真 `DocumentRepositoryImpl`，断言 message 含 `"DocumentRepositoryImpl.importDocument"`，Day 2 真实现必然让它红
  - `DocumentMappersTest` × 5（双向无损 + round-trip identity + 枚举全覆盖 + 非法值 fail-loud）
  - `test/core/TestDispatcherProvider`（UnconfinedTestDispatcher 三槽都填）
  - `./gradlew :app:testDebugUnitTest` BUILD SUCCESSFUL，新增类 PASSED 12 / 0 / 0 / 0
- ✅ **Phase 5 · 文档收尾**：本日志 + commit 6 + push `feat/library-document-import`

### Day 2（2026-05-12）

- ✅ **Phase 0 · 开工自检 + 沉降验证**：`./gradlew :app:testDebugUnitTest --tests "...ImportDocumentUseCaseTest"` 跑通确认 sentinel 仍绿（"我接的是 Day 1 留下的版本"防线）。
- ✅ **Phase 1 · PdfBox 依赖 + 启动初始化**（commit `6ac6471` deps，`fec12f6` 含 onCreate init）：
  - `libs.versions.toml` 加 `pdfboxAndroid = "2.0.27.0"` + `pdfbox-android` 库别名；`build.gradle.kts` `implementation(libs.pdfbox.android)`
  - `PocketPdfApp.onCreate`：`PDFBoxResourceLoader.init(applicationContext)` 放 `Timber.plant` 之后
  - **第一次 sync 5m 31s**（PdfBox-Android ~10MB，跟 Day 2 计划留话 #2 预判一致），第二次 build ~1m
- ✅ **Phase 2 · `data/pdf` PdfTextExtractor**（commit `fec12f6`）：
  - `PdfTextExtractor` 接口（data 层，不污染 domain）：`suspend fun extractPagesText(file): List<String>`，**零 chunking 零 splitting**，每页一个 String
  - `PdfBoxTextExtractor`：`PDDocument.load(file)` + 循环 1..numberOfPages 调 `PDFTextStripper.setStartPage(n).setEndPage(n).getText()`；`withContext(io)` 切池；`try/finally` 关 PDDocument 防 fd 泄漏
  - `RepositoryModule` `@Binds bindPdfTextExtractor(impl: PdfBoxTextExtractor): PdfTextExtractor`
- ✅ **Phase 3 · `InternalFileStorage.copyToInternal` 真实现**（commit `f501990`）：
  - `ContentResolver.openInputStream(Uri.parse(sourceUri))` + `File(documentsDir, "${UUID.randomUUID()}.pdf")` + `source.use { sink.use { copyTo() } }`
  - **失败回滚**：`try { ... } catch (t) { target.delete(); throw t }` —— 半成品文件不留盘
  - 多注入 `DispatcherProvider`，全程 `withContext(io)`
  - 删掉 `NotImplementedError` 沉降 stub；`FileStorage` 接口 KDoc 同步更新（说明失败语义）
- ✅ **Phase 4 · `DocumentRepositoryImpl.importDocument` 真实现**（commit `0b6b75e`）：
  - 编排顺序：copyToInternal → extractPagesText → DAO.insert → 返回 Document
  - **三步回滚**：
    - copyToInternal 抛 → FileStorage 自己删了，Repository 不二次删
    - extractPagesText 抛 → `copied.delete()` 再 rethrow
    - dao.insert 抛 → `copied.delete()` 再 rethrow
  - 外层 `resultOf {...}` 把所有 rethrow 接住转成 `Result.Failure`
  - `Timber.tag("DocumentRepositoryImpl").i("importDocument: id=%d ...")` 落 logcat
- ✅ **Phase 5 · 测试翻红重写 + Robolectric**（commit `de144fd`）：
  - **沉降测试 Day 1→Day 2 的失败信号**：`DocumentRepositoryImpl` 构造函数从 3 参变 4 参，sentinel 测试连**编译都不过**——比 message 字符串断言更狠的失败信号
  - `ImportDocumentUseCaseTest` 重写为 4 case：happy path + 3 个失败回滚（copy fail / extract fail / dao fail），后两个用 `File.createTempFile + assertFalse(file.exists())` 验证回滚已发生
  - 加 `PdfBoxTextExtractorTest` 3 case（合成 3 页文本 PDF / 空 PDF / 损坏 PDF）；`@RunWith(RobolectricTestRunner::class)` `@Config(sdk = [26])` 与项目 minSdk 对齐
  - `libs.versions.toml` 加 `robolectric = "4.13"`；`build.gradle.kts` `testOptions { unitTests { isIncludeAndroidResources = true } }`
  - 全跑 `:app:testDebugUnitTest` 19 PASSED / 0 / 0 / 0（业务 18，含模板 1）
- ✅ **Phase 6 · 模拟器最小 smoke**：
  - assembleDebug 装 `Medium_Phone_API_36.1` → `am start ...PingActivity`
  - logcat 命中：`I PocketPdfApp: PocketPdfApp onCreate · build=debug`（**证明 PdfBox init 没崩**，否则到不了这行）
  - PingActivity 渲染成功，无 FATAL / AndroidRuntime 异常
  - **完整 SAF 导入演示推迟 Day 3**（W1 UI 任务在 Day 3+；不写 throwaway Activity 避免 Day 3 重写）
- ✅ **Phase 7 · 文档收尾**：本日志 Day 2 节 + 6 个细粒度 commit + push

### Day 2 工程原则补丁

- 开工对齐时，我曾把"项目是面试/学习导向"作为论据来推一个 androidx.startup.Initializer 的中等模块化方案（其实只有 1 个 init 点）。用户**当场打回**："别把本项目是学习和面试导向作为功能决策的因素"
- 已写入 `CONTRIBUTING.md §3` "决策原则：技术评估只用工程论据"：技术选型只接受真实功能/性能/测试/维护需求驱动；YAGNI 优先；**AI 助手若端出"面试/简历"论据，用户应直接打回**
- 修正后决策 2 改回 A（onCreate 直接调 init）——纯工程论据下 1 个 init 点不值得引一个库

### Day 3（2026-05-12）

- ✅ **Phase 0 · 开工自检**：`./gradlew :app:testDebugUnitTest --tests "...domain.*" "...data.local.*"` 跳过 Robolectric PdfBox 测快验 16 PASSED——"我接的代码是 Day 2 留下的版本"防线生效；RecyclerView 显式升级见 commit `b8fe907`
- ✅ **Phase 1 · `ui/library` 状态机 + ViewModel**（commit `98b6f32`）：
  - `LibraryUiState`：4 态 sealed（Empty / Loading / Loaded(documents, isImporting) / Error），与 `PingUiState` 风格对齐（决策 12）
  - `LibraryEvent`：3 种一次性事件 sealed（ShowImportError / ShowDeleteUndo / ShowDeleteError），走 `Channel(BUFFERED)` + `receiveAsFlow()` 模板（与 `PingViewModel.oneShotEvents` 一致）
  - `LibraryViewModel`：`combine(observeDocuments(), pendingDeleteIds, isImporting)` 拼装 uiState；`stateIn(WhileSubscribed(5_000), Loading)`；catch 兜底转 Error 态
  - **UNDO 双轨触发**：swipe 时 launch 一个 Job 在 `pendingDeleteJobs[id]`，5s `delay` 后调 `commitDelete`；同时 Snackbar dismiss callback 也调 `onSnackbarDismissedWithoutUndo` → `commitDelete`。两路通过 `pendingDeleteIds` 集合的 idempotent 检查协调，旋屏/Activity 重建场景 timer 兜底（决策 13）
- ✅ **Phase 2 · 布局 + 资源 + Adapter**（commit `8b7bebb` 资源 / `34b0a4b` Activity+Adapter）：
  - `activity_library.xml`：CoordinatorLayout（让 Snackbar 推 FAB 上移） + RecyclerView + include EmptyView + LinearProgressIndicator + ExtendedFloatingActionButton
  - `item_document.xml`：MaterialCardView + ConstraintLayout（PDF icon / 标题 / `pageCount 页 · 相对时间` / 索引徽章）
  - `view_empty_library.xml`：图标 + 标题 + 副文 + outlined button（复用 FAB 点击行为）
  - 4 个 vector drawable（ic_pdf_doc / ic_library_empty / ic_library_add / bg_badge_index_status）+ 14 个 string 资源
  - `DocumentListAdapter`：`ListAdapter<Document, _>` + DiffUtil（id 比对）；`DateUtils.getRelativeTimeSpanString(importedAt, now, MINUTE_IN_MILLIS, FORMAT_ABBREV_RELATIVE)` 出"3 分钟前 / 昨天 / 上周三"自适应字串（决策 15）
- ✅ **Phase 3 · SAF 接线 + DisplayName 兜底**（合并进 Activity commit `34b0a4b`）：
  - `registerForActivityResult(ActivityResultContracts.OpenDocument())` 注册；FAB / empty-state 副按钮共用 launcher
  - `contentResolver.query(uri, [OpenableColumns.DISPLAY_NAME], …)` 拿原始文件名；`null/blank` 时 fallback `"未命名 PDF · ${相对时间}"`（决策 8）；**绝不从 URI 末段解析**（不同 Provider 格式不一致，决策 8 否决项 B）
- ✅ **Phase 4 · 左滑删除 + UNDO**（同 Activity commit `34b0a4b`）：
  - `ItemTouchHelper.SimpleCallback(0, LEFT or RIGHT)` 挂到 RecyclerView；`onSwiped` 用 `adapter.documentAt(bindingAdapterPosition)` 拿领域对象（`bindingAdapterPosition` 是 recyclerview 1.2.0+ 的 API，比废弃的 `adapterPosition` 在嵌套 adapter 场景安全）
  - Snackbar `addCallback`：`onDismissed(event)` 不是 `DISMISS_EVENT_ACTION`（即非 UNDO 点击触发的 dismiss）时调 `onSnackbarDismissedWithoutUndo`——超时 / 滑掉 / 手动 dismiss 三种情况都视为"确认删除"
- ✅ **Phase 5 · Manifest 切 LAUNCHER**（commit `9fc6a47`）：
  - `LibraryActivity` 拿走 `android.intent.action.MAIN + LAUNCHER` intent-filter
  - `PingActivity` 改 `android:exported="false"` 留代码不留入口（W3 接 LLM 桥接时复用其"按钮 → ViewModel → UseCase → UiState" 模板，决策 1）
- ✅ **Phase 6 · LibraryViewModelTest**（commit `a1d09ef`）：
  - **测试基础设施**：`StandardTestDispatcher` + `Dispatchers.setMain/resetMain` 让 viewModelScope 走 test dispatcher → `advanceTimeBy(5_001)` 精确触发 5s UNDO timer
  - **Mock 选择**：不 mock UseCase（final class，需要 byte-buddy）而 mock 底层 `DocumentRepository` interface——既绕开 mockk final 限制，又多覆盖一层 UseCase + Repository 组合（决策 16）
  - **12 case 覆盖矩阵**：empty → Loading→Empty / 推送 → Loaded / 上游异常 → Error / 成功切换 isImporting / 进行中再点击被忽略 / 失败发 ShowImportError / swipe 立即过滤 + 发 ShowDeleteUndo / 撤销恢复并取消 timer / timer 到点自动 commit / Snackbar dismiss 立即 commit / delete 失败发 ShowDeleteError / 同 id 重复 swipe 幂等
  - 全跑 `:app:testDebugUnitTest` **30 业务测 PASSED**（Day 1 + 2 的 18 + Day 3 新增 12）
- ✅ **Phase 7 · 模拟器最小 smoke**：
  - `adb install -r app-debug.apk` → `am start ...LibraryActivity` → logcat 命中 `PocketPdfApp: PocketPdfApp onCreate · build=debug` + `ActivityManager: Start proc ...` → **App.onCreate 通 + LibraryActivity 启动成功 + 无 FATAL / AndroidRuntime**
  - 空状态截图入库：`docs/screenshots/w1d3-library-empty.png`（59 KB，1080×2400）
  - **SAF + import 完整闭环 / 重启 App 仍在 / 左滑 UNDO 等 GUI 交互**留给开发者在 IDE 上手动验证（自动化只能验装 + 启动，SAF 选 PDF 必须 GUI 点）——同 Day 2 决策 11 精神
- ✅ **Phase 8 · 文档收尾**：本日志 Day 3 节 + 7 个细粒度 commit + push

### Day 3 Code Review 修补（2026-05-14）

- ✅ **Lint 阻断修复**：`view_empty_library.xml` 的空状态图标 tint 从 `android:tint` 改为 `app:tint`，并补 `xmlns:app`，`./gradlew :app:lintDebug` 已重新变绿。
- ✅ **滑动删除防御**：`DocumentListAdapter.documentAt(position)` 增加 `position in 0 until itemCount` 边界保护，覆盖 `RecyclerView.NO_POSITION` / 越界场景，避免 ItemTouchHelper 动画结算期崩溃。
- ✅ **协程取消语义修复**：`resultOf` 显式 rethrow `CancellationException`，普通异常仍包装为 `Result.Failure`，避免把结构化并发取消当作业务失败。
- ✅ **回归测试**：新增 `ResultTest` 2 case + `DocumentListAdapterTest` 2 case；Adapter 测试使用 Robolectric，因为 `ListAdapter` / `AsyncListDiffer` 需要 Android main looper。

### Day 4（2026-05-14）

- ✅ **Phase 0 · 开工自检**：`./gradlew :app:testDebugUnitTest` 基线绿；工作区只剩 `.vscode/` 未跟踪，不纳入本次改动。
- ✅ **Phase 1 · 阅读器方案收敛**：本日没有引入 AndroidPdfViewer；选择 Android Framework 自带 `PdfRenderer`。理由是 W1 目标只需要内部 PDF 的最小阅读闭环，`PdfRenderer` 覆盖 minSdk 26，免新增仓库/ABI/维护风险；第三方 viewer 依赖留到确有分页性能、手势、缩略图需求时再评估。
- ✅ **Phase 2 · `ui/reader` 状态机**：
  - `ReaderUiState`：Loading / Loaded(document) / Error(message)
  - `ReaderViewModel`：通过 `GetDocumentUseCase(documentId)` 读取文档；在 IO dispatcher 校验 `Document.uri` 指向的内部 PDF 文件仍存在；捕获普通异常转 Error，保留 `CancellationException` 向上取消
  - 错误覆盖：非法 id、文档不存在、文件缺失、Repository 抛错
- ✅ **Phase 3 · `ReaderActivity` + 原生渲染**：
  - `ReaderActivity.newIntent(context, documentId)` 作为唯一入口；`LibraryActivity` 列表点击从 Toast 占位改为跳转 Reader
  - `ParcelFileDescriptor.open(file, MODE_READ_ONLY)` + `PdfRenderer` 打开内部 PDF；页面渲染放到 `Dispatchers.IO`，用 `rendererLock` 保证同一时间只操作一个 renderer/page
  - 当前页渲染为白底 ARGB bitmap，宽度按设备宽度 2x、上限 2400 控制内存；页面切换时回收上一张 bitmap
  - 双指缩放使用 `ScaleGestureDetector`，缩放范围 1x–4x；切页后重置缩放
- ✅ **Phase 4 · 阅读器布局资源**：
  - `activity_reader.xml`：MaterialToolbar + 深色阅读 surface + `ImageView` 页面 + loading indicator + error view + 底部 64dp 页码条
  - 新增 `ic_arrow_back` / `ic_chevron_left` / `ic_chevron_right` 三个 vector drawable
  - Manifest 注册 `ReaderActivity(exported=false)`
- ✅ **Phase 5 · ReaderViewModelTest（新增 5 case）**：
  - document exists + file exists → Loaded
  - invalid id → Error
  - repository returns null → Error
  - PDF file missing → Error
  - repository throws → Error
- ✅ **Phase 6 · 验证**：
  - `./gradlew :app:testDebugUnitTest` BUILD SUCCESSFUL（40 tests / 0 failures）
  - `./gradlew :app:lintDebug` BUILD SUCCESSFUL
  - `./gradlew :app:assembleDebug` BUILD SUCCESSFUL
  - GUI 手验项仍需人工执行：SAF 选 PDF → 进入阅读器 → 翻页 / 双指缩放 → 杀进程重启后仍可打开

## 3. 关键决策与权衡

### 决策 1：W1 Day 1 范围内 `importDocument` 故意留 `NotImplementedError`，而不是先把 PdfBox 也一起接

- **背景**：Day 1 + Day 2 共 ~2 个工作日；PdfBox-Android 依赖体积大（~10MB），第一次 sync 慢，且 SAF `openInputStream` + `persistableUriPermission` 在 Android 14+ 有权限变化，撞坑 1–2h 不奇怪
- **候选方案**：
  - A. Day 1 一口气把 PdfBox 解析 + SAF 复制全做完（"最短可演示路径"）
  - B. Day 1 只落"接口 + Room + 半 Repository + 单测"，`importDocument` 留 stub；Day 2 把 PdfBox 接进来
  - C. Day 1 + Day 2 各写一半，但都不留沉降点（结果两天都是半成品，连"今天做完了什么"都说不清）
- **最终决策**：B
- **理由**：
  1. **接口先稳**：DocumentRepository 接口契约今天就定下来，明天 / Day 3 起 ui 层 LibraryViewModel 可以直接依赖它写状态机，**不必等 Day 2 真实现**
  2. **沉降提醒胜过日历提醒**：`ImportDocumentUseCaseTest` 断言 error message 包含 `"DocumentRepositoryImpl.importDocument"`——Day 2 真实现这行代码必删，测试立刻红，逼自己同步重写测试（避免"实现写完忘加测试"的覆盖率塌方）
  3. **单测在 PdfBox 接入之前就把 12 个绿先攒上**，Day 2 真要踩 PdfBox 的坑时心里有底（已有 12 个 PASSED 兜底）
- **代价 / 风险**：
  - Day 1 收尾没法在模拟器上演示"导入一个 PDF"，演示推迟到 Day 2 末
  - `InternalFileStorage.copyToInternal` 也是 stub——双 stub 增加心智负担，靠两处都带明确 message + Day 1 沉降测试卡住

### 决策 2：domain 不依赖 `android.net.Uri`，`importDocument(sourceUri: String, displayName: String)`

- **背景**：自然写法是 `suspend fun importDocument(uri: android.net.Uri): Result<Document>`，但 `android.net.Uri` 是 Android Framework 类
- **候选方案**：
  - A. domain 直接 import `android.net.Uri`（一行代码省事）
  - B. domain 用 `String` 装 URI，ui 层 `uri.toString()`，data 层 `Uri.parse(sourceUri)`
  - C. domain 抽 `sealed class DocumentSource { data class SafUri(val raw: String) : ... ; data class FilePath(val abs: String) : ... }`
- **最终决策**：B
- **理由**：
  1. **依赖方向**：CONTRIBUTING.md §2 末尾"domain 不能 import androidx.*、android.*"是硬性规则，违反必打回——B 是唯一既满足规则又不过度抽象的方案
  2. **可单测性**：domain 测试在 JVM 直接跑（不上 Robolectric），如果 domain 引 `android.net.Uri`，单测要不就 Robolectric 加重，要不就 mockk `Uri.parse` 静态调用——都是浪费
  3. C 看似优雅，但 W1 范围内只有 SAF 一个来源，过早抽象违反 YAGNI；真要多源时再升 C
- **代价**：ui 层多一行 `uri.toString()`，data 层多一行 `Uri.parse(sourceUri)`——可以接受

### 决策 3：`Document.uri` 存**绝对路径**而非 SAF content URI

- **背景**：SAF 选完后 `content://...` 重启 App 可能失效（除非 `takePersistableUriPermission`，但即便 take 了，设备升级 / 用户清除文件管理器数据也会断）
- **候选方案**：
  - A. 存 SAF content URI 原文 + `takePersistableUriPermission`
  - B. 复制到 `filesDir/documents/<uuid>.pdf` + 存绝对路径
- **最终决策**：B
- **理由**：
  1. **重启 App 仍可读**（W1 验收线 "导入 → 重启 App 仍在" 直接靠这个）
  2. **W2 切块 + 向量化时无需保留 SAF 权限**：所有 chunk 都引用本地路径，索引数据再大也不依赖外部状态
  3. **卸载即清理**：用户卸载 App，`filesDir` 自动被 OS 清理，不留垃圾
- **代价**：APK 内部存储占用上升；30 页 PDF ~1MB 量级，100 个文档也才 100MB，可接受。极端情况留 W4 加"按需清理"

### 决策 4：`IndexStatus` 存 String (`name`) 而非 Int (`ordinal`)

- **背景**：Room 默认枚举可以靠 `@TypeConverter` 转 Int（序数）或 String（名字），二选一
- **决策**：String
- **理由**：序数会被"中间插入枚举值 / 重排顺序"破坏（如把 `INDEXING` 从 1 改到 2，旧库里所有 1 都变成 `INDEXED`，**静默数据腐败**）。String 用 `name` 唯一稳定，最坏情况是新版本删了枚举值 → `valueOf` 抛 `IllegalArgumentException` → 在 Mapper 里 fail-loud
- **代价**：行宽多几个字节（`"NOT_INDEXED"` vs `0`），TEXT 比 INTEGER 慢极少；W1 量级看不出来

### 决策 1：W1 Day 1 范围内 `importDocument` 故意留 `NotImplementedError`，而不是先把 PdfBox 也一起接

- **候选方案**：
  - A. `object DocumentMapper { fun entityToDomain(e: DocumentEntity): Document }`
  - B. `class DocumentMapper @Inject constructor() { fun map(e: DocumentEntity): Document }`（Hilt 注入）
  - C. **顶层 internal extension function**：`internal fun DocumentEntity.toDomain(): Document`
- **决策**：C
- **理由**：
  1. **调用点简洁**：`entity.toDomain()` vs `DocumentMapper.entityToDomain(entity)` — 在 Repository 里有 N 个映射点时区别很大
  2. **`internal` 限制可见性**：ui 模块拿不到 mapper → 不能绕过 UseCase 直读 Entity，强制走 Repository 接口
  3. **无需注入**：纯函数无状态，注入只增 boilerplate；测试时直接调用
- **代价**：没法在 Hilt 图里替换实现——但 Mapper 是纯映射，没有"换实现"场景

### 决策 6（Day 2）：PdfBox 初始化放 `Application.onCreate` 而非 `androidx.startup.Initializer`

> Day 2 开工对齐时我曾推 Startup 模块化方案，被用户基于"别拿面试导向压复杂度"打回。重审后基于纯工程论据。

- **背景**：`com.tom-roush:pdfbox-android` 需要 `PDFBoxResourceLoader.init(applicationContext)` 加载 Standard 14 字体 / 字符表；否则第一次 `PDDocument.load` 解析时炸 NPE
- **候选**：
  - A. `PocketPdfApp.onCreate()` 直接调 init（1 行）
  - B. 加 `androidx.startup:startup-runtime` + `class PdfBoxInitializer : Initializer<Unit>` + manifest 注册
- **决策**：A
- **理由（纯工程论据）**：
  1. **W1 当前只有 1 个 init 点**（PdfBox）。W2-W5 ROADMAP 上**实际**会出现的 init 点：SentenceEmbeddings（W2）+ AppDatabase 预热（可选）。没有其他
  2. "为 1 行代码加 1 个库 + 加 manifest" 投入产出比为负——YAGNI
  3. "Application 类会膨胀"是未来可能的问题，不是现存问题；当前 onCreate 只 4 行
  4. W2 真要加多于 1 个 init 点时一次性迁 Startup，迁移成本与现在迁相同 → 晚迁不亏
- **代价**：未来若 init 点扩到 3+，要一次性重构 onCreate 迁 Startup。预估 1h
- **反例（被打回的论据）**：~~"演示 androidx.startup 的合理用法对面试有正价值"~~ —— **违反"决策原则只用工程论据"，禁止**

### 决策 7（Day 2）：PdfTextExtractor 接口形态 = `List<String>`（按 PDF 自带页边界切，零 chunking）

- **背景**：PdfBox 提取文本时可以"一把 strip 全文"或"按页 setStartPage/setEndPage 反复 strip"
- **候选**：
  - A. 接口只返回 `pageCount: Int`（W1 Day 2 现阶段够用，W2 切块时再扩接口）
  - B. 接口返回 `List<String>`（每页一段原文），调用方拿 size 当 pageCount，W2 切块也直接复用同一个出口
  - C. 接口返回 `data class PdfExtractResult(pageCount, pagesText, isScannedHeuristic)`
- **决策**：B
- **理由**：
  1. **职责清晰**：PdfTextExtractor 只做"按 PDF 自带页边界提取"，**不做任何 chunking / splitting**——W2 chunking 算法是用户重点研究方向（递归字符 / 滑窗 / 语义切分），与 extractor 解耦
  2. **一次封装两件事**：pageCount 和 pagesText 同一次 PDF open 拿到，多走一次 strip 比再 open 一次 PDF 便宜
  3. C 加了 `isScannedHeuristic` 这种"未来可能"字段，违反 YAGNI——真要识别扫描件 W3 再加
- **代价**：W1 Day 2 内部只用到 `list.size`，其他元素暂存内存 → 30 页 PDF 文本量 ~30KB-300KB，可接受

### 决策 8（Day 2）：PdfTextExtractor 接口放 `data` 层而非 `domain`

- **候选**：
  - A. `domain/repository/PdfTextExtractor.kt`（domain 抽接口，data Impl）
  - B. `data/pdf/PdfTextExtractor.kt` 接口 + `PdfBoxTextExtractor` 实现都在 data
  - C. 不抽接口，Repository 直接注入 `class PdfBoxTextExtractor @Inject`
- **决策**：B
- **理由**：
  1. domain 不该知道 `java.io.File` —— 接口签名 `extractPagesText(file: File)` 一旦放 domain 就违反"domain 不引 IO 概念"
  2. data 层抽接口的价值是**让 Repository 单测可以 mock**（不让 Repository 测背 PdfBox 真实例）——B 已满足；A 是过度抽象
  3. C 牺牲了"Repository 测可 mock 协作者"，回归测试金字塔倒挂

### 决策 9（Day 2）：`importDocument` 失败时显式删半成品文件（三步回滚）而非依赖清理 Worker

- **背景**：编排链路 copy → extract → dao.insert 三步，任一抛异常都可能留孤儿文件
- **候选**：
  - A. 不主动回滚 —— 失败时孤儿文件留磁盘，依赖未来清理 Worker（同 deleteDocument 的"DB 删文件留"策略）
  - B. 在 extract / dao.insert 失败时 `copied.delete()` 后 rethrow；copy 自己失败时由 FileStorage 内部删（已在 InternalFileStorage 实现）
- **决策**：B
- **理由**：
  1. **导入** 和 **删除** 的语义不对称：删除是"用户主动；DB 已删；孤儿文件不影响功能"；导入是"用户不知道导入失败了；磁盘累积 .pdf 半成品"——后者让"卸载即清理"语义打折
  2. W1 验收线 "重启 App 仍在" 不直接要求磁盘干净，但**磁盘干净是 W3 / W4 "导入失败时给用户清晰反馈"功能的底线**——现在不做就在累积技术债
  3. 多 5 行 try/catch 不是大成本；锁进 4 个测试（happy + 3 个 fail，其中后 2 个 `assertFalse(copied.exists())` 验证回滚已发生）

### 决策 10（Day 2）：PdfTextExtractor 单测用 Robolectric 而非 androidTest

- **背景**：JVM 单测试运行 `PDType1Font.HELVETICA` 静态初始化时炸 `ExceptionInInitializerError → IllegalArgumentException → IOException` —— PdfBox-Android 把 Standard 14 字体的 AFM 表搬到了 Android assets，必须有 Context 才能加载
- **候选**：
  - A. **Robolectric**：`@RunWith(RobolectricTestRunner::class)` + `@Before` 调 `PDFBoxResourceLoader.init(RuntimeEnvironment.getApplication())`
  - B. **androidTest（instrumentation）**：挪到 `src/androidTest/`，跑真机/模拟器
  - C. **跳过自动化**：只留 corrupted-pdf 一个 case，提取链路靠 Phase 6 手动 smoke 兜底
- **决策**：A
- **理由（纯工程论据）**：
  1. **真实存在的覆盖率漏洞**：W1 Day 2 范围内 PdfBox 是 import 链路核心，没自动化测等于把"PdfBox 版本升级 / API 变更"风险转嫁到肉眼 review
  2. Robolectric 是 pdfbox-android **GitHub README 官方说明**的测试方式（业界共识）
  3. B（androidTest）启动模拟器开发循环慢，且 W1 当前没配 androidTest runner——成本远大于 Robolectric
  4. C 留覆盖率漏洞且未来 PdfBox 升级无自动信号
- **代价**：
  - 加 `org.robolectric:robolectric:4.13` 单测依赖（~30MB 测试侧）
  - PdfBoxTextExtractor 第一次跑 152s（Android 26 framework jar 下载 + classloader 预热），第二次跑回到秒级
- **不算论据的副产品**：W2 末 Room migration 测 / W3 LibraryViewModel 测可能复用同一套 Robolectric 基础设施。**但这不是决策依据**，只是 nice-to-have

### 决策 11（Day 2）：模拟器演示推迟到 Day 3 接 UI 后做，不写一次性 SafSmokeActivity

- **候选**：
  - A. 推迟 Day 3 一起做（W1 任务里 "文件选择器 (SAF)" 是 Day 3+ 任务）
  - B. 今天写个一次性 `SafSmokeActivity` 只为触发 SAF 选 PDF → import → logcat，Day 3 删掉
  - C. 完全跳过演示
- **决策**：A，加最小 smoke：装 APK 看 onCreate 跑通（PdfBox init 不崩）
- **理由**：
  1. **不写吃后悔药代码**：Day 3 真的要写 `LibraryActivity`，B 写的 throwaway 必删 → 等于白写
  2. **风险已可控**：Day 2 提交里 happy path + 3 个失败回滚单测 + assembleDebug 通过 + 模拟器装 APK 不崩，**核心风险面已覆盖**；缺失部分（SAF UI → ContentResolver.openInputStream 真路径）Day 3 自然走通
  3. C 完全跳过演示是另一极——但 assembleDebug 不能验证 Application init 的 runtime 行为（init 如果崩 → app 启动闪退），最小 smoke 收益不为零

### 决策 12（Day 3）：`LibraryUiState` 用 4 态 sealed（与 `PingUiState` 对齐）而非 2 字段 data class

- **候选**：
  - A. **4 态 sealed**：`Empty / Loading / Loaded(documents, isImporting) / Error(message)`，与 `PingUiState` 同模板
  - B. **2 字段 data class**：`LibraryUiState(documents: List<Document>, isImporting: Boolean)`，Empty 用 `isEmpty()` 推导，Error 走 one-shot 事件
  - C. **3 态 sealed**：Loading / Content / Error，砍掉 Empty（让 View 自己判 isEmpty）
- **决策**：A
- **理由（纯工程论据）**：
  1. **模板一致性**：W0 写 `PingUiState` 时已经确立 4 态 sealed 模式，W3 LLM 桥接和 W4 聊天窗口都将复用——一致模板降低跨 ViewModel 阅读成本
  2. **when 强制穷尽**：View 的 `render(state)` 是 `when (state)` 形态，sealed class 让编译器在未来加新态时**强制**所有 render 点更新；data class 没有这个保证
  3. **`Loaded(isImporting=true)` 解决了"导入中要不要清空列表"的细分**：Loading 是首次订阅前的空态，isImporting 是叠加在 Loaded 上的瞬态——两套语义不混
- **代价**：`Error` 态在 W1 实际触发不了（Room Flow `observeDocuments` 不抛），但作为 catch 上游异常的兜底必须有——不是死代码，是按 Murphy's Law 的防御代码

### 决策 13（Day 3）：删除 UNDO 用"5s ViewModel timer + Snackbar dismiss callback"**双轨**触发

- **背景**：Snackbar UNDO 是 Material 标准设计，但 Snackbar 寿命在 View 范围——旋屏 / Activity 重建后 Snackbar 没了，仅靠 callback 触发会留 dangling state
- **候选**：
  - A. **仅靠 Snackbar dismiss callback 触发 commit**：实现简单；缺点：旋屏后 callback 不触发 → `pendingDeleteIds` 永久含该 id → UI 永远看不到该 doc 但 DB 还在
  - B. **仅靠 ViewModel 5s timer 触发 commit**：旋屏无问题；缺点：用户主动 dismiss Snackbar 后还要等 5s 真删，体验拖沓
  - C. **双轨触发**：swipe 时 launch 一个 `pendingDeleteJobs[id]` 5s timer + Snackbar `addCallback` 在 non-ACTION dismiss 时调 `commitDelete`。两路通过 `commitDelete` 内部 `if (id !in pendingDeleteIds) return` 的 idempotent 检查协调
- **决策**：C
- **理由**：
  1. **W1 验收线"重启 App 仍在"间接要求**："列表里看不到但 DB 还在"会让重启 App 后被删的 doc 又复现——直接破坏验收线
  2. **Idempotent 设计成本极低**：`pendingDeleteIds` 是 `MutableStateFlow<Set<Long>>`，`update { it - id }` 是 atomic CAS；`commitDelete` 第一行检查后才调 DAO，第二次调进入直接 return
  3. **测试可锁住**：`auto-commit fires after timeout` + `snackbar dismiss commits immediately` + `repeated swipe is idempotent` 三个 case 把双轨行为锁进 CI
- **代价**：ViewModel 多 `pendingDeleteJobs: MutableMap<Long, Job>` 一字段；Snackbar callback / ViewModel timer 在 idempotent 设计下都是"互相兜底"，代码看起来冗余但语义上不可省

### 决策 14（Day 3）：RecyclerView 1.3.2 显式声明，而非吃 material:1.12 的传递依赖

- **背景**：material 1.12.0 传递依赖 `androidx.recyclerview:recyclerview:1.0.0 → 1.1.0`；项目此前没显式声明
- **撞坑信号**：`viewHolder.bindingAdapterPosition` 在 1.1.0 不存在（1.2.0+ 才有），Kotlin 编译期报 `Unresolved reference`
- **候选**：
  - A. **改用废弃的 `adapterPosition`（1.0.0 起就有）**：1 行代码改完。但 `adapterPosition` 在 RecyclerView 2018+ 已标 `@Deprecated`，原因是"嵌套 adapter 时返回错乱"——是真实 bug
  - B. **显式声明 `androidx.recyclerview:recyclerview:1.3.2`**：稳定版（2022 末发布），含 bindingAdapterPosition 修复
- **决策**：B
- **理由（纯工程论据）**：
  1. **`bindingAdapterPosition` 修了真实 bug**：嵌套 adapter / 列表动画期间 `adapterPosition` 返回 -1 或错位的问题，对左滑删除场景是直接相关风险
  2. **传递依赖锁在 1.1.0 是 material:1.12 的不一致**：material 自身在 1.10+ 已用 RecyclerView 1.2.x API，但 POM 里写的下界还是 1.0.0——这是 material 维护历史包袱，不是我们项目的设计选择；显式锁版本反而对齐了"实际编码用到什么 API、声明里就显式声明"
  3. **YAGNI 检查通过**：不是"未来可能用到 1.3 新功能" → "现在引一下"，是"现在编译失败 → 必须升 → 升当前稳定版" 的最小修
- **代价**：依赖图多一行；包大小没变化（material 已经把 recyclerview 拖进来过了，只是版本号变高）

### 决策 15（Day 3）：列表元数据时间用 `DateUtils.getRelativeTimeSpanString`（相对时间）

- **候选**：
  - A. **`DateUtils.getRelativeTimeSpanString` 相对时间**：Android 系统 API，自适应 "3 分钟前 / 昨天 / 上周三 / 2024-05-12"
  - B. **`SimpleDateFormat("yyyy-MM-dd HH:mm")` 绝对时间到分钟**
  - C. **手写"1 天内显示相对，1 天外显示日期"混合策略**
- **决策**：A
- **理由**：
  1. **本质上 A 已经实现了 C**：`getRelativeTimeSpanString` 内部按 minute/hour/day/week 自适应——`FORMAT_ABBREV_RELATIVE` flag 让格式简短
  2. **零依赖 / 零自写逻辑**：避免 `SimpleDateFormat` 的时区 / locale 陷阱（系统切日语 / 阿拉伯文时 A 自动出对应语言文案）
  3. **文档库高频看"最近导入哪份"** → 相对时间语义匹配
- **代价**：Adapter 持有 `Context`（DateUtils 静态方法吃 Context）——但 ViewHolder 本来就有 `binding.root.context`，没引入额外注入

### 决策 16（Day 3）：`LibraryViewModelTest` 不 mock UseCase，而 mock 底层 `DocumentRepository`

- **背景**：ViewModel 构造函数吃 3 个 UseCase（observe / import / delete），都是 final class（不是 interface）
- **候选**：
  - A. **mock 3 个 UseCase**：每个 final class mock 需要 mockk 的 byte-buddy agent；语法 `mockk<ImportDocumentUseCase>()` 在 mockk 1.13.13 + JVM 17 可工作但偶发 ClassLoader 问题
  - B. **mock `DocumentRepository`**（interface，零黑魔法），用真 UseCase 包装：`ObserveDocumentsUseCase(repository) / ImportDocumentUseCase(repository) / DeleteDocumentUseCase(repository)`
- **决策**：B
- **理由**：
  1. **绕开 mockk final 限制**：mockk interface mocking 是稳态特性，final class mocking 依赖 JVM agent 注入——在不同 JDK / IDE 环境下偶发失败
  2. **多覆盖一层组合**：ViewModel + 3 个真 UseCase + mock Repository 比 ViewModel + 3 个 mock UseCase 更接近线上行为
  3. UseCase 本身已被各自的 *UseCaseTest 单独测过（Day 1 - 2 共 4 + 2 + 2 = 8 case），Day 3 重复 mock 无新增覆盖
- **代价**：测试 `@Before` 多写 3 行 `XxxUseCase(repository)`——可以接受

## 4. 踩坑记录

| 问题 | 原因 | 解决 | 用时 |
|---|---|---|---|
| `git commit -m "...$projectDir..."` 报 `'\/schemas' is outside repository` | PowerShell 把 `$projectDir` 当 shell 变量展开成空字符串 | 改用 `git commit -F <file>` HEREDOC 写法（用 `.git/COMMIT_EDITMSG_TMP` 临时文件）；其它带 `$` 的字符串同理 | 2 min |
| `resultOf { ... fileStorage.delete(uri) }` 编译报 `Result<Boolean>` 不能赋给 `Result<Unit>` | `resultOf` 推断的 T = 最后表达式类型 = `Boolean` | 在 block 末尾显式追加 `Unit`；顺便在注释里写清楚"文件删除失败不视为流程失败：DB 行已删，孤儿文件可由清理 Worker 兜底" | 1 min |
| Hilt aggregate deps 在 `compileDebugJavaWithJavac` 阶段提示 `uses or overrides a deprecated API` for `Hilt_PocketPdfApp.java` | Hilt 生成代码里有 deprecated API；W0 已有，无业务影响 | 不修，加 `-Xlint:deprecation` 后续看是否升 Hilt 2.53+ | 0 min |
| Moshi Kapt 警告 | W0 已确认是 Moshi codegen 的误报（已走 KSP），见 `week0.md` §4 | 不修 | 0 min |
| **Day 2**：PdfBoxTextExtractorTest JVM 跑 `PDType1Font.HELVETICA` 静态初始化炸 `ExceptionInInitializerError → IllegalArgumentException → IOException` | pdfbox-android 把 Standard 14 字体 AFM 表搬到 Android assets，JVM 无 Context 时 PDFBoxResourceLoader 找不到资源 | 走 Robolectric（决策 10）：`@RunWith(RobolectricTestRunner)` + `@Config(sdk=[26])` + `@Before` 调 `PDFBoxResourceLoader.init(RuntimeEnvironment.getApplication())`；同步加 `testOptions.unitTests.isIncludeAndroidResources = true` | 15 min（含跟用户对齐方案 + 第一次 Robolectric 启动 6m43s） |
| **Day 2**：sentinel `ImportDocumentUseCaseTest` 不是按"message 字符串断言失败"翻红，而是**构造函数签名变了直接编译失败** | `DocumentRepositoryImpl` 加了 `pdfTextExtractor` 参数从 3 参变 4 参；sentinel 测试的旧 3 参构造调用编译都不过 | 这恰恰是 Day 1 设计沉降的**最强信号**——不是 runtime assert，是 compile-time 失败。重写为 4 case（happy + 3 个失败回滚） | 0 min（识别成本） |
| **Day 2**：第一次 PdfBox sync 慢到 5m 31s | `com.tom-roush:pdfbox-android:2.0.27.0` ~10MB，Maven Central 拉取 + AGP 缓存预热 | Day 1 计划留话 #2 已预判，单独 sync 一次再写业务；第二次 build 1m 8s 回到正常 | 0 min（预案命中） |
| **Day 2**：dependencies 任务 PowerShell pipe 卡死 17 min 后被中断 | Gradle dependencies + PowerShell `Select-String` pipe 输出量大，pipe 阻塞 | 中断后直接走 assembleDebug 验证依赖可解析——本来 Phase 1 末就要跑的步骤合并了 | 0 min（中断后改步骤无延迟） |
| **Day 3**：`viewHolder.bindingAdapterPosition` 编译报 `Unresolved reference` | material:1.12.0 传递依赖 `androidx.recyclerview:recyclerview` 锁在 **1.1.0**，缺 1.2.0+ 引入的 `bindingAdapterPosition` API | 显式声明 `recyclerview = "1.3.2"`（决策 14，工程论据成立：修了嵌套 adapter / 动画期间 position 错乱的真实 bug） | 3 min（dependencies 查版本 + libs.versions.toml / build.gradle.kts 改两处 + 重新 compile） |

## 5. 关键代码片段

### Day 1 沉降测试（Day 2 真实现时必然红）

```kotlin
@Test
fun `Day 1 stub returns Result Failure with NotImplementedError carrying the impl marker`() =
    runTest {
        val dao = mockk<DocumentDao>(relaxed = true)
        val fileStorage = mockk<FileStorage>(relaxed = true)
        val impl = DocumentRepositoryImpl(dao, fileStorage, TestDispatcherProvider())
        val useCase = ImportDocumentUseCase(impl)

        val result = useCase("content://...pdf%3A42", "test.pdf")

        assertTrue(result is Result.Failure)
        val error = (result as Result.Failure).error
        assertTrue(error is NotImplementedError)
        assertTrue(
            "Day 2 marker missing from message: '${error.message}'",
            error.message?.contains("DocumentRepositoryImpl.importDocument") == true,
        )
    }
```

**沉降逻辑**：Day 2 接入 PdfBox 时，`DocumentRepositoryImpl.importDocument` 那段 stub 代码必删 → 这条测试要么不再 Failure，要么 message 不再包含 marker 字符串，**任一情况都让测试红**。CI 上看到红立刻知道"忘了同步重写沉降测试"。

### Repository 双 stub 的协同设计

```kotlin
// DocumentRepositoryImpl.importDocument()
override suspend fun importDocument(
    sourceUri: String,
    displayName: String,
): Result<Document> = Result.Failure(
    NotImplementedError(
        "DocumentRepositoryImpl.importDocument: deferred to W1 Day 2 " +
            "(PdfBox text extraction + SAF stream copy + DAO insert)",
    ),
)

// InternalFileStorage.copyToInternal()
override suspend fun copyToInternal(sourceUri: String, displayName: String): File {
    throw NotImplementedError(
        "InternalFileStorage.copyToInternal: deferred to W1 Day 2 " +
            "(SAF ContentResolver stream copy)",
    )
}
```

两个 stub 各带"我是谁/为什么没实现/Day 2 接什么"的明确 message——Day 2 grep `NotImplementedError` 即可定位所有沉降点，message 自带"接什么"的小抄。

### Hilt @Binds 三连（domain ← data）

```kotlin
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds @Singleton
    abstract fun bindLlmRepository(impl: LlmRepositoryImpl): LlmRepository

    @Binds @Singleton
    abstract fun bindDocumentRepository(impl: DocumentRepositoryImpl): DocumentRepository

    @Binds @Singleton
    abstract fun bindFileStorage(impl: InternalFileStorage): FileStorage

    companion object Providers {
        @Provides @Singleton
        fun provideDispatcherProvider(): DispatcherProvider = DefaultDispatcherProvider()
    }
}
```

`abstract class + @Binds` 比 `object + @Provides` 节字节码（不生成 Provider 包装类）；`companion object` 复用同一个 Module 文件存放 `@Provides`，避免每加一个 dispatcher / 转换器就开新文件。

### Day 2 · `importDocument` 三步编排 + 三步回滚

```kotlin
override suspend fun importDocument(
    sourceUri: String,
    displayName: String,
): Result<Document> = withContext(dispatchers.io) {
    resultOf {
        val copied: File = fileStorage.copyToInternal(sourceUri, displayName)
        val pageCount: Int = try {
            pdfTextExtractor.extractPagesText(copied).size
        } catch (t: Throwable) {
            copied.delete()
            throw t
        }
        val entity = DocumentEntity(
            title = displayName,
            uri = copied.absolutePath,
            pageCount = pageCount,
            indexStatus = IndexStatus.NOT_INDEXED.name,
            importedAt = System.currentTimeMillis(),
        )
        val insertedId = try {
            dao.insert(entity)
        } catch (t: Throwable) {
            copied.delete()
            throw t
        }
        entity.copy(id = insertedId).toDomain()
    }
}
```

**关键设计**：
- `resultOf {...}` 最外层包，所有 rethrow 都被它接住转 `Result.Failure`
- copyToInternal 自己负责删半成品（见 InternalFileStorage 的 catch），所以 Repository 这里**不再二次删**——单一权责
- extract / dao.insert 各自 try/catch 删文件后 rethrow——3 个失败路径都锁进测试

### Day 2 · 沉降测试 Day1→Day2 的最强失败信号

```kotlin
// Day 1 的 sentinel 是 message 字符串断言
assertTrue(error.message?.contains("DocumentRepositoryImpl.importDocument") == true)

// 但 Day 2 真实现把 DocumentRepositoryImpl 构造函数从 3 参变 4 参后：
// sentinel 的旧调用编译都过不去，连 runtime 都到不了
e: file:///.../ImportDocumentUseCaseTest.kt:36:17
   No value passed for parameter 'pdfTextExtractor'.
```

**比 message 字符串断言更狠**——message 还可能"被自动 grep / 模糊匹配"绕过，**编译错误是绝对失败**。这是 Day 1 设计 sentinel 时没想到的"意外加固"。

### Day 2 · Robolectric init 与按页 strip 的最小骨架

```kotlin
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26])
class PdfBoxTextExtractorTest {

    @Before
    fun setUp() {
        PDFBoxResourceLoader.init(RuntimeEnvironment.getApplication())
        extractor = PdfBoxTextExtractor(TestDispatcherProvider())
    }
    // ...
}

// PdfBoxTextExtractor 实现：按页 setStartPage/setEndPage 反复 getText
val total = document.numberOfPages
buildList(total) {
    for (page in 1..total) {        // ← PdfBox 页码从 1 开始，不是 0！
        stripper.startPage = page
        stripper.endPage = page
        add(stripper.getText(document))
    }
}
```

**两个易踩坑点**：
1. `RuntimeEnvironment.getApplication()` 是 Robolectric 自带的拿 shadow Application 方式，不需要 `androidx.test:core` 依赖
2. PDFTextStripper 页码 1-based，写 0..n-1 会丢第一页且最后一页越界——不在这里写出来下次会再踩

### Day 3 · `LibraryViewModel` combine 三源拼 uiState + UNDO 双轨

```kotlin
val uiState: StateFlow<LibraryUiState> = combine(
    observeDocuments(),
    pendingDeleteIds,
    isImporting,
) { documents, pending, importing ->
    val visible = if (pending.isEmpty()) documents else documents.filterNot { it.id in pending }
    when {
        visible.isEmpty() && !importing -> LibraryUiState.Empty
        else -> LibraryUiState.Loaded(visible, isImporting = importing)
    }
}.catch { t -> emit(LibraryUiState.Error(t.message ?: t.javaClass.simpleName)) }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState.Loading)
```

**设计要点**：
- `pendingDeleteIds` 让 swipe 后列表**立刻**看不到该 doc，不等 Snackbar dismiss 或 DAO delete
- `isImporting` 叠加在 Loaded 上，不把整页切 Loading——已有列表时导入中仍可见旧项
- `catch` 转 Error 态是 Room Flow 几乎不会触发的兜底；import/delete 失败走 `LibraryEvent` one-shot，不污染 uiState

### Day 3 · SAF DISPLAY_NAME 查询 + fallback（不从 URI 末段解析）

```kotlin
private fun resolveDisplayName(uri: Uri): String {
    val resolved = contentResolver.query(
        uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null,
    )?.use { cursor ->
        if (cursor.moveToFirst()) {
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0) cursor.getString(idx) else null
        } else null
    }
    return resolved?.takeIf { it.isNotBlank() } ?: fallbackDisplayName()
}
```

**易踩坑**：`Uri.lastPathSegment` 在 SAF 上经常是编码 docId（`primary%3ADownload%2Ffoo.pdf`），不同 Provider 格式不一致——W1 用 OpenableColumns + 时间戳 fallback。

## 6. 性能数据

| 指标 | 值 | 备注 |
|---|---|---|
| 冷启动 `:app:assembleDebug`（Day 1） | ~75 s | 加 Room 后比 W0 末（~3.5 min）反而快——KSP 已稳定，无 plugin 解析 |
| 单测全跑 `:app:testDebugUnitTest`（Day 1） | ~90 s（含冷启 KSP testDebugUnitTest） | 单独 testDebugUnitTest 任务本身只需几秒 |
| Day 1 新增代码行 | +899 行（25 个文件） | 含 `1.json` schema 74 行 + 注释占比 ~30% |
| **Day 2**：PdfBox-Android 第一次 sync `assembleDebug` | ~5 min 31 s | `pdfbox-android:2.0.27.0` ~10MB，Maven Central 拉取 + AGP 缓存预热；Day 1 计划留话 #2 已预判 |
| **Day 2**：第二次 `assembleDebug`（依赖已缓存） | ~1 min 8 s | 回到正常水平，与 Day 1 同量级 |
| **Day 2**：单测全跑 `:app:testDebugUnitTest`（含 Robolectric 第一次） | ~6 min 43 s | Robolectric 第一次跑 Android 26 framework jar 下载 + classloader 预热；`PdfBoxTextExtractorTest` 第一个 case 152.6 s |
| **Day 2**：单测全跑（Robolectric 已缓存） | 预期 ~30-40 s | 含 ImportDocumentUseCaseTest 4 个新 case + PdfBoxTextExtractorTest 3 个；Robolectric 启动 +3-5 s |
| **Day 2** 净增代码行 | +263 行 / 9 文件改 + 2 新目录（`data/pdf/` 主 2 文件 + 测 1 文件） | 含注释比 ~25%；CONTRIBUTING.md +16 行（决策原则补丁） |
| **Day 3**：`compileDebugKotlin`（RecyclerView 1.3.2 首次显式声明后） | ~11 s | 撞 `bindingAdapterPosition` 后升 recyclerview 1.3.2，重编 Kotlin 回到秒级 |
| **Day 3**：`assembleDebug`（依赖已缓存） | ~1 min 55 s | 含 ViewBinding 生成 4 个 layout + Hilt LibraryViewModel 聚合 |
| **Day 3**：单测全跑 `:app:testDebugUnitTest`（Robolectric 已缓存） | ~13 s | 31 PASSED（业务 30 + 模板 1）；`LibraryViewModelTest` 12 case ~1.0 s |
| **Day 3** 净增代码行 | +约 1.1k 行 / 4 个 Kotlin 主文件 + 3 layout + 4 drawable + 14 string + 1 测 | 含注释比 ~28%；截图 `w1d3-library-empty.png` 59 KB |

## 7. 测试

### Day 1 单测（保留不变 — 全绿）

| 测试类 | tests | failures | errors | skipped |
|---|---|---|---|---|
| `ObserveDocumentsUseCaseTest` | 2 | 0 | 0 | 0 |
| `GetDocumentUseCaseTest` | 2 | 0 | 0 | 0 |
| `DeleteDocumentUseCaseTest` | 2 | 0 | 0 | 0 |
| `ImportDocumentUseCaseTest`（sentinel） | 1 | 0 | 0 | 0 |
| `DocumentMappersTest` | 5 | 0 | 0 | 0 |
| **Day 1 合计** | **12** | **0** | **0** | **0** |

### Day 2 单测变化（净增 6）

| 测试类 | Day 1 | Day 2 | 变化 |
|---|---|---|---|
| `ImportDocumentUseCaseTest` | 1（sentinel） | **4**（happy + copy fail + extract fail + dao fail）| +3（替换 1 个为 4 个）|
| `PdfBoxTextExtractorTest`（新增，Robolectric） | — | **3**（3 页文本 / 空 PDF / 损坏 PDF）| +3 |
| 其他 Day 1 测试类 | 9 | 9 | 不变 |
| **业务测试合计（不含 ExampleUnitTest）** | **12** | **18** | **+6** |

### Day 2 全跑结果（含模板 `ExampleUnitTest` 1 个）

```
DocumentMappersTest                5 PASSED
ObserveDocumentsUseCaseTest        2 PASSED
GetDocumentUseCaseTest             2 PASSED
DeleteDocumentUseCaseTest          2 PASSED
ImportDocumentUseCaseTest          4 PASSED  ← Day 2 重写
PdfBoxTextExtractorTest            3 PASSED  ← Day 2 新增（Robolectric）
ExampleUnitTest                    1 PASSED  （模板自带，不算业务）
─────────────────────────────────
合计                              19 PASSED  / 0 failures / 0 errors / 0 skipped
```

- 测试命令：`./gradlew :app:testDebugUnitTest` BUILD SUCCESSFUL in 6m 43s（含 Robolectric 第一次启动）
- Day 2 新增 4 个回滚类测试**端到端验证**了"importDocument 三步任一失败都 Result.Failure 且磁盘干净"：
  - `extractor failure deletes copied file` —— `assertFalse(copied.exists())`
  - `dao insert failure deletes copied file` —— `assertFalse(copied.exists())`
- domain 层覆盖率（粗估）≈ 92%（Repository.importDocument 编排 + 3 个失败路径全覆盖）；data/pdf 路径 ~100%（3 个 case 覆盖正常 / 边界 / 异常）

### Day 3 单测变化（净增 12）

| 测试类 | Day 2 | Day 3 | 变化 |
|---|---|---|---|
| `LibraryViewModelTest`（新增，纯 JVM + mockk + Turbine） | — | **12**（Empty/Loaded/importing/import fail/swipe/undo/timer/dismiss/delete fail/idempotent） | +12 |
| 其他 Day 1–2 测试类 | 18 | 18 | 不变 |
| **业务测试合计（不含 ExampleUnitTest）** | **18** | **30** | **+12** |

### Day 3 全跑结果（含模板 `ExampleUnitTest` 1 个）

```
DocumentMappersTest                5 PASSED
ObserveDocumentsUseCaseTest        2 PASSED
GetDocumentUseCaseTest             2 PASSED
DeleteDocumentUseCaseTest          2 PASSED
ImportDocumentUseCaseTest          4 PASSED
PdfBoxTextExtractorTest            3 PASSED
LibraryViewModelTest              12 PASSED  ← Day 3 新增
ExampleUnitTest                    1 PASSED  （模板自带，不算业务）
─────────────────────────────────
合计                              31 PASSED  / 0 failures / 0 errors / 0 skipped
```

- 测试命令：`./gradlew :app:testDebugUnitTest` BUILD SUCCESSFUL in ~13s（Robolectric 已缓存）
- `LibraryViewModelTest` 用 `StandardTestDispatcher` + `advanceTimeBy(5_001)` 锁住 UNDO 5s timer 与 Snackbar dismiss 双轨删除
- ui ViewModel 层（粗估）状态机主路径 + import/delete 失败 one-shot 已覆盖；**未**测 Activity/SAF（留给 Day 4 前手动 smoke + 未来 Espresso）

## 8. Git 数据

### Day 1 commit 列表（5 业务 + 1 docs = 6）

- `e48a807` chore(deps): add room 2.6.1 with ksp compiler and schema export
- `6d0eedb` feat(library): domain models, repository interface and use cases
- `e2f05ee` feat(library): room database with document entity, dao and mappers
- `8382b49` feat(library): document repository impl and internal file storage (import deferred)
- `d18b68f` test(library): unit tests for document use cases and mappers
- `4c00655` docs(week1): start week 1 dev log with day 1 entries

### Day 2 commit 列表（5 业务 + 1 docs = 6，与 Day 1 同节奏）

- `6ac6471` chore(deps): add pdfbox-android 2.0.27.0 and robolectric 4.13
- `fec12f6` feat(pdf): pdfbox text extractor with per-page strip
- `f501990` feat(library): internal file storage saf stream copy with rollback
- `0b6b75e` feat(library): document repository import implementation with three-step rollback
- `de144fd` test(library): rewrite import sentinel to happy-path and add robolectric pdf extractor tests
- `<docs commit hash · 见 git log，本日志无法自指自身 hash>` docs(week1,contributing): day 2 dev log and decision-rule guardrail

### Day 3 commit 列表（6 业务 + 1 docs = 7）

- `b8fe907` chore(deps): bump recyclerview to 1.3.2 explicit dependency
- `98b6f32` feat(library): add library view model and ui state
- `8b7bebb` feat(library): add library layouts drawables and strings
- `34b0a4b` feat(library): add library activity with saf import and swipe delete
- `9fc6a47` chore(app): make library activity the launcher entry
- `a1d09ef` test(library): add library view model unit tests
- `<docs commit hash · 见 git log，本日志无法自指自身 hash>` docs(week1): day 3 dev log and library empty screenshot

### 分支策略未变

- 全部挂在 `feat/library-document-import` 分支
- **Day 3 末仍不合 `dev`**——W1 验收线还差阅读器（Day 4–5 AndroidPdfViewer）；导入 + 列表 UI 已闭环，等阅读器接上后整分支 review 合 `dev`
- `origin/feat/library-document-import` Day 3 末 push 携带 Library UI + 30 业务测

## 9. 合 feat 分支前自查 3 问（CONTRIBUTING §7）

### Day 1 自查

### Q1. `rg "^import android" app/src/main/java/com/asuka/pocketpdf/domain/` 必须 0 命中

- **结果**：0 命中 ✅
- **意义**：domain 层 7 文件（model × 2 / repository × 1 / usecase × 4）全部纯 Kotlin。`Document.uri` 用 `String` 装 URI 而不引 `android.net.Uri`、Repository 用 `kotlinx.coroutines.flow.Flow` 而不引 `androidx.lifecycle.LiveData`——依赖方向干净。

### Q2. `./gradlew :app:testDebugUnitTest` PASSED 数 ≥ 6

- **结果**：12 PASSED ✅（业务部分，不含模板带的 `ExampleUnitTest`）
- **意义**：4 个 use case × 平均 2 个 case + Mappers 5 个 case = 12，覆盖正向 / 反向 / 边界（空列表）/ 错误传播 / 沉降标记 / 全枚举值 / 非法值。

### Q3. 能讲清楚 importDocument 故意留 NotImplementedError 的两个理由吗？

**理由 1 · 接口先稳**：DocumentRepository 接口契约今天就锁死，Day 2 之后 ui 层 LibraryViewModel 可以直接依赖它写状态机、写 RecyclerView Adapter 的"空状态 / 列表态 / 错误态"切换，**不必等 Day 2 真实现**。这是 Clean Architecture "ui → domain ← data" 单向依赖的直接好处：domain 接口稳定后，ui 和 data 两边可以并行推进。

**理由 2 · 沉降提醒（不是日历提醒，是自动失败提醒）**：`ImportDocumentUseCaseTest` 那条 sentinel 测试断言 `error.message?.contains("DocumentRepositoryImpl.importDocument")`——Day 2 真实现这行代码必删，测试自动红。**比"我记得 Day 2 要补单测"靠谱得多**——后者依赖记忆，前者依赖 CI。

> **下次面试演练时**：先讲 Q1（依赖方向是 Clean Architecture 的核心），再讲 Q3 的理由 2（沉降测试是工程化思维的具体例子），每段 60 秒能讲完。

### Day 2 自查

#### Q1. domain 仍然 0 依赖 android.*？

- 命令：`rg "^import android" app/src/main/java/com/asuka/pocketpdf/domain/`
- **结果**：0 命中 ✅
- **意义**：Day 2 加了 PdfBox（这是个 Android 库），但接口都在 `data/pdf/` 而非 `domain/`。`PdfTextExtractor.extractPagesText(file: File)` 用 `java.io.File`（JVM 标准库）而非 `android.net.Uri`——domain 层契约依然纯 Kotlin。

#### Q2. 沉降测试翻红的方式是否如预期？

- **预期**（Day 1 设计）：sentinel 测试在 Day 2 真实现后**自动失败**（要么 Failure 变 Success，要么 message 不匹配）
- **实际**：失败方式比预期更强——`DocumentRepositoryImpl` 构造函数从 3 参变 4 参，sentinel 测试连**编译都过不去**
- **结论**：Day 1 沉降设计的工程价值得到验证。**重写后**当前 4 个 case 锁定 happy path + 3 个失败回滚，下次再改 importDocument 编排时，任何一处遗漏（"忘了 delete 半成品"）都会让 `assertFalse(copied.exists())` 红——同样是沉降思路的延续。

#### Q3. importDocument 三步回滚能不能 1 分钟讲清楚？

**架构**：copy → extract → dao.insert，编排在 Repository，**三步任一失败都磁盘干净**。

**为什么不靠"未来的清理 Worker 兜底"**：
- 删除路径可以"DB 删文件留"（业务上用户主动，孤儿文件不影响重启可见性）
- 导入路径不能"DB 不入文件留"（用户不知道导入失败了，磁盘累积 .pdf 半成品，违反"卸载即清理"语义）
- 不对称语义在测试里锁住——`assertFalse(copied.exists())`，下次重构编排时回滚少做一处就红

**面试讲法**：「导入和删除看着对称，其实回滚策略不对称——因为前者用户不知情，后者用户主动。Day 2 的 ImportDocumentUseCaseTest 4 个 case 把这件事写进了测试。」**60 秒讲完**。

### Day 3 自查

#### Q1. domain 仍然 0 依赖 android.*？

- 命令：`rg "^import android" app/src/main/java/com/asuka/pocketpdf/domain/`
- **结果**：0 命中 ✅
- **意义**：Day 3 新增 UI 全在 `ui/library/`，SAF 的 `Uri` / `OpenableColumns` 只在 Activity；ViewModel 只调 UseCase，不碰 Framework 类型。

#### Q2. ui 层有没有直接 import data？

- 命令：`rg "^import com\.asuka\.pocketpdf\.data" app/src/main/java/com/asuka/pocketpdf/ui/`
- **结果**：0 命中 ✅
- **意义**：列表数据经 `ObserveDocumentsUseCase` → Room Flow；导入/删除经对应 UseCase，依赖方向 `ui → domain ← data` 未破。

#### Q3. 删除 UNDO 双轨（Snackbar + 5s timer）能不能 1 分钟讲清楚？

**动机**：Snackbar 在旋屏/Activity 重建后会消失，若只靠 dismiss callback，可能出现「列表已隐藏、DB 仍在」的 dangling state，破坏 W1「重启仍在」验收。

**机制**：swipe → `pendingDeleteIds` 立刻过滤 UI + 发 `ShowDeleteUndo` + 启动 5s Job；UNDO 取消 Job 并恢复 id；非 ACTION dismiss 或 timer 到点 → `commitDelete`（`id !in pendingDeleteIds` 则幂等 return）。

**测试**：`LibraryViewModelTest` 12 case 锁住 undo / timer / dismiss / 重复 swipe。

## 10. Day 4 计划回收

### 主线任务（已完成）

- [x] 评估阅读器渲染方案：未引 AndroidPdfViewer，改用原生 `PdfRenderer` 先闭环
- [x] `ReaderActivity` + `ReaderViewModel`：从列表点击传入 `documentId`，加载内部存储绝对路径
- [x] 阅读器：翻页、双指缩放；底部页码条显示当前页 / 总页数
- [x] `LibraryActivity` 列表项点击从 Toast 占位改为跳转 `ReaderActivity`
- [ ] GUI smoke：导入 PDF → 列表 → 进入阅读 → 翻页 / 缩放 → 杀进程重启 → 列表与阅读路径仍在

### 子任务

- [x] `GetDocumentUseCase` 在阅读器启动路径上的错误态（文档不存在 / 文件缺失）UI 反馈
- [x] 阅读器相关单测（ReaderViewModel 5 case）
- [ ] **仍不合 `dev`**：阅读器接上后再整分支 review 合 `dev`，打 `v0.1.0-pdf-reader` 前最后一轮 W1 验收

### 给收尾验收的 Asuka 留话

1. **先手验 SAF 导入 → Reader 打开**：自动化已经覆盖 ViewModel 启动路径，但 SAF 选文件和真实手势仍要人工点一次。
2. **PdfBox 与 PdfRenderer 分工保持清楚**：导入时 PdfBox 提文本 / 页数，阅读时 PdfRenderer 渲染页面。
3. **底部页码条已完成「当前页 / 总页数」**，缩略图 / 书签 / 搜索留 W2+，不要在 W1 收尾时扩。

## 11. 时间分配

### Day 1 时间分配

| 类型 | 小时（约） |
|---|---|
| 计划对齐 + Plan 12 条微决策跟用户确认 | 0.3 |
| Phase 0 Room 依赖 + 构建验证 | 0.4 |
| Phase 1 domain 层 7 文件（含 KDoc） | 0.5 |
| Phase 2 Room data/local + DatabaseModule + schema 生成验证 | 0.5 |
| Phase 3 RepoImpl + Storage + RepositoryModule + 编译错误修复 | 0.6 |
| Phase 4 单测 6 文件 12 case + 全跑验证 | 0.7 |
| Phase 5 本日志 + commit 6 + push | 0.5 |
| **Day 1 小计** | **≈ 3.5** |

### Day 2 时间分配

| 类型 | 小时（约） |
|---|---|
| Day 2 计划对齐 + 9 条决策跟用户确认（含被打回的 init Startup → 改回 onCreate） | 0.4 |
| CONTRIBUTING.md 决策原则补丁（不拿面试导向压复杂度） | 0.1 |
| Phase 1 PdfBox 依赖 + onCreate init + 第一次 sync 等待 | 0.5（含等 5m31s sync） |
| Phase 2 PdfTextExtractor 接口 + Impl + Hilt @Binds | 0.4 |
| Phase 3 InternalFileStorage.copyToInternal 真实现 + 失败回滚 | 0.3 |
| Phase 4 DocumentRepositoryImpl.importDocument 三步编排 + 三步回滚 | 0.4 |
| Phase 5 重写 sentinel 4 case + Robolectric PdfBox 测 3 case + 撞墙处理 | 0.7（含 JVM 撞墙 + 跟用户讲清三层测试） |
| Phase 6 模拟器最小 smoke（boot + install + logcat 验证 init） | 0.3 |
| Phase 7 Day 2 日志 + commit 6 + push | 0.5 |
| **Day 2 小计** | **≈ 3.6** |
| **W1 累计（Day 1 + Day 2）** | **≈ 7.1** |

### Day 3 时间分配

| 类型 | 小时（约） |
|---|---|
| Day 3 计划对齐 + 8 条微决策跟用户确认 | 0.3 |
| Phase 0 开工自检（跳过 Robolectric 快验 16） | 0.1 |
| Phase 1 ViewModel + UiState + Event | 0.4 |
| Phase 2 布局 / drawable / string + Adapter | 0.5 |
| Phase 3–4 Activity + SAF + ItemTouchHelper UNDO | 0.5 |
| Phase 5 Manifest 切 LAUNCHER | 0.1 |
| Phase 6 LibraryViewModelTest 12 case | 0.5 |
| Phase 7 模拟器 boot + install + 空状态截图 | 0.3 |
| Phase 8 日志 + commit 7 + push | 0.5 |
| **Day 3 小计** | **≈ 3.2** |
| **W1 累计（Day 1 + Day 2 + Day 3）** | **≈ 10.3** |

## 12. 收尾总结

### Day 1 收尾总结

**今天做对了的**：

1. **先 plan 再 commit**：开工前把 12 条微决策列出来跟用户确认（特别是 #1 main 上小改动怎么带 / #4 ImportDocumentUseCase 参数类型 / #8 FileStorage 接口最小化），避免边写边返工
2. **沉降测试取代日历提醒**：`ImportDocumentUseCaseTest` 一行 `error.message.contains("DocumentRepositoryImpl.importDocument")` 把"Day 2 别忘了写真测"这件事从我的脑子搬到 CI，**这是今天最骄傲的设计**
3. **每个 Phase 跑 `./gradlew :app:assembleDebug` 验证**：Phase 3 末果然撞了 `Result<Boolean>` vs `Result<Unit>` 编译错，1 分钟改完——比"全 phase 写完一起 build 撞 10 个错"舒服得多
4. **commit message 用 HEREDOC 文件而非 `-m` 字符串**：避开 PowerShell 把 `$projectDir` 当变量展开的坑；后续 commit 都走这套
5. **app/schemas/1.json 入库**：未来 schema 变更走 Code Review 时能直观看到 SQL diff（"index_documents_importedAt 新建" / "新列 indexedAt"），不靠脑补

**今天做差了的**：

1. **Hilt 的 deprecation warning 没追源头**：`Hilt_PocketPdfApp.java` 在 javac 阶段提示 deprecated API，W0 也有，今天又出现一次——应该追一下是哪条 API、是不是 Hilt 2.53+ 修了。**TODO(asuka)：W1 末或 W2 初查一下，决定要不要升 Hilt 版本**
2. **`week0.md` / `libs.versions.toml` 那两个工作树脏改动**：本来该 W0 收尾时清理，留到 W1 Day 1 才一起 commit，commit 拆分时多想了一阵——下次工作树要么干净要么明确归属哪个 commit，不留中间态

**给 Day 2 的 Asuka 留话**：

1. **先把沉降测试翻红再写实现**：Day 2 开工第一件事就是 `./gradlew :app:testDebugUnitTest`，确认 `ImportDocumentUseCaseTest` 当前还是绿——这是"我接的代码是 Day 1 留下的那个版本"的最后一道防线
2. **PdfBox-Android 依赖体积大**，第一次 sync 可能慢 1–2 min，**先单独 sync 一次 + 加单个空文件确认 import 路径再写业务**，不要"加依赖 + 写 200 行业务一起跑构建"
3. **SAF 在 Android 14 (API 34) 起对 `persistableUriPermission` 有变化**，但 W1 用 SAF 只读一次（读完立刻复制到内部存储），**不需要 take persistable**——别被旧教程带偏多调用一遍
4. **Day 1 这套 `data.repository` 的命名 / Hilt @Binds 模板直接复制到未来的 `ChunkRepository` / `ChatRepository`**，每加一个域只改 4 个名字

### Day 2 收尾总结

**今天做对了的**：

1. **被打回时立刻修工程原则**：决策 2 我用"项目是面试导向"作为推 androidx.startup 的论据被用户当场打回。**没辩护、没"那其实我有更深的理由"**，直接把"决策原则只用工程论据"写进 CONTRIBUTING.md §3，再回到决策 2 用纯 YAGNI 论据重审 → 选 A。这是 Day 2 最有意义的一次自我纠偏，未来 AI 助手再端出"面试 / 简历"论据用户可以直接引用这条原则打回
2. **沉降测试的失败信号超出预期**：Day 1 设计 sentinel 时预期"runtime message 断言失败"，Day 2 真实现把构造函数从 3 参变 4 参——sentinel **编译都过不去**，这是更强的失败信号。一个意外加固
3. **撞墙时严格守"先停下来跟用户讲"协议**：JVM 单测 PdfBox 撞 PDType1Font 静态初始化时，按决策 6 协议**没擅自切 Robolectric**，先把"为什么撞墙 / A 是什么 / B 是什么 / C 是什么 / 我倾向 A 但需要你拍板"完整讲清，让用户做决策。这次还顺带把"测试金字塔三层（JVM / Robolectric / androidTest）什么时候上"讲了一遍——用户当时问"什么时候上模拟测试"，回答了一个不止解决眼前问题的问题
4. **回滚锁进测试**：`assertFalse(copied.exists())` 写在 2 个失败 case 里——下次重构 importDocument 编排时少做一步回滚就会红。比"在 KDoc 写文字承诺"靠谱 10 倍
5. **PdfBox 第一次 sync 慢的预案命中**：Day 1 计划留话 #2 就预判了 PdfBox 量级，Day 2 单独 sync + 改业务 + 等 build 三件事并行做，5m31s 没浪费

**今天做差了的**：

1. **没第一时间想到决策原则要写进文档**：用户被迫在对话里逐条评估论据合不合理。如果我能在决策对齐 stage 0 就主动**自查论据是否纯工程**，用户不会被迫做这次纠偏。**TODO(asuka)：未来微决策对齐时，每条决策的"理由"栏在写之前先过一遍"这是工程论据还是导向论据"的滤镜**
2. **dependencies 命令在 PowerShell pipe 里卡 17 min 才被中断**：我没设置合理的 timeout 兜底，浪费了用户一次中断成本。**TODO(asuka)：长跑命令默认带 timeout 上限；dependencies / dependencyInsight 这种本来就 verbose 的命令避免 pipe 接 Select-String**
3. **Day 2 commit hash 跟 Day 1 一样要回填一次 docs commit**：写日志时业务 commit 已打完 5 个，docs commit hash 只能在本 commit 后下次 push 前手动回填——这是同样的处理，不算坑只算"两步走"的固定开销

**给 Day 3 的 Asuka 留话**：

1. **Day 3 开工第一件事：跑 `:app:testDebugUnitTest` 确认 19 PASSED**——这是"我接的代码是 Day 2 留下的版本"的最后一道防线；如果撞 Robolectric 启动慢，单独跑非 PdfBoxTextExtractor 的 16 个测试快验证
2. **SAF + ImportDocumentUseCase 接 ViewModel 的代码模板**：见 Day 3 计划 §"主线任务"，关键是用 `OpenableColumns.DISPLAY_NAME` 查 SAF 元数据而不是从 URI 字符串解析
3. **不要 take persistable URI permission**（W1 验收用复制到内部存储绕过 SAF 失效问题，决策 3）
4. **Day 3 的回滚测试要继承 Day 2 套路**：ViewModel 状态机里 import 失败时弹 Snackbar 的逻辑——写测试时 mock UseCase 返回 Result.Failure，断言 uiState 切到 Error 而不是 Loaded

### Day 3 收尾总结

**今天做对了的**：

1. **开工 8 条微决策一次性对齐**：Ping 留代码去 LAUNCHER、4 态 UiState、左滑 UNDO 双轨、RecyclerView 显式 1.3.2 等，避免边写边改
2. **ViewModel 测用 mock Repository + 真 UseCase**：绕开 mockk final，多覆盖一层组合；12 case 锁住 UNDO timer 与 dismiss
3. **SAF 只读一次 + DISPLAY_NAME 查询**：不 take persistable；fallback 用相对时间，不从 URI 末段猜文件名
4. **模拟器最小 smoke + 空状态截图**：装 APK、LibraryActivity 启动、无 FATAL；GUI 级 SAF/导入/重启留给 IDE 手验

**今天做差了的**：

1. **RecyclerView 传递依赖版本未在加 ItemTouchHelper 前查**：撞 `bindingAdapterPosition` 才升 1.3.2——下次接新 AndroidX UI 组件先 `dependencyInsight` 看传递版本
2. **Day 3 计划 §10 仍写「Day 3 末合 dev」**：实际 W1 验收还差阅读器，Day 3 末已改为 Day 4 再接 AndroidPdfViewer 后再合 dev
3. **自动化未覆盖 SAF 选文件**：与 Day 2 一致，手验项要在 Day 4 前自己点一遍 FAB → 导入 → 重启

**给 Day 4 的 Asuka 留话**：

1. **列表点击 Toast 占位 → ReaderActivity**，参数用 `documentId`，内部路径走 `GetDocumentUseCase`
2. **AndroidPdfViewer 与 PdfBox 分工**：渲染用 viewer，文本提取已在 import 链路，阅读器不要重复 PdfBox open
3. **合 dev 仍等阅读器闭环**，不要 Day 4 半途 merge
