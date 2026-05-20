package com.asuka.pocketpdf.domain.usecase

import com.asuka.pocketpdf.domain.embedding.EmbeddingEngine
import com.asuka.pocketpdf.domain.model.DocumentChunk
import com.asuka.pocketpdf.domain.model.RetrievalResult
import com.asuka.pocketpdf.domain.repository.DocumentRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 覆盖 [RetrieveChunksUseCase] 的正向检索、边界条件和错误防御。
 *
 * 测试策略：mock [DocumentRepository] 和 [EmbeddingEngine] 两个协作者边界，
 * UseCase 本身用真实现，覆盖余弦相似度计算、Top-K 排序、空值过滤全路径。
 */
class RetrieveChunksUseCaseTest {

    private val repository = mockk<DocumentRepository>()
    private val embeddingEngine = mockk<EmbeddingEngine>()
    private val useCase = RetrieveChunksUseCase(repository, embeddingEngine)

    // ── 辅助方法 ────────────────────────────────────────────

    private fun chunk(
        id: Long,
        pageIndex: Int,
        chunkIndex: Int,
        text: String,
        embedding: FloatArray? = null,
    ) = DocumentChunk(
        id = id,
        documentId = 1L,
        pageIndex = pageIndex,
        chunkIndex = chunkIndex,
        text = text,
        embedding = embedding,
    )

    /** 生成一个简单的 3 维向量，方向由 seed 决定 */
    private fun vec(vararg values: Float) = floatArrayOf(*values)

    // ── Phase 3 · 测试用例 ──────────────────────────────────

    @Test
    fun `returns top-5 chunks ordered by descending similarity from 10 chunks`() = runTest {
        // 10 chunks with different embedding directions
        val chunks = listOf(
            chunk(1, 0, 0, "机器学习是人工智能的分支", vec(1f, 0f, 0f)),
            chunk(2, 0, 1, "深度学习使用神经网络", vec(0.9f, 0.1f, 0f)),
            chunk(3, 0, 2, "自然语言处理是重要应用", vec(0.5f, 0.5f, 0f)),
            chunk(4, 1, 0, "计算机视觉同样重要", vec(0.1f, 0.9f, 0f)),
            chunk(5, 1, 1, "强化学习需要奖励信号", vec(0f, 1f, 0f)),
            chunk(6, 1, 2, "迁移学习减少数据需求", vec(0.7f, 0.3f, 0f)),
            chunk(7, 2, 0, "联邦学习保护隐私", vec(0.3f, 0.7f, 0f)),
            chunk(8, 2, 1, "元学习学会学习", vec(0.6f, 0.4f, 0f)),
            chunk(9, 2, 2, "自动化机器学习", vec(0.8f, 0.2f, 0f)),
            chunk(10, 3, 0, "模型可解释性很重要", vec(0.4f, 0.6f, 0f)),
        )
        val queryVec = vec(1f, 0f, 0f) // closest to chunk 1

        coEvery { repository.getChunks(1L) } returns chunks
        coEvery { embeddingEngine.getEmbedding("机器学习") } returns queryVec

        val results = useCase(documentId = 1L, query = "机器学习", topK = 5)

        assertEquals(5, results.size)
        // 验证按相似度降序
        for (i in 0 until results.size - 1) {
            assertTrue(
                "Results should be sorted descending, but ${results[i].score} < ${results[i + 1].score}",
                results[i].score >= results[i + 1].score,
            )
        }
        // 最相似的是 chunk 1（embedding 与 query 完全相同）
        assertEquals(1L, results.first().chunk.id)
    }

    @Test
    fun `returns all chunks when fewer than K available`() = runTest {
        val chunks = listOf(
            chunk(1, 0, 0, "文本 A", vec(1f, 0f, 0f)),
            chunk(2, 0, 1, "文本 B", vec(0f, 1f, 0f)),
        )
        coEvery { repository.getChunks(1L) } returns chunks
        coEvery { embeddingEngine.getEmbedding("query") } returns vec(1f, 0f, 0f)

        val results = useCase(documentId = 1L, query = "query", topK = 5)

        assertEquals(2, results.size)
    }

    @Test
    fun `returns empty list when document has no chunks`() = runTest {
        coEvery { repository.getChunks(1L) } returns emptyList()
        coEvery { embeddingEngine.getEmbedding("query") } returns vec(1f, 0f, 0f)

        val results = useCase(documentId = 1L, query = "query", topK = 5)

        assertTrue(results.isEmpty())
    }

    @Test
    fun `returns single chunk with non-negative score`() = runTest {
        val chunks = listOf(
            chunk(1, 0, 0, "唯一片段", vec(0.5f, 0.5f, 0.5f)),
        )
        coEvery { repository.getChunks(1L) } returns chunks
        coEvery { embeddingEngine.getEmbedding("query") } returns vec(0.5f, 0.5f, 0.5f)

        val results = useCase(documentId = 1L, query = "query", topK = 5)

        assertEquals(1, results.size)
        assertEquals(1L, results[0].chunk.id)
        // 完全相同向量 → 余弦相似度约等于 1.0
        assertTrue("Expected score ≈ 1.0 but got ${results[0].score}", results[0].score > 0.99f)
    }

    @Test
    fun `returns zero score for all-zero query vector`() = runTest {
        val chunks = listOf(
            chunk(1, 0, 0, "任意文本", vec(1f, 0f, 0f)),
        )
        coEvery { repository.getChunks(1L) } returns chunks
        coEvery { embeddingEngine.getEmbedding("query") } returns vec(0f, 0f, 0f)

        val results = useCase(documentId = 1L, query = "query", topK = 5)

        assertEquals(1, results.size)
        // 零向量模为 0，相似度防御返回 0
        assertEquals(0f, results[0].score)
    }

    @Test
    fun `returns zero score for all-zero chunk vector`() = runTest {
        val chunks = listOf(
            chunk(1, 0, 0, "任意文本", vec(0f, 0f, 0f)),
        )
        coEvery { repository.getChunks(1L) } returns chunks
        coEvery { embeddingEngine.getEmbedding("query") } returns vec(1f, 0f, 0f)

        val results = useCase(documentId = 1L, query = "query", topK = 5)

        assertEquals(1, results.size)
        assertEquals(0f, results[0].score)
    }

    @Test
    fun `filters out chunks with null embedding`() = runTest {
        val chunks = listOf(
            chunk(1, 0, 0, "已向量化", vec(1f, 0f, 0f)),
            chunk(2, 0, 1, "未向量化", null),
            chunk(3, 0, 2, "空向量", floatArrayOf()),
            chunk(4, 1, 0, "另一个已向量化", vec(0.5f, 0.5f, 0.5f)),
        )
        coEvery { repository.getChunks(1L) } returns chunks
        coEvery { embeddingEngine.getEmbedding("query") } returns vec(1f, 0f, 0f)

        val results = useCase(documentId = 1L, query = "query", topK = 5)

        // null embedding 和空 embedding 都被过滤，只剩 2 个
        assertEquals(2, results.size)
        val ids = results.map { it.chunk.id }.toSet()
        assertTrue(ids.contains(1L))
        assertTrue(ids.contains(4L))
    }

    @Test
    fun `returns empty list when all chunks have null embedding`() = runTest {
        val chunks = listOf(
            chunk(1, 0, 0, "未索引 1", null),
            chunk(2, 0, 1, "未索引 2", null),
        )
        coEvery { repository.getChunks(1L) } returns chunks
        coEvery { embeddingEngine.getEmbedding("query") } returns vec(1f, 0f, 0f)

        val results = useCase(documentId = 1L, query = "query", topK = 5)

        assertTrue(results.isEmpty())
    }

    @Test
    fun `returns empty list when K is zero`() = runTest {
        val chunks = listOf(
            chunk(1, 0, 0, "文本", vec(1f, 0f, 0f)),
        )
        coEvery { repository.getChunks(1L) } returns chunks
        coEvery { embeddingEngine.getEmbedding("query") } returns vec(1f, 0f, 0f)

        val results = useCase(documentId = 1L, query = "query", topK = 0)

        assertTrue(results.isEmpty())
    }

    @Test
    fun `throws when vector dimensions mismatch`() = runTest {
        val chunks = listOf(
            chunk(1, 0, 0, "3 维", vec(1f, 0f, 0f)),
        )
        coEvery { repository.getChunks(1L) } returns chunks
        // query 向量维度是 4，chunk 是 3——不匹配
        coEvery { embeddingEngine.getEmbedding("query") } returns vec(1f, 0f, 0f, 0f)

        try {
            useCase(documentId = 1L, query = "query", topK = 5)
            // 如果没抛异常，测试失败
            assertTrue("Expected IllegalStateException for dimension mismatch", false)
        } catch (e: IllegalStateException) {
            assertNotNull(e.message)
            assertTrue(e.message!!.contains("dimension", ignoreCase = true))
        }
    }
}
