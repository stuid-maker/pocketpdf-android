# Purple Crystal Library Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Migrate the library to Compose and add deterministic cached PDF covers.

**Architecture:** `LibraryActivity` remains the launcher and SAF owner but hosts `LibraryRoute`. Cover loading is a UI service that renders page zero off the main thread and falls back to a stable typographic cover.

**Tech Stack:** Activity Compose, LazyColumn, Android `PdfRenderer`, Hilt, coroutines, LruCache.

---

### Task 1: Add Cover Model and Stable Fallback

**Files:**
- Create: `app/src/main/java/com/asuka/pocketpdf/ui/library/DocumentCover.kt`
- Test: `app/src/test/java/com/asuka/pocketpdf/ui/library/DocumentCoverTest.kt`

- [ ] **Step 1: Write deterministic fallback tests**

```kotlin
@Test fun same_document_gets_same_fallback() {
    assertEquals(fallbackCover(42, "Effective Kotlin"), fallbackCover(42, "Effective Kotlin"))
}

@Test fun fallback_uses_first_letter() {
    assertEquals("E", fallbackCover(42, "Effective Kotlin").label)
}
```

- [ ] **Step 2: Verify failure**

```powershell
.\gradlew.bat testDebugUnitTest --tests "*DocumentCoverTest"
```

Expected: FAIL because `fallbackCover` is undefined.

- [ ] **Step 3: Implement the model**

```kotlin
sealed interface DocumentCover {
    data class Thumbnail(val bitmap: ImageBitmap) : DocumentCover
    data class Fallback(val label: String, val paletteIndex: Int) : DocumentCover
}

fun fallbackCover(documentId: Long, title: String): DocumentCover.Fallback =
    DocumentCover.Fallback(
        label = title.trim().firstOrNull()?.uppercase() ?: "P",
        paletteIndex = ((documentId xor title.hashCode().toLong()).absoluteValue % 4).toInt(),
    )
```

- [ ] **Step 4: Run and commit**

```powershell
.\gradlew.bat testDebugUnitTest --tests "*DocumentCoverTest"
git add app/src/main/java/com/asuka/pocketpdf/ui/library/DocumentCover.kt app/src/test/java/com/asuka/pocketpdf/ui/library/DocumentCoverTest.kt
git commit -m "feat(library): add deterministic document covers"
```

### Task 2: Add First-Page Cover Loader

**Files:**
- Create: `app/src/main/java/com/asuka/pocketpdf/ui/library/DocumentCoverLoader.kt`
- Create: `app/src/main/java/com/asuka/pocketpdf/di/UiModule.kt`
- Test: `app/src/test/java/com/asuka/pocketpdf/ui/library/DocumentCoverLoaderTest.kt`

- [ ] **Step 1: Define injectable boundary**

```kotlin
interface DocumentCoverLoader {
    suspend fun load(document: Document, widthPx: Int, heightPx: Int): DocumentCover
}

interface PdfCoverRenderer {
    suspend fun renderFirstPage(uri: String, widthPx: Int, heightPx: Int): Bitmap
}
```

- [ ] **Step 2: Write fallback-on-render-error test**

Construct `PdfDocumentCoverLoader` with:

```kotlin
val renderer = object : PdfCoverRenderer {
    override suspend fun renderFirstPage(
        uri: String,
        widthPx: Int,
        heightPx: Int,
    ): Bitmap = error("broken pdf")
}
```

Assert `load(document, 180, 240)` equals
`fallbackCover(document.id, document.title)`.

- [ ] **Step 3: Implement cached renderer**

Use `LruCache<String, Bitmap>` keyed by
`"${document.id}:${document.uri}:$widthPx:$heightPx"`. Open `ParcelFileDescriptor`
and `PdfRenderer` inside `withContext(dispatchers.io)`, render page zero to a
white bitmap, center-crop to the requested aspect ratio, and close all handles
with `use`.

- [ ] **Step 4: Bind implementation**

```kotlin
@Binds
@Singleton
abstract fun bindDocumentCoverLoader(
    impl: PdfDocumentCoverLoader,
): DocumentCoverLoader
```

- [ ] **Step 5: Verify**

```powershell
.\gradlew.bat testDebugUnitTest --tests "*DocumentCoverLoaderTest"
```

Expected: PASS, including renderer failure fallback.

- [ ] **Step 6: Commit**

```powershell
git add app/src/main/java/com/asuka/pocketpdf/ui/library app/src/main/java/com/asuka/pocketpdf/di/UiModule.kt app/src/test/java/com/asuka/pocketpdf/ui/library
git commit -m "feat(library): render and cache pdf covers"
```

### Task 3: Build Library Compose Screen

**Files:**
- Create: `app/src/main/java/com/asuka/pocketpdf/ui/library/LibraryScreen.kt`
- Create: `app/src/main/java/com/asuka/pocketpdf/ui/library/DocumentCard.kt`
- Create: `app/src/main/java/com/asuka/pocketpdf/ui/library/LibraryPreviews.kt`
- Test: `app/src/androidTest/java/com/asuka/pocketpdf/ui/library/LibraryScreenTest.kt`

- [ ] **Step 1: Write UI tests**

Test:

```kotlin
composeRule.onNodeWithText("PocketPDF").assertExists()
composeRule.onNodeWithText("从一份文档开始").assertExists()
composeRule.onNodeWithText("导入 PDF").assertHasClickAction()
composeRule.onNodeWithText("Effective Kotlin").performClick()
assertEquals(7L, openedId)
```

- [ ] **Step 2: Verify failure**

Run the single instrumentation class; expect missing `LibraryScreen`.

- [ ] **Step 3: Implement screen contract**

```kotlin
@Composable
fun LibraryScreen(
    state: LibraryUiState,
    onImport: () -> Unit,
    onOpenDocument: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    onRetryIndexing: (Long) -> Unit,
    onDeleteDocument: (Document) -> Unit,
)
```

Use `Scaffold(contentWindowInsets = WindowInsets.safeDrawing)`, a `LazyColumn`
with inset-aware `contentPadding`, calm paper cards, a top workspace statement,
and a compact crystal import action. Do not render a search field.

- [ ] **Step 4: Add previews**

Provide empty, loaded, importing, error, dark, and `fontScale = 1.3f` previews.

- [ ] **Step 5: Run UI tests**

```powershell
.\gradlew.bat connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.asuka.pocketpdf.ui.library.LibraryScreenTest
```

Expected: PASS.

- [ ] **Step 6: Commit**

```powershell
git add app/src/main/java/com/asuka/pocketpdf/ui/library app/src/androidTest/java/com/asuka/pocketpdf/ui/library/LibraryScreenTest.kt
git commit -m "feat(library): build purple crystal library screen"
```

### Task 4: Convert LibraryActivity and Remove XML

**Files:**
- Modify: `app/src/main/java/com/asuka/pocketpdf/ui/library/LibraryActivity.kt`
- Modify: `app/src/androidTest/java/com/asuka/pocketpdf/ui/library/LibraryActivityTest.kt`
- Delete: `app/src/main/java/com/asuka/pocketpdf/ui/library/DocumentListAdapter.kt`
- Delete: `app/src/test/java/com/asuka/pocketpdf/ui/library/DocumentListAdapterTest.kt`
- Delete: `app/src/main/res/layout/activity_library.xml`
- Delete: `app/src/main/res/layout/item_document.xml`
- Delete: `app/src/main/res/layout/view_empty_library.xml`
- Delete if unreferenced: `app/src/main/res/drawable/bg_badge_index_status.xml`
- Delete if unreferenced: `app/src/main/res/drawable/ic_library_empty.xml`

- [ ] **Step 1: Update Activity test expectations**

Keep launcher/import/settings/open-reader coverage and target Compose semantics
instead of RecyclerView IDs.

- [ ] **Step 2: Replace ViewBinding host**

Use `ComponentActivity`, keep `openDocumentLauncher` and `resolveDisplayName`,
then:

```kotlin
setContent {
    PocketPDFTheme {
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        LibraryScreen(
            state = state,
            onImport = ::launchSafPicker,
            onOpenDocument = { startActivity(ReaderActivity.newIntent(this, it)) },
            onOpenSettings = { startActivity(Intent(this, SettingsActivity::class.java)) },
            onRetryIndexing = viewModel::onRetryIndexing,
            onDeleteDocument = { viewModel.onSwipeDelete(it.id, it.title) },
        )
    }
}
```

Present one-shot events through a Compose `SnackbarHostState`; preserve undo
callbacks and ViewModel timer behavior.

- [ ] **Step 3: Remove obsolete XML/adapter files**

Use `rg` to confirm no remaining references before deletion.

- [ ] **Step 4: Verify**

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

Expected: BUILD SUCCESSFUL and no generated binding reference to
`ActivityLibraryBinding`.

- [ ] **Step 5: Commit**

```powershell
git add -A app/src/main
git add app/src/androidTest app/src/test
git commit -m "refactor(library): migrate library to compose"
```
