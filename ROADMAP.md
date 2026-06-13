# PocketPDF Roadmap

Last updated: 2026-06-13

## Released

| Version | Status | Main scope |
|---|---|---|
| `v0.0.1-env-ready` | Complete | Android build, Hilt, LM Studio connectivity |
| `v0.1.0-pdf-reader` | Complete | PDF import, persistence, first reader |
| `v0.4.0-qa` | Complete | RAG Q&A, citations, settings, chat persistence |
| `v1.0.0-release` | Complete | CI, release build, documentation, portfolio baseline |
| `v1.1.0-compose` | Complete | Purple Crystal Compose migration |
| `v1.2.0` | Complete | Unified PDFium engine, search, annotations, full-document AI progress, reliability and release audit |

## v1.2.0 Scope

- [x] Unified PDFium rendering, text extraction, search coordinates, and session lifecycle.
- [x] Full-text search with page navigation and highlights.
- [x] Highlight and underline annotations persisted in Room schema v6.
- [x] Full-document Map-Reduce summary and document-wide Q&A.
- [x] Bounded map concurrency with ordered results and structured cancellation.
- [x] Stage progress and approximate ETA in reader and chat.
- [x] Collapsible summary UI that keeps generation alive.
- [x] Immediate OkHttp cancellation when generation stops.
- [x] Distinct per-call and overall timeout errors.
- [x] Prior-turn chat context without duplicating the current question.
- [x] Selected-response regeneration.
- [x] Verified embedding-model supply during Gradle builds.
- [x] Sentry configuration from Gradle property, environment, or `local.properties`.
- [x] Android 16-compatible instrumentation test dependencies.
- [x] 402 JVM tests, 31 Android tests, lint, signed release build, APK/model inspection.

## Next

### Reliability

- Add an API 35/36 emulator matrix to GitHub Actions.
- Replace broad R8 keep rules with library-specific rules and compare release size.
- Remove production-facing test hooks from `FullDocumentSummarizer`.
- Move `PdfiumDocumentSession.close()` away from main-thread `runBlocking`.

### Product

- Add OCR for scanned PDFs.
- Add first-run setup guidance for LM Studio and custom endpoints.
- Add bookmark and annotation export flows.
- Add conversation management beyond one history per document.

### Performance

- Batch or throttle streaming UI updates for very long answers.
- Add neighboring-page render prefetch.
- Evaluate a scalable vector index for very large document libraries.

Historical weekly execution details remain in [docs/dev-log/](docs/dev-log/).
