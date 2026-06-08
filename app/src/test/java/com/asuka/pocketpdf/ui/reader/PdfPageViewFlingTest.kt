package com.asuka.pocketpdf.ui.reader

import android.graphics.Bitmap
import android.os.SystemClock
import android.view.MotionEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * PdfPageView Fling（滑动手势翻页）单元测试。
 *
 * 用 Robolectric 模拟 Android View 环境，通过 dispatchTouchEvent 注入
 * MotionEvent 序列来验证 fling 检测逻辑。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PdfPageViewFlingTest {

    // ── helpers ──────────────────────────────────────────────────────────────

    /** 创建一个配置好的 PdfPageView，尺寸 800×1200，带 100×100 的 Bitmap */
    private fun createView(): PdfPageView {
        val view = PdfPageView(RuntimeEnvironment.getApplication())
        view.layout(0, 0, 800, 1200)
        view.setBitmap(Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888))
        return view
    }

    /**
     * 模拟一次水平滑动手势。
     *
     * @param fromX   起始 X 坐标
     * @param toX     结束 X 坐标
     * @param y       Y 坐标（固定）
     * @param durationMs 手势总时长（毫秒），控制速度
     * @return 最后一个事件（ACTION_UP）的时间戳，连续手势时用于衔接
     */
    private fun dispatchHorizontalSwipe(
        view: PdfPageView,
        fromX: Float,
        toX: Float,
        y: Float,
        durationMs: Long,
        startTime: Long = SystemClock.uptimeMillis(),
    ): Long {
        val downTime = startTime
        val midTime = downTime + durationMs / 2
        val upTime = downTime + durationMs

        val events = listOf(
            MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, fromX, y, 0),
            MotionEvent.obtain(downTime, midTime, MotionEvent.ACTION_MOVE, (fromX + toX) / 2f, y, 0),
            MotionEvent.obtain(downTime, upTime, MotionEvent.ACTION_UP, toX, y, 0),
        )
        events.forEach { view.dispatchTouchEvent(it) }
        events.forEach { it.recycle() }
        return upTime
    }

    /**
     * 触发一次双击放大（1x → 2.5x）。
     * 两次快速点击，间隔 < 300ms，位置 < 50px。
     */
    private fun dispatchDoubleTap(
        view: PdfPageView,
        x: Float = 100f,
        y: Float = 100f,
        startTime: Long = SystemClock.uptimeMillis(),
    ): Long {
        val t0 = startTime
        // First tap: DOWN + UP
        view.dispatchTouchEvent(MotionEvent.obtain(t0, t0, MotionEvent.ACTION_DOWN, x, y, 0).also { it.recycle() })
        view.dispatchTouchEvent(MotionEvent.obtain(t0, t0 + 10, MotionEvent.ACTION_UP, x, y, 0).also { it.recycle() })

        // Second tap (within 300ms, same position)
        val t2 = t0 + 150
        view.dispatchTouchEvent(MotionEvent.obtain(t2, t2, MotionEvent.ACTION_DOWN, x, y, 0).also { it.recycle() })
        view.dispatchTouchEvent(MotionEvent.obtain(t2, t2 + 10, MotionEvent.ACTION_UP, x, y, 0).also { it.recycle() })

        return t2 + 10
    }

    // ── tests ────────────────────────────────────────────────────────────────

    @Test
    fun `left swipe at 1x zoom fires fling callback with direction=1`() {
        val view = createView()
        var capturedDirection: Int? = null
        view.onPageFling = { capturedDirection = it }

        // Left swipe: finger moves from x=300 to x=100 over 150ms → ~1333 px/s
        dispatchHorizontalSwipe(view, fromX = 300f, toX = 100f, y = 100f, durationMs = 150)

        assertEquals("Left swipe (xVelocity < 0) should produce direction=1", 1, capturedDirection)
    }

    @Test
    fun `right swipe at 1x zoom fires fling callback with direction=-1`() {
        val view = createView()
        var capturedDirection: Int? = null
        view.onPageFling = { capturedDirection = it }

        // Right swipe: finger moves from x=100 to x=300 over 150ms → ~1333 px/s
        dispatchHorizontalSwipe(view, fromX = 100f, toX = 300f, y = 100f, durationMs = 150)

        assertEquals("Right swipe (xVelocity > 0) should produce direction=-1", -1, capturedDirection)
    }

    @Test
    fun `fast swipe at 2x zoom does NOT trigger fling`() {
        val view = createView()
        var capturedDirection: Int? = null
        view.onPageFling = { capturedDirection = it }

        // First, double-tap to zoom to 2.5x
        val lastTime = dispatchDoubleTap(view)

        // Wait a bit for the double-tap state to settle, then do a fast swipe
        val swipeStart = lastTime + 50
        dispatchHorizontalSwipe(view, fromX = 300f, toX = 100f, y = 100f, durationMs = 150, startTime = swipeStart)

        assertNull("Fling should NOT fire when zoomed (currentScale > 1.05)", capturedDirection)
    }

    @Test
    fun `slow swipe below velocity threshold does NOT trigger fling`() {
        val view = createView()
        var capturedDirection: Int? = null
        view.onPageFling = { capturedDirection = it }

        // Slow swipe: 40px over 250ms → ~160 px/s (below 300 threshold)
        dispatchHorizontalSwipe(view, fromX = 300f, toX = 260f, y = 100f, durationMs = 250)

        assertNull("Slow swipe should NOT trigger fling (velocity < 300 px/s)", capturedDirection)
    }

    @Test
    fun `vertical swipe does NOT trigger fling`() {
        val view = createView()
        var capturedDirection: Int? = null
        view.onPageFling = { capturedDirection = it }

        val downTime = SystemClock.uptimeMillis()
        val midTime = downTime + 75
        val upTime = downTime + 150

        // Vertical swipe: move from y=100 to y=300, minimal horizontal movement
        val events = listOf(
            MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, 100f, 100f, 0),
            MotionEvent.obtain(downTime, midTime, MotionEvent.ACTION_MOVE, 105f, 200f, 0),
            MotionEvent.obtain(downTime, upTime, MotionEvent.ACTION_UP, 110f, 300f, 0),
        )
        events.forEach { view.dispatchTouchEvent(it) }
        events.forEach { it.recycle() }

        assertNull("Vertical swipe should NOT trigger fling (vx <= vy * 1.5)", capturedDirection)
    }
}
