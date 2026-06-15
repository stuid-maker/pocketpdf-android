# PocketPDF Android Course Report Design

## 1. Deliverable

- Source template: `C:\Users\33755\Desktop\model.docx`
- Final document: `C:\Users\33755\Desktop\PocketPDF-Android课程设计报告.docx`
- Personal information: leave class, student number, and name blank.
- Target length: approximately 20-25 A4 pages after screenshots and diagrams are inserted.
- Preserve the template's A4 page geometry, margins, base font size, and heading appearance.
- Replace the template's instructional prompts with the finished report. Keep the cover structure and six required chapters.

## 2. Writing Position

The report is a first-person development retrospective written from the perspective of a student who started the project with almost no Android development experience. It must explain what was actually built, why important choices were made, what failed during development, and how the result was checked.

The prose should be direct and specific. It should prefer concrete details such as class names, dates, error symptoms, test counts, device behavior, and trade-offs over broad claims. It should not use promotional language, inflated conclusions, repeated three-part summaries, or generic openings about the development of mobile Internet and artificial intelligence.

The report must not claim that all code was independently handwritten. It must describe the real use of Cursor, Claude, Codex, and Hermes. It must also state that early AI-generated code was usually read, modified, tested, and checked on a device, while later work relied more heavily on direct adoption but still used builds, automated tests, code review, logs, and real-device results as acceptance evidence.

## 3. Evidence Rules

Every technical claim must be supported by at least one current project artifact:

- source code under `app/src/main`
- tests under `app/src/test` or `app/src/androidTest`
- Gradle and manifest configuration
- Git commits and release tags
- development logs under `docs/dev-log`
- architecture and audit documents
- current PocketPDF screenshots or fresh device captures

Repository documentation is a routing source, not unquestioned truth. Before describing a subsystem, verify its current implementation because some early documents still mention XML, `PdfRenderer`, or PDFBox paths that were later replaced by Compose and unified PDFium.

Do not use these unrelated or unsuitable images:

- `artifacts/pocket_home.png`, which is a game screenshot
- `artifacts/device-startup.png`, which only shows the phone notification panel
- duplicate home screenshots when one clearer image is enough
- old screenshots that materially conflict with the current Compose interface

## 4. Document Structure

### Cover

- Beijing Institute of Technology School of Computer Science heading retained.
- Change the second line from a template label to `《Android技术开发基础》课程设计报告`.
- Add the project subtitle `PocketPDF：基于 RAG 的智能 PDF 阅读器`.
- Leave class, student number, and name lines blank.

### Chapter 1: App Runtime and Development Environment

Cover the following facts in compact prose and one technology table:

- Android 8.0 / API 26 or newer
- Kotlin 2.0.21 and Java 17
- Android Studio, AGP 8.7.3, Gradle 8.10.2, compile/target SDK 35
- Room, DataStore, Hilt, WorkManager, Compose Material 3, PDFiumAndroid, MediaPipe, OkHttp, and Moshi
- OpenAI-compatible LLM service, with LM Studio used during development through `adb reverse tcp:1234 tcp:1234`
- direct installation of the signed APK
- current source scale: 124 production Kotlin files and about 10,807 production Kotlin lines; 77 test files and about 10,002 test Kotlin lines
- explain that AI generation requires a reachable LLM endpoint, while PDF storage, text extraction, indexing, embeddings, and retrieval run locally

### Chapter 2: Requirements Analysis

#### 2.1 Topic Selection

Explain the real starting point:

- the developer began with almost no Android experience
- ordinary PDF readers solve viewing but not efficient understanding of long papers and technical documents
- the project goal became a reader that keeps normal reading usable while adding local indexing, document Q&A, summaries, and page traceability

Avoid claiming formal user research that did not happen. Define likely users as students and readers of papers, reports, and technical documents.

#### 2.2 Functional Decomposition

Include a function tree with these branches:

1. Document library: SAF import, cover display, index status, retry, and delete.
2. PDF reading: rendering, page turning, zooming, dragging, page search, highlight, and underline.
3. Local indexing: PDF text extraction, chunking, on-device embedding, Room persistence, and background status updates.
4. Intelligent reading: page summary, full-document summary, progress display, cancellation, and caching.
5. Document Q&A: RAG retrieval, streaming response, page citations, citation jump, multiple conversations, rename, clear, and regenerate.
6. Settings and diagnostics: endpoint, model, API key, prompt, chunk strategy, connection test, and diagnostics.

#### 2.3 UI and Interaction

Discuss the current Compose interface and the retained custom `PdfPageView`. Explain:

- why the library emphasizes the most recent document and a clear import action
- why the reader reserves most of the screen for the PDF
- why AI functions are placed in a bottom control area instead of covering the document
- why long-running summaries can be collapsed without cancelling
- why citations are clickable and return to the exact page
- why errors are shown near the failed operation with retry actions

Use current screenshots of the library, reader, chat, summary/progress, and settings or diagnostics pages. Each screenshot must have a short caption and a paragraph explaining one design decision, not merely restating what is visible.

### Chapter 3: Architecture and Technical Implementation

#### 3.1 Overall Architecture

Explain the single-module Clean Architecture structure:

`ui -> domain <- data`, with `di` assembling implementations and `core` providing shared utilities.

Include a package architecture diagram and a short description of:

- UI: Activities, Compose screens, ViewModels, and `PdfPageView`
- Domain: models, repository contracts, use cases, prompt templates, and interfaces
- Data: Room, PDFium, MediaPipe, OkHttp, WorkManager, storage, and DataStore
- DI: Hilt modules

Explain why a single Gradle module was retained: lower configuration cost for a five-week beginner project, while package boundaries and tests still preserve dependency direction.

#### 3.2 PDF Reading and Annotation

Describe the current unified PDFium session:

- one engine supplies rendering, text, search matches, and coordinates
- `PdfPageView` handles bitmap display, matrix transforms, gestures, search highlighting, and annotation drawing
- Compose hosts the custom View through `AndroidView`
- search and annotation coordinates are mapped between screen, bitmap, and PDF spaces

Mention that the early version used `PdfRenderer` and PDFBox, but later inconsistencies led to a unified PDFium design. This evolution is important evidence of real development rather than a perfect first attempt.

#### 3.3 Local Indexing Pipeline

Include a flow diagram:

`PDF import -> text extraction -> chunking -> MediaPipe embedding -> Room chunks -> INDEXED`

Explain:

- WorkManager runs indexing outside the foreground UI
- sliding-window chunk size is 512 characters with 50-character overlap
- paragraph chunking is also available
- Universal Sentence Encoder is stored as a verified TFLite asset
- embeddings are serialized to Room BLOB values
- Room Flow automatically refreshes the document status badge
- failures are marked `FAILED` and can be retried

#### 3.4 Retrieval and RAG Q&A

Explain the algorithm in practical steps:

1. Embed the user's question.
2. Calculate cosine similarity against stored chunk vectors.
3. Sort by score and select Top-K chunks.
4. Build a prompt containing chunk text and page numbers.
5. Send it to an OpenAI-compatible chat completion endpoint.
6. Parse SSE tokens and update the answer progressively.
7. Parse page citations and jump back to the reader when clicked.

Give cosine similarity as:

`cos(theta) = (a dot b) / (|a| * |b|)`

State the main complexity as `O(n*d)` time for comparing `n` chunks with vector dimension `d`, plus sorting cost. Avoid pretending that an approximate nearest-neighbor index was implemented.

#### 3.5 Full-Document Summary

Describe bounded-concurrency Map-Reduce:

- Map stage summarizes document chunks
- concurrency is limited to two requests
- results retain original chunk order
- Reduce stage merges partial summaries
- staged progress and approximate remaining time are shown
- the user can collapse the panel and continue reading
- cancellation propagates to the active OkHttp call

#### 3.6 Persistence and Configuration

Explain Room schema responsibilities:

- documents
- chunks
- conversations and chat messages
- annotations

Explain DataStore usage for settings and summary cache. Mention API-key encryption and disabled Android backup. Do not claim full database encryption.

### Chapter 4: Technical Highlights, Difficulties, and Solutions

Use five detailed cases. Each case follows: symptom, investigation, decision, implementation, and verification.

1. **Build tool compatibility**
   - Initial AGP 9, Kotlin, KSP, and Hilt compatibility failures.
   - Downgrade to AGP 8.7.3, Kotlin 2.0.21, KSP 2.0.21-1.0.28, and Hilt 2.52.
   - Explain that choosing a stable combination was more useful than continuing to chase new versions.

2. **PDF engine inconsistency**
   - Early rendering, text extraction, search, and coordinates came from different paths.
   - Migrate to unified PDFium for rendering, extraction, search rectangles, and indexing.
   - Add lifecycle, multilingual search, and render parity tests.

3. **Long-running AI generation**
   - Full-document summaries were slow and initially gave little feedback.
   - Add Map-Reduce, concurrency limit two, staged progress, ETA, collapse-without-cancel, and explicit stop behavior.

4. **Streaming cancellation and regeneration**
   - Cancelling a coroutine did not immediately stop the underlying HTTP request.
   - Keep the OkHttp `Call`, cancel it in `awaitClose`, and add cancellation regression tests.
   - Fix regeneration so it uses the selected assistant answer instead of the most recent question.

5. **Release-only indexing failure**
   - A release APK could stay in indexing because MediaPipe/protobuf linkage failed after shrinking.
   - Use real-device `adb logcat` to identify the failure.
   - Catch `LinkageError`, mark the document `FAILED`, and add targeted R8 keep rules.
   - Record that this was verified on a real Huawei P40 rather than inferred from a successful local build.

Also summarize the visual redesign from early XML/Material pages to the later Compose "purple crystal" interface. Present it as an iterative correction, not as an original perfect design.

### Chapter 5: Brief Development Process

Use a dated timeline derived from Git history:

- May 11-12: project planning, environment, architecture skeleton, and LM Studio connection
- May 12-14: import, Room document library, and first PDF reader
- May 19: chunk storage, MediaPipe embedding, WorkManager indexing, and status UI
- May 20: cosine retrieval, streaming API, summaries, and settings
- May 21-23: chat, citations, history, cache, and initial polishing
- May 28: testing, documentation, CI, and v1.0
- June 7-8: security fixes, Compose redesign, search, annotation, and gestures
- June 9: unified PDFium engine and native search
- June 10-12: progress UX, UI contrast, code-review remediation, cancellation, timeout, and regeneration fixes
- June 13-14: release audits, multi-conversation support, signed releases, and release-only indexing repair

Mention 124 Git commits and milestone tags from `v0.0.1-env-ready` through `v1.3.1`. Do not claim that work occurred on dates with no repository evidence.

### Chapter 6: Learning Reflection and Course Suggestions

#### 6.1 Learning Reflection

Write from the confirmed beginner background:

- initially unfamiliar with Gradle, Android lifecycle, Room, coroutines, Compose state, and device debugging
- learned to divide work into small features and verify each layer
- learned that a build passing does not prove a real-device feature works
- learned to read logs and tests instead of repeatedly changing code by guesswork
- acknowledge that some architecture and implementation details are still understood less deeply than desired

#### 6.2 Use of AI During Development

Describe tools and evidence-based roles:

- Cursor: early planning, scaffolding, library/import features, tests, and development logs; visible in many co-authored commits
- Claude: reader gesture and indexing improvements; visible in co-authored commits
- Codex: later Compose redesign, PDFium unification, code review fixes, testing, device verification, release audit, and publication work
- Hermes: independent design or release review, including the recorded design review and `Reviewed-by` commit

Describe the real workflow:

- early stage: read explanations, compare choices, modify code, run tests, and check the app manually
- later stage: more code was accepted directly because the project became larger and time pressure increased
- acceptance still relied on Gradle builds, JVM tests, Android instrumentation tests, `adb logcat`, Git diffs, and visible phone behavior
- state the limitation honestly: passing tests reduces risk but does not replace understanding; several late defects showed the cost of relying too much on generated changes

#### 6.3 Course Suggestions

Keep this short and practical:

- add one lesson on Gradle dependency compatibility and reading build errors
- show a full example from ViewModel to Repository rather than isolated API snippets
- include real-device debugging, logcat, and release-build differences
- explain how to review and verify AI-generated code

## 5. Visual Plan

Use no more than seven screenshots or diagrams to avoid turning the report into a gallery:

1. Current document library screenshot.
2. Current reader screenshot.
3. Fresh successful chat screenshot with a cited answer.
4. Fresh full-document summary or progress screenshot.
5. Fresh settings or diagnostics screenshot.
6. Functional decomposition tree.
7. Architecture or RAG pipeline diagram.

Phone screenshots should be cropped only to remove irrelevant outer margins or private notification content. Do not alter the application UI. Use captions such as `图 2-1 文档库主界面` and explain the image in the following paragraph.

## 6. Formatting Rules

- Preserve A4 page size and the template margins.
- Use the template's 10.5 pt body size and 16 pt chapter heading size.
- Use real Word heading styles for chapters and subsection headings.
- Use first-line indentation for ordinary Chinese paragraphs.
- Keep paragraphs moderate in length; use lists only for real enumeration.
- Use tables only for environment information, technology choices, test evidence, and the development timeline.
- Keep screenshots centered and normally between 8.0 and 11.5 cm wide.
- Keep captions with their figures and avoid leaving a heading alone at the bottom of a page.
- Add page numbers in the footer if the template can support them without changing its overall appearance.

## 7. Verification

Before delivery:

1. Recheck all versions, counts, dates, and current implementation details from the repository.
2. Run relevant project tests or use the latest verified audit only when a fresh run is impractical.
3. Inspect every inserted screenshot and remove unrelated or outdated images.
4. Scan the document for template instructions, unfinished markers, inflated claims, duplicated paragraphs, and unsupported performance numbers.
5. Render the DOCX to page images and inspect every page for clipped text, stretched screenshots, broken tables, awkward page breaks, and missing Chinese glyphs.
6. If LibreOffice remains unavailable, use Microsoft Word automation or another local rendering path; disclose any remaining visual QA limitation rather than claiming it passed.
