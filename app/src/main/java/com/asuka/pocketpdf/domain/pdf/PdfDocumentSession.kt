package com.asuka.pocketpdf.domain.pdf

import android.graphics.Bitmap
import java.io.Closeable

interface PdfDocumentSession : Closeable {
    val pageCount: Int

    suspend fun pageInfo(pageIndex: Int): PdfPageInfo

    suspend fun render(request: PdfRenderRequest): Bitmap

    suspend fun extractText(pageIndex: Int): PdfPageText

    suspend fun searchPage(pageIndex: Int, query: String): List<PdfSearchMatch>
}
