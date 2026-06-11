# Unified PDFium Engine Design

## Goal

Replace the current split PDF pipeline with one native PDFium document session
for page rendering, text extraction, keyword search, match rectangles, and
eventually AI indexing. The target is WPS-style behavior: a match is found by
the same engine that renders the page, so the highlight is derived from the
same character stream and page coordinate system.

This design improves searchable, text-based PDFs. Image-only scanned PDFs still
require OCR and are explicitly outside the first delivery.

## Current Failure Mode

The reader currently renders with Android `PdfRenderer`, while search and AI
text extraction use PDFBox. `PdfBoxTextExtractor` builds displayed text through
PDFBox's text writer but separately advances character offsets from
`TextPosition` objects. Inserted spaces and line breaks can make those offsets
diverge. Search then indexes one string and selects rectangles using offsets
from another representation.

Even when offsets happen to align, rendering and text geometry come from
different PDF engines. Font substitution, rotation, crop boxes, ligatures, and
writing direction can therefore produce visible highlight drift.

## Dependency Decision

Use `io.legere:pdfiumandroid:1.0.30` for the first integration.

- It supports Android API 24 and above; pocketPDF uses minSdk 26.
- It exposes document/page handles, bitmap rendering, text extraction,
  character boxes, and native page search.
- The wrapper is Apache-2.0 and the bundled PDFium code uses its BSD-style
  license. Required notices will be retained.
- Versions `1.0.34` and newer are not the initial choice because their Kotlin
  metadata requires Kotlin `2.2` or newer, while this project uses Kotlin
  `2.0.21`. Version `1.0.30` was built with Kotlin `2.0.21` and retains the
  required rendering, text geometry, and native search APIs. We will avoid a
  toolchain upgrade inside the PDF search change.

## Architecture

Introduce an app-owned engine boundary:

```kotlin
interface PdfDocumentEngine {
    suspend fun open(file: File, password: String? = null): PdfDocumentSession
}

interface PdfDocumentSession : Closeable {
    val pageCount: Int
    suspend fun pageInfo(pageIndex: Int): PdfPageInfo
    suspend fun render(request: PdfRenderRequest): Bitmap
    suspend fun extractText(pageIndex: Int): PdfPageText
    suspend fun searchPage(pageIndex: Int, query: String): List<PdfSearchMatch>
}
```

The domain-facing models contain no PDFium classes. `PdfSearchMatch` stores the
page index, exact character range, matched text, and one or more rectangles in
PDF page coordinates. Multi-line matches remain multiple rectangles instead of
being expanded into one large box.

`PdfiumDocumentEngine` owns native handles behind `PdfDocumentSession`. Reader,
search, and indexing code depend on the app interface, not the third-party
binding.

## Resource And Concurrency Model

One open reader owns one document session. The session serializes native calls
with a `Mutex` on the IO dispatcher because PDFium page and text handles share
document state and should not be treated as freely thread-safe.

The session keeps a small LRU cache of open page/text-page pairs around the
visible page. Eviction closes the text page before the page. Closing the session
cancels pending work, closes every cached page, closes the document, and then
closes the file descriptor.

Indexing opens its own short-lived session. It never borrows the UI session,
which prevents a background worker from blocking rendering or extending an
Activity-owned resource lifetime.

## Rendering And Coordinates

PDFium becomes the source of page dimensions, rotation, rendering, text ranges,
and match rectangles.

Engine results use PDF page coordinates with the page origin and rotation
normalized by the adapter. `PdfPageView` receives the rendered bitmap plus its
page metadata and applies one transform from normalized page coordinates to the
currently zoomed and panned bitmap. Search must not estimate rectangles from
font size or rebuild them from independently extracted glyphs.

The first rendering migration preserves the current bitmap sizing and white
background behavior. Visual parity is checked before search UI is re-enabled.

## Search Behavior

Search is page-incremental and cancellable:

1. Search the current page from the current position.
2. Continue toward the document end.
3. Wrap once to page zero and stop before the starting position.
4. Previous-search follows the inverse order.
5. A new query cancels the old search generation.

The engine uses PDFium's page search to obtain exact character ranges, then
requests rectangles for those ranges from the same text page. The UI
distinguishes the active match from other visible-page matches and navigates to
the active match's page before drawing it.

Case-insensitive matching is the default. Whole-word and case-sensitive options
remain future extensions unless PDFium behavior can be exposed without
reimplementing matching outside the engine.

## AI Indexing

AI indexing will use `PdfDocumentSession.extractText()` so search, citations,
summary, and retrieval are based on the same page text stream.

Migration is staged:

1. Add PDFium extraction beside the existing PDFBox extractor.
2. Compare page text and chunk/citation behavior on the regression corpus.
3. Make PDFium the default indexing source.
4. Retain PDFBox only as a temporary feature-flagged fallback for documents
   that PDFium cannot open, then remove it after telemetry and device testing.

Existing chunks must be versioned by extractor identity. Documents indexed by
the old extractor are marked stale and rebuilt lazily; otherwise old offsets
and new reader text could be mixed.

## Error Handling

- Invalid and unsupported PDFs return a typed open error.
- Password-protected PDFs return a password-required or invalid-password error.
- A page render failure does not invalidate already rendered pages.
- A textless page returns empty text and no matches.
- Image-only documents are identified as textless; the UI explains that OCR is
  required instead of reporting a false "no result."
- Native resources are closed after cancellation and errors.

## Testing

Unit tests cover range ordering, wrap-around navigation, cancellation
generations, coordinate transforms, cache eviction, and deterministic close
order.

Android integration tests use fixed PDF fixtures containing:

- single-line and multi-line English;
- Chinese text with embedded fonts;
- ligatures and punctuation;
- rotated pages and non-default crop boxes;
- repeated terms on one page;
- a match crossing a visual line break;
- an image-only page.

For searchable fixtures, assertions verify the matched text and that every
returned rectangle intersects the rendered glyphs within a small pixel
tolerance. Merely asserting that a rectangle is non-empty is not sufficient.

## Delivery Stages

1. Add the engine boundary and PDFium adapter with lifecycle tests.
2. Move reader rendering to PDFium and verify visual parity.
3. Re-enable keyword search using native ranges and rectangles.
4. Move AI indexing to PDFium with extractor-version invalidation.
5. Evaluate OCR as a separate feature for scanned PDFs.

Each stage keeps the previous path available until its parity checks pass.

## Acceptance Criteria

- Rendering and keyword search use the same open PDFium session.
- Search highlights align on the regression corpus at normal and zoomed scales.
- Next/previous search wraps once and remains responsive to cancellation.
- Chinese, rotated, and multi-line searchable PDFs have explicit passing tests.
- AI chunks and citations are generated from PDFium page text after migration.
- Scanned PDFs are reported as requiring OCR, not claimed as searchable.
- No native handle or file descriptor remains open after reader/indexer close.

## Non-Goals

- OCR in the first delivery.
- PDF editing, annotations embedded into the PDF, or form filling.
- Exact behavioral cloning or reuse of proprietary WPS/Huawei code.
- A Kotlin/AGP toolchain upgrade as part of this migration.
