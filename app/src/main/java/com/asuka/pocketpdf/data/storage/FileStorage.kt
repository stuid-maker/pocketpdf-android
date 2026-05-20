package com.asuka.pocketpdf.data.storage

import java.io.File

/**
 * App 内部存储里 PDF 文件的最小操作面。
 *
 * 抽出接口的两个理由：
 * 1. Repository 单测可以注入 fake，不必真碰文件系统
 * 2. 未来如果想从 `filesDir/documents/` 切到 SAF tree URI 或外部目录，只换实现，仓库零改动
 *
 * 接口刻意保持极简——SAF 的 ContentResolver 细节、PdfBox 解析、目录创建都封在实现里，
 * domain / repository 调用方完全感知不到 `android.net.Uri` 的存在（依赖方向友好）。
 */
interface FileStorage {

    /**
     * 把 SAF 选中的源（content://...）的字节流复制到内部存储 documents/ 目录，
     * 文件名用 UUID 防同名碰撞，扩展名固定 `.pdf`。
     *
     * 失败语义：openInputStream 拿不到流 / 写入中途 IO 异常 → 删掉半成品文件并 rethrow，
     * 调用方用 [com.asuka.pocketpdf.core.resultOf] 包成 [com.asuka.pocketpdf.core.Result.Failure]。
     *
     * @param sourceUri SAF 返回 URI 的字符串形式
     * @param displayName SAF DISPLAY_NAME，仅做诊断日志用
     * @return 目标文件（已落盘）
     * @throws java.io.FileNotFoundException sourceUri 不可读 / 已失效
     * @throws java.io.IOException 写入中途 IO 异常
     */
    suspend fun copyToInternal(sourceUri: String, displayName: String): File

    /**
     * 删除内部存储下的指定 PDF 文件。
     *
     * @param absolutePath [com.asuka.pocketpdf.domain.model.Document.uri] 里存的绝对路径
     * @return 文件被成功删除返回 true；文件不存在或删除失败返回 false（不抛异常）
     */
    fun delete(absolutePath: String): Boolean
}
