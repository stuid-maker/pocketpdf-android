package com.asuka.pocketpdf.ui.ai

import com.asuka.pocketpdf.domain.usecase.FullDocumentProgress

data class GenerationProgressDisplay(
    val fraction: Float?,
    val stageLabel: String,
    val remainingSeconds: Long?,
) {
    val etaLabel: String?
        get() = remainingSeconds?.let(::formatApproximateRemaining)
}

class GenerationProgressEstimator {
    private var maxFraction: Float = 0f
    private var lastTimestamp: Long? = null
    private var previousCompleted: Int = 0
    private var currentPhase: WorkPhase? = null
    private val durations = mutableListOf<Long>()

    fun update(event: FullDocumentProgress, nowMs: Long): GenerationProgressDisplay {
        val previousMaxFraction = maxFraction

        // Compute raw fraction for this event
        val rawFraction = when (event) {
            is FullDocumentProgress.Preparing -> 0.0f
            is FullDocumentProgress.Mapping -> {
                if (event.total > 0) (event.completed.toFloat() / event.total) * 0.7f else 0f
            }
            is FullDocumentProgress.Reducing -> {
                if (event.total > 0) 0.7f + (event.completed.toFloat() / event.total) * 0.2f else 0.7f
            }
            is FullDocumentProgress.Finalizing -> 0.95f
            is FullDocumentProgress.Completed -> 1.0f
        }

        // Enforce monotonic progress
        maxFraction = maxOf(maxFraction, rawFraction)

        // Track durations and extract phase info
        var completedUnits = 0
        var totalUnits = 0

        when (event) {
            is FullDocumentProgress.Mapping -> {
                completedUnits = event.completed
                totalUnits = event.total
                recordDuration(nowMs, event.completed, WorkPhase.MAPPING)
            }
            is FullDocumentProgress.Reducing -> {
                completedUnits = event.completed
                totalUnits = event.total
                recordDuration(nowMs, event.completed, WorkPhase.REDUCING)
            }
            is FullDocumentProgress.Preparing -> {
                lastTimestamp = nowMs
                previousCompleted = 0
                currentPhase = null
                durations.clear()
            }
            is FullDocumentProgress.Finalizing, is FullDocumentProgress.Completed -> {
                lastTimestamp = nowMs
            }
        }

        // Calculate ETA from rolling average of recent durations
        val remainingSeconds: Long? = if (durations.isNotEmpty() && totalUnits > 0) {
            val avgDuration = durations.map { it.coerceAtLeast(1L) }.average().toLong()
            val remaining = totalUnits - completedUnits
            if (remaining > 0) {
                val rawSeconds = (avgDuration * remaining) / 1000
                roundEta(rawSeconds)
            } else {
                null
            }
        } else {
            null
        }

        // Determine display fraction (null for Finalizing without credible progress)
        val displayFraction: Float? = when (event) {
            is FullDocumentProgress.Finalizing -> {
                if (previousMaxFraction == 0f) null else maxFraction
            }
            is FullDocumentProgress.Completed -> 1.0f
            else -> maxFraction
        }

        // Map event to localized stage label
        val stageLabel = when (event) {
            is FullDocumentProgress.Preparing -> "正在准备"
            is FullDocumentProgress.Mapping -> "正在总结第 ${minOf(event.completed + 1, event.total)} / ${event.total} 部分"
            is FullDocumentProgress.Reducing -> "正在整合摘要第 ${minOf(event.completed + 1, event.total)} / ${event.total} 部分"
            is FullDocumentProgress.Finalizing -> "正在撰写最终总结"
            is FullDocumentProgress.Completed -> "已完成"
        }

        return GenerationProgressDisplay(
            fraction = displayFraction,
            stageLabel = stageLabel,
            remainingSeconds = remainingSeconds
        )
    }

    private fun recordDuration(nowMs: Long, completed: Int, phase: WorkPhase) {
        if (currentPhase != null && currentPhase != phase) {
            durations.clear()
            previousCompleted = 0
            lastTimestamp = nowMs
        }
        currentPhase = phase

        if (completed > previousCompleted && lastTimestamp != null) {
            val duration = nowMs - lastTimestamp!!
            if (duration > 0) {
                durations.add(duration)
                if (durations.size > 4) {
                    durations.removeAt(0)
                }
            }
        }
        previousCompleted = completed
        lastTimestamp = nowMs
    }

    private enum class WorkPhase {
        MAPPING,
        REDUCING,
    }

    private fun roundEta(seconds: Long): Long {
        if (seconds <= 0) return 0
        return if (seconds < 60) {
            ((seconds + 2) / 5) * 5
        } else {
            ((seconds + 5) / 10) * 10
        }
    }
}

internal fun formatApproximateRemaining(seconds: Long): String =
    if (seconds < 60) "约剩 ${seconds}秒"
    else "约剩 ${seconds / 60}分${seconds % 60}秒"
