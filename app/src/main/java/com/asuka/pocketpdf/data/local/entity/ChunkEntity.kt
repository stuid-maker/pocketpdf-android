package com.asuka.pocketpdf.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 文档文本切片的数据表。
 * 与 [DocumentEntity] 建立级联删除的外键关系：当文档被删除时，关联的切片也会自动被删除。
 */
@Entity(
    tableName = "chunks",
    foreignKeys = [
        ForeignKey(
            entity = DocumentEntity::class,
            parentColumns = ["id"],
            childColumns = ["documentId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("documentId") // 加索引以加速按 documentId 查询的性能
    ]
)
data class ChunkEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    val documentId: Long,
    
    val pageIndex: Int,
    
    val chunkIndex: Int,
    
    val text: String,
    
    /**
     * 将 FloatArray 向量保存为 ByteArray (BLOB)
     * 需要通过 Room TypeConverter 转换
     */
    @ColumnInfo(typeAffinity = ColumnInfo.BLOB)
    val embedding: FloatArray? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ChunkEntity

        if (id != other.id) return false
        if (documentId != other.documentId) return false
        if (pageIndex != other.pageIndex) return false
        if (chunkIndex != other.chunkIndex) return false
        if (text != other.text) return false
        if (embedding != null) {
            if (other.embedding == null) return false
            if (!embedding.contentEquals(other.embedding)) return false
        } else if (other.embedding != null) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + documentId.hashCode()
        result = 31 * result + pageIndex
        result = 31 * result + chunkIndex
        result = 31 * result + text.hashCode()
        result = 31 * result + (embedding?.contentHashCode() ?: 0)
        return result
    }
}
