# Phase B: P0 核心功能补齐 — 文本搜索 · 手势翻页 · 高亮标注

> **For Hermes:** Use delegate_task with subagent-driven-development skill. Each feature is a self-contained sub-plan. Execute features sequentially (F1 → F2 → F3).

**Goal:** 补齐 PDF 阅读器的 3 个 P0 基础能力：滑动手势翻页、全文文本搜索（高亮匹配+跳转）、长按选中文本高亮/下划线标注。

**Architecture:** 所有 3 个功能共用 PdfPageView 扩展。搜索和高亮需要新增 PdfBox TextPosition API 支持（当前只提取文本，未提取坐标）。标注数据需 Room 持久化。

**Tech Stack:** Kotlin 2.0, Compose, Android PdfRenderer, PdfBox-Android (TextPosition API), Room, Canvas, Matrix

**当前基础：**
- `PdfPageView`: Canvas + Matrix 缩放/平移，支持 onTouchEvent（单指拖拽、双指缩放、双击）
- `PdfBoxTextExtractor`: 按页提取纯文本 `List<String>`，不保留坐标
- `ReaderScreen`: Compose + AndroidView 包裹 PdfPageView
- `ReaderController`: 管理 PdfRenderer 渲染和页面导航

---

## Feature 1: 滑动手势翻页 (1天)

**文件变化：** 2 个文件修改，~80 行新增

### Task 1.1: PdfPageView 添加 Fling 检测

**Goal:** 在 PdfPageView 中检测水平快速滑动，当缩放为 1x 时触发翻页回调。

**Files:**
- Modify: `app/src/main/java/com/asuka/pocketpdf/ui/reader/PdfPageView.kt`

**实现:**
```kotlin
// 新增字段
private var flingVelocityX = 0f
private val velocityTracker = VelocityTracker.obtain()
private val flingThreshold = 300f  // px/s 阈值
private val flingDistanceThreshold = 60f  // px 最小滑动距离

// 新增回调
var onPageFling: ((direction: Int) -> Unit)? = null  // -1=左翻, 1=右翻

// 在 onTouchEvent 中:
// ACTION_DOWN: velocityTracker.clear(); velocityTracker.addMovement(event)
// ACTION_MOVE: velocityTracker.addMovement(event)
// ACTION_UP: 
//   velocityTracker.computeCurrentVelocity(1000)
//   val vx = abs(velocityTracker.xVelocity)
//   val vy = abs(velocityTracker.yVelocity)
//   if (currentScale <= 1.05f && vx > flingThreshold && vx > vy * 1.5f && abs(totalDx) > flingDistanceThreshold) {
//       onPageFling?.invoke(if (velocityTracker.xVelocity > 0) -1 else 1)
//   }
//   velocityTracker.clear()
```

**Step 2: 验证编译**
```bash
./gradlew.bat :app:compileDebugKotlin
```

### Task 1.2: ReaderScreen 接入翻页回调

**Goal:** 在 PdfPageHost 中连接 onPageFling 到 ReaderScreen 的 onPageRequested。

**Files:**
- Modify: `app/src/main/java/com/asuka/pocketpdf/ui/reader/ReaderScreen.kt`

**修改 PdfPageHost:**
```kotlin
@Composable
private fun PdfPageHost(
    bitmap: Bitmap?,
    onTap: () -> Unit,
    onPageFling: (Int) -> Unit,  // 新增
    modifier: Modifier,
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            PdfPageView(context).apply {
                setOnTouchListener { _, event ->
                    if (event.action == MotionEvent.ACTION_UP) onTap()
                    false
                }
                onPageFling = { direction -> onPageFling(direction) }  // 新增
            }
        },
        update = { view -> view.setBitmap(bitmap) },
    )
}
```

**修改 ReaderScreen 调用处:**
```kotlin
PdfPageHost(
    bitmap = pageState.bitmap,
    onTap = { chromeVisible = !chromeVisible },
    onPageFling = { direction ->
        val newPage = pageState.pageIndex + direction
        if (newPage in 0 until pageState.pageCount) {
            onPageRequested(newPage)
        }
    },
    modifier = Modifier.fillMaxSize(),
)
```

### Task 1.3: 手势翻页测试

**Files:**
- Create: `app/src/test/java/com/asuka/pocketpdf/ui/reader/PdfPageViewFlingTest.kt`

**测试 case:**
1. 1x 缩放时左滑 → 回调 direction=1（下一页）
2. 1x 缩放时右滑 → 回调 direction=-1（上一页）
3. 放大(2x)时滑动 → 不触发翻页（仅平移）
4. 慢速滑动(<threshold) → 不触发翻页

**验证:**
```bash
./gradlew.bat testDebugUnitTest
```

---

## Feature 2: 全文文本搜索 (2-3天)

**文件变化：** 4 个文件新建，3 个文件修改，~400 行新增

### Task 2.1: PdfBox 文本位置提取

**Goal:** 扩展 PdfBoxTextExtractor，新增按页提取文本+坐标的方法。PdfBox 的 `PDFTextStripper` 支持 `getCharactersByArticle()` 或自定义 `writeString()` 获取 `TextPosition` 列表，包含每段文本的 x/y/width/height。

**Files:**
- Modify: `app/src/main/java/com/asuka/pocketpdf/data/pdf/PdfBoxTextExtractor.kt`
- Create: `app/src/main/java/com/asuka/pocketpdf/data/pdf/PdfTextPosition.kt`

**PdfTextPosition 数据类:**
```kotlin
data class PdfTextPosition(
    val text: String,
    val pageIndex: Int,       // 0-based
    val x: Float,             // PDF 坐标
    val y: Float,
    val width: Float,
    val height: Float,
)

data class PageTextWithPositions(
    val pageIndex: Int,
    val fullText: String,
    val positions: List<PdfTextPosition>,
)
```

**PdfBoxTextExtractor 新增方法:**
```kotlin
suspend fun extractPagesTextWithPositions(file: File): List<PageTextWithPositions>
```

使用 PdfBox 的 `PDFTextStripper` 子类覆写 `writeString(String, List<TextPosition>)` 来捕获每个字符的坐标。

### Task 2.2: 搜索算法 + UseCase

**Goal:** 实现大小写不敏感、中文友好的全文搜索，返回所有匹配项的位置信息。

**Files:**
- Create: `app/src/main/java/com/asuka/pocketpdf/domain/usecase/SearchDocumentUseCase.kt`
- Create: `app/src/main/java/com/asuka/pocketpdf/domain/model/SearchResult.kt`

**SearchResult 数据类:**
```kotlin
data class SearchResult(
    val pageIndex: Int,
    val matchText: String,         // 匹配的文本片段
    val matchIndex: Int,           // 在页面文本中的字符偏移
    val positions: List<PdfTextPosition>,  // 匹配区域的坐标
)
```

**SearchDocumentUseCase:**
```kotlin
class SearchDocumentUseCase @Inject constructor(
    private val textExtractor: PdfBoxTextExtractor,
    private val documentRepository: DocumentRepository,
    private val dispatchers: DispatcherProvider,
) {
    suspend operator fun invoke(documentId: Long, query: String): Result<List<SearchResult>>
}
```

- 从 Room 获取文档 URI
- 调用 `extractPagesTextWithPositions`
- 对每页文本做 `indexOf(query, startIndex, ignoreCase=true)` 循环查找
- 聚合匹配位置的 `PdfTextPosition`

### Task 2.3: SearchViewModel + UiState

**Files:**
- Create: `app/src/main/java/com/asuka/pocketpdf/ui/reader/SearchViewModel.kt`
- Create: `app/src/main/java/com/asuka/pocketpdf/ui/reader/SearchUiState.kt`

**SearchUiState:**
```kotlin
data class SearchUiState(
    val query: String = "",
    val results: List<SearchResult> = emptyList(),
    val currentMatchIndex: Int = 0,   // 当前高亮的匹配项
    val totalMatches: Int = 0,
    val isSearching: Boolean = false,
)
```

**SearchViewModel:**
- `fun search(query: String)` — 触发搜索，更新 results
- `fun nextMatch()` — 移动到下一个匹配项
- `fun previousMatch()` — 移动到上一个匹配项
- `fun clear()` — 清除搜索

### Task 2.4: PdfPageView 搜索高亮叠加

**Goal:** 在 PdfPageView 的 onDraw 中绘制搜索结果高亮矩形（半透明黄色）。

**Files:**
- Modify: `app/src/main/java/com/asuka/pocketpdf/ui/reader/PdfPageView.kt`

**新增字段和方法:**
```kotlin
// 当前页的搜索结果位置（由外部设置）
private var searchHighlights: List<RectF> = emptyList()
private var currentHighlightIndex: Int = -1

fun setSearchHighlights(highlights: List<RectF>, currentIndex: Int) {
    this.searchHighlights = highlights
    this.currentHighlightIndex = currentIndex
    invalidate()
}
```

**在 onDraw 中添加:**
```kotlin
// 在 drawBitmap 之后绘制搜索高亮
for ((i, rect) in searchHighlights.withIndex()) {
    // 将 PDF 坐标转换为 Canvas 坐标（通过 drawMatrix）
    val mappedRect = RectF(rect)
    drawMatrix.mapRect(mappedRect)
    
    if (i == currentHighlightIndex) {
        // 当前匹配项：橙色高亮
        canvas.drawRect(mappedRect, highlightPaint)
    } else {
        // 其他匹配项：黄色高亮
        canvas.drawRect(mappedRect, normalHighlightPaint)
    }
}
```

### Task 2.5: 搜索 UI (Compose BottomSheet)

**Goal:** 在 ReaderScreen 中添加搜索栏 UI。

**Files:**
- Modify: `app/src/main/java/com/asuka/pocketpdf/ui/reader/ReaderScreen.kt`
- Create: `app/src/main/java/com/asuka/pocketpdf/ui/reader/SearchBar.kt`

**SearchBar Composable:**
```kotlin
@Composable
fun SearchBar(
    query: String,
    matchIndex: Int,
    totalMatches: Int,
    onQueryChanged: (String) -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onClose: () -> Unit,
)
```

包含：搜索输入框 + "N/M" 计数 + 上下箭头按钮 + 关闭按钮。

**ReaderScreen 改动:**
- 在顶部栏添加搜索图标按钮
- 点击后显示 SearchBar（AnimatedVisibility 滑入）
- SearchBar 固定在顶部栏下方

### Task 2.6: 搜索测试

**Files:**
- Create: `app/src/test/java/com/asuka/pocketpdf/domain/usecase/SearchDocumentUseCaseTest.kt`

**测试 case:**
1. 搜索"Android" → 返回所有匹配页和位置
2. 搜索不存在词 → 返回空列表
3. 大小写不敏感搜索
4. 中文搜索"架构"
5. 空查询 → 空结果

---

## Feature 3: 高亮/下划线标注 (3-5天)

**文件变化：** 5 个文件新建，4 个文件修改，~500 行新增

### Task 3.1: Annotation 数据模型 + Room

**Goal:** 定义标注数据模型并在 Room 中持久化。

**Files:**
- Create: `app/src/main/java/com/asuka/pocketpdf/data/local/entity/AnnotationEntity.kt`
- Create: `app/src/main/java/com/asuka/pocketpdf/data/local/dao/AnnotationDao.kt`
- Create: `app/src/main/java/com/asuka/pocketpdf/domain/model/Annotation.kt`

**AnnotationEntity:**
```kotlin
@Entity(
    tableName = "annotations",
    foreignKeys = [ForeignKey(
        entity = DocumentEntity::class,
        parentColumns = ["id"],
        childColumns = ["documentId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("documentId"), Index("pageIndex")]
)
data class AnnotationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val documentId: Long,
    val pageIndex: Int,
    val annotationType: String,     // "highlight" | "underline"
    val color: Int,                 // ARGB color int
    val text: String,               // 被标注的文本内容
    val rectLeft: Float,            // PDF 坐标
    val rectTop: Float,
    val rectRight: Float,
    val rectBottom: Float,
    val createdAt: Long = System.currentTimeMillis(),
)
```

**Domain Annotation:**
```kotlin
enum class AnnotationType { HIGHLIGHT, UNDERLINE }

data class Annotation(
    val id: Long,
    val documentId: Long,
    val pageIndex: Int,
    val type: AnnotationType,
    val color: Int,
    val text: String,
    val rect: RectF,
    val createdAt: Long,
)
```

### Task 3.2: 长按文本选择 + 标注工具栏

**Goal:** 在 PdfPageView 中检测长按，弹出标注选项（高亮/下划线/取消）。

**Files:**
- Modify: `app/src/main/java/com/asuka/pocketpdf/ui/reader/PdfPageView.kt`
- Create: `app/src/main/java/com/asuka/pocketpdf/ui/reader/AnnotationToolbar.kt`

**PdfPageView 改动:**
- 添加长按检测（`ACTION_DOWN` 记录时间，若 `eventTime - downTime > 500ms` 且无移动，则触发长按）
- 新增回调 `var onLongPress: ((x: Float, y: Float) -> Unit)? = null`
- 长按时查找该位置最近的 `PdfTextPosition`，选中该文本片段
- 选中文本的高亮绘制（蓝色半透明矩形覆盖选中文本的 PdfTextPosition 范围）

**AnnotationToolbar Composable:**
```kotlin
@Composable
fun AnnotationToolbar(
    selectedText: String,
    onHighlight: (color: Int) -> Unit,
    onUnderline: (color: Int) -> Unit,
    onDismiss: () -> Unit,
)
```

弹出式工具栏，显示选中文本摘要 + 颜色选择（黄/绿/蓝/红）+ 高亮按钮 + 下划线按钮。

### Task 3.3: 标注渲染 (Canvas 叠加)

**Goal:** 在 PdfPageView 的 onDraw 中渲染已有的标注。

**Files:**
- Modify: `app/src/main/java/com/asuka/pocketpdf/ui/reader/PdfPageView.kt`

**新增方法:**
```kotlin
fun setAnnotations(annotations: List<Annotation>) {
    this.annotations = annotations
    invalidate()
}
```

**在 onDraw 中:**
```kotlin
// 在 drawBitmap 之后绘制标注
for (annotation in annotations) {
    val rect = RectF(annotation.rect)
    drawMatrix.mapRect(rect)
    when (annotation.type) {
        AnnotationType.HIGHLIGHT -> {
            canvas.drawRect(rect, highlightPaint)
        }
        AnnotationType.UNDERLINE -> {
            canvas.drawLine(rect.left, rect.bottom, rect.right, rect.bottom, underlinePaint)
        }
    }
}
```

### Task 3.4: AnnotationViewModel + 标注管理

**Files:**
- Create: `app/src/main/java/com/asuka/pocketpdf/ui/reader/AnnotationViewModel.kt`

**功能:**
- `fun loadAnnotations(documentId, pageIndex)` — 从 Room 加载当前页标注
- `fun addAnnotation(annotation)` — 保存新标注
- `fun deleteAnnotation(id)` — 删除标注
- `fun getAnnotationsForPage(pageIndex): Flow<List<Annotation>>` — 观察当前页标注

### Task 3.5: ReaderScreen 集成

**Files:**
- Modify: `app/src/main/java/com/asuka/pocketpdf/ui/reader/ReaderScreen.kt`

**新增参数:**
```kotlin
annotations: List<Annotation>,
onLongPress: (Float, Float) -> Unit,
onAddAnnotation: (Annotation) -> Unit,
```

**PdfPageHost 更新:**
```kotlin
// update 中添加:
view.setAnnotations(annotations)
```

**新增标注工具栏显示逻辑:**
- 长按时显示 AnnotationToolbar
- 选择标注类型/颜色后调用 onAddAnnotation
- 切换页面时重新加载该页标注

### Task 3.6: 数据库迁移

**Files:**
- Modify: `app/src/main/java/com/asuka/pocketpdf/data/local/AppDatabase.kt`

Room AutoMigration 从 v3 → v4，新增 `annotations` 表。

### Task 3.7: 标注测试

**Files:**
- Create: `app/src/test/java/com/asuka/pocketpdf/data/local/dao/AnnotationDaoTest.kt`
- Create: `app/src/test/java/com/asuka/pocketpdf/data/pdf/PdfBoxTextPositionTest.kt`

---

## 总文件清单

| 文件 | F1 | F2 | F3 | 操作 |
|------|:--:|:--:|:--:|------|
| `ui/reader/PdfPageView.kt` | ● | ● | ● | 修改 |
| `ui/reader/ReaderScreen.kt` | ● | ● | ● | 修改 |
| `ui/reader/ReaderController.kt` | | ● | | 修改 |
| `data/pdf/PdfBoxTextExtractor.kt` | | ● | | 修改 |
| `data/pdf/PdfTextPosition.kt` | | ● | | 新建 |
| `domain/usecase/SearchDocumentUseCase.kt` | | ● | | 新建 |
| `domain/model/SearchResult.kt` | | ● | | 新建 |
| `ui/reader/SearchViewModel.kt` | | ● | | 新建 |
| `ui/reader/SearchUiState.kt` | | ● | | 新建 |
| `ui/reader/SearchBar.kt` | | ● | | 新建 |
| `data/local/entity/AnnotationEntity.kt` | | | ● | 新建 |
| `data/local/dao/AnnotationDao.kt` | | | ● | 新建 |
| `domain/model/Annotation.kt` | | | ● | 新建 |
| `ui/reader/AnnotationToolbar.kt` | | | ● | 新建 |
| `ui/reader/AnnotationViewModel.kt` | | | ● | 新建 |
| `data/local/AppDatabase.kt` | | | ● | 修改 |

**总计：** 16 文件（10 新建 + 6 修改），~1000 行新增

---

## 执行顺序与依赖

```
F1 手势翻页 (独立，无依赖)
  └─ F2 文本搜索 (依赖 F1 的 PdfPageView 改动)
       └─ F3 高亮标注 (依赖 F2 的 TextPosition + F1 的长按检测)
```

**推荐执行：F1 → F2 → F3 顺序执行，每个 Feature 完成后 commit + 测试后再开始下一个。**

---

## 验证

- [ ] `./gradlew.bat testDebugUnitTest` — 所有测试通过
- [ ] 手势翻页：1x 缩放时左右滑动翻页，放大时不触发
- [ ] 文本搜索：搜索"Android"，高亮所有匹配，上下导航跳页
- [ ] 高亮标注：长按选中→选颜色→标注保存→重启后仍在
- [ ] 无回归：263 个已有测试全部通过
