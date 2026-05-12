package com.asuka.pocketpdf.data.storage

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [FileStorage] 走 App 内部存储 (`filesDir/documents/`) 的实现。
 *
 * 选 `filesDir` 而非 `cacheDir`：cache 系统可能在低存储时清掉，PDF 一旦丢失
 * 不光是缓存问题——Room 里的引用变孤儿、用户阅读历史失效。filesDir 由 App 完全管控。
 *
 * 不选外部存储：minSdk 26 仍可写公共外部目录，但要权限申请，且用户卸载 App 不会清理；
 * 内部存储天然遵循"卸载即清理"。
 *
 * 单例：目录创建是幂等的轻量操作，但全 App 共享同一个 documentsDir 对象更省。
 */
@Singleton
class InternalFileStorage @Inject constructor(
    @ApplicationContext private val context: Context,
) : FileStorage {

    private val documentsDir: File by lazy {
        File(context.filesDir, DOCUMENTS_DIR_NAME).apply { mkdirs() }
    }

    override suspend fun copyToInternal(sourceUri: String, displayName: String): File {
        // TODO(asuka): W1 Day 2 接入：ContentResolver.openInputStream(Uri.parse(sourceUri))
        //  → File(documentsDir, "${UUID.randomUUID()}.pdf").outputStream() → use { copyTo }
        //  + PdfBox 解析 pageCount 在上层完成。当前 stub 让 Repository 测试可以验证
        //  "importDocument 现在是 NotImplementedError 路径"。
        throw NotImplementedError(
            "InternalFileStorage.copyToInternal: deferred to W1 Day 2 " +
                "(SAF ContentResolver stream copy)",
        )
    }

    override fun delete(absolutePath: String): Boolean = runCatching {
        File(absolutePath).delete()
    }.getOrElse { false }

    companion object {
        private const val DOCUMENTS_DIR_NAME = "documents"
    }
}
