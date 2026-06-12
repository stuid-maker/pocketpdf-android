# Code Review Remediation Design

## Goal

Close the six review findings with regression tests and release verification:

1. HTTP cancellation must stop the underlying OkHttp call immediately.
2. Clean checkouts must receive a verified embedding model or fail the build clearly.
3. The current question must appear only once in the LLM request.
4. Sentry configuration must work from documented local and CI inputs.
5. Per-call and overall summary timeouts must retain distinct error semantics.
6. Regenerate must target the selected assistant response rather than the latest question.

## Architecture

### HTTP streaming lifecycle

`LlmRepositoryImpl` will launch the blocking OkHttp request and SSE collection in a child coroutine owned by `callbackFlow`. `awaitClose` will be registered without waiting for the stream to finish, cancel the `Call`, close the response, and cancel the worker job. Normal completion will close the channel from the worker.

An integration-style JVM test with `MockWebServer` will hold an SSE response open, cancel collection, and assert that the server observes the disconnected request.

### Embedding model supply

Gradle will expose a model preparation task that:

- accepts an existing model only when its SHA-256 matches the pinned checksum;
- downloads the pinned MediaPipe model when missing;
- writes through a temporary file and atomically replaces the destination;
- fails with a clear remediation message when download or verification fails.

Android asset merge/build tasks will depend on model preparation. CI therefore cannot produce an AI-broken APK silently. The model remains ignored by Git.

### Conversation history and regeneration

`ChatViewModel` will read the history snapshot before persisting the current question, then pass only prior turns to `AskDocumentUseCase`. Persistence will still happen before generation starts so the conversation remains durable.

Regenerate will accept the selected assistant message ID and find the nearest preceding user message in the displayed conversation. It will preserve the existing history, append that user question as a new turn, and generate a new assistant response without relying on a global `lastQuestion`. The visible menu callback will carry the selected message ID.

### Runtime configuration

Build configuration will load one normalized property set from Gradle properties, environment variables, and `local.properties`, with precedence:

1. Gradle property
2. Environment variable
3. `local.properties`

Signing and `SENTRY_DSN` will use the same loader. CI will pass Sentry through an environment variable. Tests will verify the property resolution helper independently where practical, and release output will be inspected to ensure the configured DSN reaches `BuildConfig`.

### Timeout semantics

A dedicated per-call timeout exception will be thrown when an individual LLM stream exceeds 120 seconds. The outer 600-second timeout will convert only its own timeout into `OverallTimeoutException`; it will not rewrite nested timeout failures.

Tests will use injectable timeout durations so they can deterministically verify both paths without waiting in real time.

## Error Handling

- Cancellation always preserves `CancellationException` and closes network resources.
- Model checksum mismatch deletes the invalid temporary/downloaded file and fails the build.
- Missing Sentry configuration remains non-fatal and disables reporting.
- Regeneration is ignored when the selected assistant message has no matching user message.
- Partial assistant output is not persisted as a successful regenerated response after failure unless the existing cancellation behavior explicitly saves it.

## Testing

- JVM unit tests for history ordering, selected-message regeneration, configuration resolution, and timeout classification.
- MockWebServer cancellation test for the underlying HTTP call.
- Gradle task verification for existing valid model, invalid model, and download behavior where feasible.
- Full `testDebugUnitTest`, lint, and `assembleRelease`.
- APK inspection for the model asset and signature.
- Optional physical-device smoke test: indexing, streaming cancellation, multi-turn query, selected regeneration, and summary timeout/error presentation.

## Scope

This remediation does not redesign chat storage IDs, add encrypted chat persistence, implement neighboring-page rendering, or broaden the existing product UX beyond what is required to close these findings.
