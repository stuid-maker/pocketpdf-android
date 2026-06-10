# AI Generation Progress Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add truthful stage progress and approximate ETA for full-document AI work, while allowing the reader summary sheet to collapse without cancelling generation.

**Architecture:** `FullDocumentSummarizer` emits domain progress events alongside its existing final-token flow. A pure `GenerationProgressEstimator` converts events and completed request durations into monotonic display progress and approximate remaining time; reader and chat ViewModels project that data into their own UI state. Compose controls sheet visibility only, with a compact reader status bar for reopening active, completed, or failed summaries.

**Tech Stack:** Kotlin, coroutines `Flow`, Android ViewModel, Jetpack Compose Material 3, JUnit 4, MockK, kotlinx-coroutines-test, Compose UI tests.

---

## File Structure

- Create `app/src/main/java/com/asuka/pocketpdf/domain/usecase/FullDocumentProgress.kt`
  - Domain-only progress stages emitted by full-document map-reduce.
- Modify `app/src/main/java/com/asuka/pocketpdf/domain/usecase/FullDocumentSummarizer.kt`
  - Emit progress and request timing around map, reduce, and final LLM calls.
- Modify `app/src/main/java/com/asuka/pocketpdf/domain/usecase/SummarizeDocumentUseCase.kt`
  - Forward progress for full summaries without changing cache semantics.
- Modify `app/src/main/java/com/asuka/pocketpdf/domain/usecase/AskDocumentUseCase.kt`
  - Forward progress only for `FULL_DOCUMENT` chat routing.
- Create `app/src/main/java/com/asuka/pocketpdf/ui/ai/GenerationProgressEstimator.kt`
  - Pure monotonic fraction and ETA calculation shared by reader and chat.
- Create `app/src/test/java/com/asuka/pocketpdf/ui/ai/GenerationProgressEstimatorTest.kt`
  - Estimator behavior and formatting tests.
- Modify `app/src/main/java/com/asuka/pocketpdf/ui/reader/ReaderUiState.kt`
  - Add active progress metadata to summary states.
- Modify `app/src/main/java/com/asuka/pocketpdf/ui/reader/ReaderViewModel.kt`
  - Consume progress, update ETA, and preserve explicit cancellation.
- Modify `app/src/main/java/com/asuka/pocketpdf/ui/reader/ReaderScreen.kt`
  - Decouple dismiss from stop and add collapsed status bar.
- Modify `app/src/test/java/com/asuka/pocketpdf/ui/reader/ReaderViewModelTest.kt`
  - Reader progress, completion, error, and stop behavior.
- Modify `app/src/androidTest/java/com/asuka/pocketpdf/ui/reader/ReaderScreenTest.kt`
  - Collapse/reopen UI behavior.
- Modify `app/src/main/java/com/asuka/pocketpdf/ui/chat/ChatModels.kt`
  - Attach optional generation progress to the active assistant message.
- Modify `app/src/main/java/com/asuka/pocketpdf/ui/chat/ChatViewModel.kt`
  - Consume full-document progress for the active assistant message.
- Modify `app/src/main/java/com/asuka/pocketpdf/ui/chat/ChatActivity.kt`
  - Render progress before final answer tokens.
- Create `app/src/test/java/com/asuka/pocketpdf/ui/chat/ChatViewModelProgressTest.kt`
  - Full-document versus Top-K progress behavior.
- Modify `app/src/androidTest/java/com/asuka/pocketpdf/ui/chat/ChatActivityTest.kt`
  - Visible assistant progress state.

> Worktree note: the full-document summarizer and routing files are currently
> uncommitted user work. Execute this plan in the current workspace or a
> worktree that contains those changes. Never reset, replace, or independently
> recreate them from `HEAD`.

### Task 1: Define Full-Document Progress Events

**Files:**
- Create: `app/src/main/java/com/asuka/pocketpdf/domain/usecase/FullDocumentProgress.kt`
- Modify: `app/src/main/java/com/asuka/pocketpdf/domain/usecase/FullDocumentSummarizer.kt`
- Modify: `app/src/test/java/com/asuka/pocketpdf/domain/usecase/FullDocumentSummarizerTest.kt`

- [ ] **Step 1: Write failing progress-event tests**

Add tests using a mutable event list:

```kotlin
@Test
fun `multi batch summary reports ordered map and final stages`() = runTest {
    val events = mutableListOf<FullDocumentProgress>()
    coEvery { documentRepository.getChunks(DOC_ID) } returns chunks("aaaa", "bbbb")
    every { llmRepository.chatCompletionStream(any(), any(), any()) } returnsMany listOf(
        flowOf("map-1"),
        flowOf("map-2"),
        flowOf("merged"),
        flowOf("final"),
    )

    createSummarizer(batchCharBudget = 4).summarize(
        documentId = DOC_ID,
        model = MODEL,
        onProgress = events::add,
    ).toList()

    assertEquals(FullDocumentProgress.Preparing, events.first())
    assertTrue(events.contains(FullDocumentProgress.Mapping(1, 2)))
    assertTrue(events.contains(FullDocumentProgress.Mapping(2, 2)))
    assertTrue(events.any { it is FullDocumentProgress.Reducing })
    assertTrue(events.any { it is FullDocumentProgress.Finalizing })
    assertEquals(FullDocumentProgress.Completed, events.last())
}

@Test
fun `single batch summary skips map and reduce progress`() = runTest {
    val events = mutableListOf<FullDocumentProgress>()
    coEvery { documentRepository.getChunks(DOC_ID) } returns chunks("short")
    every { llmRepository.chatCompletionStream(any(), any(), any()) } returns flowOf("final")

    createSummarizer().summarize(
        documentId = DOC_ID,
        model = MODEL,
        onProgress = events::add,
    ).toList()

    assertEquals(
        listOf(
            FullDocumentProgress.Preparing,
            FullDocumentProgress.Finalizing,
            FullDocumentProgress.Completed,
        ),
        events,
    )
}
```

- [ ] **Step 2: Run the tests and verify RED**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.asuka.pocketpdf.domain.usecase.FullDocumentSummarizerTest"
```

Expected: compilation fails because `FullDocumentProgress` and `onProgress` do
not exist.

- [ ] **Step 3: Add the domain progress model**

Create:

```kotlin
package com.asuka.pocketpdf.domain.usecase

sealed interface FullDocumentProgress {
    data object Preparing : FullDocumentProgress
    data class Mapping(val completed: Int, val total: Int) : FullDocumentProgress
    data class Reducing(val completed: Int, val total: Int) : FullDocumentProgress
    data object Finalizing : FullDocumentProgress
    data object Completed : FullDocumentProgress
}
```

Extend `summarize`:

```kotlin
suspend fun summarize(
    documentId: Long,
    model: String,
    systemPrompt: String = "",
    question: String? = null,
    onProgress: (FullDocumentProgress) -> Unit = {},
): Flow<String> = flow {
    onProgress(FullDocumentProgress.Preparing)
    // existing chunk loading and batching
}
```

Emit `Mapping(i + 1, batches.size)` after each successful map request. Track
reduce requests with a small internal counter and emit `Reducing(completed,
knownTotal)` after each successful merge. Emit `Finalizing` immediately before
the final user-visible request and `Completed` only after non-empty final output
finishes.

For recursive reduce, increase `knownTotal` before executing a newly discovered
merge level. Do not emit progress on failed requests.

- [ ] **Step 4: Run the tests and verify GREEN**

Run the Task 1 command. Expected: all `FullDocumentSummarizerTest` tests pass.

- [ ] **Step 5: Commit the domain progress increment**

```powershell
git add app/src/main/java/com/asuka/pocketpdf/domain/usecase/FullDocumentProgress.kt `
  app/src/main/java/com/asuka/pocketpdf/domain/usecase/FullDocumentSummarizer.kt `
  app/src/test/java/com/asuka/pocketpdf/domain/usecase/FullDocumentSummarizerTest.kt
git commit -m "feat(ai): report full document generation stages"
```

### Task 2: Forward Progress Through Use Cases

**Files:**
- Modify: `app/src/main/java/com/asuka/pocketpdf/domain/usecase/SummarizeDocumentUseCase.kt`
- Modify: `app/src/main/java/com/asuka/pocketpdf/domain/usecase/AskDocumentUseCase.kt`
- Modify: `app/src/test/java/com/asuka/pocketpdf/domain/usecase/SummarizeDocumentUseCaseTest.kt`
- Modify: `app/src/test/java/com/asuka/pocketpdf/domain/usecase/SummarizeDocumentUseCaseCacheTest.kt`
- Modify: `app/src/test/java/com/asuka/pocketpdf/domain/usecase/AskDocumentUseCaseTest.kt`

- [ ] **Step 1: Write failing forwarding tests**

For summary:

```kotlin
@Test
fun `full summary forwards progress callback`() = runTest {
    val events = mutableListOf<FullDocumentProgress>()
    every {
        fullDocumentSummarizer.summarize(
            documentId = DOC_ID,
            model = MODEL,
            systemPrompt = "",
            question = null,
            onProgress = any(),
        )
    } answers {
        fifthArg<(FullDocumentProgress) -> Unit>()(FullDocumentProgress.Preparing)
        flowOf("done")
    }

    useCase(
        documentId = DOC_ID,
        model = MODEL,
        scope = SummaryScope.Full,
        onProgress = events::add,
    ).toList()

    assertEquals(listOf(FullDocumentProgress.Preparing), events)
}
```

For chat:

```kotlin
@Test
fun `top k query does not report full document progress`() = runTest {
    val events = mutableListOf<FullDocumentProgress>()
    coEvery { queryIntentRouter.route(any(), any()) } returns QueryIntent.TOP_K
    coEvery { retrieveChunks(any(), any(), any()) } returns emptyList()

    useCase(DOC_ID, "金额是多少", MODEL, onProgress = events::add).toList()

    assertTrue(events.isEmpty())
}
```

Also add a cache-hit assertion that the summary callback remains empty.

- [ ] **Step 2: Run the focused tests and verify RED**

```powershell
.\gradlew.bat testDebugUnitTest `
  --tests "com.asuka.pocketpdf.domain.usecase.SummarizeDocumentUseCaseTest" `
  --tests "com.asuka.pocketpdf.domain.usecase.SummarizeDocumentUseCaseCacheTest" `
  --tests "com.asuka.pocketpdf.domain.usecase.AskDocumentUseCaseTest"
```

Expected: compilation fails because use-case `onProgress` parameters are absent.

- [ ] **Step 3: Add optional callbacks and forward only on full-document paths**

Add to both operators:

```kotlin
onProgress: (FullDocumentProgress) -> Unit = {},
```

Pass it to `fullDocumentSummarizer.summarize(...)`. Do not call it in page
summary, cache-hit, intent-classification, or Top-K branches.

- [ ] **Step 4: Run the focused tests and verify GREEN**

Run the Task 2 command. Expected: all selected tests pass.

- [ ] **Step 5: Commit use-case forwarding**

```powershell
git add app/src/main/java/com/asuka/pocketpdf/domain/usecase/SummarizeDocumentUseCase.kt `
  app/src/main/java/com/asuka/pocketpdf/domain/usecase/AskDocumentUseCase.kt `
  app/src/test/java/com/asuka/pocketpdf/domain/usecase/SummarizeDocumentUseCaseTest.kt `
  app/src/test/java/com/asuka/pocketpdf/domain/usecase/SummarizeDocumentUseCaseCacheTest.kt `
  app/src/test/java/com/asuka/pocketpdf/domain/usecase/AskDocumentUseCaseTest.kt
git commit -m "feat(ai): forward document generation progress"
```

### Task 3: Build the Monotonic Progress and ETA Estimator

**Files:**
- Create: `app/src/main/java/com/asuka/pocketpdf/ui/ai/GenerationProgressEstimator.kt`
- Create: `app/src/test/java/com/asuka/pocketpdf/ui/ai/GenerationProgressEstimatorTest.kt`

- [ ] **Step 1: Write failing estimator tests**

```kotlin
@Test
fun `eta is unavailable before a completed work unit`() {
    val estimator = GenerationProgressEstimator()

    val display = estimator.update(FullDocumentProgress.Mapping(0, 4), nowMs = 1_000)

    assertNull(display.remainingSeconds)
    assertEquals("正在总结第 1 / 4 部分", display.stageLabel)
}

@Test
fun `completed units produce rounded approximate eta`() {
    val estimator = GenerationProgressEstimator()
    estimator.update(FullDocumentProgress.Preparing, nowMs = 0)
    estimator.update(FullDocumentProgress.Mapping(1, 4), nowMs = 20_000)

    val display = estimator.update(FullDocumentProgress.Mapping(2, 4), nowMs = 40_000)

    assertEquals(40, display.remainingSeconds)
    assertEquals("约剩 40秒", display.etaLabel)
}

@Test
fun `discovered reduce work never moves progress backwards`() {
    val estimator = GenerationProgressEstimator()
    val before = estimator.update(FullDocumentProgress.Reducing(1, 1), nowMs = 20_000)
    val after = estimator.update(FullDocumentProgress.Reducing(1, 3), nowMs = 21_000)

    assertTrue(after.fraction >= before.fraction)
}

@Test
fun `finalizing omits countdown when estimate is not credible`() {
    val display = GenerationProgressEstimator()
        .update(FullDocumentProgress.Finalizing, nowMs = 5_000)

    assertNull(display.remainingSeconds)
    assertEquals("正在撰写最终总结", display.stageLabel)
}
```

- [ ] **Step 2: Run the estimator tests and verify RED**

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.asuka.pocketpdf.ui.ai.GenerationProgressEstimatorTest"
```

Expected: compilation fails because the estimator and display model do not
exist.

- [ ] **Step 3: Implement the pure estimator**

Define:

```kotlin
data class GenerationProgressDisplay(
    val fraction: Float?,
    val stageLabel: String,
    val remainingSeconds: Long?,
) {
    val etaLabel: String?
        get() = remainingSeconds?.let(::formatApproximateRemaining)
}
```

`GenerationProgressEstimator.update(event, nowMs)` must:

- retain the largest emitted fraction,
- derive completed and pending known units from map/reduce events,
- record positive duration deltas between completed units,
- use a rolling mean of at most the latest four durations,
- round ETA to 5 seconds below one minute and 10 seconds above one minute,
- clamp negative or zero durations,
- return no ETA before one completed duration sample,
- return an indeterminate fraction for `Finalizing` only when no credible
  determinate fraction remains.

Formatting:

```kotlin
internal fun formatApproximateRemaining(seconds: Long): String =
    if (seconds < 60) "约剩 ${seconds}秒"
    else "约剩 ${seconds / 60}分${seconds % 60}秒"
```

- [ ] **Step 4: Run estimator tests and verify GREEN**

Run the Task 3 command. Expected: all estimator tests pass.

- [ ] **Step 5: Commit the estimator**

```powershell
git add app/src/main/java/com/asuka/pocketpdf/ui/ai/GenerationProgressEstimator.kt `
  app/src/test/java/com/asuka/pocketpdf/ui/ai/GenerationProgressEstimatorTest.kt
git commit -m "feat(ai): estimate generation progress and remaining time"
```

### Task 4: Project Progress Into Reader State

**Files:**
- Modify: `app/src/main/java/com/asuka/pocketpdf/ui/reader/ReaderUiState.kt`
- Modify: `app/src/main/java/com/asuka/pocketpdf/ui/reader/ReaderViewModel.kt`
- Modify: `app/src/test/java/com/asuka/pocketpdf/ui/reader/ReaderViewModelTest.kt`

- [ ] **Step 1: Write failing ReaderViewModel tests**

Add settings stubs and a controllable summary flow:

```kotlin
@Test
fun `full summary exposes stage progress before tokens`() = runTest(dispatcher) {
    loadIndexedDocument()
    every {
        summarizeDocument(
            documentId = DOC_ID,
            model = any(),
            scope = SummaryScope.Full,
            systemPrompt = any(),
            onProgress = any(),
        )
    } answers {
        arg<(FullDocumentProgress) -> Unit>(4)(
            FullDocumentProgress.Mapping(completed = 1, total = 4),
        )
        emptyFlow()
    }

    viewModel.summarizeFullDocument()
    runCurrent()

    val summary = (viewModel.uiState.value as ReaderUiState.Loaded).summaryState
    assertTrue(summary is SummaryState.Generating)
    assertEquals("正在总结第 2 / 4 部分", (summary as SummaryState.Generating).progress.stageLabel)
}

@Test
fun `stop summary cancels active job`() = runTest(dispatcher) {
    loadIndexedDocument()
    val cancelled = CompletableDeferred<Unit>()
    every { summarizeDocument(any(), any(), any(), any(), any(), any()) } returns flow {
        try {
            awaitCancellation()
        } finally {
            cancelled.complete(Unit)
        }
    }

    viewModel.summarizeFullDocument()
    runCurrent()
    viewModel.stopSummarizing()
    runCurrent()

    assertTrue(cancelled.isCompleted)
}
```

Retain existing tests for partial output on cancellation.

- [ ] **Step 2: Run ReaderViewModel tests and verify RED**

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.asuka.pocketpdf.ui.reader.ReaderViewModelTest"
```

Expected: compilation fails because progress-aware summary state and callback
are absent.

- [ ] **Step 3: Add progress-aware reader state**

Use:

```kotlin
sealed class SummaryState {
    data object Idle : SummaryState()
    data class Generating(
        val text: String = "",
        val progress: GenerationProgressDisplay,
    ) : SummaryState()
    data class Done(val fullText: String) : SummaryState()
    data class Error(val message: String) : SummaryState()
}
```

In `startSummary`, create a fresh estimator per job and pass:

```kotlin
onProgress = { event ->
    val display = estimator.update(event, elapsedRealtime())
    updateSummaryState(
        SummaryState.Generating(
            text = accumulated.toString(),
            progress = display,
        ),
    )
},
```

Inject a default clock function into `ReaderViewModel` only if needed for
deterministic tests:

```kotlin
private val elapsedRealtime: () -> Long = SystemClock::elapsedRealtime
```

Tokens update the `text` while preserving the latest display. Page summaries
may use a simple finalizing display and must not fabricate map stages.

- [ ] **Step 4: Run ReaderViewModel tests and verify GREEN**

Run the Task 4 command. Expected: all ReaderViewModel tests pass.

- [ ] **Step 5: Commit reader state projection**

```powershell
git add app/src/main/java/com/asuka/pocketpdf/ui/reader/ReaderUiState.kt `
  app/src/main/java/com/asuka/pocketpdf/ui/reader/ReaderViewModel.kt `
  app/src/test/java/com/asuka/pocketpdf/ui/reader/ReaderViewModelTest.kt
git commit -m "feat(reader): expose summary generation progress"
```

### Task 5: Make the Reader Sheet Collapsible Without Cancellation

**Files:**
- Modify: `app/src/main/java/com/asuka/pocketpdf/ui/reader/ReaderScreen.kt`
- Modify: `app/src/androidTest/java/com/asuka/pocketpdf/ui/reader/ReaderScreenTest.kt`

- [ ] **Step 1: Write failing Compose tests**

Add semantic tags or content descriptions and test:

```kotlin
@Test
fun collapseKeepsProgressVisibleAndAllowsReopen() {
    composeRule.setContent {
        PocketPDFTheme {
            ReaderScreen(
                title = "paper.pdf",
                pageState = readerPageState(),
                summaryState = SummaryState.Generating(
                    progress = GenerationProgressDisplay(
                        fraction = .4f,
                        stageLabel = "正在总结第 3 / 8 部分",
                        remainingSeconds = 80,
                    ),
                ),
                isIndexed = true,
                onBack = {},
                onPageRequested = {},
                onSummarizePage = {},
                onSummarizeDocument = {},
                onStopSummary = {},
                onOpenChat = {},
            )
        }
    }

    composeRule.onNodeWithText("收起并继续阅读").performClick()
    composeRule.onNodeWithText("正在总结第 3 / 8 部分").assertIsDisplayed()
    composeRule.onNodeWithContentDescription("打开 AI 总结").performClick()
    composeRule.onNodeWithText("停止生成").assertIsDisplayed()
}
```

Add a second test whose `onStopSummary` counter remains zero after collapse and
becomes one only after clicking `停止生成`.

- [ ] **Step 2: Run ReaderScreen device tests and verify RED**

```powershell
.\gradlew.bat connectedDebugAndroidTest `
  -Pandroid.testInstrumentationRunnerArguments.class=com.asuka.pocketpdf.ui.reader.ReaderScreenTest
```

Expected: tests fail because collapse and compact status controls do not exist.

- [ ] **Step 3: Implement collapse, status bar, and reopen**

Change `onDismiss` to:

```kotlin
onDismiss = { summarySheetVisible = false }
```

Do not call `onStopSummary`. Add an explicit `收起并继续阅读` button and rename
the active stop action to `停止生成`.

Render a compact `SummaryStatusBar` when:

```kotlin
summaryState !is SummaryState.Idle && !summarySheetVisible
```

The bar shows:

- active stage plus ETA,
- `全文总结已完成` for `Done`,
- `总结失败，点击查看` for `Error`.

Give the bar `contentDescription = "打开 AI 总结"` and place it above the
reader toolbar without consuming PDF gestures outside its own bounds.

Remove the unconditional behavior that reopens the sheet on every progress
state update. Auto-open only when transitioning from `Idle` to a newly started
summary, so user collapse remains respected.

- [ ] **Step 4: Run ReaderScreen tests and verify GREEN**

Run the Task 5 command. Expected: all `ReaderScreenTest` tests pass.

- [ ] **Step 5: Commit reader interaction**

```powershell
git add app/src/main/java/com/asuka/pocketpdf/ui/reader/ReaderScreen.kt `
  app/src/androidTest/java/com/asuka/pocketpdf/ui/reader/ReaderScreenTest.kt
git commit -m "feat(reader): collapse active summaries while reading"
```

### Task 6: Show Full-Document Progress in Chat

**Files:**
- Modify: `app/src/main/java/com/asuka/pocketpdf/ui/chat/ChatModels.kt`
- Modify: `app/src/main/java/com/asuka/pocketpdf/ui/chat/ChatViewModel.kt`
- Modify: `app/src/main/java/com/asuka/pocketpdf/ui/chat/ChatActivity.kt`
- Create: `app/src/test/java/com/asuka/pocketpdf/ui/chat/ChatViewModelProgressTest.kt`
- Modify: `app/src/androidTest/java/com/asuka/pocketpdf/ui/chat/ChatActivityTest.kt`

- [ ] **Step 1: Write failing ChatViewModel progress tests**

```kotlin
@Test
fun `full document progress is attached to active assistant message`() = runTest(dispatcher) {
    every {
        askDocument(any(), any(), any(), any(), any(), any())
    } answers {
        arg<(FullDocumentProgress) -> Unit>(5)(
            FullDocumentProgress.Mapping(completed = 0, total = 3),
        )
        emptyFlow()
    }

    loadAndSend("总结全文")
    runCurrent()

    val assistant = viewModel.uiState.value.messages.last()
    assertEquals("正在总结第 1 / 3 部分", assistant.progress?.stageLabel)
}

@Test
fun `ordinary token stream works without progress events`() = runTest(dispatcher) {
    every { askDocument(any(), any(), any(), any(), any(), any()) } returns flowOf("答案")

    loadAndSend("金额是多少")
    advanceUntilIdle()

    val assistant = viewModel.uiState.value.messages.last()
    assertNull(assistant.progress)
    assertEquals("答案", assistant.content)
}
```

- [ ] **Step 2: Run chat unit tests and verify RED**

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.asuka.pocketpdf.ui.chat.ChatViewModelProgressTest"
```

Expected: compilation fails because messages do not carry progress and
`AskDocumentUseCase` is not called with a callback.

- [ ] **Step 3: Add progress to the active assistant message**

Extend:

```kotlin
data class ChatDisplayMessage(
    val id: Long,
    val role: ChatRole,
    val content: String,
    val isStreaming: Boolean = false,
    val progress: GenerationProgressDisplay? = null,
)
```

Create a fresh estimator in `sendMessage`. The progress callback updates only
the matching `aiMsgId`. Clear `progress` after successful completion,
cancellation, or failure. When final tokens arrive, retain a compact
`正在撰写最终回答` display until completion.

- [ ] **Step 4: Render progress in `ChatBubble`**

Before nonblank content:

```kotlin
message.progress?.let { progress ->
    if (progress.fraction != null) {
        LinearProgressIndicator(
            progress = { progress.fraction },
            modifier = Modifier.fillMaxWidth(),
        )
    } else {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    }
    Text(progress.stageLabel)
    progress.etaLabel?.let { Text(it) }
}
```

Use the indeterminate overload when `fraction == null`. Do not add progress UI
to user messages or completed historical messages.

- [ ] **Step 5: Run chat unit tests and verify GREEN**

Run the Task 6 unit-test command. Expected: all progress tests pass.

- [ ] **Step 6: Add and run the Compose assertion**

In `ChatActivityTest`, render an assistant message with progress and assert
`正在总结第 1 / 3 部分` and `约剩 40秒` are displayed.

Run:

```powershell
.\gradlew.bat connectedDebugAndroidTest `
  -Pandroid.testInstrumentationRunnerArguments.class=com.asuka.pocketpdf.ui.chat.ChatActivityTest
```

Expected: all `ChatActivityTest` tests pass.

- [ ] **Step 7: Commit chat progress**

```powershell
git add app/src/main/java/com/asuka/pocketpdf/ui/chat/ChatModels.kt `
  app/src/main/java/com/asuka/pocketpdf/ui/chat/ChatViewModel.kt `
  app/src/main/java/com/asuka/pocketpdf/ui/chat/ChatActivity.kt `
  app/src/test/java/com/asuka/pocketpdf/ui/chat/ChatViewModelProgressTest.kt `
  app/src/androidTest/java/com/asuka/pocketpdf/ui/chat/ChatActivityTest.kt
git commit -m "feat(chat): display full document generation progress"
```

### Task 7: Regression and Device Acceptance

**Files:**
- Modify only files required by failures found in this task.

- [ ] **Step 1: Run all affected unit tests**

```powershell
.\gradlew.bat testDebugUnitTest `
  --tests "com.asuka.pocketpdf.domain.usecase.FullDocumentSummarizerTest" `
  --tests "com.asuka.pocketpdf.domain.usecase.SummarizeDocumentUseCaseTest" `
  --tests "com.asuka.pocketpdf.domain.usecase.SummarizeDocumentUseCaseCacheTest" `
  --tests "com.asuka.pocketpdf.domain.usecase.AskDocumentUseCaseTest" `
  --tests "com.asuka.pocketpdf.ui.ai.GenerationProgressEstimatorTest" `
  --tests "com.asuka.pocketpdf.ui.reader.ReaderViewModelTest" `
  --tests "com.asuka.pocketpdf.ui.chat.ChatViewModelProgressTest"
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Run the full debug unit suite**

```powershell
.\gradlew.bat testDebugUnitTest
```

Expected: BUILD SUCCESSFUL with no new failures.

- [ ] **Step 3: Run focused device tests**

```powershell
.\gradlew.bat connectedDebugAndroidTest `
  -Pandroid.testInstrumentationRunnerArguments.class=com.asuka.pocketpdf.ui.reader.ReaderScreenTest,com.asuka.pocketpdf.ui.chat.ChatActivityTest
```

Expected: both test classes pass.

- [ ] **Step 4: Perform manual reader acceptance**

On a long indexed PDF:

1. Start `总结全文`.
2. Confirm a stage and `正在估算` appear.
3. Wait for one batch and confirm an approximate ETA appears.
4. Collapse the sheet and turn pages, zoom, search, and annotate.
5. Tap the compact status bar and confirm the same task reopens.
6. Collapse again, wait for completion, and reopen the final summary.
7. Start again and click `停止生成`; confirm the task stops.

Expected: collapse never cancels, explicit stop always cancels, and progress
never moves backwards.

- [ ] **Step 5: Perform manual chat acceptance**

1. Ask `总结全文` and verify batch progress appears before final tokens.
2. Ask a focused question such as `文中的金额是多少`.
3. Confirm focused chat streams normally without map-reduce progress.

- [ ] **Step 6: Inspect logs for crashes and cancellation leaks**

```powershell
adb logcat -d | Select-String -Pattern `
  'FATAL EXCEPTION|ANR in com.asuka.pocketpdf|JobCancellationException|FullDocSummarizer'
```

Expected: no PocketPDF crash or ANR. Expected user-triggered coroutine
cancellation may be logged at debug level but must not surface as an error.

- [ ] **Step 7: Keep regression fixes in their owning task**

Run `git status --short`. If verification required a code fix, return to the
task that owns that file, rerun its focused RED/GREEN test, and amend that
task's commit. When no fix was required, leave the verified task commits
unchanged.
