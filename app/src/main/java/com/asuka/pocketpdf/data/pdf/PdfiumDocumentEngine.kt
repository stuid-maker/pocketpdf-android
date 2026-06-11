package com.asuka.pocketpdf.data.pdf

import android.content.Context
import android.os.ParcelFileDescriptor
import com.asuka.pocketpdf.core.DispatcherProvider
import com.asuka.pocketpdf.domain.pdf.PdfDocumentEngine
import com.asuka.pocketpdf.domain.pdf.PdfDocumentSession
import com.asuka.pocketpdf.domain.pdf.PdfOpenException
import dagger.hilt.android.qualifiers.ApplicationContext
import io.legere.pdfiumandroid.PdfiumCore
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PdfiumDocumentEngine @Inject constructor(
    @ApplicationContext context: Context,
    private val dispatchers: DispatcherProvider,
) : PdfDocumentEngine {

    private val pdfiumCore = PdfiumCore(context.applicationContext)

    override suspend fun open(file: File, password: String?): PdfDocumentSession =
        withContext(dispatchers.io) {
            val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            try {
                val document = pdfiumCore.newDocument(descriptor, password)
                PdfiumDocumentSession(document, dispatchers)
            } catch (error: Throwable) {
                descriptor.close()
                throw mapOpenError(error, password)
            }
        }

    private fun mapOpenError(error: Throwable, password: String?): PdfOpenException {
        val message = error.message.orEmpty().lowercase()
        return when {
            "password" in message && password == null -> PdfOpenException.PasswordRequired(error)
            "password" in message -> PdfOpenException.InvalidPassword(error)
            else -> PdfOpenException.InvalidDocument(error)
        }
    }
}
