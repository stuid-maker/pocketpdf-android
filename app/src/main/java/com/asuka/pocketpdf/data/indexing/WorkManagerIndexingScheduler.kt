package com.asuka.pocketpdf.data.indexing

import android.content.Context
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkManagerIndexingScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) : IndexingScheduler {

    override fun schedule(documentId: Long) {
        val request = OneTimeWorkRequestBuilder<IndexWorker>()
            .setInputData(IndexWorker.buildInputData(documentId))
            .addTag("index_doc_$documentId")
            .build()
        WorkManager.getInstance(context).enqueue(request)
        Timber.tag("IndexingScheduler").d("IndexWorker enqueued for document #%d", documentId)
    }
}
