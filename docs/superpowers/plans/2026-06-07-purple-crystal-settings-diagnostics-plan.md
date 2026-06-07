# Purple Crystal Settings and Diagnostics Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the long XML settings form and legacy ping utility with grouped Compose settings and actionable connection diagnostics.

**Architecture:** Keep `SettingsViewModel` as the editable source of truth, but split the screen into summary rows and focused editor dialogs/sheets. Reuse `PingViewModel`'s model request through a renamed diagnostics presentation layer.

**Tech Stack:** Compose Material 3, Hilt ViewModels, StateFlow, existing LLM repository.

---

### Task 1: Make Settings Events Consumable

**Files:**
- Modify: `app/src/main/java/com/asuka/pocketpdf/ui/settings/SettingsUiState.kt`
- Modify: `app/src/main/java/com/asuka/pocketpdf/ui/settings/SettingsViewModel.kt`
- Test: `app/src/test/java/com/asuka/pocketpdf/ui/settings/SettingsViewModelTest.kt`

- [ ] **Step 1: Write event-consumption tests**

Assert save success and errors are emitted once rather than remaining in
`SettingsUiState` and replaying after recomposition.

- [ ] **Step 2: Add event model**

```kotlin
sealed interface SettingsEvent {
    data object Saved : SettingsEvent
    data class ShowError(val message: String) : SettingsEvent
    data class ConnectionResult(val message: String, val success: Boolean) : SettingsEvent
}
```

Expose `Channel<SettingsEvent>(Channel.BUFFERED).receiveAsFlow()`. Remove
`saveSuccess`, `error`, and `connectionTestResult` after callers migrate.

- [ ] **Step 3: Verify**

```powershell
.\gradlew.bat testDebugUnitTest --tests "*SettingsViewModelTest"
```

Expected: PASS.

- [ ] **Step 4: Commit**

```powershell
git add app/src/main/java/com/asuka/pocketpdf/ui/settings app/src/test/java/com/asuka/pocketpdf/ui/settings
git commit -m "refactor(settings): expose one-shot settings events"
```

### Task 2: Build Grouped Settings Screen

**Files:**
- Create: `app/src/main/java/com/asuka/pocketpdf/ui/settings/SettingsScreen.kt`
- Create: `app/src/main/java/com/asuka/pocketpdf/ui/settings/SettingsEditors.kt`
- Test: `app/src/androidTest/java/com/asuka/pocketpdf/ui/settings/SettingsScreenTest.kt`

- [ ] **Step 1: Write UI tests**

Assert group labels `AI 服务`, `文档理解`, `维护`; current values; compact
`保存`; editor open/cancel/confirm; password concealment; reset confirmation;
and diagnostics callback.

- [ ] **Step 2: Implement screen contract**

```kotlin
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    actions: SettingsActions,
)

data class SettingsActions(
    val onPresetSelected: (String) -> Unit,
    val onBaseUrlChanged: (String) -> Unit,
    val onModelNameChanged: (String) -> Unit,
    val onApiKeyChanged: (String) -> Unit,
    val onChunkingChanged: (String) -> Unit,
    val onSystemPromptChanged: (String) -> Unit,
    val onConfirmPreset: () -> Unit,
    val onCancelPreset: () -> Unit,
    val onResetDefaults: () -> Unit,
)
```

Render opaque paper groups. Each row shows title, supporting text, current
summary, and chevron. Editors are focused `AlertDialog` or bottom-sheet forms,
not a single long screen.

- [ ] **Step 3: Enforce button rule**

The save action and test actions size to content. Reset is a quiet text action
until the confirmation dialog.

- [ ] **Step 4: Add previews and verify**

Provide default, saving, confirmation, dark, and large-font previews. Run the
instrumentation class and expect PASS.

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/java/com/asuka/pocketpdf/ui/settings app/src/androidTest/java/com/asuka/pocketpdf/ui/settings/SettingsScreenTest.kt
git commit -m "feat(settings): add grouped compose settings"
```

### Task 3: Convert SettingsActivity and Remove XML

**Files:**
- Modify: `app/src/main/java/com/asuka/pocketpdf/ui/settings/SettingsActivity.kt`
- Delete: `app/src/main/res/layout/activity_settings.xml`

- [ ] **Step 1: Replace ViewBinding host**

Use `ComponentActivity`, `setContent`, `collectAsStateWithLifecycle`, and a
`SnackbarHostState` collecting `SettingsEvent`.

- [ ] **Step 2: Preserve preset confirmation**

Render `state.confirmPresetId` as a Compose confirmation dialog and call
`confirmPresetOverride()` or `cancelPresetOverride()` exactly once.

- [ ] **Step 3: Verify and remove XML**

```powershell
rg "ActivitySettingsBinding|activity_settings" app/src
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

Expected: no binding/layout references and BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```powershell
git add -A app/src/main/java/com/asuka/pocketpdf/ui/settings app/src/main/res/layout/activity_settings.xml
git commit -m "refactor(settings): migrate settings to compose"
```

### Task 4: Replace Ping with Diagnostics

**Files:**
- Create: `app/src/main/java/com/asuka/pocketpdf/ui/diagnostics/DiagnosticsUiState.kt`
- Create: `app/src/main/java/com/asuka/pocketpdf/ui/diagnostics/DiagnosticsViewModel.kt`
- Create: `app/src/main/java/com/asuka/pocketpdf/ui/diagnostics/DiagnosticsActivity.kt`
- Create: `app/src/main/java/com/asuka/pocketpdf/ui/diagnostics/DiagnosticsScreen.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/java/com/asuka/pocketpdf/ui/settings/SettingsActivity.kt`
- Delete: `app/src/main/java/com/asuka/pocketpdf/ui/ping/PingActivity.kt`
- Delete: `app/src/main/java/com/asuka/pocketpdf/ui/ping/PingUiState.kt`
- Delete: `app/src/main/java/com/asuka/pocketpdf/ui/ping/PingViewModel.kt`
- Delete: `app/src/main/res/layout/activity_ping.xml`
- Test: `app/src/test/java/com/asuka/pocketpdf/ui/diagnostics/DiagnosticsViewModelTest.kt`
- Test: `app/src/androidTest/java/com/asuka/pocketpdf/ui/diagnostics/DiagnosticsScreenTest.kt`

- [ ] **Step 1: Define diagnostic checks**

```kotlin
enum class DiagnosticStatus { Idle, Running, Passed, Failed }

data class DiagnosticCheck(
    val label: String,
    val status: DiagnosticStatus,
    val detail: String? = null,
)

data class DiagnosticsUiState(
    val checks: List<DiagnosticCheck>,
    val isRunning: Boolean,
    val errorSummary: String? = null,
)
```

Use checks for network/service response, model availability, and local index
availability based on data already accessible in the project. Do not invent
credential validation beyond the endpoint response.

- [ ] **Step 2: Write ViewModel tests**

Cover idle -> running -> success, endpoint failure while local index remains
available, and repeated retry.

- [ ] **Step 3: Implement the screen**

Use `PocketStatusPanel`. At the bottom-left render compact `重新检测` and a
text-style `检查服务设置` beside it; neither spans the screen.

- [ ] **Step 4: Wire settings navigation**

The Settings maintenance row launches `DiagnosticsActivity`.
`DiagnosticsActivity` secondary action finishes back to settings.

- [ ] **Step 5: Remove ping**

Update manifest references, then:

```powershell
rg "ui\\.ping|PingActivity|activity_ping" app/src
```

Expected: no matches.

- [ ] **Step 6: Verify**

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
.\gradlew.bat connectedDebugAndroidTest
```

Expected: PASS. Manually verify successful and failed endpoint states.

- [ ] **Step 7: Commit**

```powershell
git add -A app/src
git commit -m "feat(settings): replace ping with connection diagnostics"
```

### Task 5: Final UI Regression

**Files:**
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/AndroidManifest.xml`
- Test: all existing unit and instrumentation suites

- [ ] **Step 1: Scan removed UI references**

```powershell
rg "ActivityLibraryBinding|ActivityReaderBinding|ActivitySettingsBinding|ActivityPingBinding|DocumentListAdapter|dialog_summary" app/src
```

Expected: no matches.

- [ ] **Step 2: Run complete verification**

```powershell
.\gradlew.bat clean testDebugUnitTest lintDebug assembleDebug
.\gradlew.bat connectedDebugAndroidTest
```

Expected: BUILD SUCCESSFUL and all connected tests pass.

- [ ] **Step 3: Perform manual acceptance**

Verify import/cover generation, delete undo, PDF zoom/pan/page navigation,
immersive chrome, AI partial/full sheet, streaming/cancel/citation, settings
edit/save/reset, diagnostics success/failure, keyboard insets, light/dark, and
font scale `1.3`.

- [ ] **Step 4: Commit final corrections**

```powershell
git add app/src
git commit -m "test(ui): complete purple crystal regression"
```
