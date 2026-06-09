# Full-Document Summary and Semantic Routing Design

## Problem

PocketPDF currently treats a full-document summary as a semantic retrieval query
for `"全文核心内容"` with `topK = 5`. Document chat uses the same Top-K path for
every question. As a result, both the summary button and chat requests such as
“总结全文” send only five fragments to the LLM. The model correctly observes that
the supplied context is fragmented and cannot represent the whole document.

The summary cache is keyed only by document and scope. It can therefore return an
old Top-K-based summary after the model or summary algorithm changes.

## Goals

- The “总结全文” button must cover every indexed text chunk in document order.
- Document chat must route global analysis requests to full-document processing
  and focused lookup requests to semantic Top-K retrieval.
- Clear requests should be routed locally without an extra network call.
- Ambiguous requests may use the configured LLM as a lightweight classifier.
- Long documents must not rely on a provider-specific context-window size.
- Cached summaries must not reuse output from an old algorithm or another model.
- Existing page summaries and focused document Q&A must continue to work.

## Non-Goals

- OCR improvements and PDF text extraction changes.
- Provider-specific tokenizers or hard-coded DeepSeek/Qwen context windows.
- A new chat UI or visible routing controls.
- Sending the entire raw document in a single LLM request.

## Selected Approach

Use hybrid intent routing for chat and hierarchical map-reduce summarization for
full-document operations.

This is preferred over always calling an LLM classifier because it avoids an
extra request for obvious intents. It is preferred over increasing Top-K because
Top-K cannot guarantee document-wide coverage. It is preferred over one large
prompt because documents and provider context windows vary.

## Architecture

### 1. Query Intent Router

Add a domain-level router that returns one of:

- `FULL_DOCUMENT`: summaries, outlines, themes, argument structure, overall
  conclusions, comparisons spanning the document, or other global analysis.
- `TOP_K`: dates, names, amounts, clauses, definitions, locations, and other
  focused fact lookup.

Routing has two stages:

1. Deterministic local rules classify high-confidence Chinese and English
   phrasing. Full-document indicators include concepts such as “全文总结”,
   “概括整篇”, “主要观点”, “整体结构”, “核心思想”, `summarize the document`,
   and `overall`. Focused lookup indicators include direct field questions such
   as “多少”, “哪一页”, “日期”, “金额”, “谁”, “在哪里”, and `what is`.
2. If neither side is high confidence, call the configured model with a short
   classification prompt. The response must be reduced to `FULL_DOCUMENT` or
   `TOP_K`; malformed or failed classification falls back to `TOP_K`.

Local rules must be conservative. A focused keyword does not override explicit
global wording. For example, “总结全文中所有金额相关结论” is global.

### 2. Full-Document Summarizer

Create one shared full-document summarization component used by:

- `SummarizeDocumentUseCase` for `SummaryScope.Full`.
- `AskDocumentUseCase` when the router returns `FULL_DOCUMENT`.

The component performs these steps:

1. Load all chunks with `DocumentRepository.getChunks(documentId)`.
2. Remove blank chunks and sort by `chunkIndex`, with page and ID as stable
   fallback ordering.
3. Group adjacent chunks into bounded batches using a character budget. The
   initial implementation uses a conservative provider-independent budget and
   never splits a stored chunk unless that single chunk exceeds the budget.
4. Summarize each batch internally using its page range and original order.
5. Merge the batch summaries in order.
6. If the merge input is still over budget, recursively group and merge until
   one final summary remains.
7. Stream only the final merge response to the UI. Intermediate map summaries
   are accumulated internally and are not displayed as separate answers.

For a small document that fits in one batch, use a single final summary request
without an unnecessary map-reduce round trip.

The final prompt asks for a coherent answer to the user's global request. The
summary button supplies the standard full-summary instruction. Chat supplies the
original user question so requests such as “分析全文的论证结构” receive an
appropriate global analysis rather than a generic summary.

### 3. Focused Top-K Path

When routing returns `TOP_K`, preserve the current semantic retrieval flow:

1. Retrieve the five most relevant chunks.
2. Include page markers and similarity metadata.
3. Stream the model answer.

This keeps focused questions fast and inexpensive.

### 4. Cache Semantics

Full summaries are cached with all output-affecting identity:

- document ID
- summary scope
- summary algorithm version
- configured model name
- effective system prompt identity

The new algorithm version ensures existing fragment-based summaries are ignored
after upgrade. Page summaries use the same versioned identity so changing the
model or system prompt cannot return stale output.

Chat-generated global analyses are not stored in the summary cache because the
user question may request a different type of analysis. A plain chat request
equivalent to “总结全文” may reuse the standard full-summary cache only when its
requested output matches the summary-button instruction exactly.

## Data Flow

### Summary Button

`ReaderViewModel` -> `SummarizeDocumentUseCase` -> versioned cache -> shared
full-document summarizer -> all ordered chunks -> batch summaries -> recursive
merge -> final streamed answer -> cache.

### Document Chat

`ChatViewModel` -> `AskDocumentUseCase` -> intent router:

- `TOP_K` -> current retrieval and RAG prompt.
- `FULL_DOCUMENT` -> shared full-document summarizer using the original question.

## Error and Cancellation Behavior

- No indexed chunks: retain the current user-facing “未索引或无文本内容” error.
- Classifier timeout, malformed output, or provider failure: fall back to Top-K
  unless the local rules already selected full-document processing.
- Failure during a map or reduce request: stop processing and surface the real
  provider error; never cache partial output.
- User cancellation: cancel the active classifier/map/reduce request chain.
- Only a completed final result is written to cache.
- Empty intermediate model output is treated as an error rather than silently
  dropping part of the document.

## Testing

### Unit Tests

- Explicit global queries route locally to `FULL_DOCUMENT`.
- Explicit field lookups route locally to `TOP_K`.
- Ambiguous queries invoke the classifier.
- Invalid classifier output and classifier failure fall back to `TOP_K`.
- Explicit global wording wins over focused keywords.
- Full summarization loads every chunk and preserves document order.
- Batch boundaries respect the configured budget.
- Long merge inputs recurse until one final result remains.
- Intermediate summaries are not emitted to the caller.
- Small documents use one request.
- Full chat queries bypass semantic retrieval.
- Focused chat queries retain Top-K retrieval.
- Cache keys vary by algorithm version, model, scope, and system prompt.
- Failed or cancelled generation does not populate the cache.

### Device Acceptance Tests

Use the same multi-page PDF with LM Studio, DeepSeek, and Qwen:

1. Tap “总结全文” and verify the result mentions material from the beginning,
   middle, and end of the document.
2. Ask “总结全文” in chat and verify it uses document-wide content.
3. Ask for the document's overall structure or argument and verify global
   routing.
4. Ask for a specific date, amount, person, or clause and verify a fast,
   page-cited Top-K answer.
5. Ask an ambiguous analytical question and verify it completes through model
   classification.
6. Switch models and verify the previous model's cached summary is not reused.
7. Cancel a long summary and retry; verify no partial result is returned as a
   completed cached summary.
8. Reopen the document and verify a completed summary can be loaded from cache.

## Success Criteria

- Full-document operations process all nonblank indexed chunks exactly once at
  the map stage.
- Full summaries no longer claim that only fragmented context was supplied.
- Focused chat questions retain existing response speed and page citations.
- Obvious chat intents require no classifier call.
- Stale Top-K summaries are automatically bypassed after the upgrade.
