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
            // Top bar: step dots + skip
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
        // Icon area
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
        // Icon area
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
            // Final step: primary action
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
