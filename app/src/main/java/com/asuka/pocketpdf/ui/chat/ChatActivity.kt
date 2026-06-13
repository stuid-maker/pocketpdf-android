package com.asuka.pocketpdf.ui.chat

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import com.asuka.pocketpdf.domain.model.Conversation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import com.asuka.pocketpdf.R
import com.asuka.pocketpdf.ui.theme.PocketPDFTheme
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.core.view.WindowCompat
import com.asuka.pocketpdf.core.CitationParser
import com.asuka.pocketpdf.ui.reader.ReaderActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ChatActivity : ComponentActivity() {

    private val viewModel: ChatViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val documentId = intent.getLongExtra(EXTRA_DOCUMENT_ID, -1L)
        val conversationId = intent.getLongExtra(EXTRA_CONVERSATION_ID, -1L)
        viewModel.load(documentId, conversationId.takeIf { it > 0 })

        setContent {
            PocketPDFTheme {
                ChatScreen(viewModel, documentId, onClose = { finish() })
            }
        }
    }

    companion object {
        private const val EXTRA_DOCUMENT_ID = "com.asuka.pocketpdf.extra.DOCUMENT_ID"
        private const val EXTRA_CONVERSATION_ID = "com.asuka.pocketpdf.extra.CONVERSATION_ID"

        fun newIntent(context: Context, documentId: Long, conversationId: Long = -1L): Intent =
            Intent(context, ChatActivity::class.java)
                .putExtra(EXTRA_DOCUMENT_ID, documentId)
                .putExtra(EXTRA_CONVERSATION_ID, conversationId)
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(viewModel: ChatViewModel, documentId: Long, onClose: () -> Unit) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var previousMessageCount by remember { mutableIntStateOf(0) }

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var overflowExpanded by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<Conversation?>(null) }

    val currentTitle = uiState.conversations.firstOrNull { it.id == uiState.conversationId }?.title
        ?: stringResource(R.string.chat_title)

    val lastMessage = uiState.messages.lastOrNull()
    LaunchedEffect(uiState.messages.size, lastMessage?.content, lastMessage?.isStreaming) {
        if (uiState.messages.isNotEmpty()) {
            val lastVisibleIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            val isNearBottom = lastVisibleIndex >= uiState.messages.lastIndex - 1
            val messageCountChanged = uiState.messages.size != previousMessageCount
            if (
                shouldFollowLatest(
                    messageCountChanged = messageCountChanged,
                    isNearBottom = isNearBottom,
                    isStreaming = lastMessage?.isStreaming == true,
                )
            ) {
                listState.scrollToItem(uiState.messages.lastIndex, Int.MAX_VALUE)
            }
        }
        previousMessageCount = uiState.messages.size
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ConversationDrawer(
                conversations = uiState.conversations,
                activeId = uiState.conversationId,
                onSelect = { id ->
                    viewModel.switchConversation(id)
                    scope.launch { drawerState.close() }
                },
                onNew = {
                    viewModel.newConversation()
                    scope.launch { drawerState.close() }
                },
                onRename = { renameTarget = it },
                onDelete = { viewModel.deleteConversation(it) },
            )
        },
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(currentTitle, maxLines = 1) },
                    navigationIcon = {
                        IconButton(onClick = onClose) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.chat_close))
                        }
                    },
                    actions = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Filled.Menu, contentDescription = stringResource(R.string.chat_conversations))
                        }
                        Box {
                            IconButton(onClick = { overflowExpanded = true }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.chat_more))
                            }
                            DropdownMenu(
                                expanded = overflowExpanded,
                                onDismissRequest = { overflowExpanded = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.chat_clear_current)) },
                                    onClick = {
                                        viewModel.clearCurrentConversation()
                                        overflowExpanded = false
                                    },
                                )
                            }
                        }
                    },
                )
            },
            bottomBar = {
                Column {
                    uiState.error?.let { error ->
                        Surface(color = MaterialTheme.colorScheme.errorContainer) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(error, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onErrorContainer, fontSize = 13.sp)
                                TextButton(onClick = viewModel::retryLastFailure) {
                                    Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(stringResource(R.string.chat_retry))
                                }
                                TextButton(onClick = { viewModel.clearError() }) { Text(stringResource(R.string.chat_dismiss)) }
                            }
                        }
                    }
                    ChatInputBar(
                        text = uiState.inputText,
                        onTextChange = { viewModel.onInputChanged(it) },
                        onSend = {
                            viewModel.sendMessage()
                            focusManager.clearFocus(force = true)
                            keyboardController?.hide()
                        },
                        onStop = { viewModel.stopGenerating() },
                        isGenerating = uiState.isGenerating,
                    )
                }
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                if (uiState.messages.isEmpty()) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            Text(stringResource(R.string.chat_empty_hint), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                items(uiState.messages, key = { it.id }) { message ->
                    ChatBubble(
                        message = message,
                        documentId = documentId,
                        onRegenerate = viewModel::retry,
                        pageCount = uiState.pageCount,
                    )
                }
            }
        }
    }

    renameTarget?.let { target ->
        RenameConversationDialog(
            initialName = target.title,
            onConfirm = { newName ->
                viewModel.renameConversation(target.id, newName)
                renameTarget = null
            },
            onDismiss = { renameTarget = null },
        )
    }
}

@Composable
private fun ConversationDrawer(
    conversations: List<Conversation>,
    activeId: Long,
    onSelect: (Long) -> Unit,
    onNew: () -> Unit,
    onRename: (Conversation) -> Unit,
    onDelete: (Long) -> Unit,
) {
    ModalDrawerSheet {
        Column(modifier = Modifier.fillMaxHeight()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 16.dp, end = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.chat_conversations),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onNew) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.chat_new_conversation))
                }
            }
            HorizontalDivider()
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(conversations, key = { it.id }) { conversation ->
                    val untitled = stringResource(R.string.chat_untitled)
                    NavigationDrawerItem(
                        label = { Text(conversation.title.ifBlank { untitled }, maxLines = 1) },
                        selected = conversation.id == activeId,
                        onClick = { onSelect(conversation.id) },
                        badge = {
                            Row {
                                IconButton(onClick = { onRename(conversation) }) {
                                    Icon(
                                        Icons.Filled.Edit,
                                        contentDescription = stringResource(R.string.chat_rename),
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                                IconButton(onClick = { onDelete(conversation.id) }) {
                                    Icon(
                                        Icons.Filled.Delete,
                                        contentDescription = stringResource(R.string.chat_delete),
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                        },
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun RenameConversationDialog(
    initialName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.chat_rename_dialog_title)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text(stringResource(R.string.chat_conversation_name_hint)) },
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name) },
                enabled = name.isNotBlank(),
            ) { Text(stringResource(R.string.chat_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.chat_cancel)) }
        },
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatBubble(message: ChatDisplayMessage, documentId: Long, onRegenerate: ((Long) -> Unit)? = null, pageCount: Int = Int.MAX_VALUE) {
    val isUser = message.role == ChatRole.USER
    val isDark = isSystemInDarkTheme()
    val context = LocalContext.current
    val bgColor = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val textColor = when {
        isUser -> MaterialTheme.colorScheme.onPrimary
        isDark -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onSurface
    }
    val shape = RoundedCornerShape(
        topStart = 16.dp, topEnd = 16.dp,
        bottomStart = if (isUser) 16.dp else 4.dp,
        bottomEnd = if (isUser) 4.dp else 16.dp,
    )
    var showMenu by remember { mutableStateOf(false) }
    val hasMenu = !message.isStreaming && message.role != ChatRole.USER

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start) {
        Box(
            modifier = Modifier
                .widthIn(max = 340.dp)
                .clip(shape)
                .background(bgColor)
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .then(
                    if (hasMenu) Modifier.combinedClickable(
                        onClick = {},
                        onLongClick = { showMenu = true }
                    ) else Modifier
                ),
        ) {
            Column {
                message.progress?.let { progress ->
                    Text(
                        text = progress.stageLabel,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = textColor,
                    )
                    progress.etaLabel?.let {
                        Text(
                            text = it,
                            fontSize = 12.sp,
                            color = textColor.copy(alpha = .72f),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    val fraction = progress.fraction
                    if (fraction != null) {
                        LinearProgressIndicator(
                            progress = { fraction },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    if (message.content.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                    }
                }
                if (message.isStreaming || isUser) {
                    if (message.content.isNotEmpty() || message.progress == null) {
                        Text(
                            text = message.content.ifEmpty { "…" },
                            fontFamily = FontFamily.Default,
                            fontSize = 15.sp,
                            lineHeight = 22.sp,
                            color = textColor,
                        )
                    }
                } else {
                    val citations = CitationParser.parseWithRanges(message.content)
                        .filter { it.pageIndex in 0 until pageCount }
                    if (citations.isEmpty()) {
                        Text(message.content, fontFamily = FontFamily.Default, fontSize = 15.sp, lineHeight = 22.sp, color = textColor)
                    } else {
                        val annotated = buildAnnotatedString {
                            var lastEnd = 0
                            for (c in citations) {
                                append(message.content.substring(lastEnd, c.start))
                                pushLink(LinkAnnotation.Clickable(
                                    tag = "page_${c.pageIndex}",
                                    styles = TextLinkStyles(SpanStyle(color = Color(0xFF1565C0), fontWeight = FontWeight.Bold))
                                ) {
                                    val p = c.pageIndex
                                    context.startActivity(ReaderActivity.newIntent(context, documentId, p))
                                })
                                withStyle(SpanStyle(color = Color(0xFF1565C0), fontWeight = FontWeight.Bold)) {
                                    append(stringResource(R.string.chat_citation_page, c.pageIndex + 1))
                                }
                                pop()
                                lastEnd = c.end
                            }
                            if (lastEnd < message.content.length) append(message.content.substring(lastEnd))
                        }
                        BasicText(
                            text = annotated,
                            style = TextStyle(fontSize = 15.sp, lineHeight = 22.sp, color = textColor),
                        )
                    }
                }
                if (message.isStreaming && message.progress == null) {
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.chat_copy)) },
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("chat_message", message.content))
                        showMenu = false
                    }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.chat_regenerate)) },
                    onClick = {
                        onRegenerate?.invoke(message.id)
                        showMenu = false
                    }
                )
            }
        }
    }
}

@Composable
fun ChatInputBar(text: String, onTextChange: (String) -> Unit, onSend: () -> Unit, onStop: () -> Unit, isGenerating: Boolean) {
    var isCancelling by remember { mutableStateOf(false) }
    LaunchedEffect(isGenerating) {
        if (!isGenerating) isCancelling = false
    }
    Surface(tonalElevation = 4.dp, shadowElevation = 8.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp).navigationBarsPadding(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(value = text, onValueChange = onTextChange, modifier = Modifier.weight(1f), placeholder = { Text(stringResource(R.string.chat_input_placeholder)) }, maxLines = 3, shape = RoundedCornerShape(24.dp))
            Spacer(Modifier.width(8.dp))
            if (isGenerating) {
                FilledIconButton(
                    onClick = {
                        isCancelling = true
                        onStop()
                    },
                    enabled = !isCancelling
                ) { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.chat_stop)) }
            } else {
                FilledIconButton(onClick = onSend, enabled = text.isNotBlank()) { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.chat_send)) }
            }
        }
    }
}
