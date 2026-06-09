package com.asuka.pocketpdf.data.local.mapper

import com.asuka.pocketpdf.data.local.entity.ChunkEntity
import com.asuka.pocketpdf.data.local.entity.DocumentEntity
import com.asuka.pocketpdf.domain.model.Document
import com.asuka.pocketpdf.domain.model.DocumentChunk
import com.asuka.pocketpdf.domain.model.IndexStatus

/**
 * Entity ↔ Domain 双向映射。
 *
 * 设计成顶层 internal extension 函数而非 object / class：
 * 1. 调用点 `entity.toDomain()` 比 `Mapper.entityToDomain(entity)` 更紧凑
 * 2. internal 限制可见性到模块内，避免 ui 层误用绕过 UseCase 直读 Entity
 * 3. 顶层函数无需注入，单测里直接构造 Entity 调用
 *
 * 失败语义：
 * [IndexStatus.valueOf] 在遇到未知字符串时会抛 [IllegalArgumentException]。
 * 当前选择**不静默兜底**——如果 DB 里出现非法枚举值，说明发生了字段值篡改 / 错误迁移，
 * fail-fast 比"伪装成 NOT_INDEXED 然后丢失原信息"更安全。Repository 会把这层异常包成
 * [com.asuka.pocketpdf.core.Result.Failure] 上抛。
 */
internal fun DocumentEntity.toDomain(): Document = Document(
    id = id,
    title = title,
    uri = uri,
    pageCount = pageCount,
    indexStatus = IndexStatus.valueOf(indexStatus),
    importedAt = importedAt,
    extractorVersion = extractorVersion,
)

internal fun Document.toEntity(): DocumentEntity = DocumentEntity(
    id = id,
    title = title,
    uri = uri,
    pageCount = pageCount,
    indexStatus = indexStatus.name,
    importedAt = importedAt,
    extractorVersion = extractorVersion,
)

internal fun ChunkEntity.toDomain(): DocumentChunk = DocumentChunk(
    id = id,
    documentId = documentId,
    pageIndex = pageIndex,
    chunkIndex = chunkIndex,
    text = text,
    embedding = embedding,
)

internal fun DocumentChunk.toEntity(): ChunkEntity = ChunkEntity(
    id = id,
    documentId = documentId,
    pageIndex = pageIndex,
    chunkIndex = chunkIndex,
    text = text,
    embedding = embedding,
)
