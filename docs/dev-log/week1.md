# Week 1 · 2026-05-12 至 -

> Day 1 收尾时间：2026-05-12 11:??（UTC+8）。
> 第 1 周：PDF 阅读器 Demo。**当前进度：Day 1 完成（domain + Room 骨架 + Repository 半成品 + 12 个单测）**；Day 2 接入 PdfBox 真把 `importDocument` 实现完后，整个 `feat/library-document-import` 分支一次性 review 合 `dev`。

## 1. 本周目标（来自 ROADMAP）

- [ ] 文件选择器（SAF · `ACTION_OPEN_DOCUMENT`）
- [ ] 把选中 PDF 复制到 App 内部存储（`filesDir/documents/`）
- [ ] PdfBox-Android 集成，封装 `PdfTextExtractor`，按页提取文本
- [ ] AndroidPdfViewer 集成，阅读器界面（翻页、双指缩放）
- [x] Room 表：`DocumentEntity` + DAO（**Day 1 完成**；`PageEntity` 推迟到 W1 末或 W2，PDF 文本切块时再加，避免空表）
- [x] 仓库 `DocumentRepository` 接口 + UseCase 四件套（observe/get/import/delete）（**Day 1 完成**；`ImportDocumentUseCase` 当前走 stub 路径，Day 2 真实现）
- [ ] 文档库主页（RecyclerView 列表 + 空状态）
- [ ] 文档卡片：标题、页数、导入时间、索引状态徽章
- [ ] 阅读器底部页码条
- [ ] 单元测试：`PdfTextExtractor`（用 assets 里小 PDF）→ Day 2 起补

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

### 决策 5：Mapper 用顶层 `internal` extension function 而非 `object Mapper` / `class DocumentMapper`

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

## 4. 踩坑记录

| 问题 | 原因 | 解决 | 用时 |
|---|---|---|---|
| `git commit -m "...$projectDir..."` 报 `'\/schemas' is outside repository` | PowerShell 把 `$projectDir` 当 shell 变量展开成空字符串 | 改用 `git commit -F <file>` HEREDOC 写法（用 `.git/COMMIT_EDITMSG_TMP` 临时文件）；其它带 `$` 的字符串同理 | 2 min |
| `resultOf { ... fileStorage.delete(uri) }` 编译报 `Result<Boolean>` 不能赋给 `Result<Unit>` | `resultOf` 推断的 T = 最后表达式类型 = `Boolean` | 在 block 末尾显式追加 `Unit`；顺便在注释里写清楚"文件删除失败不视为流程失败：DB 行已删，孤儿文件可由清理 Worker 兜底" | 1 min |
| Hilt aggregate deps 在 `compileDebugJavaWithJavac` 阶段提示 `uses or overrides a deprecated API` for `Hilt_PocketPdfApp.java` | Hilt 生成代码里有 deprecated API；W0 已有，无业务影响 | 不修，加 `-Xlint:deprecation` 后续看是否升 Hilt 2.53+ | 0 min |
| Moshi Kapt 警告 | W0 已确认是 Moshi codegen 的误报（已走 KSP），见 `week0.md` §4 | 不修 | 0 min |

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

## 6. 性能数据

| 指标 | 值 | 备注 |
|---|---|---|
| 冷启动 `:app:assembleDebug` | ~75 s | 加 Room 后比 W0 末（~3.5 min）反而快——KSP 已稳定，无 plugin 解析 |
| 单测全跑 `:app:testDebugUnitTest` | ~90 s（含冷启 KSP testDebugUnitTest） | 单独 testDebugUnitTest 任务本身只需几秒 |
| Day 1 新增代码行 | +899 行（25 个文件） | 含 `1.json` schema 74 行 + 注释占比 ~30% |

## 7. 测试

| 测试类 | tests | failures | errors | skipped |
|---|---|---|---|---|
| `ObserveDocumentsUseCaseTest` | 2 | 0 | 0 | 0 |
| `GetDocumentUseCaseTest` | 2 | 0 | 0 | 0 |
| `DeleteDocumentUseCaseTest` | 2 | 0 | 0 | 0 |
| `ImportDocumentUseCaseTest` | 1 (sentinel) | 0 | 0 | 0 |
| `DocumentMappersTest` | 5 | 0 | 0 | 0 |
| **合计（W1 新增）** | **12** | **0** | **0** | **0** |

- 测试命令：`./gradlew :app:testDebugUnitTest` BUILD SUCCESSFUL
- domain 层覆盖率（粗估，4 个 use case + 2 个 model）≈ 90%；data mapper ≈ 100%（5 case 覆盖了正向 / 反向 / 全枚举 / 非法值）
- 目标 ≥ 6（4 use case 测 + Mappers ≥ 2 case），**实际 12，翻倍达成**

## 8. Day 1 Git 数据

- Day 1 commit 数：5 业务 commit（Phase 0–4）+ 1 docs commit（本日志）= **6**
- 全部挂在 `feat/library-document-import` 分支
- **不 push 到 dev**（约束：Day 2 真实现 importDocument 后整组 review 合 dev）
- Day 1 commit 列表：
  - `e48a807` chore(deps): add room 2.6.1 with ksp compiler and schema export
  - `6d0eedb` feat(library): domain models, repository interface and use cases
  - `e2f05ee` feat(library): room database with document entity, dao and mappers
  - `8382b49` feat(library): document repository impl and internal file storage (import deferred)
  - `d18b68f` test(library): unit tests for document use cases and mappers
  - `<docs commit hash 在本 commit 后回填>` docs(week1): start week 1 dev log with day 1 entries
- 分支远端：`origin/feat/library-document-import` Day 1 末 push（不合 dev）

## 9. Day 1 合 feat 分支前自查 3 问（CONTRIBUTING §7）

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

## 10. Day 2 计划

- [ ] 接 PdfBox-Android 依赖（`com.tom-roush:pdfbox-android:2.0.27.0` 量级）
- [ ] 封装 `PdfTextExtractor`（按页提取 + 异常路径：扫描件 / 损坏 PDF）
- [ ] `InternalFileStorage.copyToInternal` 真实现（`ContentResolver.openInputStream(Uri.parse(sourceUri))` → `File(documentsDir, "${UUID.randomUUID()}.pdf").outputStream().use { ... }`）
- [ ] `DocumentRepositoryImpl.importDocument` 真实现（调 FileStorage + 调 PdfBox 拿 pageCount + 调 DAO insert + 返回 Document with id）
- [ ] **沉降测试翻红**后重写为 happy-path：mock `FileStorage` + `DocumentDao` 验证 import 成功路径
- [ ] 加 `assets/sample.pdf`（一小份 ≤ 5 页文本 PDF），写 `PdfTextExtractorTest`（用 Robolectric 或 instrumentation）
- [ ] 第一次 W1 演示自检：模拟器跑 → SAF 选 PDF → 看 logcat 看到 import 成功日志（UI 仍未做，下一步 Day 3）

## 11. Day 1 时间分配

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

## 12. Day 1 收尾总结

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
