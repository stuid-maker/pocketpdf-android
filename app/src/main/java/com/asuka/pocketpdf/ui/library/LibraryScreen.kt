package com.asuka.pocketpdf.ui.library

import android.text.format.DateUtils
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
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
    var searchQuery by rememberSaveable { mutableStateOf("") }
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        colors.paper,
                        colors.workspace,
                        colors.workspace,
                    ),
                ),
            ),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 72.dp, end = 6.dp)
                .size(180.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = .18f),
                            Color.Transparent,
                        ),
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 16.dp, bottom = 96.dp)
                .size(220.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = .12f),
                            Color.Transparent,
                        ),
                    ),
                ),
        )
        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(resolvedSnackbarHostState) },
            topBar = {
                LibraryHeader(
                    searchQuery = searchQuery,
                    onSearchQueryChanged = { searchQuery = it },
                    onOpenSettings = onOpenSettings,
                )
            },
            floatingActionButton = {
                if (state is LibraryUiState.Loaded) {
                    Surface(
                        onClick = onImport,
                        modifier = Modifier.shadow(
                            elevation = 10.dp,
                            shape = RoundedCornerShape(PocketRadii.Floating),
                            ambientColor = colors.shadowAmbient,
                            spotColor = colors.shadowSpot,
                            )
                            .background(colors.paper.copy(alpha = .82f))
                            .defaultMinSize(minHeight = 48.dp),
                        color = Color(0xEE302739),
                        contentColor = colors.ink,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(19.dp),
                            )
                            Spacer(Modifier.width(PocketSpacing.Sm))
                            Text(
                                stringResource(R.string.library_fab_import),
                                style = MaterialTheme.typography.labelMedium,
                            )
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
                    searchQuery = searchQuery,
                    padding = innerPadding,
                    coverLoader = coverLoader,
                    onOpenDocument = onOpenDocument,
                    onRetryIndexing = onRetryIndexing,
                    onDeleteDocument = onDeleteDocument,
                )
            }
        }
    }
}

@Composable
private fun LibraryHeader(
    searchQuery: String,
    onSearchQueryChanged: (String) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val colors = LocalPocketColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = PocketSpacing.Xl)
            .padding(top = PocketSpacing.Xxl, bottom = 10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "PocketPDF",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = colors.ink,
            )
            Surface(
                onClick = onOpenSettings,
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(18.dp),
                color = colors.paper.copy(alpha = .62f),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    colors.crystalBorder,
                ),
            ) {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = stringResource(R.string.settings_title),
                    tint = colors.ink,
                    modifier = Modifier.padding(8.dp),
                )
            }
        }
        Spacer(Modifier.height(PocketSpacing.Xl))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(26.dp),
            color = colors.paper.copy(alpha = .68f),
            border = androidx.compose.foundation.BorderStroke(1.dp, colors.crystalBorder),
        ) {
            Column(
                modifier = Modifier.padding(PocketSpacing.Xl),
            ) {
                Text(
                    text = stringResource(R.string.library_greeting),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(R.string.library_workspace_statement),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.ink,
                )
                Text(
                    text = stringResource(R.string.library_workspace_supporting),
                    modifier = Modifier.padding(top = 6.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.mutedInk,
                )
                Spacer(Modifier.height(PocketSpacing.Lg))
                LibrarySearchBar(
                    query = searchQuery,
                    onQueryChanged = onSearchQueryChanged,
                )
            }
        }
    }
}

@Composable
private fun LibraryContent(
    state: LibraryUiState.Loaded,
    searchQuery: String,
    padding: PaddingValues,
    coverLoader: DocumentCoverLoader?,
    onOpenDocument: (Long) -> Unit,
    onRetryIndexing: (Long) -> Unit,
    onDeleteDocument: (Document) -> Unit,
) {
    val documents = remember(state.documents, searchQuery) {
        val query = searchQuery.trim()
        if (query.isEmpty()) {
            state.documents
        } else {
            state.documents.filter { document ->
                document.title.contains(query, ignoreCase = true)
            }
        }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = PocketSpacing.Xl,
            top = padding.calculateTopPadding() + 10.dp,
            end = PocketSpacing.Xl,
            bottom = padding.calculateBottomPadding() + 104.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        item {
            Text(
                text = stringResource(R.string.library_continue_reading),
                style = MaterialTheme.typography.titleMedium,
                color = LocalPocketColors.current.ink,
                modifier = Modifier.padding(start = PocketSpacing.Xs, bottom = PocketSpacing.Xs),
            )
        }
        items(documents, key = { it.id }) { document ->
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

@Composable
private fun LibrarySearchBar(
    query: String,
    onQueryChanged: (String) -> Unit,
) {
    val colors = LocalPocketColors.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(PocketRadii.Control),
        color = colors.paper.copy(alpha = .58f),
        border = androidx.compose.foundation.BorderStroke(1.dp, colors.crystalBorder),
    ) {
        TextField(
            value = query,
            onValueChange = onQueryChanged,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = {
                Icon(
                    Icons.Default.Search,
                    contentDescription = null,
                    tint = colors.mutedInk,
                )
            },
            placeholder = {
                Text(
                    text = "搜索文档",
                    color = colors.mutedInk,
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.ink),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                cursorColor = MaterialTheme.colorScheme.primary,
            ),
        )
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
            .shadow(
                elevation = 3.dp,
                shape = RoundedCornerShape(PocketRadii.Card),
                ambientColor = colors.shadowAmbient,
                spotColor = colors.shadowSpot,
            )
            .background(colors.paper.copy(alpha = .82f))
            .border(
                width = 1.dp,
                color = colors.crystalBorder,
                shape = RoundedCornerShape(PocketRadii.Card),
            )
            .clickable(onClick = onOpen)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(48.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0xFFC4A4ED),
                            Color(0x557652A8),
                        ),
                    ),
                ),
        )
        Spacer(Modifier.width(11.dp))
        DocumentCoverView(document, coverLoader)
        Spacer(Modifier.width(PocketSpacing.Md))
        Column(Modifier.weight(1f)) {
            Text(
                text = document.title,
                style = MaterialTheme.typography.titleMedium,
                color = colors.ink,
                maxLines = 1,
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
                    } else Modifier
                        .clip(RoundedCornerShape(PocketRadii.Compact))
                        .background(indexColor(document.indexStatus).copy(alpha = .09f))
                        .padding(horizontal = 7.dp, vertical = 3.dp),
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
    val colors = LocalPocketColors.current
    var cover by remember(document.id, document.uri) {
        mutableStateOf<DocumentCover>(fallbackCover(document.id, document.title))
    }
    LaunchedEffect(document.id, document.uri, coverLoader) {
        cover = coverLoader?.load(document, 180, 240)
            ?: fallbackCover(document.id, document.title)
    }
    val fallbackPalettes = listOf(
        listOf(Color(0xFF7652A8), Color(0xFF302739)),
        listOf(Color(0xFF4E5F80), Color(0xFF252C3A)),
        listOf(Color(0xFF806070), Color(0xFF3D2932)),
        listOf(Color(0xFF67597A), Color(0xFF2E2735)),
    )
    Box(
        modifier = Modifier
            .size(width = 48.dp, height = 64.dp)
            .shadow(
                elevation = 3.dp,
                shape = RoundedCornerShape(8.dp),
                ambientColor = colors.shadowAmbient,
                spotColor = colors.shadowSpot,
            )
            .background(colors.paper.copy(alpha = .82f)),
        contentAlignment = Alignment.Center,
    ) {
        when (val current = cover) {
            is DocumentCover.Thumbnail -> Image(
                bitmap = current.bitmap.asImageBitmap(),
                contentDescription = document.title,
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
        IndexStatus.NEEDS_OCR -> R.string.library_badge_needs_ocr
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
        IndexStatus.NEEDS_OCR -> colors.warning
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
