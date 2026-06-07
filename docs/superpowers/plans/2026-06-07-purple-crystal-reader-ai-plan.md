# Purple Crystal Reader and AI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build an immersive Compose reader shell and integrate summary/chat as a contextual AI sheet without rewriting `PdfPageView`.

**Architecture:** Move renderer ownership from Activity view bindings into a lifecycle-aware `ReaderController`. The Compose screen hosts `PdfPageView` with `AndroidView`, renders chrome from immutable page state, and presents the existing summary/chat flows in one modal sheet.

**Tech Stack:** Compose, AndroidView, PdfRenderer, StateFlow, Material 3 bottom sheet.

---

### Task 1: Extract PDF Rendering Controller

**Files:**
- Create: `app/src/main/java/com/asuka/pocketpdf/ui/reader/ReaderController.kt`
- Create: `app/src/main/java/com/asuka/pocketpdf/ui/reader/ReaderControllerFactory.kt`
- Test: `app/src/test/java/com/asuka/pocketpdf/ui/reader/ReaderControllerTest.kt`

- [ ] **Step 1: Define state and interface**

```kotlin
data class ReaderPageState(
    val pageIndex: Int = 0,
    val pageCount: Int = 0,
    val bitmap: Bitmap? = null,
    val isRendering: Boolean = false,
    val error: String? = null,
)

interface ReaderController : Closeable {
    val state: StateFlow<ReaderPageState>
    suspend fun open(document: Document, initialPage: Int)
    fun render(pageIndex: Int)
}

interface ReaderControllerFactory {
    fun create(scope: CoroutineScope): ReaderController
}
```

- [ ] **Step 2: Write clamping and stale-render tests**

Use a fake renderer with three pages. Assert `render(-1)` requests page `0`,
`render(99)` requests page `2`, and completion from an older canceled request
does not replace the latest bitmap.

- [ ] **Step 3: Verify failure**

```powershell
.\gradlew.bat testDebugUnitTest --tests "*ReaderControllerTest"
```

Expected: FAIL because the controller is not implemented.

- [ ] **Step 4: Move renderer logic**

Move the lock, `ParcelFileDescriptor`, `PdfRenderer`, render job, render width
calculation, and close behavior from `ReaderActivity` into
`PdfReaderController`. Preserve white bitmap initialization and IO dispatch.
Inject `ReaderControllerFactory` into `ReaderActivity`; the factory receives
`DispatcherProvider` and creates one controller for the Activity lifecycle.

- [ ] **Step 5: Verify and commit**

```powershell
.\gradlew.bat testDebugUnitTest --tests "*ReaderControllerTest"
git add app/src/main/java/com/asuka/pocketpdf/ui/reader/ReaderController.kt app/src/test/java/com/asuka/pocketpdf/ui/reader/ReaderControllerTest.kt
git commit -m "refactor(reader): extract pdf rendering controller"
```

### Task 2: Build Reader Compose Shell

**Files:**
- Create: `app/src/main/java/com/asuka/pocketpdf/ui/reader/ReaderScreen.kt`
- Create: `app/src/main/java/com/asuka/pocketpdf/ui/reader/ReaderChrome.kt`
- Create: `app/src/main/java/com/asuka/pocketpdf/ui/reader/PdfPageHost.kt`
- Test: `app/src/androidTest/java/com/asuka/pocketpdf/ui/reader/ReaderScreenTest.kt`

- [ ] **Step 1: Write interaction tests**

Cover:

```kotlin
composeRule.onNodeWithContentDescription("显示阅读控件").performClick()
composeRule.onNodeWithText("20 / 29").assertExists()
composeRule.onNodeWithContentDescription("上一页").performClick()
assertEquals(18, requestedPage)
composeRule.onNodeWithContentDescription("文档 AI").performClick()
assertTrue(aiOpened)
```

- [ ] **Step 2: Implement the host**

```kotlin
@Composable
fun PdfPageHost(
    bitmap: Bitmap?,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        modifier = modifier,
        factory = { context -> PdfPageView(context) },
        update = { view -> view.setBitmap(bitmap) },
    )
}
```

Add a tap callback to `PdfPageView` that fires only for an unconsumed single
tap, so zoom and pan remain unaffected.

- [ ] **Step 3: Implement reader chrome**

Use edge-to-edge content, a 2dp reading progress line, animated top chrome, and
one compact `PocketCrystalBar`. The AI action is the only pale-amethyst filled
control. Chrome visibility uses local saveable state and `PocketMotion.Content`.

- [ ] **Step 4: Add previews and tests**

Provide loaded controls-visible, controls-hidden, loading, error, dark, and
large-font previews. Run the single instrumentation class and expect PASS.

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/java/com/asuka/pocketpdf/ui/reader app/src/androidTest/java/com/asuka/pocketpdf/ui/reader/ReaderScreenTest.kt
git commit -m "feat(reader): add immersive compose reader"
```

### Task 3: Build Contextual AI Sheet

**Files:**
- Create: `app/src/main/java/com/asuka/pocketpdf/ui/assistant/PocketAiSheet.kt`
- Create: `app/src/main/java/com/asuka/pocketpdf/ui/assistant/AiConversation.kt`
- Modify: `app/src/main/java/com/asuka/pocketpdf/ui/chat/ChatActivity.kt`
- Test: `app/src/androidTest/java/com/asuka/pocketpdf/ui/assistant/PocketAiSheetTest.kt`

- [ ] **Step 1: Write scope and citation tests**

Assert the sheet displays `正在基于第 20 页与文档索引回答`, exposes page/full
summary actions, accepts a question, and invokes `onCitationClick(pageIndex)`.

- [ ] **Step 2: Implement sheet contract**

```kotlin
@Composable
fun PocketAiSheet(
    documentId: Long,
    pageIndex: Int,
    summaryState: SummaryState,
    chatState: ChatUiState,
    onSummarizePage: () -> Unit,
    onSummarizeDocument: () -> Unit,
    onQuestionChanged: (String) -> Unit,
    onSendQuestion: () -> Unit,
    onStop: () -> Unit,
    onCitationClick: (Int) -> Unit,
    onDismiss: () -> Unit,
)
```

Use `ModalBottomSheet` with partial expansion enabled. Render scope, streamed
summary, suggested prompts, messages, citations, error, and input in the same
visual system.

- [ ] **Step 3: Split reusable chat UI**

Move message rendering and input from `ChatActivity.kt` into
`AiConversation.kt`. Remove the dark-mode `Color.Black` override and use theme
semantic colors. Keep `ChatActivity` temporarily as a full-screen host for
back-stack compatibility until Reader integration passes.

- [ ] **Step 4: Verify**

Run `PocketAiSheetTest` and existing `ChatActivityTest`; both must pass.

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/java/com/asuka/pocketpdf/ui/assistant app/src/main/java/com/asuka/pocketpdf/ui/chat app/src/androidTest/java/com/asuka/pocketpdf/ui
git commit -m "feat(ai): add contextual document assistant sheet"
```

### Task 4: Convert ReaderActivity and Remove Reader XML

**Files:**
- Modify: `app/src/main/java/com/asuka/pocketpdf/ui/reader/ReaderActivity.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Delete: `app/src/main/res/layout/activity_reader.xml`
- Delete: `app/src/main/res/layout/dialog_summary.xml`
- Delete if unreferenced: `app/src/main/res/menu/menu_reader.xml`
- Test: `app/src/androidTest/java/com/asuka/pocketpdf/ui/reader/ReaderActivityTest.kt`

- [ ] **Step 1: Add Activity regression coverage**

Test initial page intent, page navigation, AI open/dismiss, citation page
return, and no summary reopening after dismiss.

- [ ] **Step 2: Replace ViewBinding with Compose**

Keep intent extras unchanged. Create and remember the controller, close it with
`DisposableEffect`, collect both ViewModels with lifecycle awareness, and pass
callbacks to `ReaderScreen`.

- [ ] **Step 3: Set keyboard resize**

For reader/chat input Activities:

```xml
android:windowSoftInputMode="adjustResize"
```

Use one IME inset strategy in Compose; do not stack `imePadding` over a Scaffold
already consuming `WindowInsets.safeDrawing`.

- [ ] **Step 4: Remove obsolete XML**

Confirm with:

```powershell
rg "ActivityReaderBinding|dialog_summary|reader_summary_bar" app/src
```

Expected: no matches before deleting generated layout dependencies.

- [ ] **Step 5: Full regression**

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
.\gradlew.bat connectedDebugAndroidTest
```

Expected: PASS; manually verify zoom, pan, page changes, immersive chrome, AI
sheet, streaming, keyboard, and citation return.

- [ ] **Step 6: Commit**

```powershell
git add -A app/src
git commit -m "refactor(reader): migrate reader and ai to compose"
```
