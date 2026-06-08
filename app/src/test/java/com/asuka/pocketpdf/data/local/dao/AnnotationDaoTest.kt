package com.asuka.pocketpdf.data.local.dao

import androidx.room.Room
import com.asuka.pocketpdf.data.local.AppDatabase
import com.asuka.pocketpdf.data.local.entity.AnnotationEntity
import com.asuka.pocketpdf.data.local.entity.DocumentEntity
import com.asuka.pocketpdf.domain.model.IndexStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AnnotationDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var documentDao: DocumentDao
    private lateinit var annotationDao: AnnotationDao

    @Before
    fun createDb() {
        db = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            AppDatabase::class.java,
        )
            .allowMainThreadQueries()
            .build()

        documentDao = db.documentDao()
        annotationDao = db.annotationDao()
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun `insert + observeByPage returns correct annotations`() = runTest {
        val docId = documentDao.insert(
            DocumentEntity(
                title = "test.pdf",
                uri = "content://test",
                pageCount = 2,
                indexStatus = IndexStatus.NOT_INDEXED.name,
                importedAt = 100L,
            ),
        )

        annotationDao.insert(
            AnnotationEntity(
                documentId = docId,
                pageIndex = 0,
                annotationType = "highlight",
                color = 0x80FFD700.toInt(),
                text = "Hello",
                rectLeft = 10f,
                rectTop = 20f,
                rectRight = 110f,
                rectBottom = 35f,
            ),
        )
        annotationDao.insert(
            AnnotationEntity(
                documentId = docId,
                pageIndex = 1,
                annotationType = "underline",
                color = 0x800000FF.toInt(),
                text = "World",
                rectLeft = 50f,
                rectTop = 100f,
                rectRight = 200f,
                rectBottom = 115f,
            ),
        )

        val page0 = annotationDao.observeByPage(docId, 0).first()
        assertEquals(1, page0.size)
        assertEquals("highlight", page0[0].annotationType)
        assertEquals("Hello", page0[0].text)

        val page1 = annotationDao.observeByPage(docId, 1).first()
        assertEquals(1, page1.size)
        assertEquals("underline", page1[0].annotationType)
    }

    @Test
    fun `delete removes annotation`() = runTest {
        val docId = documentDao.insert(
            DocumentEntity(
                title = "test.pdf",
                uri = "content://test",
                pageCount = 1,
                indexStatus = IndexStatus.NOT_INDEXED.name,
                importedAt = 100L,
            ),
        )

        val annotation = AnnotationEntity(
            documentId = docId,
            pageIndex = 0,
            annotationType = "highlight",
            color = 0x80FFD700.toInt(),
            text = "test",
            rectLeft = 0f,
            rectTop = 0f,
            rectRight = 100f,
            rectBottom = 20f,
        )
        val id = annotationDao.insert(annotation)

        val initial = annotationDao.observeByPage(docId, 0).first()
        assertEquals(1, initial.size)

        annotationDao.delete(initial[0])
        val after = annotationDao.observeByPage(docId, 0).first()
        assertTrue(after.isEmpty())
    }

    @Test
    fun `getByDocument returns all page annotations ordered`() = runTest {
        val docId = documentDao.insert(
            DocumentEntity(
                title = "test.pdf",
                uri = "content://test",
                pageCount = 3,
                indexStatus = IndexStatus.NOT_INDEXED.name,
                importedAt = 100L,
            ),
        )

        // Insert out of page order to verify ordering
        annotationDao.insert(
            AnnotationEntity(
                documentId = docId, pageIndex = 2,
                annotationType = "highlight", color = 0x80FFD700.toInt(),
                text = "page2", rectLeft = 0f, rectTop = 0f,
                rectRight = 10f, rectBottom = 10f,
            ),
        )
        annotationDao.insert(
            AnnotationEntity(
                documentId = docId, pageIndex = 0,
                annotationType = "underline", color = 0x8000FF00.toInt(),
                text = "page0", rectLeft = 0f, rectTop = 0f,
                rectRight = 10f, rectBottom = 10f,
            ),
        )

        val all = annotationDao.getByDocument(docId)
        assertEquals(2, all.size)
        assertEquals(0, all[0].pageIndex)  // page 0 first (ordered by pageIndex)
        assertEquals(2, all[1].pageIndex)
    }

    @Test
    fun `cascade delete works when document is deleted`() = runTest {
        val docId = documentDao.insert(
            DocumentEntity(
                title = "test.pdf",
                uri = "content://test",
                pageCount = 1,
                indexStatus = IndexStatus.NOT_INDEXED.name,
                importedAt = 100L,
            ),
        )

        annotationDao.insert(
            AnnotationEntity(
                documentId = docId,
                pageIndex = 0,
                annotationType = "highlight",
                color = 0x80FFD700.toInt(),
                text = "cascade test",
                rectLeft = 0f,
                rectTop = 0f,
                rectRight = 100f,
                rectBottom = 20f,
            ),
        )

        // Verify annotation exists
        val before = annotationDao.observeByPage(docId, 0).first()
        assertEquals(1, before.size)

        // Delete the document
        documentDao.deleteById(docId)

        // Annotation should be cascade-deleted
        val after = annotationDao.observeByPage(docId, 0).first()
        assertTrue("Annotations should be cascade-deleted", after.isEmpty())
    }
}
