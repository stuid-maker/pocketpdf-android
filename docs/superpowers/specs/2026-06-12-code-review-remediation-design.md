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

Cancellation-triggered `IOException` from OkHttp will be treated as normal shutdown when the flow is already closed; unrelated I/O failures will still close the channel with an error. An integration-style JVM test with `MockWebServer` will hold an SSE response open, cancel collection, and assert that the server observes the disconnected request.

### Embedding model supply

Gradle will expose a model preparation task that:

- accepts an existing model only when its SHA-256 matches the pinned checksum;
- downloads the pinned MediaPipe model when missing;
- writes through a temporary file and atomically replaces the destination;
- fails with a clear remediation message when download or verification fails.

The audited URL and SHA-256 will be constants in the Gradle build logic. Replacement will use `Files.move(..., ATOMIC_MOVE, REPLACE_EXISTING)` with a `REPLACE_EXISTING` fallback for filesystems that do not support atomic moves. The current model path remains `app/src/main/assets/models/universal_sentence_encoder.tflite`, it remains ignored by Git, and the pinned artifact is 6,120,274 bytes with SHA-256 `89ad3c74175dd8caa398cc22b657296d94302d20c525c12b58b29420f7249749`.

Android asset merge/build tasks will depend on model preparation. CI therefore cannot produce an AI-broken APK silently. The model remains ignored by Git.

### Conversation history and regeneration

`ChatViewModel` will read the history snapshot before persisting the current question, then pass only prior turns to `AskDocumentUseCase`. Persistence will still happen before generation starts so the conversation remains durable.

The DAO already returns messages in ascending ID order, so `takeLast` continues to select the newest prior turns. A failed save remains non-fatal for generation, matching current behavior, but the misleading save-before-snapshot comment will be removed.

Regenerate will accept the selected assistant message ID and find the nearest preceding user message in the displayed conversation. It will preserve the existing history, append that user question as a new turn, and generate a new assistant response without relying on a global `lastQuestion`. `ChatBubble` will invoke `onRegenerate(message.id)`, `ChatScreen` will forward it, and `ChatViewModel.retry(assistantMessageId)` will resolve the question. If no preceding user message exists, the ViewModel will expose a user-visible error rather than falling back to the latest question.

### Runtime configuration

Sentry configuration will load from Gradle properties, environment variables, and `local.properties`, with precedence:

1. Gradle property
2. Environment variable
3. `local.properties`

An explicit `-P` value is treated as the strongest per-build override. CI will pass Sentry explicitly as `-PSENTRY_DSN=...`, preventing user-level Gradle properties from silently selecting a different DSN. Signing will continue to use the CI-generated `local.properties` file and will not inherit the Sentry source policy. The key name is `SENTRY_DSN`, and an absent value produces an empty `BuildConfig.SENTRY_DSN`.

### Timeout semantics

A dedicated per-call timeout exception will be thrown when an individual LLM stream exceeds 120 seconds. The outer 600-second timeout will convert only its own timeout into `OverallTimeoutException`; it will not rewrite nested timeout failures.

`PerCallTimeoutException` will live beside `OverallTimeoutException` in `FullDocumentSummarizer.kt`. Constructor timeout parameters will have production defaults, allowing tests to deterministically verify both paths without waiting in real time. Per-call timeout tests will also verify that collection cancellation reaches the repository cleanup path.

## Error Handling

- Cancellation always preserves `CancellationException` and closes network resources.
- Model checksum mismatch deletes the invalid temporary/downloaded file and fails the build.
- Missing Sentry configuration remains non-fatal and disables reporting.
- Regeneration reports a readable error when the selected assistant message has no matching user message.
- Partial assistant output is not persisted as a successful regenerated response after failure unless the existing cancellation behavior explicitly saves it.

## Testing

- JVM unit tests for history ordering, selected-message regeneration, configuration resolution, and timeout classification.
- MockWebServer cancellation test for the underlying HTTP call.
- Gradle verification for an existing valid model, checksum mismatch, temporary-file replacement, and a clear offline download failure.
- Full `testDebugUnitTest`, lint, and `assembleRelease`.
- APK inspection for the model asset and signature.
- Optional physical-device smoke test: indexing, streaming cancellation, multi-turn query, selected regeneration, and summary timeout/error presentation.

## Scope

This remediation does not redesign chat storage IDs, add encrypted chat persistence, implement neighboring-page rendering, or broaden the existing product UX beyond what is required to close these findings.

## Delivery

Each finding will be implemented as an independently reviewable commit where file overlap permits. HTTP cancellation precedes timeout semantics; history ordering precedes selected-message regeneration. Final verification runs the complete unit suite, lint, release assembly, APK model inspection, signature verification, and optional physical-device smoke tests.
