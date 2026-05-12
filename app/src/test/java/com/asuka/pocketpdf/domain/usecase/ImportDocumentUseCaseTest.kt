package com.asuka.pocketpdf.domain.usecase

import com.asuka.pocketpdf.core.Result
import com.asuka.pocketpdf.core.TestDispatcherProvider
import com.asuka.pocketpdf.data.local.dao.DocumentDao
import com.asuka.pocketpdf.data.local.entity.DocumentEntity
import com.asuka.pocketpdf.data.pdf.PdfTextExtractor
import com.asuka.pocketpdf.data.repository.DocumentRepositoryImpl
import com.asuka.pocketpdf.data.storage.FileStorage
import com.asuka.pocketpdf.domain.model.IndexStatus
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.IOException

/**
 * W1 Day 1 阶段这里是一条 sentinel 测试，断言 importDocument 必返 NotImplementedError。
 * Day 2 真实现 importDocument 后，sentinel 通过"构造函数从 3 参变 4 参编译失败"被强制翻红
 * （比 message 字符串断言更狠的失败信号）。本文件 Day 2 完整重写为：
 *
 * - happy path × 1：编排 fileStorage + extractor + dao 正确，返回 Document(id=...)
 * - 失败路径 × 3：copy / extract / insert 三处任一抛异常，均返 Result.Failure
 * - 回滚验证 × 2：extract / insert 抛异常时，已落地的 File 被删（决策 5）
 *
 * 注：测试**直接构造真 Impl**而非 mock Repository——这样 Repository 内部的编排顺序、
 * 失败回滚逻辑都在覆盖范围内。Mock 只用在 Repository 的 3 个协作者边界上。
 */
class ImportDocumentUseCaseTest {

    private val dao = mockk<DocumentDao>()
    private val fileStorage = mockk<FileStorage>()
    private val extractor = mockk<PdfTextExtractor>()
    private val impl = DocumentRepositoryImpl(
        dao = dao,
        fileStorage = fileStorage,
        pdfTextExtractor = extractor,
        dispatchers = TestDispatcherProvider(),
    )
    private val useCase = ImportDocumentUseCase(impl)

    @Test
    fun `happy path returns Success with document carrying inserted id and parsed pageCount`() =
        runTest {
            val copied = File.createTempFile("happy", ".pdf").apply { deleteOnExit() }
            val sourceUri = "content://saf/doc/42"
            val displayName = "spec.pdf"
            val capturedEntity = slot<DocumentEntity>()

            coEvery { fileStorage.copyToInternal(sourceUri, displayName) } returns copied
            coEvery { extractor.extractPagesText(copied) } returns
                List(3) { "Page ${it + 1} body" }
            coEvery { dao.insert(capture(capturedEntity)) } returns 99L

            val result = useCase(sourceUri, displayName)

            assertTrue("expected Success, got $result", result is Result.Success)
            val document = (result as Result.Success).data
            assertEquals(99L, document.id)
            assertEquals(displayName, document.title)
            assertEquals(copied.absolutePath, document.uri)
            assertEquals(3, document.pageCount)
            assertEquals(IndexStatus.NOT_INDEXED, document.indexStatus)
            assertEquals("NOT_INDEXED", capturedEntity.captured.indexStatus)
            coVerify(exactly = 1) { fileStorage.copyToInternal(sourceUri, displayName) }
            coVerify(exactly = 1) { extractor.extractPagesText(copied) }
            coVerify(exactly = 1) { dao.insert(any()) }
        }

    @Test
    fun `copyToInternal failure surfaces as Result Failure and storage owns its own cleanup`() =
        runTest {
            val boom = IOException("SAF denied read access")
            coEvery { fileStorage.copyToInternal(any(), any()) } throws boom

            val result = useCase("content://denied", "denied.pdf")

            assertTrue(result is Result.Failure)
            assertSame(boom, (result as Result.Failure).error)
            coVerify(exactly = 0) { extractor.extractPagesText(any()) }
            coVerify(exactly = 0) { dao.insert(any()) }
        }

    @Test
    fun `extractor failure deletes copied file and surfaces Failure (rollback)`() = runTest {
        val copied = File.createTempFile("brokenpdf", ".pdf").apply {
            writeText("not a real pdf")
            deleteOnExit()
        }
        val boom = RuntimeException("PDF parse error: corrupted xref")
        coEvery { fileStorage.copyToInternal(any(), any()) } returns copied
        coEvery { extractor.extractPagesText(copied) } throws boom

        val result = useCase("content://broken", "broken.pdf")

        assertTrue(result is Result.Failure)
        assertSame(boom, (result as Result.Failure).error)
        assertFalse(
            "rollback contract violated: copied file should be deleted on extractor failure",
            copied.exists(),
        )
        coVerify(exactly = 0) { dao.insert(any()) }
    }

    @Test
    fun `dao insert failure deletes copied file and surfaces Failure (rollback)`() = runTest {
        val copied = File.createTempFile("daoboom", ".pdf").apply {
            writeText("pdf payload")
            deleteOnExit()
        }
        val boom = IllegalStateException("Room insert failed: disk full")
        coEvery { fileStorage.copyToInternal(any(), any()) } returns copied
        coEvery { extractor.extractPagesText(copied) } returns listOf("p1")
        coEvery { dao.insert(any()) } throws boom

        val result = useCase("content://x", "x.pdf")

        assertTrue(result is Result.Failure)
        assertSame(boom, (result as Result.Failure).error)
        assertFalse(
            "rollback contract violated: copied file should be deleted on dao failure",
            copied.exists(),
        )
    }

}
