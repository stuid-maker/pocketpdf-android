package com.asuka.pocketpdf.data.storage

import android.content.Context
import android.net.Uri
import com.asuka.pocketpdf.core.DispatcherProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileNotFoundException
import java.util.UUID
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
    private val dispatchers: DispatcherProvider,
) : FileStorage {

    private val documentsDir: File by lazy {
        File(context.filesDir, DOCUMENTS_DIR_NAME).apply { mkdirs() }
    }

    /**
     * SAF 选中的 `content://...` URI 在 data 层反解：domain 不能引 [Uri] 类，
     * 所以契约用 String 装，反解放这里。
     *
     * 失败回滚：openInputStream 拿不到流 / copyTo 中途 IO 异常 → 删掉半成品文件，
     * 让 [com.asuka.pocketpdf.data.repository.DocumentRepositoryImpl.importDocument]
     * 的 try/catch 接住异常包成 Result.Failure。**绝不留半截 .pdf**。
     */
    override suspend fun copyToInternal(sourceUri: String, displayName: String): File =
        withContext(dispatchers.io) {
            val target = File(documentsDir, "${UUID.randomUUID()}.pdf")
            try {
                val input = context.contentResolver.openInputStream(Uri.parse(sourceUri))
                    ?: throw FileNotFoundException(
                        "ContentResolver returned null InputStream for $sourceUri",
                    )
                input.use { source ->
                    target.outputStream().use { sink ->
                        source.copyTo(sink)
                    }
                }
                Timber.tag(TAG).i(
                    "copyToInternal: %s (%d bytes) → %s",
                    displayName,
                    target.length(),
                    target.absolutePath,
                )
                target
            } catch (t: Throwable) {
                target.delete()
                throw t
            }
        }

    override fun delete(absolutePath: String): Boolean = runCatching {
        File(absolutePath).delete()
    }.getOrElse { false }

    private companion object {
        const val DOCUMENTS_DIR_NAME = "documents"
        const val TAG = "InternalFileStorage"
    }
}
