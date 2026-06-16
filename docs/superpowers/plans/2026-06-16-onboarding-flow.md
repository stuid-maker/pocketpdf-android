# 新手引导 Onboarding 实现计划

> **For Hermes:** Use subagent-driven-development skill to implement this plan task-by-task.

**Goal:** 为新下载用户提供 3 步新手引导（欢迎 → 配置 AI → 导入文档），首次启动展示，完成后不再出现。

**Architecture:** 新增 `OnboardingActivity` + `OnboardingScreen` Composable。LibraryActivity 启动时检查 DataStore 中的 `onboarding_completed` 标记，未完成则先启动 OnboardingActivity。引导流程中第 2 步可直接跳转设置页配置 AI。

**Tech Stack:** Jetpack Compose, Material3, DataStore Preferences, Hilt

---

## 当前状态基线

- **入口 Activity**: `LibraryActivity`（AndroidManifest 中 LAUNCHER）
- **设置页**: `SettingsActivity` + `SettingsScreen`（已支持预设选择、API Key、连接测试）
- **空状态**: `PocketEmptyState`（显示"从一份文档开始"，有导入按钮）
- **持久化**: `SettingsDataStore`（基于 DataStore Preferences）
- **设计系统**: `PocketTokens`, `PocketSpacing`, `PocketRadii`, `LocalPocketColors`, `PocketBrandMark`, `PocketCompactButton`
- **主题**: `PocketPDFTheme`（在 setContent 中使用）
- **导航**: 简单 Activity 跳转，无 Jetpack Navigation

---

### Task 1: 添加 onboarding_completed 持久化标记

**Objective:** 在 SettingsDataStore 中添加 boolean 标记，记录用户是否已完成引导。

**Files:**
- Modify: `app/src/main/java/com/asuka/pocketpdf/data/local/SettingsDataStore.kt`

**Step 1: 添加 key 和 flow**

在 `SettingsDataStore` 中添加：

```kotlin
import androidx.datastore.preferences.core.booleanPreferencesKey

// 在 companion object 中添加：
private val KEY_ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")

// 在类体中添加 flow：
val onboardingCompleted: Flow<Boolean> = context.dataStore.data.map { prefs ->
    prefs[KEY_ONBOARDING_COMPLETED] ?: false
}
```

**Step 2: 添加 setter**

```kotlin
suspend fun setOnboardingCompleted(completed: Boolean) {
    context.dataStore.edit { it[KEY_ONBOARDING_COMPLETED] = completed }
}
```

**Step 3: 在 resetDefaults() 中不重置此标记**

`resetDefaults()` 使用 `it.clear()` 会清除所有 key。需要在 clear 后重新设置该标记保持原值：

```kotlin
suspend fun resetDefaults() {
    // 保持 onboarding 标记不变（用户不应该因为重置设置而重新看到引导）
    val wasOnboarded = onboardingCompleted.first()
    context.dataStore.edit { it.clear() }
    if (wasOnboarded) {
        context.dataStore.edit { it[KEY_ONBOARDING_COMPLETED] = true }
    }
}
```

**Verification**: 编译通过即可（无运行时行为变化）。

---

### Task 2: 创建 OnboardingScreen Composable

**Objective:** 创建引导页面的 Compose UI，包含 3 个步骤的页面切换、步骤指示器和操作按钮。

**Files:**
- Create: `app/src/main/java/com/asuka/pocketpdf/ui/onboarding/OnboardingScreen.kt`

**Step 1: 创建目录**

```bash
mkdir -p app/src/main/java/com/asuka/pocketpdf/ui/onboarding
```

**Step 2: 编写完整 OnboardingScreen**

```kotlin
package com.asuka.pocketpdf.ui.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.asuka.pocketpdf.ui.components.PocketBrandMark
import com.asuka.pocketpdf.ui.theme.LocalPocketColors
import com.asuka.pocketpdf.ui.theme.PocketRadii
import com.asuka.pocketpdf.ui.theme.PocketSpacing

private const val TOTAL_STEPS = 3

@Composable
fun OnboardingScreen(
    onOpenSettings: () -> Unit,
    onFinish: () -> Unit,
) {
    val colors = LocalPocketColors.current
    var currentStep by rememberSaveable { mutableIntStateOf(0) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(colors.paper, colors.workspace, colors.workspace),
                ),
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = PocketSpacing.Xl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Top bar: skip + step dots
            OnboardingTopBar(
                currentStep = currentStep,
                onSkip = onFinish,
            )

            Spacer(Modifier.weight(1f))

            // Step content with animation
            AnimatedContent(
                targetState = currentStep,
                transitionSpec = {
                    fadeIn() togetherWith fadeOut()
                },
                modifier = Modifier.weight(4f),
            ) { step ->
                when (step) {
                    0 -> WelcomeStep()
                    1 -> AiSetupStep(onOpenSettings = onOpenSettings)
                    2 -> ImportStep()
                }
            }

            Spacer(Modifier.weight(1f))

            // Bottom: action buttons
            OnboardingBottomBar(
                currentStep = currentStep,
                onNext = { currentStep++ },
                onPrev = { currentStep-- },
                onFinish = onFinish,
            )

            Spacer(Modifier.height(PocketSpacing.Xxl))
        }
    }
}

@Composable
private fun OnboardingTopBar(
    currentStep: Int,
    onSkip: () -> Unit,
) {
    val colors = LocalPocketColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = PocketSpacing.Md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Step dots
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(TOTAL_STEPS) { index ->
                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .size(if (index == currentStep) 10.dp else 8.dp)
                        .clip(CircleShape)
                        .background(
                            if (index == currentStep) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                colors.mutedInk.copy(alpha = 0.3f)
                            },
                        ),
                )
            }
        }
        // Skip button
        TextButton(onClick = onSkip) {
            Text("跳过", color = colors.mutedInk)
        }
    }
}

@Composable
private fun WelcomeStep() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = PocketSpacing.Md),
    ) {
        PocketBrandMark()
        Spacer(Modifier.height(PocketSpacing.Xxl))
        Text(
            text = "欢迎使用 PocketPDF",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = LocalPocketColors.current.ink,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(PocketSpacing.Lg))
        Text(
            text = "PocketPDF 是一款本地优先的 PDF 阅读与 AI 问答工具。\n\n" +
                "你可以导入论文、教材或技术资料，在设备上建立可搜索的索引，\n" +
                "并向文档直接提问——所有处理都在你的掌控之中。",
            style = MaterialTheme.typography.bodyLarge,
            color = LocalPocketColors.current.mutedInk,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 340.dp),
        )
    }
}

@Composable
private fun AiSetupStep(onOpenSettings: () -> Unit) {
    val colors = LocalPocketColors.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = PocketSpacing.Md),
    ) {
        // Icon area: decorative AI icon
        Surface(
            modifier = Modifier.size(80.dp),
            shape = RoundedCornerShape(PocketRadii.Floating),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = "AI",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Spacer(Modifier.height(PocketSpacing.Xxl))
        Text(
            text = "连接 AI 服务",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = colors.ink,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(PocketSpacing.Lg))
        Text(
            text = "PocketPDF 需要连接 AI 服务才能进行文档问答和摘要。\n\n" +
                "推荐方案：\n" +
                "• LM Studio — 本地免费运行，无需联网\n" +
                "• DeepSeek / 通义千问 — 云端服务，需 API Key",
            style = MaterialTheme.typography.bodyLarge,
            color = colors.mutedInk,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 340.dp),
        )
        Spacer(Modifier.height(PocketSpacing.Xxl))
        Button(
            onClick = onOpenSettings,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
            ),
        ) {
            Text("前往设置 AI 服务")
        }
    }
}

@Composable
private fun ImportStep() {
    val colors = LocalPocketColors.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = PocketSpacing.Md),
    ) {
        // Icon area: decorative import icon
        Surface(
            modifier = Modifier.size(80.dp),
            shape = RoundedCornerShape(PocketRadii.Floating),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = "PDF",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Spacer(Modifier.height(PocketSpacing.Xxl))
        Text(
            text = "导入你的第一份 PDF",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = colors.ink,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(PocketSpacing.Lg))
        Text(
            text = "一切就绪！进入文档库后，点击底部的「导入 PDF」按钮，\n" +
                "从文件管理器中选择 PDF 文档。\n\n" +
                "PocketPDF 会自动为文档建立索引，\n" +
                "之后你就可以搜索和提问了。",
            style = MaterialTheme.typography.bodyLarge,
            color = colors.mutedInk,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 340.dp),
        )
    }
}

@Composable
private fun OnboardingBottomBar(
    currentStep: Int,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onFinish: () -> Unit,
) {
    val colors = LocalPocketColors.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (currentStep < TOTAL_STEPS - 1) {
            // Next button for steps 0 and 1
            Button(
                onClick = onNext,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(PocketRadii.Control),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                ),
            ) {
                Text(
                    text = if (currentStep == 0) "开始设置" else "下一步",
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            Spacer(Modifier.height(PocketSpacing.Sm))
            OutlinedButton(
                onClick = onFinish,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(PocketRadii.Control),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = colors.mutedInk,
                ),
            ) {
                Text("跳过引导，直接开始")
            }
        } else {
            // Final step: "开始使用" primary button
            Button(
                onClick = onFinish,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(PocketRadii.Control),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                ),
            ) {
                Text(
                    text = "开始使用",
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            Spacer(Modifier.height(PocketSpacing.Sm))
            TextButton(onClick = onPrev) {
                Text("上一步", color = colors.mutedInk)
            }
        }
    }
}
```

**Verification**: 文件创建完成，编译检查。

---

### Task 3: 创建 OnboardingActivity

**Objective:** 包裹 OnboardingScreen 的 Activity，处理设置跳转和完成回调。

**Files:**
- Create: `app/src/main/java/com/asuka/pocketpdf/ui/onboarding/OnboardingActivity.kt`

```kotlin
package com.asuka.pocketpdf.ui.onboarding

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import com.asuka.pocketpdf.ui.settings.SettingsActivity
import com.asuka.pocketpdf.ui.theme.PocketPDFTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class OnboardingActivity : ComponentActivity() {

    private val viewModel: OnboardingViewModel by viewModels()

    private val settingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        // User returned from settings — stay on the same onboarding step
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PocketPDFTheme {
                OnboardingScreen(
                    onOpenSettings = {
                        settingsLauncher.launch(
                            Intent(this, SettingsActivity::class.java),
                        )
                    },
                    onFinish = {
                        viewModel.completeOnboarding()
                        // Navigate to library
                        startActivity(
                            Intent(this, com.asuka.pocketpdf.ui.library.LibraryActivity::class.java),
                        )
                        finish()
                    },
                )
            }
        }
    }
}
```

**Verification**: 编译通过。

---

### Task 4: 创建 OnboardingViewModel

**Objective:** 处理引导完成后的持久化存储。

**Files:**
- Create: `app/src/main/java/com/asuka/pocketpdf/ui/onboarding/OnboardingViewModel.kt`

```kotlin
package com.asuka.pocketpdf.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asuka.pocketpdf.data.local.SettingsDataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
) : ViewModel() {

    fun completeOnboarding() {
        viewModelScope.launch {
            settingsDataStore.setOnboardingCompleted(true)
        }
    }
}
```

**Verification**: 编译通过。

---

### Task 5: 修改 LibraryActivity 添加引导检查逻辑

**Objective:** LibraryActivity 启动时检查是否需要展示引导。

**Files:**
- Modify: `app/src/main/java/com/asuka/pocketpdf/ui/library/LibraryActivity.kt`

**修改点 1**: 在 `onCreate` 开头注入 `SettingsDataStore` 并检查引导状态。

在 `LibraryActivity` 中添加：

```kotlin
@Inject
lateinit var settingsDataStore: SettingsDataStore
```

**修改点 2**: 在 `onCreate` 中 `super.onCreate(savedInstanceState)` 之前添加引导检查：

```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    // Check if onboarding is needed before setting content
    lifecycleScope.launch {
        val completed = settingsDataStore.onboardingCompleted.first()
        if (!completed) {
            startActivity(Intent(this@LibraryActivity, OnboardingActivity::class.java))
            finish()
            return@launch
        }
    }
    super.onCreate(savedInstanceState)
    // ... rest of existing code
}
```

⚠️ **PITFALL**: `lifecycleScope` 在 `super.onCreate` 之前不可用。需要改用另一种方式。

**正确方式**: 在 `onCreate` 中使用 `runBlocking` 或改用 `OnboardingGate` Composable。

最佳方案：使用 `OnboardingGate` Composable 包裹，在 `setContent` 内部检查：

```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
        PocketPDFTheme {
            val onboardingCompleted by settingsDataStore.onboardingCompleted
                .collectAsStateWithLifecycle(initialValue = null)
            
            when (onboardingCompleted) {
                null -> {
                    // Still loading — 显示短暂 loading 或空白
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                false -> {
                    // 未完成引导 — 启动 OnboardingActivity
                    LaunchedEffect(Unit) {
                        startActivity(Intent(this@LibraryActivity, OnboardingActivity::class.java))
                        finish()
                    }
                }
                true -> {
                    // 已完成引导 — 正常显示 Library
                    LibraryContent(...)
                }
            }
        }
    }
}
```

但这会把整个 Library 的 Composable 树包一层条件判断... 更简洁的方式是在 `onCreate` 中用 `runBlocking` 同步读取一次（DataStore 读取很快）：

**最终采用方案**:

```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    
    // 同步检查引导状态（DataStore 本地读取，极快）
    val onboardingCompleted = runBlocking {
        settingsDataStore.onboardingCompleted.first()
    }
    if (!onboardingCompleted) {
        startActivity(Intent(this, OnboardingActivity::class.java))
        finish()
        return
    }
    
    setContent {
        // ... existing code unchanged
    }
}
```

**Import 需要添加**:
```kotlin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
```

**Verification**: 全新安装后首次启动显示引导；再次启动直接进入文档库。

---

### Task 6: 修改 AndroidManifest 注册新 Activity

**Objective:** 在 AndroidManifest 中注册 OnboardingActivity。

**Files:**
- Modify: `app/src/main/AndroidManifest.xml`

在 `</application>` 结束标签前添加：

```xml
<activity
    android:name=".ui.onboarding.OnboardingActivity"
    android:exported="false"
    android:label="新手引导" />
```

**Verification**: 编译通过，APK 安装后新 Activity 可被启动。

---

### Task 7: 编译验证

**Objective:** 确保所有代码编译通过并运行单元测试。

```bash
cd D:\work\pocketPDF
./gradlew compileDebugKotlin 2>&1
```

**Expected**: BUILD SUCCESSFUL，无编译错误。

```bash
./gradlew testDebugUnitTest 2>&1
```

**Expected**: 所有已有测试继续通过。

---

## 文件变更清单

| 文件 | 操作 | 说明 |
|------|------|------|
| `data/local/SettingsDataStore.kt` | 修改 | 添加 onboarding_completed 标记 |
| `ui/onboarding/OnboardingScreen.kt` | 新建 | 引导页 Compose UI |
| `ui/onboarding/OnboardingActivity.kt` | 新建 | 引导页 Activity |
| `ui/onboarding/OnboardingViewModel.kt` | 新建 | 引导完成持久化 |
| `ui/library/LibraryActivity.kt` | 修改 | 添加引导检查和跳转 |
| `AndroidManifest.xml` | 修改 | 注册 OnboardingActivity |

---

## 设计决策记录

1. **为什么不把 Onboarding 做成 LibraryActivity 内部的 Overlay？**
   - Activity 跳转更清晰，不污染 Library 代码
   - 第 2 步需要跳转到 SettingsActivity，用独立 Activity 更自然
   - 未来如果要增加/修改引导步骤，修改隔离在 Onboarding 模块内

2. **为什么用 `runBlocking` 而不是 LaunchedEffect？**
   - `runBlocking` 在 `onCreate` 中同步读取 DataStore，确保在 `setContent` 之前完成判断
   - DataStore 本地读取极快（<5ms），不会造成 ANR
   - 避免在 Composable 树中混入导航逻辑

3. **resetDefaults 时保留 onboarding 标记**
   - 用户重置设置不应该重新看到引导（会让人烦）
   - 只有全新安装或清除应用数据才重新显示

4. **3 步设计**
   - 欢迎 → 解释产品价值
   - AI 配置 → 直接链接设置页
   - 导入 → 告知下一步操作
   - 每步都可跳过
