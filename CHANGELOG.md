# Changelog

All notable user-facing and engineering changes are recorded here.

## [1.3.0] - 2026-06-13

### Added

- Multiple independent chat conversations per document, with a conversation drawer to switch, create, rename, and delete.
- Per-conversation message history and LLM context; each conversation keeps its own thread.
- Clear-current-conversation action in the chat overflow menu.

### Changed

- Chat persistence is now scoped by conversation instead of a single history per document.
- Room schema upgraded to v7: new `conversations` table and a `conversationId` foreign key on `chat_messages`.
- `FullDocumentSummarizer` now receives an injected `FullDocumentSummarizerConfig` instead of mutable test hooks.
- Release metadata now reports `versionName 1.3.0` and `versionCode 4`.

### Fixed

- `PdfiumDocumentSession.close()` no longer blocks the main thread with `runBlocking`; native close runs on an IO dispatcher while the session is marked closed synchronously.

### Migration

- Manual migration 6 to 7 backfills one default conversation per document that already had chat history, preserving existing messages.

## [1.2.0] - 2026-06-13

### Added

- Unified PDFium rendering, extraction, search, and coordinate mapping.
- Full-text PDF search with highlighted results.
- Persistent highlight and underline annotations.
- Full-document Map-Reduce summaries and document-wide questions.
- Generation stage, progress, and approximate ETA in reader and chat.
- Collapsible reader summary generation.
- Selected assistant-response regeneration.
- Automatic verified embedding-model preparation during builds.
- Sentry runtime configuration and signed release build support.

### Changed

- Map batches run with bounded concurrency while preserving source order.
- AndroidX Test was updated to support Android 16 instrumentation.
- Release metadata now reports `versionName 1.2.0` and `versionCode 3`.
- Obsolete XML-era resources were removed after the Compose migration.
- PDF tap handling now uses Android's standard accessibility click path.

### Fixed

- Cancelling generation now cancels the underlying OkHttp call.
- Per-call LLM timeouts are no longer misreported as overall summary timeouts.
- The current question is no longer duplicated in multi-turn LLM context.
- Regeneration uses the question preceding the selected assistant message.
- Invalid or missing embedding models can no longer silently produce a broken APK.
- Sentry values resolve consistently from supported build inputs.

### Verified

- 402 JVM tests passed.
- 31 Android instrumentation tests passed on Android 16 / API 36.1.
- Android lint completed with zero errors.
- Release APK signature and embedded model were verified.
- Release APK size: 70,059,611 bytes.
- Release APK SHA-256: `4eb326e9cfedf390ed5745137a216a7737585ca891205c56e71b7dcc486bcc1b`.

## [1.1.0-compose] - 2026-06-08

- Migrated application screens to the Purple Crystal Compose design system.

## [1.0.0-release] - 2026-05-28

- Completed the first portfolio-grade release baseline with CI, tests, release build, and documentation.

## [0.4.0-qa] - 2026-05-23

- Added document Q&A, citations, chat persistence, and model settings.
