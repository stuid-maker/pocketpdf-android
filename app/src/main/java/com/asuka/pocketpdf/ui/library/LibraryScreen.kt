package com.asuka.pocketpdf.ui.library

import android.text.format.DateUtils
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.asuka.pocketpdf.R
import com.asuka.pocketpdf.domain.model.Document
import com.asuka.pocketpdf.domain.model.IndexStatus
import com.asuka.pocketpdf.ui.components.PocketEmptyState
import com.asuka.pocketpdf.ui.theme.LocalPocketColors
import com.asuka.pocketpdf.ui.theme.PocketRadii
import com.asuka.pocketpdf.ui.theme.PocketSpacing

@Composable
fun LibraryScreen(
    state: LibraryUiState,
    onImport: () -> Unit,
    onOpenDocument: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    onRetryIndexing: (Long) -> Unit,
    onDeleteDocument: (Document) -> Unit,
    coverLoader: DocumentCoverLoader?,
    snackbarHostState: SnackbarHostState? = null,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPocketColors.current
    val resolvedSnackbarHostState = snackbarHostState ?: remember { SnackbarHostState() }
    Scaffold(
        modifier = modifier,
        containerColor = colors.workspace,
        snackbarHost = { SnackbarHost(resolvedSnackbarHostState) },
        topBar = {
            LibraryHeader(onOpenSettings)
        },
        floatingActionButton = {
            if (state !is LibraryUiState.Loading) {
                FloatingActionButton(
                    onClick = onImport,
                    containerColor = colors.crystal,
                    contentColor = Color.White,
                    shape = RoundedCornerShape(PocketRadii.Floating),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = PocketSpacing.Lg),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(PocketSpacing.Sm))
                        Text(stringResource(R.string.library_fab_import))
                    }
                }
            }
        },
    ) { innerPadding ->
        when (state) {
            LibraryUiState.Loading -> Box(
                Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            LibraryUiState.Empty -> Box(
                Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                PocketEmptyState(
                    title = stringResource(R.string.library_empty_title_new),
                    message = stringResource(R.string.library_empty_subtitle_new),
                    actionLabel = stringResource(R.string.library_fab_import),
                    onAction = onImport,
                )
            }
            is LibraryUiState.Error -> Box(
                Modifier.fillMaxSize().padding(innerPadding).padding(PocketSpacing.Xxl),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.library_load_failed, state.message),
                    color = MaterialTheme.colorScheme.error,
                )
            }
            is LibraryUiState.Loaded -> LibraryContent(
                state = state,
                padding = innerPadding,
                coverLoader = coverLoader,
                onOpenDocument = onOpenDocument,
                onRetryIndexing = onRetryIndexing,
                onDeleteDocument = onDeleteDocument,
            )
        }
    }
}

@Composable
private fun LibraryHeader(onOpenSettings: () -> Unit) {
    val colors = LocalPocketColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.workspace)
            .padding(horizontal = PocketSpacing.Xl, vertical = PocketSpacing.Md),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "PocketPDF",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = colors.ink,
            )
            IconButton(onClick = onOpenSettings) {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = stringResource(R.string.settings_title),
                    tint = colors.ink,
                )
            }
        }
        Spacer(Modifier.height(PocketSpacing.Xl))
        Text(
            text = stringResource(R.string.library_greeting),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = stringResource(R.string.library_workspace_statement),
            style = MaterialTheme.typography.headlineMedium,
            color = colors.ink,
        )
        Text(
            text = stringResource(R.string.library_workspace_supporting),
            modifier = Modifier.padding(top = PocketSpacing.Sm),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.mutedInk,
        )
    }
}

@Composable
private fun LibraryContent(
    state: LibraryUiState.Loaded,
    padding: PaddingValues,
    coverLoader: DocumentCoverLoader?,
    onOpenDocument: (Long) -> Unit,
    onRetryIndexing: (Long) -> Unit,
    onDeleteDocument: (Document) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = PocketSpacing.Lg,
            top = padding.calculateTopPadding() + PocketSpacing.Lg,
            end = PocketSpacing.Lg,
            bottom = padding.calculateBottomPadding() + 104.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(PocketSpacing.Md),
    ) {
        item {
            Text(
                text = stringResource(R.string.library_continue_reading),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = PocketSpacing.Xs),
            )
        }
        items(state.documents, key = { it.id }) { document ->
            DismissibleDocumentCard(
                document = document,
                coverLoader = coverLoader,
                onOpen = { onOpenDocument(document.id) },
                onRetryIndexing = { onRetryIndexing(document.id) },
                onDelete = { onDeleteDocument(document) },
            )
        }
        if (state.isImporting) {
            item {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DismissibleDocumentCard(
    document: Document,
    coverLoader: DocumentCoverLoader?,
    onOpen: () -> Unit,
    onRetryIndexing: () -> Unit,
    onDelete: () -> Unit,
) {
    val dismissState = rememberSwipeToDismissBoxState()
    LaunchedEffect(dismissState.currentValue) {
        if (dismissState.currentValue != SwipeToDismissBoxValue.Settled) {
            onDelete()
            dismissState.reset()
        }
    }
    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            Box(
                modifier = Modifier.fillMaxSize().padding(horizontal = PocketSpacing.Xl),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Text(stringResource(R.string.library_swipe_delete), color = MaterialTheme.colorScheme.error)
            }
        },
    ) {
        DocumentCard(document, coverLoader, onOpen, onRetryIndexing)
    }
}

@Composable
private fun DocumentCard(
    document: Document,
    coverLoader: DocumentCoverLoader?,
    onOpen: () -> Unit,
    onRetryIndexing: () -> Unit,
) {
    val colors = LocalPocketColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(PocketRadii.Card))
            .background(colors.paper)
            .clickable(onClick = onOpen)
            .padding(PocketSpacing.Md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DocumentCoverView(document, coverLoader)
        Spacer(Modifier.width(PocketSpacing.Md))
        Column(Modifier.weight(1f)) {
            Text(
                text = document.title,
                style = MaterialTheme.typography.titleMedium,
                color = colors.ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = documentMeta(document),
                modifier = Modifier.padding(top = PocketSpacing.Xs),
                style = MaterialTheme.typography.bodySmall,
                color = colors.mutedInk,
            )
            Row(
                modifier = Modifier.padding(top = PocketSpacing.Sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = indexLabel(document.indexStatus),
                    style = MaterialTheme.typography.labelSmall,
                    color = indexColor(document.indexStatus),
                    modifier = if (document.indexStatus == IndexStatus.FAILED) {
                        Modifier.clickable(onClick = onRetryIndexing)
                    } else Modifier,
                )
                if (document.indexStatus == IndexStatus.INDEXING) {
                    Spacer(Modifier.width(PocketSpacing.Sm))
                    LinearProgressIndicator(
                        modifier = Modifier.weight(1f).height(3.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun DocumentCoverView(document: Document, coverLoader: DocumentCoverLoader?) {
    val cover by produceState<DocumentCover>(
        initialValue = fallbackCover(document.id, document.title),
        document.id,
        document.uri,
    ) {
        if (coverLoader != null) value = coverLoader.load(document, 180, 240)
    }
    val fallbackPalettes = listOf(
        listOf(Color(0xFF7652A8), Color(0xFF302739)),
        listOf(Color(0xFF4E5F80), Color(0xFF252C3A)),
        listOf(Color(0xFF806070), Color(0xFF3D2932)),
        listOf(Color(0xFF67597A), Color(0xFF2E2735)),
    )
    Box(
        modifier = Modifier
            .size(width = 54.dp, height = 72.dp)
            .clip(RoundedCornerShape(PocketRadii.Compact)),
        contentAlignment = Alignment.Center,
    ) {
        when (val current = cover) {
            is DocumentCover.Thumbnail -> Image(
                bitmap = current.bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            is DocumentCover.Fallback -> {
                val palette = fallbackPalettes[current.paletteIndex]
                Box(
                    Modifier.fillMaxSize().background(Brush.linearGradient(palette)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        current.label,
                        color = Color.White,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun indexLabel(status: IndexStatus): String = stringResource(
    when (status) {
        IndexStatus.NOT_INDEXED -> R.string.library_badge_not_indexed
        IndexStatus.INDEXING -> R.string.library_badge_indexing
        IndexStatus.INDEXED -> R.string.library_badge_indexed
        IndexStatus.FAILED -> R.string.library_badge_failed
    },
)

@Composable
private fun indexColor(status: IndexStatus): Color {
    val colors = LocalPocketColors.current
    return when (status) {
        IndexStatus.NOT_INDEXED -> colors.mutedInk
        IndexStatus.INDEXING -> colors.warning
        IndexStatus.INDEXED -> colors.success
        IndexStatus.FAILED -> MaterialTheme.colorScheme.error
    }
}

private fun documentMeta(document: Document): String {
    val relative = DateUtils.getRelativeTimeSpanString(
        document.importedAt,
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS,
    )
    return "${document.pageCount} 页 · $relative"
}
