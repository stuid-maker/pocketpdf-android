package com.asuka.pocketpdf.data.local.mapper

import com.asuka.pocketpdf.data.local.entity.DocumentEntity
import com.asuka.pocketpdf.domain.model.Document
import com.asuka.pocketpdf.domain.model.IndexStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class DocumentMappersTest {

    @Test
    fun `entity to domain preserves all fields`() {
        val entity = DocumentEntity(
            id = 42L,
            title = "Compiler Construction.pdf",
            uri = "/data/data/com.asuka.pocketpdf/files/documents/abc.pdf",
            pageCount = 480,
            indexStatus = "INDEXED",
            importedAt = 1_234_567_890L,
        )

        val domain = entity.toDomain()

        assertEquals(42L, domain.id)
        assertEquals("Compiler Construction.pdf", domain.title)
        assertEquals("/data/data/com.asuka.pocketpdf/files/documents/abc.pdf", domain.uri)
        assertEquals(480, domain.pageCount)
        assertEquals(IndexStatus.INDEXED, domain.indexStatus)
        assertEquals(1_234_567_890L, domain.importedAt)
    }

    @Test
    fun `domain to entity preserves all fields including enum name`() {
        val domain = Document(
            id = 7L,
            title = "Another.pdf",
            uri = "/x.pdf",
            pageCount = 50,
            indexStatus = IndexStatus.FAILED,
            importedAt = 999L,
        )

        val entity = domain.toEntity()

        assertEquals(7L, entity.id)
        assertEquals("Another.pdf", entity.title)
        assertEquals("/x.pdf", entity.uri)
        assertEquals(50, entity.pageCount)
        assertEquals("FAILED", entity.indexStatus)
        assertEquals(999L, entity.importedAt)
    }

    @Test
    fun `entity to domain to entity round trip is lossless`() {
        val original = DocumentEntity(
            id = 1L,
            title = "Round Trip",
            uri = "/r.pdf",
            pageCount = 7,
            indexStatus = "NOT_INDEXED",
            importedAt = 1L,
        )

        val roundTripped = original.toDomain().toEntity()

        assertEquals(original, roundTripped)
    }

    @Test
    fun `mapper handles every IndexStatus enum case`() {
        IndexStatus.entries.forEach { status ->
            val entity = DocumentEntity(
                id = 0L,
                title = "t",
                uri = "u",
                pageCount = 0,
                indexStatus = status.name,
                importedAt = 0L,
            )
            assertEquals(
                "round-trip failed for IndexStatus.$status",
                status,
                entity.toDomain().indexStatus,
            )
        }
    }

    @Test
    fun `entity to domain fails loudly on unknown indexStatus value`() {
        val bogus = DocumentEntity(
            id = 0L,
            title = "t",
            uri = "u",
            pageCount = 0,
            indexStatus = "GARBAGE_VALUE",
            importedAt = 0L,
        )
        assertThrows(IllegalArgumentException::class.java) { bogus.toDomain() }
    }
}
