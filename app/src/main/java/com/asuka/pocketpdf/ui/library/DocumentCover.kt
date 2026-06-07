package com.asuka.pocketpdf.ui.library

import android.graphics.Bitmap
import kotlin.math.absoluteValue

sealed interface DocumentCover {
    data class Thumbnail(val bitmap: Bitmap) : DocumentCover
    data class Fallback(val label: String, val paletteIndex: Int) : DocumentCover
}

fun fallbackCover(documentId: Long, title: String): DocumentCover.Fallback {
    val normalizedTitle = title.trim()
    val stableHash = documentId xor normalizedTitle.hashCode().toLong()
    return DocumentCover.Fallback(
        label = normalizedTitle.firstOrNull()?.uppercase() ?: "P",
        paletteIndex = (stableHash.absoluteValue % 4).toInt(),
    )
}
