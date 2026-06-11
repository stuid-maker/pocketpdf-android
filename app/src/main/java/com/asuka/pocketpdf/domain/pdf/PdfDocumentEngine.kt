package com.asuka.pocketpdf.domain.pdf

import java.io.File

interface PdfDocumentEngine {
    suspend fun open(file: File, password: String? = null): PdfDocumentSession
}
