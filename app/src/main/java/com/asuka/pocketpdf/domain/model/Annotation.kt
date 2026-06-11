package com.asuka.pocketpdf.domain.model

import android.graphics.RectF

enum class AnnotationType { HIGHLIGHT, UNDERLINE }

data class Annotation(
    val id: Long = 0,
    val documentId: Long,
    val pageIndex: Int,
    val type: AnnotationType,
    val color: Int,
    val text: String,
    val rect: RectF,
    val createdAt: Long,
)
