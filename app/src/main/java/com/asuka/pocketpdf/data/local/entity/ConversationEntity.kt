package com.asuka.pocketpdf.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 一个文档下的独立聊天会话。
 *
 * 每个文档可拥有多条会话（v1.3.0 多会话），每条会话有自己的消息历史与 LLM 上下文。
 * 删除文档时通过 CASCADE 自动清理其全部会话（再由会话 CASCADE 清理消息）。
 */
@Entity(
    tableName = "conversations",
    foreignKeys = [ForeignKey(
        entity = DocumentEntity::class,
        parentColumns = ["id"],
        childColumns = ["documentId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("documentId")],
)
data class ConversationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val documentId: Long,
    val title: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)
