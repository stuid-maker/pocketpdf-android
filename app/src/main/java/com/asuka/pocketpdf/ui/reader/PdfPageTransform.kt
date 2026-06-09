package com.asuka.pocketpdf.ui.reader

import android.graphics.RectF
import com.asuka.pocketpdf.domain.pdf.PdfPageRect

/**
 * Maps PDF text page coordinates (in PDF points) to rendered Bitmap pixel coordinates.
 *
 * PDF coordinates come from PDFium [PdfPageRect] via [com.asuka.pocketpdf.data.pdf.PdfiumDocumentSession].
 * Bitmap dimensions are specified through [com.asuka.pocketpdf.domain.pdf.PdfRenderRequest]:
 *   widthPx = renderWidth (view width in px)
 *   heightPx = widthPx * pageInfo.heightPoints / pageInfo.widthPoints
 *
 * Therefore scaleX == scaleY (proportional scaling is preserved).
 */
data class PdfPageTransform(
    val pdfPageWidthPoints: Float,
    val pdfPageHeightPoints: Float,
    val bitmapWidthPx: Int,
    val bitmapHeightPx: Int,
) {
    init {
        require(pdfPageWidthPoints > 0f) {
            "pdfPageWidthPoints must be positive, got $pdfPageWidthPoints"
        }
        require(pdfPageHeightPoints > 0f) {
            "pdfPageHeightPoints must be positive, got $pdfPageHeightPoints"
        }
        require(bitmapWidthPx > 0) {
            "bitmapWidthPx must be positive, got $bitmapWidthPx"
        }
        require(bitmapHeightPx > 0) {
            "bitmapHeightPx must be positive, got $bitmapHeightPx"
        }
    }

    val scaleX: Float get() = bitmapWidthPx.toFloat() / pdfPageWidthPoints
    val scaleY: Float get() = bitmapHeightPx.toFloat() / pdfPageHeightPoints

    fun pdfRectToBitmapRect(rect: PdfPageRect): RectF = RectF(
        rect.left * scaleX,
        rect.top * scaleY,
        rect.right * scaleX,
        rect.bottom * scaleY,
    )

    fun pdfRectsToBitmapRects(rects: List<PdfPageRect>): List<RectF> =
        rects.map { pdfRectToBitmapRect(it) }
}
