package com.asuka.pocketpdf.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room 持久化形态的文档。
 *
 * 与 domain [com.asuka.pocketpdf.domain.model.Document] 的差异：
 * - [indexStatus] 存为 String（枚举的 `name`），不存序数——避免新增 / 重排枚举值导致旧数据错位
 * - [id] 默认 0 由 Room autoGenerate；导入流程结束前的内存对象一律 0
 *
 * 索引：列表按 importedAt 倒序查，建索引提速并避免 W2/W3 列表多到几百条时的全表扫。
 */
@Entity(
    tableName = "documents",
    indices = [androidx.room.Index(value = ["importedAt"])],
)
data class DocumentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "uri")
    val uri: String,

    @ColumnInfo(name = "pageCount")
    val pageCount: Int,

    @ColumnInfo(name = "indexStatus")
    val indexStatus: String,

    @ColumnInfo(name = "importedAt")
    val importedAt: Long,
)
