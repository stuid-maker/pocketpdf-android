# Code Review Remediation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Close the six confirmed code-review findings with regression tests and release/device verification.

**Architecture:** Three independent work streams cover networking/timeouts, chat behavior, and build/release supply. Each stream follows red-green-refactor and owns a disjoint file set; integration is followed by the full Android verification pipeline.

**Tech Stack:** Kotlin, coroutines Flow, OkHttp/MockWebServer, Jetpack Compose, Gradle Kotlin DSL, JUnit/MockK, Android Gradle Plugin.

---

### Task 1: Make HTTP stream cancellation close the OkHttp call

**Files:**
- Modify: `app/src/main/java/com/asuka/pocketpdf/data/remote/repository/LlmRepositoryImpl.kt`
- Create: `app/src/test/java/com/asuka/pocketpdf/data/remote/repository/LlmRepositoryImplCancellationTest.kt`
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`

- [ ] Add MockWebServer as a test dependency and write a test that starts an unending SSE response, collects the stream, cancels the collector, and asserts prompt request disconnection.
- [ ] Run the test and confirm it fails because `awaitClose` is not registered while the read blocks.
- [ ] Move blocking execution/SSE collection into a `launch(Dispatchers.IO)` worker inside `callbackFlow`; register `awaitClose` immediately and cancel `Call`, response, and worker.
- [ ] Distinguish cancellation-caused `IOException` from genuine transport failure and close the flow appropriately.
- [ ] Run the cancellation test and existing repository/parser tests.
- [ ] Commit as `fix: cancel active llm http streams`.

### Task 2: Preserve per-call versus overall timeout semantics

**Files:**
- Modify: `app/src/main/java/com/asuka/pocketpdf/domain/usecase/FullDocumentSummarizer.kt`
- Modify: `app/src/test/java/com/asuka/pocketpdf/domain/usecase/FullDocumentSummarizerTest.kt`

- [ ] Write a test using short injected durations that expects `PerCallTimeoutException` when one LLM flow stalls.
- [ ] Write a separate test that expects `OverallTimeoutException` when total work exceeds the outer deadline.
- [ ] Run both tests and verify the per-call case is incorrectly reported as overall timeout.
- [ ] Add constructor parameters `perCallTimeoutMillis` and `overallTimeoutMillis` with production defaults.
- [ ] Convert only the inner timeout to `PerCallTimeoutException`; keep the outer conversion scoped to its own `withTimeout`.
- [ ] Run all summarizer tests and commit as `fix: distinguish summary timeout failures`.

### Task 3: Remove the current question from prior history

**Files:**
- Modify: `app/src/main/java/com/asuka/pocketpdf/ui/chat/ChatViewModel.kt`
- Modify: `app/src/test/java/com/asuka/pocketpdf/ui/chat/ChatViewModelProgressTest.kt`

- [ ] Add a ViewModel test that returns prior history, captures the `history` passed to `AskDocumentUseCase`, and verifies the newly submitted question is absent.
- [ ] Run the test and confirm it fails under save-before-snapshot ordering.
- [ ] Read `getHistorySnapshot` before `saveMessage`, retain non-fatal save handling, and rewrite the misleading comment.
- [ ] Run chat ViewModel and AskDocument tests.
- [ ] Commit as `fix: exclude current question from chat history`.

### Task 4: Regenerate the selected assistant response

**Files:**
- Modify: `app/src/main/java/com/asuka/pocketpdf/ui/chat/ChatViewModel.kt`
- Modify: `app/src/main/java/com/asuka/pocketpdf/ui/chat/ChatActivity.kt`
- Modify: `app/src/test/java/com/asuka/pocketpdf/ui/chat/ChatViewModelProgressTest.kt`
- Modify: `app/src/androidTest/java/com/asuka/pocketpdf/ui/chat/ChatActivityTest.kt`

- [ ] Add a ViewModel test with multiple user/assistant pairs and assert `retry(selectedAssistantId)` resubmits the nearest preceding user question.
- [ ] Add a test asserting an unmatched assistant ID sets a readable error and sends nothing.
- [ ] Run tests and confirm the parameterized retry API/behavior is missing.
- [ ] Remove `lastQuestion`, add `retry(assistantMessageId: Long)`, and route the selected message ID from `ChatBubble` through `ChatScreen`.
- [ ] Update the top-level error retry button to retain a separate latest-failed-question path only when an actual generation failure occurred, without using it for message-menu regeneration.
- [ ] Run unit and Compose tests; commit as `fix: regenerate selected chat response`.

### Task 5: Make Sentry configuration match local and CI documentation

**Files:**
- Create: `buildSrc/src/main/kotlin/BuildProperties.kt` or a focused root Gradle helper if `buildSrc` is not already used
- Modify: `app/build.gradle.kts`
- Modify: `.github/workflows/ci.yml`
- Modify: `local.properties.example`
- Create/Modify: focused build-property tests supported by the selected Gradle helper location

- [ ] Write property-resolution tests for `Gradle property > environment > local.properties > empty`.
- [ ] Run them and confirm current `findProperty` behavior cannot read local/environment inputs.
- [ ] Implement the resolver and use it only for `SENTRY_DSN`; keep signing sourced from `local.properties`.
- [ ] Pass CI Sentry explicitly with `-PSENTRY_DSN="$SENTRY_DSN"` during release assembly.
- [ ] Generate release `BuildConfig` with a test DSN and assert the exact escaped value is present.
- [ ] Commit as `fix: load sentry dsn from supported sources`.

### Task 6: Guarantee a verified embedding model during builds

**Files:**
- Create: `buildSrc/src/main/kotlin/EmbeddingModelTasks.kt` or focused Gradle build logic matching Task 5's selected structure
- Modify: `app/build.gradle.kts`
- Modify: `README.md`
- Modify: `.github/workflows/ci.yml` only if caching needs an explicit path
- Create/Modify: Gradle build-logic tests

- [ ] Write tests for valid checksum acceptance, invalid checksum rejection, temporary download replacement, and offline failure messaging.
- [ ] Run tests and confirm no model preparation task exists.
- [ ] Implement pinned URL, size, and SHA-256 constants; download to a temporary file; verify; atomically move with Windows-compatible fallback.
- [ ] Wire Android asset merge/pre-build tasks to model preparation.
- [ ] Correct README's model size from approximately 68 MB to approximately 6.1 MB and document automatic preparation plus manual fallback.
- [ ] Verify a valid cached model avoids download and an invalid model fails or is replaced before assembly.
- [ ] Commit as `build: verify embedding model supply`.

### Task 7: Integration and physical-device verification

**Files:**
- Modify only files required by integration failures.

- [ ] Run `.\gradlew.bat testDebugUnitTest`.
- [ ] Run `.\gradlew.bat lint`.
- [ ] Run `.\gradlew.bat assembleRelease`.
- [ ] Inspect the APK for `assets/models/universal_sentence_encoder.tflite` and verify its SHA-256 source artifact.
- [ ] Verify APK signing with `apksigner`.
- [ ] On the connected device, verify PDF indexing, cancellation of a long generation, a two-turn follow-up without duplicated current question, regeneration of an older selected answer, and full-document summary behavior.
- [ ] Record exact verification results and any device-only caveats.
