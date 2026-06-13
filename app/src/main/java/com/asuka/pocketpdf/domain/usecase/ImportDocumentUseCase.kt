package com.asuka.pocketpdf.domain.usecase

import com.asuka.pocketpdf.core.Result
import com.asuka.pocketpdf.domain.model.Document
import com.asuka.pocketpdf.domain.repository.DocumentRepository
import javax.inject.Inject

/**
 * Imports a SAF-selected PDF through the repository.
 *
 * The URI remains a String so the domain layer does not depend on `android.net.Uri`
 * and can be covered by plain JVM tests.
 *
 * @param sourceUri String form of the SAF URI (`content://...`)
 * @param displayName SAF display name used as the initial title
 */
class ImportDocumentUseCase @Inject constructor(
    private val repository: DocumentRepository,
) {
    suspend operator fun invoke(
        sourceUri: String,
        displayName: String,
    ): Result<Document> = repository.importDocument(sourceUri, displayName)
}
