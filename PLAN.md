# PocketPDF Project Plan

Last updated: 2026-06-13

## Product Goal

PocketPDF is a local-first Android reader that turns imported PDFs into searchable, citable reading sessions. PDF rendering, text extraction, embeddings, retrieval, and persistence run on the device. Answer and summary generation uses a configurable OpenAI-compatible LLM endpoint.

## Current Release

`v1.2.0` is the current release target.

The release includes:

- PDF import, reading, zoom, swipe navigation, and full-text search.
- Persistent highlights and underlines.
- MediaPipe on-device embeddings and Room-backed retrieval.
- Cited document Q&A and page/full-document summaries.
- Bounded Map-Reduce concurrency, cancellation, timeout classification, progress, and ETA.
- Compose UI across application screens with a custom PDF canvas hosted by `AndroidView`.
- Signed APK generation, Sentry support, CI, lint, JVM tests, and Android tests.

## Architecture Constraints

- Keep the single `app` module.
- Preserve `ui -> domain <- data`.
- Keep Android dependencies out of `domain`.
- Use repository contracts at the domain boundary.
- Prefer existing Compose, Flow, Hilt, Room, and WorkManager patterns.
- Add regression tests before behavioral fixes.

## Release Contract

Every release must:

1. Pass `testDebugUnitTest`, `lint`, `connectedDebugAndroidTest`, and `assembleRelease`.
2. Contain the verified embedding model.
3. Produce a signed APK with a monotonically increasing `versionCode`.
4. Update `README.md`, `ROADMAP.md`, `CHANGELOG.md`, and architecture notes when behavior changes.
5. Publish only runtime deliverables as release assets.

## Near-Term Priorities

1. CI emulator coverage.
2. OCR for scanned documents.
3. Streaming UI update throttling.
4. Neighbor-page rendering prefetch.
5. Conversation and annotation export management.

Detailed release findings are in [docs/project-audit-2026-06-13.md](docs/project-audit-2026-06-13.md).
