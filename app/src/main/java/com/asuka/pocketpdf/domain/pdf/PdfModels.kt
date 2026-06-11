package com.asuka.pocketpdf.domain.pdf

data class PdfPageInfo(
    val pageIndex: Int,
    val widthPoints: Float,
    val heightPoints: Float,
    val rotationDegrees: Int = 0,
) {
    init {
        require(pageIndex >= 0) { "pageIndex must be non-negative" }
        require(widthPoints.isFinite() && widthPoints > 0f) {
            "widthPoints must be finite and positive"
        }
        require(heightPoints.isFinite() && heightPoints > 0f) {
            "heightPoints must be finite and positive"
        }
        require(rotationDegrees in setOf(0, 90, 180, 270)) {
            "rotationDegrees must be 0, 90, 180, or 270"
        }
    }
}

class PdfPageRect(
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
) {
    val left: Float = minOf(left, right)
    val top: Float = minOf(top, bottom)
    val right: Float = maxOf(left, right)
    val bottom: Float = maxOf(top, bottom)

    init {
        require(listOf(left, top, right, bottom).all(Float::isFinite)) {
            "rectangle coordinates must be finite"
        }
    }

    val width: Float get() = right - left
    val height: Float get() = bottom - top

    override fun equals(other: Any?): Boolean =
        other is PdfPageRect &&
            left == other.left &&
            top == other.top &&
            right == other.right &&
            bottom == other.bottom

    override fun hashCode(): Int {
        var result = left.hashCode()
        result = 31 * result + top.hashCode()
        result = 31 * result + right.hashCode()
        result = 31 * result + bottom.hashCode()
        return result
    }

    override fun toString(): String =
        "PdfPageRect(left=$left, top=$top, right=$right, bottom=$bottom)"
}

data class PdfPageText(
    val pageIndex: Int,
    val text: String,
) {
    init {
        require(pageIndex >= 0) { "pageIndex must be non-negative" }
    }
}

data class PdfSearchMatch(
    val pageIndex: Int,
    val startIndex: Int,
    val length: Int,
    val text: String,
    val rects: List<PdfPageRect>,
) {
    init {
        require(pageIndex >= 0) { "pageIndex must be non-negative" }
        require(startIndex >= 0) { "startIndex must be non-negative" }
        require(length > 0) { "length must be positive" }
        require(text.isNotEmpty()) { "text must not be empty" }
        require(rects.isNotEmpty()) { "rects must not be empty" }
    }
}

data class PdfRenderRequest(
    val pageInfo: PdfPageInfo,
    val widthPx: Int,
    val heightPx: Int,
    val renderAnnotations: Boolean = true,
) {
    init {
        require(widthPx > 0) { "widthPx must be positive" }
        require(heightPx > 0) { "heightPx must be positive" }
    }
}

sealed class PdfOpenException(message: String, cause: Throwable? = null) :
    Exception(message, cause) {
    class PasswordRequired(cause: Throwable? = null) :
        PdfOpenException("PDF password is required", cause)

    class InvalidPassword(cause: Throwable? = null) :
        PdfOpenException("PDF password is invalid", cause)

    class InvalidDocument(cause: Throwable? = null) :
        PdfOpenException("PDF document is invalid or unsupported", cause)
}

class PdfSessionClosedException : IllegalStateException("PDF document session is closed")
