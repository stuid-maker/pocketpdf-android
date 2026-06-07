# Purple Crystal Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Establish the Purple Crystal tokens, reusable primitives, preview fixtures, and Compose test infrastructure.

**Architecture:** Extend the existing `ui/theme` package with explicit surface, spacing, shape, and motion tokens. Add stateless components under `ui/components`; they accept semantic state and callbacks and never access ViewModels or repositories.

**Tech Stack:** Compose Material 3, Compose UI tooling, Compose UI test JUnit4, Android instrumented tests.

---

### Task 1: Configure Compose Testing

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`

- [ ] **Step 1: Add test library aliases**

Add:

```toml
compose-ui-test-junit4 = { group = "androidx.compose.ui", name = "ui-test-junit4" }
compose-ui-test-manifest = { group = "androidx.compose.ui", name = "ui-test-manifest" }
androidx-lifecycle-runtime-compose = { group = "androidx.lifecycle", name = "lifecycle-runtime-compose", version.ref = "lifecycle" }
```

- [ ] **Step 2: Add dependencies**

```kotlin
implementation(libs.androidx.lifecycle.runtime.compose)
androidTestImplementation(platform(libs.compose.bom))
androidTestImplementation(libs.compose.ui.test.junit4)
debugImplementation(libs.compose.ui.test.manifest)
```

- [ ] **Step 3: Verify dependency resolution**

Run:

```powershell
.\gradlew.bat :app:dependencies --configuration debugAndroidTestRuntimeClasspath
```

Expected: exit code `0`, with `androidx.compose.ui:ui-test-junit4` in the graph.

- [ ] **Step 4: Commit**

```powershell
git add gradle/libs.versions.toml app/build.gradle.kts
git commit -m "test(ui): add compose test infrastructure"
```

### Task 2: Define Theme Tokens

**Files:**
- Replace: `app/src/main/java/com/asuka/pocketpdf/ui/theme/Color.kt`
- Modify: `app/src/main/java/com/asuka/pocketpdf/ui/theme/Theme.kt`
- Modify: `app/src/main/java/com/asuka/pocketpdf/ui/theme/Type.kt`
- Create: `app/src/main/java/com/asuka/pocketpdf/ui/theme/PocketTokens.kt`
- Test: `app/src/test/java/com/asuka/pocketpdf/ui/theme/PocketTokensTest.kt`

- [ ] **Step 1: Write token invariants**

```kotlin
class PocketTokensTest {
    @Test fun spacing_scale_is_monotonic() {
        val values = listOf(4, 8, 12, 16, 20, 24, 32)
        assertEquals(values.sorted(), values)
    }

    @Test fun motion_durations_stay_restrained() {
        assertTrue(PocketMotion.Micro in 100..180)
        assertTrue(PocketMotion.Panel in 180..240)
    }
}
```

- [ ] **Step 2: Run the test and verify failure**

```powershell
.\gradlew.bat testDebugUnitTest --tests "*PocketTokensTest"
```

Expected: FAIL because `PocketMotion` does not exist.

- [ ] **Step 3: Add concrete tokens**

```kotlin
object PocketSpacing {
    val Xs = 4.dp
    val Sm = 8.dp
    val Md = 12.dp
    val Lg = 16.dp
    val Xl = 20.dp
    val Xxl = 24.dp
    val Xxxl = 32.dp
}

object PocketRadii {
    val Compact = 10.dp
    val Control = 14.dp
    val Card = 16.dp
    val Floating = 20.dp
}

object PocketMotion {
    const val Micro = 150
    const val Content = 200
    const val Panel = 220
}
```

Define light colors around warm paper (`#F8F5F9`), workspace (`#F0EAF3`),
ink (`#211C27`), amethyst (`#7652A8`), and pale crystal (`#C4A4ED`).
Define dark colors around workspace (`#100D14`), paper (`#1B1720`), and
crystal surfaces (`#302739` with alpha supplied at use sites).

Define:

```kotlin
data class PocketColors(
    val workspace: Color,
    val paper: Color,
    val crystal: Color,
    val crystalBorder: Color,
    val ink: Color,
    val mutedInk: Color,
    val success: Color,
    val warning: Color,
)

val LocalPocketColors = staticCompositionLocalOf<PocketColors> {
    error("PocketColors not provided")
}

val LocalPocketSpacing = staticCompositionLocalOf { PocketSpacing }
```

- [ ] **Step 4: Update `PocketPDFTheme`**

Use explicit light/dark schemes, `PocketPDFTypography`, and:

```kotlin
CompositionLocalProvider(
    LocalPocketColors provides pocketColors,
    LocalPocketSpacing provides PocketSpacing,
) {
    MaterialTheme(
        colorScheme = colorScheme,
        typography = PocketPDFTypography,
        shapes = pocketShapes,
        content = content,
    )
}
```

- [ ] **Step 5: Run tests**

```powershell
.\gradlew.bat testDebugUnitTest --tests "*PocketTokensTest"
```

Expected: PASS.

- [ ] **Step 6: Commit**

```powershell
git add app/src/main/java/com/asuka/pocketpdf/ui/theme app/src/test/java/com/asuka/pocketpdf/ui/theme
git commit -m "feat(ui): add purple crystal design tokens"
```

### Task 3: Add Shared Components

**Files:**
- Create: `app/src/main/java/com/asuka/pocketpdf/ui/components/PocketButtons.kt`
- Create: `app/src/main/java/com/asuka/pocketpdf/ui/components/PocketSurfaces.kt`
- Create: `app/src/main/java/com/asuka/pocketpdf/ui/components/PocketStates.kt`
- Create: `app/src/main/java/com/asuka/pocketpdf/ui/components/PocketBrandMark.kt`
- Test: `app/src/androidTest/java/com/asuka/pocketpdf/ui/components/PocketComponentsTest.kt`

- [ ] **Step 1: Write semantics tests**

```kotlin
@get:Rule val composeRule = createComposeRule()

@Test fun compact_button_exposes_label_and_click() {
    var clicked = false
    composeRule.setContent {
        PocketPDFTheme {
            PocketCompactButton("重新检测", onClick = { clicked = true })
        }
    }
    composeRule.onNodeWithText("重新检测").assertHasClickAction().performClick()
    assertTrue(clicked)
}

@Test fun empty_state_exposes_title_and_action() {
    composeRule.setContent {
        PocketPDFTheme {
            PocketEmptyState("从一份文档开始", "本地建立索引", "导入 PDF", {})
        }
    }
    composeRule.onNodeWithText("从一份文档开始").assertExists()
    composeRule.onNodeWithText("导入 PDF").assertHasClickAction()
}
```

- [ ] **Step 2: Run the tests and verify failure**

```powershell
.\gradlew.bat connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.asuka.pocketpdf.ui.components.PocketComponentsTest
```

Expected: compile failure because the components do not exist.

- [ ] **Step 3: Implement focused primitives**

Implement:

```kotlin
enum class PocketStatusTone { Neutral, Success, Warning, Error }

data class PocketAction(
    val label: String,
    val onClick: () -> Unit,
)

@Composable fun PocketCompactButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
)

@Composable fun PocketCrystalSurface(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
)

@Composable fun PocketEmptyState(
    title: String,
    message: String,
    actionLabel: String,
    onAction: () -> Unit,
)

@Composable fun PocketStatusPanel(
    title: String,
    message: String,
    tone: PocketStatusTone,
    primaryAction: PocketAction? = null,
    secondaryAction: PocketAction? = null,
)
```

`PocketCompactButton` uses content width but applies `defaultMinSize(minHeight =
48.dp)`. `PocketCrystalSurface` uses a theme tint with a hairline border; do
not hard-code a blur dependency that is unavailable on older Android versions.

- [ ] **Step 4: Add previews**

Add light/dark previews for compact buttons, status panels, and the near-square
document/pocket/AI mark.

- [ ] **Step 5: Run verification**

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```powershell
git add app/src/main/java/com/asuka/pocketpdf/ui/components app/src/androidTest/java/com/asuka/pocketpdf/ui/components
git commit -m "feat(ui): add purple crystal shared components"
```
