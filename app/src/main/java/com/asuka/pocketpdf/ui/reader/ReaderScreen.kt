package com.asuka.pocketpdf.ui.reader

import android.graphics.Bitmap
import android.view.MotionEvent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.asuka.pocketpdf.ui.theme.LocalPocketColors
import com.asuka.pocketpdf.ui.theme.PocketRadii
import com.asuka.pocketpdf.ui.theme.PocketSpacing

@Composable
fun ReaderScreen(
    title: String,
    pageState: ReaderPageState,
    summaryState: SummaryState,
    isIndexed: Boolean,
    onBack: () -> Unit,
    onPageRequested: (Int) -> Unit,
    onSummarizePage: () -> Unit,
    onSummarizeDocument: () -> Unit,
    onStopSummary: () -> Unit,
    onOpenChat: () -> Unit,
) {
    val colors = LocalPocketColors.current
    var chromeVisible by rememberSaveable { mutableStateOf(true) }
    var summarySheetVisible by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(summaryState) {
        if (summaryState !is SummaryState.Idle) summarySheetVisible = true
    }

    Box(
        modifier = Modifier.fillMaxSize().background(Color(0xFF29252D)),
    ) {
        PdfPageHost(
            bitmap = pageState.bitmap,
            onTap = { chromeVisible = !chromeVisible },
            onPageFling = { direction ->
                val newPage = pageState.pageIndex + direction
                if (newPage in 0 until pageState.pageCount) {
                    onPageRequested(newPage)
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        if (pageState.isRendering) {
            CircularProgressIndicator(Modifier.align(Alignment.Center))
        }

        pageState.error?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.align(Alignment.Center).padding(PocketSpacing.Xxl),
            )
        }

        if (pageState.pageCount > 0) {
            LinearProgressIndicator(
                progress = { (pageState.pageIndex + 1f) / pageState.pageCount },
                modifier = Modifier.fillMaxWidth().height(2.dp).align(Alignment.TopCenter),
                color = Color(0xFFC4A4ED),
                trackColor = Color.Transparent,
            )
        }

        AnimatedVisibility(
            visible = chromeVisible,
            modifier = Modifier.align(Alignment.TopCenter),
            enter = fadeIn() + slideInVertically { -it / 2 },
            exit = fadeOut() + slideOutVertically { -it / 2 },
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color(0xF0302739),
                                Color(0xD9302739),
                            ),
                        ),
                    )
                    .statusBarsPadding()
                    .padding(horizontal = PocketSpacing.Sm, vertical = PocketSpacing.Sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        tint = colors.ink,
                    )
                }
                Text(
                    text = title,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    color = Color.White,
                    style = MaterialTheme.typography.titleSmall,
                )
            }
        }

        AnimatedVisibility(
            visible = chromeVisible,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 },
        ) {
            ReaderToolbar(
                pageState = pageState,
                onPageRequested = onPageRequested,
                onSummary = {
                    summarySheetVisible = true
                    onSummarizePage()
                },
                onAi = {
                    summarySheetVisible = true
                },
            )
        }
    }

    if (summarySheetVisible) {
        ReaderAiSheet(
            pageIndex = pageState.pageIndex,
            summaryState = summaryState,
            isIndexed = isIndexed,
            onSummarizePage = onSummarizePage,
            onSummarizeDocument = onSummarizeDocument,
            onStop = onStopSummary,
            onOpenChat = onOpenChat,
            onDismiss = {
                onStopSummary()
                summarySheetVisible = false
            },
        )
    }
}

@Composable
private fun PdfPageHost(
    bitmap: Bitmap?,
    onTap: () -> Unit,
    onPageFling: (Int) -> Unit,
    modifier: Modifier,
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            PdfPageView(context).apply {
                setOnTouchListener { _, event ->
                    if (event.action == MotionEvent.ACTION_UP) onTap()
                    false
                }
                this.onPageFling = { direction -> onPageFling(direction) }
            }
        },
        update = { view -> view.setBitmap(bitmap) },
    )
}

@Composable
private fun ReaderToolbar(
    pageState: ReaderPageState,
    onPageRequested: (Int) -> Unit,
    onSummary: () -> Unit,
    onAi: () -> Unit,
) {
    val colors = LocalPocketColors.current
    Row(
        modifier = Modifier
            .padding(PocketSpacing.Lg)
            .navigationBarsPadding()
            .shadow(
                elevation = 12.dp,
                shape = RoundedCornerShape(PocketRadii.Floating),
                ambientColor = colors.shadowAmbient,
                spotColor = colors.shadowSpot,
            )
            .clip(RoundedCornerShape(PocketRadii.Floating))
            .background(Color(0xEE302739))
            .border(
                1.dp,
                Color.White.copy(alpha = .14f),
                RoundedCornerShape(PocketRadii.Floating),
            )
            .padding(horizontal = PocketSpacing.Sm, vertical = PocketSpacing.Xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PocketSpacing.Xs),
    ) {
        IconButton(
            onClick = { onPageRequested(pageState.pageIndex - 1) },
            enabled = pageState.pageIndex > 0,
        ) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = "上一页",
                tint = colors.ink,
            )
        }
        Text(
            text = if (pageState.pageCount > 0) {
                "${pageState.pageIndex + 1} / ${pageState.pageCount}"
            } else "— / —",
            modifier = Modifier.padding(horizontal = PocketSpacing.Md),
            color = Color.White,
            style = MaterialTheme.typography.labelLarge,
        )
        IconButton(
            onClick = { onPageRequested(pageState.pageIndex + 1) },
            enabled = pageState.pageIndex + 1 < pageState.pageCount,
        ) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "下一页",
                tint = colors.ink,
            )
        }
        IconButton(onClick = onSummary) {
            Icon(Icons.Default.Refresh, contentDescription = "页面摘要", tint = colors.ink)
        }
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(colors.crystal)
                .clickable(onClick = onAi),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "✦",
                color = MaterialTheme.colorScheme.onPrimary,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier
                    .semantics { contentDescription = "文档 AI" }
                    .clickable(onClick = onAi),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderAiSheet(
    pageIndex: Int,
    summaryState: SummaryState,
    isIndexed: Boolean,
    onSummarizePage: () -> Unit,
    onSummarizeDocument: () -> Unit,
    onStop: () -> Unit,
    onOpenChat: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = LocalPocketColors.current.paper,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = PocketSpacing.Xl)
                .padding(bottom = PocketSpacing.Xxl),
        ) {
            Text(
                text = "✦ POCKET INTELLIGENCE",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelMedium,
            )
            Text(
                text = "理解这一页",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(top = PocketSpacing.Xs),
            )
            Text(
                text = "正在基于第 ${pageIndex + 1} 页与文档索引回答",
                style = MaterialTheme.typography.bodySmall,
                color = LocalPocketColors.current.mutedInk,
                modifier = Modifier.padding(vertical = PocketSpacing.Md),
            )
            when (summaryState) {
                SummaryState.Idle -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(PocketSpacing.Sm)) {
                        TextButton(onClick = onSummarizePage) { Text("总结本页") }
                        TextButton(
                            onClick = onSummarizeDocument,
                            enabled = isIndexed,
                        ) { Text("总结全文") }
                    }
                }
                SummaryState.Loading -> CircularProgressIndicator()
                is SummaryState.Streaming -> {
                    Text(summaryState.tokens, style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = onStop) { Text("停止") }
                }
                is SummaryState.Done -> Text(
                    summaryState.fullText,
                    style = MaterialTheme.typography.bodyMedium,
                )
                is SummaryState.Error -> Text(
                    summaryState.message,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Spacer(Modifier.height(PocketSpacing.Lg))
            TextButton(onClick = onOpenChat) {
                Text("继续与文档对话")
            }
        }
    }
}
