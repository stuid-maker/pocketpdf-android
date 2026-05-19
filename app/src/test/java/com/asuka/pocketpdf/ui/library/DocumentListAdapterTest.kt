package com.asuka.pocketpdf.ui.library

import com.asuka.pocketpdf.domain.model.Document
import com.asuka.pocketpdf.domain.model.IndexStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26])
class DocumentListAdapterTest {

    @Test
    fun `documentAt returns null for invalid adapter positions`() {
        val adapter = DocumentListAdapter(onClick = {})
        adapter.submitList(listOf(STUB))

        assertNull(adapter.documentAt(-1))
        assertNull(adapter.documentAt(adapter.itemCount))
    }

    @Test
    fun `documentAt returns document for valid adapter position`() {
        val adapter = DocumentListAdapter(onClick = {})
        adapter.submitList(listOf(STUB))

        assertEquals(STUB, adapter.documentAt(0))
    }

    private companion object {
        val STUB = Document(
            id = 1L,
            title = "stub.pdf",
            uri = "/data/data/com.asuka.pocketpdf/files/documents/stub.pdf",
            pageCount = 3,
            indexStatus = IndexStatus.NOT_INDEXED,
            importedAt = 1_700_000_000_000L,
        )
    }
}
