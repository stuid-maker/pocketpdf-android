package com.asuka.pocketpdf.data.pdf

/**
 * 单字符/词位置的 PDF 文本坐标。
 *
 * 所有坐标来自 PdfBox [com.tom_roush.pdfbox.text.TextPosition]，
 * 是 PDF 页面坐标系（左下角原点，未经缩放的 user-space 单位）。
 */
data class PdfTextPosition(
    val text: String,
    val pageIndex: Int,       // 0-based
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
)

/**
 * 单页文本及其全部字符坐标。
 */
data class PageTextWithPositions(
    val pageIndex: Int,
    val fullText: String,
    val positions: List<PdfTextPosition>,
    val pdfPageWidth: Float,   // PDF user-space 页面宽度（来自 MediaBox）
    val pdfPageHeight: Float,  // PDF user-space 页面高度
)
