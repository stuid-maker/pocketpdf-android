package com.asuka.pocketpdf.domain.repository

import com.asuka.pocketpdf.core.Result
import com.asuka.pocketpdf.domain.model.Document
import kotlinx.coroutines.flow.Flow

/**
 * 文档库的数据契约。
 *
 * 设计要点：
 * - [observeDocuments] 返回冷流 [Flow]，由 Room 的 Flow DAO 驱动；UI 订阅后任何 INSERT / UPDATE / DELETE
 *   都会自动触发回流，**不需要 ViewModel 手动刷新**。
 * - [importDocument] 用 String 装 SAF URI，避免让 domain 引入 `android.net.Uri`（违反依赖方向）。
 *   调用方（ui）需自行 `uri.toString()`，data 层 Impl 内部再反解。
 * - 写操作（import / delete）走 [Result] 包装，读单条用 nullable Document？
 *   是因为"找不到"在业务上属正常分支（非异常），用 null 比 `Result.Failure(NotFound)` 更直接。
 *
 * 实现见 `data/repository/DocumentRepositoryImpl`，通过 Hilt `@Binds` 装配。
 */
interface DocumentRepository {

    /** 文档库列表数据源。Room Flow 实现保证 DB 变更自动推送。 */
    fun observeDocuments(): Flow<List<Document>>

    /** 按主键拉单条；不存在返回 `null`。 */
    suspend fun getDocument(id: Long): Document?

    /**
     * 把 SAF 选中的 PDF 复制到内部存储并落库。
     *
     * @param sourceUri SAF 返回的 URI 的字符串形式（`content://...`）
     * @param displayName SAF DISPLAY_NAME，作为初始标题
     * @return 成功时返回**已落库**的 [Document]（含数据库分配的 id 与解析后的 pageCount）
     */
    suspend fun importDocument(sourceUri: String, displayName: String): Result<Document>

    /** 同步删除：DB 行 + 内部存储 PDF 文件。任一环节失败回 [Result.Failure]。 */
    suspend fun deleteDocument(id: Long): Result<Unit>
}
