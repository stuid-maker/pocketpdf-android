package com.asuka.pocketpdf.ui.reader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * 单页 PDF 渲染视图：Canvas + Matrix 实现缩放与平移。
 *
 * 设计要点：
 * - 用 [Matrix] 控制 scale + translate，在 [onDraw] 中 `canvas.drawBitmap(bitmap, matrix, null)`
 * - 双指缩放（以焦点为中心）
 * - 单指平移（任何时候都能拖拽，1x 时边界钳制自动居中）
 * - 双击切换 1x ↔ 2.5x
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

    private var currentScale = 1f
    private var isZooming = false

    // 触控状态追踪
    private var activePointerId = -1          // 单指跟踪的 pointer id
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var lastSpan = 0f                 // 双指间距
    private var lastSpanX = 0f
    private var lastSpanY = 0f
    private var downTime = 0L                 // 按下时间（双击检测）
    private var lastTapTime = 0L              // 上次点击时间
    private var lastTapX = 0f
    private var lastTapY = 0f

    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    /** 设置当前页的位图并重置缩放 */
    fun setBitmap(bitmap: Bitmap?) {
        pageBitmap = bitmap?.let {
            if (it.isMutable) Bitmap.createBitmap(it) else it
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
        val pointerCount = event.pointerCount

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                // 取第一个手指作为跟踪指针
                activePointerId = event.getPointerId(0)
                val idx = event.findPointerIndex(activePointerId)
                lastTouchX = event.getX(idx)
                lastTouchY = event.getY(idx)
                lastSpan = 0f
                isZooming = false
                downTime = event.eventTime
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                // 第二根手指按下 → 进入缩放模式
                isZooming = true
                lastSpan = calculateSpan(event)
                // 计算双指中点
                lastSpanX = calculateMidX(event)
                lastSpanY = calculateMidY(event)
                // 保存当前矩阵状态用于缩放焦点计算
            }

            MotionEvent.ACTION_MOVE -> {
                if (pointerCount >= 2 && isZooming) {
                    // 双指缩放
                    val newSpan = calculateSpan(event)
                    if (lastSpan > 0f) {
                        val scaleFactor = newSpan / lastSpan
                        currentScale = (currentScale * scaleFactor).coerceIn(MIN_SCALE, MAX_SCALE)
                        val focusX = calculateMidX(event)
                        val focusY = calculateMidY(event)
                        applyScaleAround(focusX, focusY)
                        invalidate()
                    }
                    lastSpan = newSpan
                    lastSpanX = calculateMidX(event)
                    lastSpanY = calculateMidY(event)
                } else if (pointerCount == 1 && !isZooming) {
                    // 单指拖拽平移
                    val idx = event.findPointerIndex(activePointerId)
                    if (idx >= 0) {
                        val x = event.getX(idx)
                        val y = event.getY(idx)
                        val dx = x - lastTouchX
                        val dy = y - lastTouchY
                        applyPan(dx, dy)
                        invalidate()
                        lastTouchX = x
                        lastTouchY = y
                    }
                }
            }

            MotionEvent.ACTION_POINTER_UP -> {
                // 抬起一根手指 → 如果还剩一根则切换到单指模式
                val remaining = pointerCount - 1
                if (remaining == 1) {
                    // 找到剩下的那根手指
                    val upIndex = event.actionIndex
                    val remainingId = if (upIndex == 0) event.getPointerId(1) else event.getPointerId(0)
                    activePointerId = remainingId
                    val idx = event.findPointerIndex(activePointerId)
                    if (idx >= 0) {
                        lastTouchX = event.getX(idx)
                        lastTouchY = event.getY(idx)
                    }
                    isZooming = false
                }
            }

            MotionEvent.ACTION_UP -> {
                // 单指抬起 → 检测双击
                val upX = event.x
                val upY = event.y
                val now = event.eventTime
                val elapsed = now - lastTapTime
                val distance = sqrt((upX - lastTapX) * (upX - lastTapX) + (upY - lastTapY) * (upY - lastTapY))

                if (elapsed < DOUBLE_TAP_TIME && distance < DOUBLE_TAP_SLOP) {
                    // 双击
                    currentScale = if (currentScale < 1.5f) 2.5f else 1f
                    applyScaleAround(upX, upY)
                    invalidate()
                    lastTapTime = 0  // 防止三击触发
                } else {
                    lastTapTime = now
                    lastTapX = upX
                    lastTapY = upY
                }
                isZooming = false
            }

            MotionEvent.ACTION_CANCEL -> {
                isZooming = false
            }
        }

        return true
    }

    /** 计算双指间距 */
    private fun calculateSpan(event: MotionEvent): Float {
        val dx = event.getX(0) - event.getX(1)
        val dy = event.getY(0) - event.getY(1)
        return sqrt(dx * dx + dy * dy)
    }

    /** 计算双指中点 X */
    private fun calculateMidX(event: MotionEvent): Float =
        (event.getX(0) + event.getX(1)) / 2f

    /** 计算双指中点 Y */
    private fun calculateMidY(event: MotionEvent): Float =
        (event.getY(0) + event.getY(1)) / 2f

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

    /** 以焦点为中心缩放 */
    private fun applyScaleAround(focusX: Float, focusY: Float) {
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

        // 获取当前矩阵的变换值
        val prevValues = FloatArray(9)
        drawMatrix.getValues(prevValues)
        val prevScale = prevValues[Matrix.MSCALE_X]
        val prevTransX = prevValues[Matrix.MTRANS_X]
        val prevTransY = prevValues[Matrix.MTRANS_Y]

        // 焦点对应的 bitmap 坐标
        val focusBmpX = if (prevScale != 0f) (focusX - prevTransX) / prevScale else 0f
        val focusBmpY = if (prevScale != 0f) (focusY - prevTransY) / prevScale else 0f

        // 新平移：让焦点保持在同一屏幕位置
        var newTransX = focusX - focusBmpX * scale
        var newTransY = focusY - focusBmpY * scale

        // 边界钳制
        if (scaledW > viewW) {
            newTransX = newTransX.coerceIn(viewW - scaledW, 0f)
        } else {
            newTransX = (viewW - scaledW) / 2f
        }
        if (scaledH > viewH) {
            newTransY = newTransY.coerceIn(viewH - scaledH, 0f)
        } else {
            newTransY = (viewH - scaledH) / 2f
        }

        drawMatrix.setScale(scale, scale)
        drawMatrix.postTranslate(newTransX, newTransY)
    }

    /** 平移，并钳制边界 */
    private fun applyPan(dx: Float, dy: Float) {
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

        var newTransX = transX + dx
        var newTransY = transY + dy

        if (scaledW > viewW) {
            newTransX = newTransX.coerceIn(viewW - scaledW, 0f)
        } else {
            newTransX = transX  // 内容比视图窄时不水平移动
        }
        if (scaledH > viewH) {
            newTransY = newTransY.coerceIn(viewH - scaledH, 0f)
        } else {
            newTransY = transY  // 内容比视图矮时不垂直移动
        }

        values[Matrix.MTRANS_X] = newTransX
        values[Matrix.MTRANS_Y] = newTransY
        drawMatrix.setValues(values)
    }

    companion object {
        private const val MIN_SCALE = 0.5f
        private const val MAX_SCALE = 5f
        private const val DOUBLE_TAP_TIME = 300L  // 双击最大间隔（毫秒）
        private const val DOUBLE_TAP_SLOP = 50f   // 双击最大偏移（像素）
    }
}
