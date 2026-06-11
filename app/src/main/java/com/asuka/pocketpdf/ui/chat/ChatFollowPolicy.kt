package com.asuka.pocketpdf.ui.chat

internal fun shouldFollowLatest(
    messageCountChanged: Boolean,
    isNearBottom: Boolean,
    isStreaming: Boolean,
): Boolean = messageCountChanged || (isStreaming && isNearBottom)
