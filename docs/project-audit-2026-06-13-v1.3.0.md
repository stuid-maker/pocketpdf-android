# PocketPDF v1.3.0 Release Audit

Audit date: 2026-06-13
Release target: `v1.3.0`

## Result

v1.3.0 is ready for release. All tests pass, no security issues found, code quality is consistent with the existing codebase. Three minor observations documented below (none blocking).

## Changes Summary

24 files modified, 7 files created. +1,018 / -418 lines (net +600).

### Architecture Changes

| Component | Change | Risk |
|---|---|---|
| Room schema v6→v7 | New `conversations` table, `conversationId` FK on `chat_messages` | Medium — manual migration |
| `ConversationEntity`/`ConversationDao`/`Conversation` | New data layer for multi-session chat | Low |
| `ChatRepository` interface | All methods now conversation-scoped (breaking API change) | Low — all call sites updated |
| `FullDocumentSummarizer` | `testInstance()` hook removed, `FullDocumentSummarizerConfig` injected via Hilt | Low — tests updated |
| `PdfiumDocumentSession.close()` | `runBlocking` replaced with `CoroutineScope(IO).launch` | Low — well-tested async boundary |
| `ChatActivity` | ModalNavigationDrawer for conversation list, rename dialog, overflow menu | Low — pure UI addition |
| `ChatViewModel` | Conversation lifecycle (switch/create/rename/delete/clear) | Low — new state management |

### Manual Migration v6→v7 (Key Risk Area)

```sql
-- Step 1: Create conversations table with CASCADE FK
-- Step 2: Backfill one "对话 1" per document with existing chat history
-- Step 3: Rebuild chat_messages with conversationId FK
-- Step 4: Drop old table, rename, create indexes
```

**Verified:** Migration test (`migrate6To7BackfillsDefaultConversation`) passes on API 34 emulator. Both message CASCADE paths (conversation delete → messages, document delete → conversations → messages) have DAO-level tests.

### Findings

| # | Finding | Severity | Resolution |
|---|---|---|---|
| F1 | `build.gradle.kts` diff contains LF→CRLF noise across ~200 lines — only semantic changes are `versionCode 3→4`, `versionName 1.2.0→1.3.0` | Info | Accept as-is; Windows git crlf config |
| F2 | `ChatRepositoryImpl.saveMessage()` returned silently when conversation not found | Fixed | Changed `?: return` to `checkNotNull` with error message; test updated |
| F3 | `ChatViewModel.load()` crashed process when launched with non-existent document ID (FK constraint on `createConversation`) | Fixed | Added try-catch in conversation init block; `activityLaunchesWithDocumentIdZero` now passes |

### Verification

| Check | Result |
|---|---|
| JVM tests | All passed |
| Android instrumentation | 32/32 passed on HBN-AL80 (API 36) |
| Lint | 0 errors |
| Security scan | No hardcoded secrets, eval, shell injection, or debug debris found |
| Code hygiene | No `println`, `printStackTrace`, `TODO`, or `FIXME` in production code |
| API consistency | All `ChatRepository` call sites updated; no stale `documentId`-scoped references remain |
| Read-only access | `ReaderActivity` launches `ChatActivity` without `conversationId` → auto-resolves to most recent conversation. Verified correct. |

### New Files

| File | Lines | Purpose |
|---|---|---|
| `ConversationEntity.kt` | 30 | Room entity with CASCADE FK to documents |
| `ConversationDao.kt` | 35 | Flow-observable query + CRUD |
| `Conversation.kt` | 14 | Domain model (data class) |
| `FullDocumentSummarizerConfig.kt` | 18 | Injectable config replacing test hooks |
| `ConversationDaoTest.kt` | 132 | 5 tests: ordering, persistence, count, CASCADE delete (messages + documents) |
| `schema/7.json` | 427 | Room exported schema for v7 |

### v1.2.0 Residual Risk Status

| Risk | v1.3.0 Status |
|---|---|
| CI emulator matrix | Unchanged — still a local release gate |
| OCR for scanned PDFs | Unchanged |
| Per-token Compose state updates | Unchanged |
| Broad R8 keep rules | Unchanged |
| Application-layer encryption | Unchanged |

### Recommendation

Commit with message `chore(release): audit and prepare v1.3.0`, tag as `v1.3.0`, push.

Release asset: signed APK with `versionCode 4`, `versionName 1.3.0`.
