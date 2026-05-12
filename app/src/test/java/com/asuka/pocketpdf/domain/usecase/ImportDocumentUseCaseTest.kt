package com.asuka.pocketpdf.domain.usecase

import com.asuka.pocketpdf.core.Result
import com.asuka.pocketpdf.core.TestDispatcherProvider
import com.asuka.pocketpdf.data.local.dao.DocumentDao
import com.asuka.pocketpdf.data.repository.DocumentRepositoryImpl
import com.asuka.pocketpdf.data.storage.FileStorage
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Day 1 沉降测试（sentinel）**。
 *
 * 跟其他 use case 测试**不一样**：本测试不 mock Repository，而是直接构造
 * 真的 [DocumentRepositoryImpl]，端到端验证 `importDocument` 当前确实走在
 * "返回 Result.Failure(NotImplementedError) 且 message 含 'DocumentRepositoryImpl.importDocument'"
 * 这条路径上。
 *
 * 设计意图（CONTRIBUTING.md §3 推荐做法 "AI 写的代码不经过编译就 commit" 的反面）：
 * - Day 2 真实现 `importDocument` 后，本测试**必然红**（要么不再 Failure，
 *   要么 message 不再包含那个字符串），逼自己同步重写为 happy-path 测试
 * - 防止"Day 2 实现写完忘加测试"的覆盖率塌方
 */
class ImportDocumentUseCaseTest {

    @Test
    fun `Day 1 stub returns Result Failure with NotImplementedError carrying the impl marker`() =
        runTest {
            val dao = mockk<DocumentDao>(relaxed = true)
            val fileStorage = mockk<FileStorage>(relaxed = true)
            val impl = DocumentRepositoryImpl(
                dao = dao,
                fileStorage = fileStorage,
                dispatchers = TestDispatcherProvider(),
            )
            val useCase = ImportDocumentUseCase(impl)

            val result = useCase(
                sourceUri = "content://com.android.providers.media.documents/document/pdf%3A42",
                displayName = "test.pdf",
            )

            assertTrue("expected Failure, got $result", result is Result.Failure)
            val error = (result as Result.Failure).error
            assertTrue(
                "expected NotImplementedError, got ${error::class.simpleName}",
                error is NotImplementedError,
            )
            assertTrue(
                "Day 2 marker missing from message: '${error.message}'. " +
                    "If you rewrote importDocument, also rewrite this sentinel test.",
                error.message?.contains("DocumentRepositoryImpl.importDocument") == true,
            )
        }
}
