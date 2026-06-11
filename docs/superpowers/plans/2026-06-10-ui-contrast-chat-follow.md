# UI Contrast and Chat Follow Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Improve requested control contrast across PocketPDF themes and make chat submission dismiss the keyboard and follow the AI response without overriding intentional upward scrolling.

**Architecture:** Keep visual changes local to the existing Compose screens and shared compact button. Add one pure chat-follow decision helper so auto-scroll behavior is deterministic and unit-testable. Do not modify AI routing, retrieval, summary generation, persistence, or provider settings.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, Coroutines, JUnit 4, Android instrumented Compose tests, Gradle, ADB.

---

## File Map

- Modify `app/src/main/java/com/asuka/pocketpdf/ui/library/LibraryScreen.kt`
  - Strengthen the workspace card and search field.
  - Make import content white.
  - Brighten dark-theme delete feedback.
- Modify `app/src/main/java/com/asuka/pocketpdf/ui/reader/ReaderScreen.kt`
  - Make reader chrome icons white.
  - Give disabled navigation icons translucent white.
  - Give the AI button a bright violet fill.
- Modify `app/src/main/java/com/asuka/pocketpdf/ui/components/PocketButtons.kt`
  - Allow callers to provide compact-button container/content colors.
- Modify `app/src/main/java/com/asuka/pocketpdf/ui/settings/SettingsScreen.kt`
  - Make the Save label white without changing unrelated compact buttons.
- Modify `app/src/main/java/com/asuka/pocketpdf/ui/chat/ChatActivity.kt`
  - Clear focus and hide the keyboard after send.
  - Follow the new AI placeholder and streaming answer only near the bottom.
- Create `app/src/main/java/com/asuka/pocketpdf/ui/chat/ChatFollowPolicy.kt`
  - Pure decision helper for auto-follow.
- Create `app/src/test/java/com/asuka/pocketpdf/ui/chat/ChatFollowPolicyTest.kt`
  - Unit tests for bottom-follow behavior.
- Modify `app/src/androidTest/java/com/asuka/pocketpdf/ui/library/LibraryScreenTest.kt`
  - Assert import and search controls remain discoverable.
- Modify `app/src/androidTest/java/com/asuka/pocketpdf/ui/reader/ReaderScreenTest.kt`
  - Assert reader AI and navigation semantics remain available.
- Modify `app/src/androidTest/java/com/asuka/pocketpdf/ui/chat/ChatActivityTest.kt`
  - Assert send clears input and presents the assistant placeholder.

### Task 1: Add Chat Follow Policy

**Files:**
- Create: `app/src/main/java/com/asuka/pocketpdf/ui/chat/ChatFollowPolicy.kt`
- Create: `app/src/test/java/com/asuka/pocketpdf/ui/chat/ChatFollowPolicyTest.kt`

- [ ] **Step 1: Write failing policy tests**

```kotlin
package com.asuka.pocketpdf.ui.chat

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatFollowPolicyTest {
    @Test
    fun `new message follows even when historical list just loaded`() {
        assertTrue(
            shouldFollowLatest(
                messageCountChanged = true,
                isNearBottom = false,
                isStreaming = false,
            )
        )
    }

    @Test
    fun `streaming follows while viewport is near bottom`() {
        assertTrue(
            shouldFollowLatest(
                messageCountChanged = false,
                isNearBottom = true,
                isStreaming = true,
            )
        )
    }

    @Test
    fun `streaming does not override intentional upward scroll`() {
        assertFalse(
            shouldFollowLatest(
                messageCountChanged = false,
                isNearBottom = false,
                isStreaming = true,
            )
        )
    }
}
```

- [ ] **Step 2: Run the test and verify failure**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.asuka.pocketpdf.ui.chat.ChatFollowPolicyTest"
```

Expected: compilation fails because `shouldFollowLatest` does not exist.

- [ ] **Step 3: Implement the pure policy**

```kotlin
package com.asuka.pocketpdf.ui.chat

internal fun shouldFollowLatest(
    messageCountChanged: Boolean,
    isNearBottom: Boolean,
    isStreaming: Boolean,
): Boolean = messageCountChanged || (isStreaming && isNearBottom)
```

- [ ] **Step 4: Run the focused test**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.asuka.pocketpdf.ui.chat.ChatFollowPolicyTest"
```

Expected: all three tests pass.

### Task 2: Implement Chat Focus, Keyboard, and Scroll Behavior

**Files:**
- Modify: `app/src/main/java/com/asuka/pocketpdf/ui/chat/ChatActivity.kt`
- Modify: `app/src/androidTest/java/com/asuka/pocketpdf/ui/chat/ChatActivityTest.kt`

- [ ] **Step 1: Extend the chat UI test**

Add a test that enters a question, taps Send, and asserts the input becomes empty
and the last assistant message/streaming placeholder is displayed. Use the
existing test setup and semantics rather than starting a real LLM request.

```kotlin
composeRule.onNodeWithText("输入问题…").performTextInput("总结全文")
composeRule.onNodeWithContentDescription("发送").performClick()
composeRule.onNodeWithText("总结全文").assertExists()
composeRule.onNodeWithText("输入问题…").assertExists()
```

Stub the ViewModel/use-case stream in the existing test fixture so the assistant
placeholder is stable and no network request occurs.

- [ ] **Step 2: Run the chat instrumented test and verify failure**

Run with the connected device:

```powershell
.\gradlew.bat connectedDebugAndroidTest `
  -Pandroid.testInstrumentationRunnerArguments.class=com.asuka.pocketpdf.ui.chat.ChatActivityTest
```

Expected: the new keyboard/focus or placeholder assertion fails before the UI
behavior is implemented.

- [ ] **Step 3: Add focus and keyboard dismissal**

In `ChatScreen`, obtain:

```kotlin
val focusManager = LocalFocusManager.current
val keyboardController = LocalSoftwareKeyboardController.current
```

Pass a wrapped send callback:

```kotlin
onSend = {
    viewModel.sendMessage()
    focusManager.clearFocus(force = true)
    keyboardController?.hide()
}
```

Do not clear text locally; `ChatViewModel.sendMessage()` remains the source of
truth for `inputText`.

- [ ] **Step 4: Replace unconditional scroll with policy-driven follow**

Track the previous message count:

```kotlin
var previousMessageCount by remember { mutableIntStateOf(0) }
val messageCountChanged = uiState.messages.size != previousMessageCount
val lastVisibleIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
val isNearBottom = lastVisibleIndex >= uiState.messages.lastIndex - 1
val isStreaming = uiState.messages.lastOrNull()?.isStreaming == true
```

Use a `LaunchedEffect` keyed by message count, final message content, and
streaming state:

```kotlin
LaunchedEffect(
    uiState.messages.size,
    uiState.messages.lastOrNull()?.content,
    isStreaming,
) {
    if (
        uiState.messages.isNotEmpty() &&
        shouldFollowLatest(messageCountChanged, isNearBottom, isStreaming)
    ) {
        listState.animateScrollToItem(uiState.messages.lastIndex)
    }
    previousMessageCount = uiState.messages.size
}
```

The new user message and AI placeholder are added synchronously in one
`sendMessage()` state transition sequence, so the final count scroll targets the
assistant placeholder. Streaming follows only while near the bottom.

- [ ] **Step 5: Run focused unit and instrumented tests**

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.asuka.pocketpdf.ui.chat.ChatFollowPolicyTest"
.\gradlew.bat connectedDebugAndroidTest `
  -Pandroid.testInstrumentationRunnerArguments.class=com.asuka.pocketpdf.ui.chat.ChatActivityTest
```

Expected: tests pass; no network call is made.

### Task 3: Strengthen Library Contrast

**Files:**
- Modify: `app/src/main/java/com/asuka/pocketpdf/ui/library/LibraryScreen.kt`
- Modify: `app/src/androidTest/java/com/asuka/pocketpdf/ui/library/LibraryScreenTest.kt`

- [ ] **Step 1: Preserve library semantics in tests**

Ensure the test asserts these nodes exist:

```kotlin
composeRule.onNodeWithText("搜索文档").assertExists()
composeRule.onNodeWithText("导入 PDF").assertExists()
```

Keep the existing loaded-document assertions.

- [ ] **Step 2: Apply approved workspace-card treatment**

Change the workspace card to:

```kotlin
color = colors.paper.copy(alpha = .92f)
border = BorderStroke(
    1.dp,
    MaterialTheme.colorScheme.outline.copy(alpha = .48f),
)
```

Do not increase shadow elevation or radius.

- [ ] **Step 3: Apply approved search-field treatment**

Change the search surface to:

```kotlin
color = colors.paper
border = BorderStroke(
    1.5.dp,
    MaterialTheme.colorScheme.outline.copy(alpha = .72f),
)
```

Keep the transparent `TextField` container and existing text/cursor colors.

- [ ] **Step 4: Make import icon and text white**

Set the import `Surface` content color and explicit children to white:

```kotlin
contentColor = Color.White
```

```kotlin
Icon(..., tint = Color.White)
Text(..., color = Color.White)
```

- [ ] **Step 5: Brighten dark-theme delete feedback**

Use the current system-theme check:

```kotlin
val deleteColor = if (isSystemInDarkTheme()) {
    Color(0xFFFF7A8E)
} else {
    MaterialTheme.colorScheme.error
}
```

Apply `deleteColor` only to the swipe-delete label.

- [ ] **Step 6: Run the focused library instrumented test**

```powershell
.\gradlew.bat connectedDebugAndroidTest `
  -Pandroid.testInstrumentationRunnerArguments.class=com.asuka.pocketpdf.ui.library.LibraryScreenTest
```

Expected: test passes.

### Task 4: Improve Reader Chrome and AI Action

**Files:**
- Modify: `app/src/main/java/com/asuka/pocketpdf/ui/reader/ReaderScreen.kt`
- Modify: `app/src/androidTest/java/com/asuka/pocketpdf/ui/reader/ReaderScreenTest.kt`

- [ ] **Step 1: Preserve reader control semantics**

Assert the existing controls remain discoverable:

```kotlin
composeRule.onNodeWithContentDescription("返回").assertExists()
composeRule.onNodeWithContentDescription("搜索").assertExists()
composeRule.onNodeWithContentDescription("上一页").assertExists()
composeRule.onNodeWithContentDescription("下一页").assertExists()
composeRule.onNodeWithContentDescription("页面摘要").assertExists()
composeRule.onNodeWithContentDescription("文档 AI").assertExists()
```

- [ ] **Step 2: Make top reader icons white**

Set both back and search icon tint to:

```kotlin
Color.White
```

- [ ] **Step 3: Make toolbar icons white with disabled treatment**

For previous/next:

```kotlin
val enabled = /* existing page condition */
Icon(
    ...,
    tint = if (enabled) Color.White else Color.White.copy(alpha = .35f),
)
```

Keep `IconButton(enabled = enabled)`. Set the page-summary refresh icon tint to
`Color.White`.

- [ ] **Step 4: Apply the bright-violet AI button**

Use:

```kotlin
private val ReaderAiViolet = Color(0xFFA875F0)
```

Set the AI button background to `ReaderAiViolet` and the sparkle text to
`Color.White`. Keep the current 48 dp size and 14 dp radius.

- [ ] **Step 5: Run focused reader instrumented tests**

```powershell
.\gradlew.bat connectedDebugAndroidTest `
  -Pandroid.testInstrumentationRunnerArguments.class=com.asuka.pocketpdf.ui.reader.ReaderScreenTest
```

Expected: test passes.

### Task 5: Make Settings Save Text White

**Files:**
- Modify: `app/src/main/java/com/asuka/pocketpdf/ui/components/PocketButtons.kt`
- Modify: `app/src/main/java/com/asuka/pocketpdf/ui/settings/SettingsScreen.kt`

- [ ] **Step 1: Add optional colors to the compact button**

Extend the signature:

```kotlin
fun PocketCompactButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color = LocalPocketColors.current.crystal,
    contentColor: Color = LocalPocketColors.current.ink,
)
```

Use those values in `ButtonDefaults.buttonColors`, including:

```kotlin
disabledContentColor = contentColor.copy(alpha = .55f)
```

Existing callers preserve their current appearance through defaults.

- [ ] **Step 2: Configure Settings Save explicitly**

Call:

```kotlin
PocketCompactButton(
    text = if (isSaving) "保存中" else "保存",
    onClick = onSave,
    enabled = !isSaving,
    containerColor = MaterialTheme.colorScheme.primary,
    contentColor = Color.White,
)
```

- [ ] **Step 3: Compile the affected Compose code**

```powershell
.\gradlew.bat compileDebugKotlin
```

Expected: build succeeds and existing compact-button callers require no changes.

### Task 6: Full Verification and Device Acceptance

**Files:**
- No additional source files unless verification finds a defect.

- [ ] **Step 1: Run all unit tests**

```powershell
.\gradlew.bat testDebugUnitTest
```

Expected: all tests pass.

- [ ] **Step 2: Build the debug APK**

```powershell
.\gradlew.bat assembleDebug
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Install without clearing app data**

```powershell
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

Expected: `Success`.

- [ ] **Step 4: Verify light theme**

On device:

1. Open the library.
2. Confirm the workspace card and search field are visibly separated.
3. Confirm the import icon and label are white.
4. Open Settings and confirm Save text is white.
5. Open a PDF and confirm all reader chrome icons are white.
6. Confirm the AI button is bright violet.

- [ ] **Step 5: Verify dark theme**

Switch the device/app to dark theme and confirm:

1. Swipe a document card and verify the delete label is bright coral.
2. Confirm workspace/search contrast remains readable.
3. Confirm reader chrome and AI button remain correct.

- [ ] **Step 6: Verify chat interaction without changing AI behavior**

Send one short question using the configured provider:

1. Keyboard dismisses immediately.
2. Input loses focus and clears.
3. Viewport moves to the AI placeholder.
4. Streaming remains visible while at the bottom.
5. Scroll upward during a longer response and verify the app does not force the
   viewport back down.

- [ ] **Step 7: Inspect runtime logs**

```powershell
adb logcat -d -v brief |
  Select-String -Pattern 'FATAL EXCEPTION|ANR in com.asuka.pocketpdf|IllegalStateException'
```

Expected: no PocketPDF crash or ANR.

## Scope Guard

Do not modify or revert the existing uncommitted full-document summary files.
Before each commit or final handoff, verify that UI changes are limited to the
paths listed in this plan. Do not stage unrelated AI implementation files.
