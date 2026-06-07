# Citation Navigation Design

## Goal

Fix citation navigation so tapping a page citation in document chat returns to
the existing reader and opens the cited page. It must not create another
`ReaderActivity`, and returning from chat must not reopen a previously dismissed
summary sheet.

## Design

`ReaderActivity` launches `ChatActivity` for a result. When a citation is
clicked, `ChatActivity` places the zero-based page index in its result intent
and finishes. `ReaderActivity` receives that result, resets the summary UI state,
dismisses any summary sheet, and renders the requested page in its existing PDF
renderer.

Closing chat without selecting a citation returns `RESULT_CANCELED` and leaves
the current reader page unchanged.

The result-extra key and intent construction remain owned by `ChatActivity`.
The reader does not use task flags or manifest launch modes, avoiding behavior
changes for normal library-to-reader navigation.

## State Handling

`ReaderViewModel.stopSummarizing()` remains the single operation that cancels an
active summary and returns `summaryState` to `Idle`. Before launching chat, the
reader calls it and dismisses the sheet. This prevents a completed summary from
being re-rendered when the reader resumes.

When a citation result arrives, the reader validates it against the current PDF
page count through the existing `renderPage` clamping behavior.

## Tests

- Unit/instrumentation coverage verifies that citation result intents preserve
  the selected zero-based page index.
- Reader navigation coverage verifies that a returned page is rendered in the
  existing activity and that a canceled chat result does not change pages.
- Existing reader, chat, and ViewModel tests continue to pass.
- Real-device regression verifies the stack is
  `LibraryActivity -> ReaderActivity -> ChatActivity`, then returns directly to
  the same `ReaderActivity` at the cited page with no summary sheet visible.

## Non-Goals

- Changing citation parsing or visual styling.
- Persisting the reader page across process death.
- Refactoring chat UI architecture.
