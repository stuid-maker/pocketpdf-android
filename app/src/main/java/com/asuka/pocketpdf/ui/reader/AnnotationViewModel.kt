package com.asuka.pocketpdf.ui.reader

import android.graphics.RectF
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asuka.pocketpdf.core.DispatcherProvider
import com.asuka.pocketpdf.data.local.dao.AnnotationDao
import com.asuka.pocketpdf.data.local.entity.AnnotationEntity
import com.asuka.pocketpdf.domain.model.Annotation
import com.asuka.pocketpdf.domain.model.AnnotationType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AnnotationViewModel @Inject constructor(
    private val annotationDao: AnnotationDao,
    private val dispatchers: DispatcherProvider,
) : ViewModel() {

    private val _annotations = MutableStateFlow<Map<Int, List<Annotation>>>(emptyMap())
    val annotations: StateFlow<Map<Int, List<Annotation>>> = _annotations.asStateFlow()

    private var documentId: Long = 0
    private var pageJobs: MutableMap<Int, Job> = mutableMapOf()

    fun init(documentId: Long) {
        this.documentId = documentId
    }

    fun observePage(pageIndex: Int) {
        pageJobs[pageIndex]?.cancel()
        pageJobs[pageIndex] = viewModelScope.launch(dispatchers.io) {
            annotationDao.observeByPage(documentId, pageIndex).collect { entities ->
                val list = entities.map { it.toDomain() }
                _annotations.update { it + (pageIndex to list) }
            }
        }
    }

    fun addAnnotation(
        pageIndex: Int,
        type: AnnotationType,
        color: Int,
        text: String,
        rect: RectF,
    ) {
        viewModelScope.launch(dispatchers.io) {
            annotationDao.insert(
                AnnotationEntity(
                    documentId = documentId,
                    pageIndex = pageIndex,
                    annotationType = if (type == AnnotationType.HIGHLIGHT) "highlight" else "underline",
                    color = color,
                    text = text,
                    rectLeft = rect.left,
                    rectTop = rect.top,
                    rectRight = rect.right,
                    rectBottom = rect.bottom,
                ),
            )
        }
    }

    fun deleteAnnotation(entity: AnnotationEntity) {
        viewModelScope.launch(dispatchers.io) { annotationDao.delete(entity) }
    }

    private fun AnnotationEntity.toDomain() = Annotation(
        id = id,
        documentId = documentId,
        pageIndex = pageIndex,
        type = if (annotationType == "highlight") AnnotationType.HIGHLIGHT else AnnotationType.UNDERLINE,
        color = color,
        text = text,
        rect = RectF(rectLeft, rectTop, rectRight, rectBottom),
        createdAt = createdAt,
    )
}
