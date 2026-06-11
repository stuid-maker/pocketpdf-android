# Unified PDFium Engine Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver WPS-like PDF keyword search by using one PDFium session for rendering, text, native search rectangles, and AI indexing.

**Architecture:** Add an app-owned `PdfDocumentEngine` boundary, implement it with `pdfiumandroid`, migrate rendering first, then search and indexing. Native resources stay inside serialized, closeable sessions; UI and domain code receive app models only.

**Tech Stack:** Kotlin, Coroutines, Android Bitmap, PdfiumAndroidKt `1.0.30`, Hilt, JUnit, Android instrumentation tests.

---

### Task 1: Add PDFium And Prove Build Compatibility

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`
- Create: `app/src/androidTest/java/com/asuka/pocketpdf/data/pdf/PdfiumSmokeTest.kt`
- Create: `app/src/main/res/raw/pdfium_license.txt`

- [x] **Step 1: Add a failing smoke test**

Create a test that opens `docs/screenshots/test.pdf` copied into test assets,
asserts a positive page count, opens page zero, extracts text, and closes all
handles.

- [x] **Step 2: Verify the test cannot compile**

```powershell
.\gradlew.bat connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.asuka.pocketpdf.data.pdf.PdfiumSmokeTest
```

Expected: FAIL because PDFium classes and the fixture asset are absent.

- [x] **Step 3: Add the dependency and fixture**

Add `io.legere:pdfiumandroid:1.0.30` to the version catalog and app
dependencies. Copy the small fixture into
`app/src/androidTest/assets/pdfium-smoke.pdf`. Add the Apache/PDFium notice.

- [x] **Step 4: Verify compatibility**

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.asuka.pocketpdf.data.pdf.PdfiumSmokeTest
```

- [x] **Step 5: Commit**

```powershell
git add gradle/libs.versions.toml app/build.gradle.kts app/src/androidTest app/src/main/res/raw/pdfium_license.txt
git commit -m "build(pdf): add compatible pdfium runtime"
```

### Task 2: Define The Engine Contract

**Files:**
- Create: `app/src/main/java/com/asuka/pocketpdf/domain/pdf/PdfDocumentEngine.kt`
- Create: `app/src/main/java/com/asuka/pocketpdf/domain/pdf/PdfDocumentSession.kt`
- Create: `app/src/main/java/com/asuka/pocketpdf/domain/pdf/PdfModels.kt`
- Create: `app/src/test/java/com/asuka/pocketpdf/domain/pdf/PdfModelsTest.kt`

- [x] **Step 1: Write model invariant tests**

Test page-index validation, normalized rectangle ordering, multi-rectangle
matches, and render request dimensions.

- [x] **Step 2: Verify failure**

```powershell
.\gradlew.bat testDebugUnitTest --tests "*PdfModelsTest"
```

Expected: FAIL because the PDF domain package does not exist.

- [x] **Step 3: Implement the minimal contract**

Define `PdfPageInfo`, `PdfPageRect`, `PdfPageText`, `PdfSearchMatch`,
`PdfRenderRequest`, typed open errors, and closeable engine/session interfaces.
Keep Android `Bitmap` at the rendering boundary but expose no PDFium types.

- [x] **Step 4: Verify and commit**

```powershell
.\gradlew.bat testDebugUnitTest --tests "*PdfModelsTest"
git add app/src/main/java/com/asuka/pocketpdf/domain/pdf app/src/test/java/com/asuka/pocketpdf/domain/pdf
git commit -m "feat(pdf): define unified engine contract"
```

### Task 3: Implement PDFium Session Lifecycle

**Files:**
- Create: `app/src/main/java/com/asuka/pocketpdf/data/pdf/PdfiumDocumentEngine.kt`
- Create: `app/src/main/java/com/asuka/pocketpdf/data/pdf/PdfiumDocumentSession.kt`
- Create: `app/src/main/java/com/asuka/pocketpdf/di/PdfModule.kt`
- Create: `app/src/test/java/com/asuka/pocketpdf/data/pdf/PdfPageCacheTest.kt`
- Create: `app/src/androidTest/java/com/asuka/pocketpdf/data/pdf/PdfiumDocumentSessionTest.kt`

- [x] **Step 1: Write failing lifecycle tests**

Cover page cache hit, LRU eviction, text-page-before-page close order, complete
session close, out-of-range pages, cancellation, and repeated close.

- [ ] **Step 2: Verify failure**

```powershell
.\gradlew.bat testDebugUnitTest --tests "*PdfPageCacheTest"
```

- [x] **Step 3: Implement session ownership**

Open the file descriptor and PDFium document once. Serialize native calls with
a `Mutex` on `DispatcherProvider.io`. Cache a small number of page/text-page
pairs and close deterministically. Bind `PdfDocumentEngine` through Hilt.

- [x] **Step 4: Add device integration coverage**

Assert page count, page dimensions, extracted text, and that all operations fail
with a typed closed-session error after close.

- [x] **Step 5: Verify and commit**

```powershell
.\gradlew.bat testDebugUnitTest --tests "*PdfPageCacheTest"
.\gradlew.bat connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.asuka.pocketpdf.data.pdf.PdfiumDocumentSessionTest
git add app/src/main/java/com/asuka/pocketpdf/data/pdf app/src/main/java/com/asuka/pocketpdf/di/PdfModule.kt app/src/test app/src/androidTest
git commit -m "feat(pdf): implement closeable pdfium sessions"
```

### Task 4: Migrate Reader Rendering

**Files:**
- Modify: `app/src/main/java/com/asuka/pocketpdf/ui/reader/ReaderController.kt`
- Modify: `app/src/main/java/com/asuka/pocketpdf/ui/reader/ReaderActivity.kt`
- Modify: `app/src/test/java/com/asuka/pocketpdf/ui/reader/ReaderControllerTest.kt`
- Create: `app/src/androidTest/java/com/asuka/pocketpdf/ui/reader/PdfiumReaderRenderTest.kt`

- [x] **Step 1: Rewrite controller tests against an engine fake**

Preserve clamping, stale-render rejection, white background, current page, and
close behavior. Assert one document session is reused across page renders.

- [x] **Step 2: Verify failure**

```powershell
.\gradlew.bat testDebugUnitTest --tests "*ReaderControllerTest"
```

- [x] **Step 3: Replace `PdfRenderer` ownership**

Inject `PdfDocumentEngine`, open one session per reader, and render through the
session. Remove Android `PdfRenderer` and its separate descriptor lifecycle.

- [x] **Step 4: Verify visual parity**

Run the reader instrumentation test for portrait, landscape, rotated, and
cropped fixtures. Compare dimensions and sampled rendered regions.

- [x] **Step 5: Verify and commit**

```powershell
.\gradlew.bat testDebugUnitTest --tests "*ReaderControllerTest"
.\gradlew.bat connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.asuka.pocketpdf.ui.reader.PdfiumReaderRenderTest
git add app/src/main/java/com/asuka/pocketpdf/ui/reader app/src/test/java/com/asuka/pocketpdf/ui/reader app/src/androidTest/java/com/asuka/pocketpdf/ui/reader
git commit -m "refactor(reader): render pages through pdfium"
```

### Task 5: Implement Native Page Search And Coordinate Mapping

**Files:**
- Modify: `app/src/main/java/com/asuka/pocketpdf/data/pdf/PdfiumDocumentSession.kt`
- Create: `app/src/main/java/com/asuka/pocketpdf/ui/reader/PdfPageTransform.kt`
- Create: `app/src/test/java/com/asuka/pocketpdf/ui/reader/PdfPageTransformTest.kt`
- Create: `app/src/androidTest/java/com/asuka/pocketpdf/data/pdf/PdfiumSearchFixtureTest.kt`
- Create: `app/src/androidTest/assets/pdf-search-*.pdf`

- [x] **Step 1: Write failing transform tests**

Cover scale, centering, pan, zoom, rotation, crop-box normalization, and
multi-line rectangle preservation.

- [x] **Step 2: Write failing fixture expectations**

For English, Chinese, ligature, repeated-term, rotated, crop-box, and multi-line
fixtures, assert exact match count/text and expected rectangle neighborhoods.

- [x] **Step 3: Implement native search**

Use PDFium page search to obtain character ranges and PDFium text-page geometry
to obtain rectangles. Do not search a separately normalized Kotlin string and
do not synthesize boxes from font metrics.

- [x] **Step 4: Implement one page-to-view transform**

Map engine page rectangles to the rendered bitmap and then through the existing
view matrix. Keep active and inactive match styling separate from geometry.

- [x] **Step 5: Verify and commit**

```powershell
.\gradlew.bat testDebugUnitTest --tests "*PdfPageTransformTest"
.\gradlew.bat connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.asuka.pocketpdf.data.pdf.PdfiumSearchFixtureTest
git add app/src/main app/src/test app/src/androidTest
git commit -m "feat(search): return exact pdfium match rectangles"
```

### Task 6: Restore Document Search Navigation

**Files:**
- Modify: `app/src/main/java/com/asuka/pocketpdf/domain/usecase/SearchDocumentUseCase.kt`
- Modify: `app/src/main/java/com/asuka/pocketpdf/ui/reader/SearchViewModel.kt`
- Modify: `app/src/main/java/com/asuka/pocketpdf/ui/reader/SearchUiState.kt`
- Modify: `app/src/main/java/com/asuka/pocketpdf/ui/reader/SearchBar.kt`
- Modify: `app/src/main/java/com/asuka/pocketpdf/ui/reader/ReaderScreen.kt`
- Modify: `app/src/main/java/com/asuka/pocketpdf/ui/reader/PdfPageView.kt`
- Create: `app/src/test/java/com/asuka/pocketpdf/ui/reader/SearchNavigatorTest.kt`
- Modify: `app/src/androidTest/java/com/asuka/pocketpdf/ui/reader/ReaderScreenTest.kt`

- [x] **Step 1: Write navigation tests**

Cover current-page-first ordering, forward/backward wrap exactly once, repeated
matches on a page, empty query, a textless document, and cancellation when the
query changes.

- [x] **Step 2: Verify failure**

```powershell
.\gradlew.bat testDebugUnitTest --tests "*SearchNavigatorTest"
```

- [x] **Step 3: Implement incremental navigation**

Drive page search from the open reader session. Publish progress and matches by
query generation. Page-jump to the active result before applying visible-page
highlights.

- [x] **Step 4: Reconnect the reader UI**

Restore the search action and bar. Draw all visible-page matches and emphasize
the active one. Show an OCR-required message for image-only PDFs.

- [x] **Step 5: Verify and commit**

```powershell
.\gradlew.bat testDebugUnitTest --tests "*SearchNavigatorTest" --tests "*SearchDocumentUseCaseTest"
.\gradlew.bat connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.asuka.pocketpdf.ui.reader.ReaderScreenTest
git add app/src/main app/src/test app/src/androidTest
git commit -m "feat(reader): restore native document search"
```

### Task 7: Move AI Indexing To The Unified Text Source

**Files:**
- Create: `app/src/main/java/com/asuka/pocketpdf/data/pdf/PdfiumTextExtractor.kt`
- Modify: `app/src/main/java/com/asuka/pocketpdf/data/pdf/PdfTextExtractor.kt`
- Modify: `app/src/main/java/com/asuka/pocketpdf/data/indexing/IndexWorker.kt`
- Modify: `app/src/main/java/com/asuka/pocketpdf/data/local/entity/DocumentEntity.kt`
- Modify: `app/src/main/java/com/asuka/pocketpdf/data/local/AppDatabase.kt`
- Create: `app/src/main/java/com/asuka/pocketpdf/data/local/migration/MigrationExtractorVersion.kt`
- Modify: `app/src/test/java/com/asuka/pocketpdf/data/indexing/IndexWorkerTest.kt`
- Create: `app/src/androidTest/java/com/asuka/pocketpdf/data/pdf/PdfiumTextExtractorParityTest.kt`
- Modify: `app/src/androidTest/java/com/asuka/pocketpdf/data/local/MigrationTest.kt`

- [x] **Step 1: Write extraction and invalidation tests**

Assert page boundaries, textless pages, deterministic text, extractor identity,
and that documents indexed with the old identity become stale.

- [x] **Step 2: Verify failure**

```powershell
.\gradlew.bat testDebugUnitTest --tests "*IndexWorkerTest"
```

- [x] **Step 3: Implement PDFium extraction**

Open a worker-owned PDFium session, extract pages sequentially, and preserve
page numbers used by chunks and citations. Keep PDFBox behind a temporary
fallback flag only for PDFium open failures.

- [x] **Step 4: Add extractor version migration**

Store the extractor identity with document index metadata. Mark old indexes
stale without deleting the source PDF; rebuild lazily through the existing
scheduler.

- [x] **Step 5: Verify and commit**

```powershell
.\gradlew.bat testDebugUnitTest --tests "*IndexWorkerTest"
.\gradlew.bat connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.asuka.pocketpdf.data.pdf.PdfiumTextExtractorParityTest
.\gradlew.bat connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.asuka.pocketpdf.data.local.MigrationTest
git add app/src/main app/src/test app/src/androidTest
git commit -m "refactor(index): extract pdf text through pdfium"
```

### Task 8: Regression, Cleanup, And Documentation

**Files:**
- Modify: `docs/ARCHITECTURE.md`
- Create: `docs/pdf-search-regression-corpus.md`
- Modify: `app/src/main/java/com/asuka/pocketpdf/data/pdf/PdfBoxTextExtractor.kt`
- Modify: `app/build.gradle.kts`
- Modify: `gradle/libs.versions.toml`

- [x] **Step 1: Run the full test suite**

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat connectedDebugAndroidTest
.\gradlew.bat lintDebug
```

- [x] **Step 2: Run real-device acceptance**

Verify search and highlights at multiple zoom levels on Chinese, English,
rotated, cropped, multi-line, and scanned documents. Record device/API, fixture,
query, result count, highlight alignment, and latency.

- [x] **Step 3: Remove the old split path**

After parity acceptance, remove PDFBox search geometry and Android
`PdfRenderer`. Remove PDFBox entirely only when the fallback counter is zero on
the acceptance corpus.

- [x] **Step 4: Update architecture and limitations**

Document the unified session, extractor versioning, native licenses, and the
explicit OCR limitation.

- [x] **Step 5: Final verification and commit**

```powershell
.\gradlew.bat clean testDebugUnitTest lintDebug
git add app gradle docs
git commit -m "docs(pdf): finalize unified search architecture"
```
