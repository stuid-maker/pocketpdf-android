package com.asuka.pocketpdf.ui.reader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.RectF
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View

/**
 * 单页 PDF 渲染视图：Canvas + Matrix 实现平滑缩放与平移。
 *
 * 设计要点：
 * - 用 [Matrix] 控制 scale + translate，在 [onDraw] 中 `canvas.drawBitmap(bitmap, matrix, null)`
 * - [ScaleGestureDetector] 处理双指缩放（以焦点为中心）
 * - [GestureDetector] 处理单指平移 + 双击切换缩放
 * - 平移边界钳制：内容不会完全移出屏幕
 */
class PdfPageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    private var pageBitmap: Bitmap? = null
    private val drawMatrix = Matrix()
    private val bitmapRect = RectF()
    private val viewRect = RectF()

    private var isZooming = false
    private var currentScale = 1f

    private val scaleDetector = ScaleGestureDetector(context, ScaleListener())
    private val gestureDetector = GestureDetector(context, PanListener())

    init {
        // 关闭硬件加速的 drawing cache 以避免大 Bitmap 的渲染问题
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    /** 设置当前页的位图并重置缩放 */
    fun setBitmap(bitmap: Bitmap?) {
        pageBitmap = bitmap?.let { bitmap ->
            // 只有当传入的 bitmap 可能被外部复用时才防御性拷贝
            // 如果 bitmap 被传入后不再被外部修改，直接引用以节省内存
            if (bitmap.isMutable) Bitmap.createBitmap(bitmap) else bitmap
        }
        currentScale = 1f
        if (pageBitmap != null) {
            fitToCenter()
        }
        invalidate()
    }

    /** 复位到 1x 并居中 */
    fun resetZoom() {
        currentScale = 1f
        if (pageBitmap != null) {
            fitToCenter()
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        pageBitmap?.let { bitmap ->
            canvas.drawBitmap(bitmap, drawMatrix, null)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // 双指缩放优先，再交给手势检测器
        val inScale = scaleDetector.onTouchEvent(event)

        // 缩放进行中时不把手势交给 GestureDetector，避免两者互相干扰
        val inGesture = if (!inScale) {
            gestureDetector.onTouchEvent(event)
        } else {
            // 通知 GestureDetector 手势结束，防止残留状态干扰后续单指操作
            val cancelEvent = MotionEvent.obtain(event).apply { action = MotionEvent.ACTION_CANCEL }
            gestureDetector.onTouchEvent(cancelEvent)
            cancelEvent.recycle()
            false
        }

        if (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL) {
            isZooming = false
        }

        return true // 始终消费触摸事件，保证完整手势流
    }

    /** 将 bitmap 居中适配到 View 内，计算初始 Matrix */
    private fun fitToCenter() {
        val bmp = pageBitmap ?: return
        bitmapRect.set(0f, 0f, bmp.width.toFloat(), bmp.height.toFloat())
        viewRect.set(0f, 0f, width.toFloat(), height.toFloat())

        val scale = minOf(
            viewRect.width() / bitmapRect.width(),
            viewRect.height() / bitmapRect.height(),
        ) * currentScale

        val offsetX = (viewRect.width() - bitmapRect.width() * scale) / 2f
        val offsetY = (viewRect.height() - bitmapRect.height() * scale) / 2f

        drawMatrix.setScale(scale, scale)
        drawMatrix.postTranslate(offsetX, offsetY)
    }

    /** 以当前 [currentScale] 和指定焦点更新 Matrix，并钳制平移边界 */
    private fun applyScaleToMatrix(focusX: Float, focusY: Float) {

        val bmp = pageBitmap ?: return
        val bmpW = bmp.width.toFloat()
        val bmpH = bmp.height.toFloat()
        val viewW = width.toFloat()
        val viewH = height.toFloat()

        val scale = if (bmpW > 0 && bmpH > 0) {
            minOf(viewW / bmpW, viewH / bmpH) * currentScale
        } else {
            currentScale
        }

        val scaledW = bmpW * scale
        val scaledH = bmpH * scale

        // 计算当前焦点在 bitmap 上的归一化位置
        val prevValues = FloatArray(9)
        drawMatrix.getValues(prevValues)
        val prevScale = prevValues[Matrix.MSCALE_X]
        val prevTransX = prevValues[Matrix.MTRANS_X]
        val prevTransY = prevValues[Matrix.MTRANS_Y]

        // 焦点对应的 bitmap 坐标
        val focusBmpX = (focusX - prevTransX) / prevScale
        val focusBmpY = (focusY - prevTransY) / prevScale

        // 新平移：让焦点保持在同一屏幕位置
        var newTransX = focusX - focusBmpX * scale
        var newTransY = focusY - focusBmpY * scale

        // 边界钳制：内容边缘不能进入 View 内部太远（保留至少 1/4 内容可见）
        val minX = viewW - scaledW
        val minY = viewH - scaledH
        if (scaledW > viewW) {
            newTransX = newTransX.coerceIn(minX, 0f)
        } else {
            newTransX = (viewW - scaledW) / 2f
        }
        if (scaledH > viewH) {
            newTransY = newTransY.coerceIn(minY, 0f)
        } else {
            newTransY = (viewH - scaledH) / 2f
        }

        drawMatrix.setScale(scale, scale)
        drawMatrix.postTranslate(newTransX, newTransY)
    }

    /** 在缩放时平移，并钳制边界 */
    private fun applyPan(dx: Float, dy: Float) {
        if (currentScale <= 1f) return

        val values = FloatArray(9)
        drawMatrix.getValues(values)
        val scale = values[Matrix.MSCALE_X]
        val transX = values[Matrix.MTRANS_X]
        val transY = values[Matrix.MTRANS_Y]

        val bmp = pageBitmap ?: return
        val scaledW = bmp.width.toFloat() * scale
        val scaledH = bmp.height.toFloat() * scale
        val viewW = width.toFloat()
        val viewH = height.toFloat()

        var newTransX = transX - dx
        var newTransY = transY - dy

        if (scaledW > viewW) {
            newTransX = newTransX.coerceIn(viewW - scaledW, 0f)
        } else {
            newTransX = transX
        }
        if (scaledH > viewH) {
            newTransY = newTransY.coerceIn(viewH - scaledH, 0f)
        } else {
            newTransY = transY
        }

        values[Matrix.MTRANS_X] = newTransX
        values[Matrix.MTRANS_Y] = newTransY
        drawMatrix.setValues(values)
    }

    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            isZooming = true
            return true
        }

        override fun onScale(detector: ScaleGestureDetector): Boolean {
            // scaleFactor 是增量值（currentSpan / previousSpan），逐帧累乘
            currentScale = (currentScale * detector.scaleFactor).coerceIn(MIN_SCALE, MAX_SCALE)
            applyScaleToMatrix(
                focusX = detector.focusX,
                focusY = detector.focusY,
            )
            invalidate()
            return true
        }
    }

    private inner class PanListener : GestureDetector.SimpleOnGestureListener() {
        override fun onScroll(
            e1: MotionEvent?,
            e2: MotionEvent,
            distanceX: Float,
            distanceY: Float,
        ): Boolean {
            if (isZooming || currentScale <= 1f) return false
            applyPan(distanceX, distanceY)
            invalidate()
            return true
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            // 双击切换 1x ↔ 2.5x
            currentScale = if (currentScale < 1.5f) 2.5f else 1f
            applyScaleToMatrix(e.x, e.y)
            invalidate()
            return true
        }
    }

    companion object {
        private const val MIN_SCALE = 0.5f
        private const val MAX_SCALE = 5f
    }
}
