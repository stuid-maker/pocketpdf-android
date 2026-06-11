# AI Generation Progress and Collapsible Summary Design

## Problem

Full-document summaries and document-wide chat questions may require several
sequential LLM calls. PocketPDF currently exposes no useful progress during the
internal map-reduce work, so users may see an indefinite spinner for a long
time.

The reader also couples dismissing the summary sheet with cancellation:
closing the sheet calls `stopSummarizing()`. This prevents users from returning
to the PDF while a full summary continues.

## Goals

- Show meaningful progress for full-document summaries and document-wide chat.
- Show a clearly approximate remaining-time estimate once enough timing data is
  available.
- Allow the user to collapse the reader summary sheet and continue reading the
  current PDF without cancelling generation.
- Keep explicit cancellation available and visually distinct from collapsing.
- Let the user reopen the active or completed summary from the reader.
- Reuse one progress model for the summary button and full-document chat route.

## Scope

Generation continues only while the current reader or chat activity remains
alive. This design does not add cross-activity task persistence, background
execution after leaving the reader, lock-screen execution, notifications, or
process-death recovery.

Focused Top-K chat questions retain their current streaming behavior and do not
show map-reduce progress.

## Selected Approach

Use stage-aware progress reported by `FullDocumentSummarizer`.

This is preferred over a timer-only progress bar because map-reduce already has
observable units of work. A fixed timer would become misleading across
different document lengths, models, and devices. An application-wide task
manager or foreground service is unnecessary for the selected scope.

## Domain Progress Model

Add a domain model representing observable full-document work:

- `Preparing`: chunks are loading and batches are being calculated.
- `Mapping(completed, total)`: one internal summary is generated per document
  batch.
- `Reducing(completed, total)`: intermediate summaries are being merged. The
  total may be revised when another recursive reduction level is discovered.
- `Finalizing`: the final user-visible answer is being generated.
- `Completed`: the final stream completed successfully.

`FullDocumentSummarizer.summarize` accepts a progress callback or equivalent
progress sink in addition to returning the final token `Flow`. Intermediate
map and reduce text remains internal and is never exposed to the UI.

Small documents that require only one LLM request move from `Preparing`
directly to `Finalizing`. Cache hits complete immediately and do not animate
through invented work.

## Progress Calculation

Progress is based on completed observable work rather than elapsed time alone.

- Preparation occupies the initial portion of the indicator.
- Map work advances by completed batches.
- Reduce work advances by completed merge requests.
- Finalizing occupies the last portion and remains visibly active until the
  final token stream finishes.

The UI must not claim an exact final percentage while the model is streaming,
because the provider does not expose the total output token count. It may show
the final stage with an indeterminate animated segment instead.

Recursive reduction can discover additional merge work. When this happens, the
displayed fraction must remain monotonic: newly discovered work may slow future
advancement but must not move the progress bar backwards.

## Remaining-Time Estimate

Record the duration of each completed LLM work unit within the current
generation. Estimate remaining time from the rolling average duration and the
known pending map/reduce units.

- Before the first useful sample, display `正在估算`.
- After samples exist, display `约剩 1分20秒` or an equivalent rounded value.
- Smooth abrupt changes so the estimate does not visibly jump on every token.
- Label every estimate as approximate.
- During final streaming, use the recent request-duration history only as a
  coarse estimate. If it is not credible, show `正在撰写最终总结` without a
  countdown.

The estimator is session-local. It does not persist performance history or
attempt to predict provider queue delays across app launches.

## Reader Interaction

### Expanded Summary Sheet

For active full-document generation, the sheet shows:

- Current stage, such as `正在总结第 3 / 8 部分`.
- Determinate progress for known map/reduce work.
- Approximate remaining time or `正在估算`.
- A `收起并继续阅读` action.
- A separate `停止生成` action.

Closing the sheet by swipe, back gesture, tapping outside, or the collapse
action only hides the sheet. It does not invoke `stopSummarizing()`.

### Collapsed Reader State

While generation is active and the sheet is hidden, show a compact status bar
above the reader toolbar. It contains the current stage and ETA when available.
Tapping it reopens the sheet.

The status bar must not block PDF paging, zooming, search, or annotation
controls.

When generation completes while collapsed, the bar changes to
`全文总结已完成`. Tapping it opens the completed summary. Errors similarly
remain reopenable so the user can inspect the message and retry.

Starting another summary remains disabled while one is active. Explicit stop
cancels the job and preserves the existing partial-output behavior.

## Chat Interaction

`AskDocumentUseCase` forwards full-document progress only when
`QueryIntent.FULL_DOCUMENT` is selected. `ChatViewModel` associates progress
with the active assistant placeholder.

Before visible answer tokens arrive, the assistant item shows the current
stage, progress, and approximate remaining time. Once final tokens begin, the
same item transitions into the normal streaming answer while retaining a
compact `正在撰写最终回答` status.

Focused `TOP_K` questions do not display batch progress and keep their current
response path.

Leaving `ChatActivity` may cancel its generation under the existing
ViewModel-scoped lifecycle; cross-page persistence is outside this design.

## State Ownership

`FullDocumentSummarizer` owns knowledge of work stages and emits domain
progress. It does not format user-facing strings or calculate Compose layout.

`ReaderViewModel` and `ChatViewModel` own timing samples, ETA calculation, and
UI-state projection. A small pure progress/ETA helper should be shared when it
prevents divergent calculations between the two screens.

Compose owns only presentation and sheet visibility. Sheet visibility must not
own or control the generation job.

## Error and Cancellation Behavior

- Provider, map, reduce, or final-stream failures preserve the existing real
  error and stop the progress indicator.
- Failed and cancelled work is never written to the summary cache.
- Explicit stop cancels the active request chain.
- Collapsing never cancels.
- Activity destruction retains the existing ViewModel cancellation behavior.
- Empty intermediate or final model output remains an error.
- A cache hit produces the completed summary without showing a stale ETA.

## Testing

### Unit Tests

- Progress events preserve map batch order and counts.
- Recursive reduce progress remains monotonic when more work is discovered.
- Small documents report preparation, finalization, and completion without fake
  map work.
- Cache hits do not start progress timing.
- ETA displays `正在估算` before a timing sample exists.
- ETA uses completed work-unit durations and rounded approximate output.
- Explicit stop cancels generation.
- Collapsing the sheet does not call the stop callback.
- Reader state retains active, completed, and failed summaries while collapsed.
- Full-document chat receives progress; Top-K chat does not.
- Failed or cancelled generation does not populate the cache.

### Compose and Device Tests

- Start a long full summary, collapse the sheet, and continue paging the PDF.
- Verify the compact status bar updates and reopens the sheet.
- Verify `停止生成` remains explicit and cancels.
- Let a collapsed summary finish and reopen the completed result.
- Trigger a provider failure and reopen the error state.
- Ask a full-document chat question and observe stage progress before tokens.
- Ask a focused question and verify the existing chat behavior is unchanged.

## Success Criteria

- Users can continue reading the current PDF while its full summary runs.
- Dismissing the summary sheet no longer cancels generation.
- Full-document work displays a truthful stage and monotonic progress.
- Remaining time is always marked approximate and omitted when not credible.
- The completed or failed result remains discoverable after the sheet is
  collapsed.
- Existing Top-K chat, summary caching, and explicit cancellation continue to
  behave correctly.
