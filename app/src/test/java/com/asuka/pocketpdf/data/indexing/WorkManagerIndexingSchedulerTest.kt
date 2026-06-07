package com.asuka.pocketpdf.data.indexing

import org.junit.Assert.assertEquals
import org.junit.Test

class WorkManagerIndexingSchedulerTest {

    @Test
    fun `unique work name is stable and scoped by document`() {
        assertEquals("index_doc_7", WorkManagerIndexingScheduler.uniqueWorkName(7L))
        assertEquals("index_doc_42", WorkManagerIndexingScheduler.uniqueWorkName(42L))
    }
}
