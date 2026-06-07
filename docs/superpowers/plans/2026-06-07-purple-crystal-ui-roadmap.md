# Purple Crystal UI Redesign Implementation Roadmap

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace PocketPDF's mixed XML/default-Material UI with the approved Purple Crystal Compose experience while preserving current domain behavior and PDF gestures.

**Architecture:** Keep the existing Activity navigation contracts and ViewModels. Each Activity becomes a Compose host; the reader embeds the existing `PdfPageView` through `AndroidView`. Shared design tokens and focused components are established first, then screens migrate in independently verifiable phases.

**Tech Stack:** Kotlin 2.0, Jetpack Compose Material 3, Activity Compose, Hilt, StateFlow, Android `PdfRenderer`, Robolectric/JUnit, Compose UI testing, Espresso.

---

## Phase Documents

Implement these plans in order:

1. [`2026-06-07-purple-crystal-foundation-plan.md`](2026-06-07-purple-crystal-foundation-plan.md)
2. [`2026-06-07-purple-crystal-library-plan.md`](2026-06-07-purple-crystal-library-plan.md)
3. [`2026-06-07-purple-crystal-reader-ai-plan.md`](2026-06-07-purple-crystal-reader-ai-plan.md)
4. [`2026-06-07-purple-crystal-settings-diagnostics-plan.md`](2026-06-07-purple-crystal-settings-diagnostics-plan.md)

## Cross-Phase Rules

- Do not alter domain-layer interfaces for visual concerns.
- Do not add search, bookmarks, annotations, folders, or cross-document chat.
- Preserve Activity intent contracts.
- Delete an XML layout only in the phase that replaces every reference to it.
- Every new stateful screen must provide light, dark, its supported loading/error states, and a large-font preview.
- Every compact visible button keeps a minimum `48.dp` touch target.
- Crystal translucency is allowed only on floating navigation, toolbars, import action, and AI panels.
- Run `.\gradlew.bat testDebugUnitTest lintDebug` before each phase commit.
- Run connected tests when an emulator/device is available:

```powershell
.\gradlew.bat connectedDebugAndroidTest
```

Expected: all instrumentation tests pass.

## Final Acceptance

- Library, reader, AI, settings, and diagnostics share one design system.
- PDF zoom/pan and citation return behavior remain intact.
- Each migrated Activity is edge-to-edge without double-applied insets.
- No removed XML/ViewBinding symbol remains in source.
- `.\gradlew.bat testDebugUnitTest lintDebug assembleDebug` succeeds.
