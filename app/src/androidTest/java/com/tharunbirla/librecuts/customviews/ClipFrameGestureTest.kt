package com.tharunbirla.librecuts.customviews

import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tharunbirla.librecuts.models.EditOperation.ClipFrame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Gestos sobre el video principal (M-198): tocar avisa para seleccionar; con el clip seleccionado,
 * arrastrar mueve y pellizcar escala, con una sola entrada al historial por gesto.
 */
@RunWith(AndroidJUnit4::class)
class ClipFrameGestureTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test
    fun tapReportsItWithoutChangingTheFrame() {
        instrumentation.runOnMainSync {
            val (view, log) = createView()
            tap(view, 500f, 300f)
            assertEquals(1, log.taps)
            assertTrue(log.committed.isEmpty())
        }
    }

    @Test
    fun dragMovesTheClipByAFractionOfTheReference() {
        instrumentation.runOnMainSync {
            val (view, log) = createView()
            drag(view, from = 500f to 300f, to = 700f to 240f)

            val frame = log.committed.single()
            assertEquals(0.2f, frame.offsetX, 0.01f)   // 200 px / 1000
            assertEquals(-0.1f, frame.offsetY, 0.01f)  // -60 px / 600
            assertEquals(1f, frame.scale, 0f)
            assertEquals("un toque de arrastre no es un tap", 0, log.taps)
            assertTrue("el arrastre avisa en vivo", log.changing > 0)
        }
    }

    @Test
    fun dragStartsFromTheCurrentFrame() {
        instrumentation.runOnMainSync {
            val (view, log) = createView(current = ClipFrame(scale = 0.5f, offsetX = 0.1f))
            drag(view, from = 500f to 300f, to = 600f to 300f)
            val frame = log.committed.single()
            assertEquals(0.2f, frame.offsetX, 0.01f)
            assertEquals(0.5f, frame.scale, 0f)
        }
    }

    @Test
    fun pinchScalesAndFollowsTheMidpoint() {
        instrumentation.runOnMainSync {
            val (view, log) = createView()
            // Span inicial grande: ScaleGestureDetector descarta el primer tramo (span slop) y con
            // spans chicos esa pérdida pesa mucho en la proporción.
            pinch(view, fromCenterX = 500f, toCenterX = 650f, fromSpan = 400f, toSpan = 800f)
            val frame = log.committed.single()
            assertEquals(2f, frame.scale, 0.2f)
            assertEquals(0.15f, frame.offsetX, 0.02f)
        }
    }

    @Test
    fun dragThenPinchKeepsTheDraggedOffset() {
        instrumentation.runOnMainSync {
            val (view, log) = createView()
            val t = SystemClock.uptimeMillis()
            send(view, MotionEvent.ACTION_DOWN, 500f, 300f, t, t)
            for (i in 1..20) send(view, MotionEvent.ACTION_MOVE, 500f + 10f * i, 300f, t, t + i * 16L)
            twoFingerPinch(view, downTime = t, startTime = t + 400, firstX = 700f, fromSpan = 200f, toSpan = 300f)

            val frame = log.committed.single()
            assertEquals("el arrastre previo no debe perderse", 0.2f, frame.offsetX, 0.03f)
            assertTrue(frame.scale > 1.2f)
        }
    }

    @Test
    fun theFingerLeftAfterAPinchKeepsDraggingWithoutJumping() {
        instrumentation.runOnMainSync {
            val (view, log) = createView()
            val downTime = SystemClock.uptimeMillis()
            var time = downTime
            val pointer1 = 1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT
            // Dos dedos quietos (sin pellizco), se levanta el segundo y el primero arrastra 100 px.
            sendMulti(view, MotionEvent.ACTION_DOWN, downTime, time, listOf(300f)); time += 16
            sendMulti(view, MotionEvent.ACTION_POINTER_DOWN or pointer1, downTime, time, listOf(300f, 700f)); time += 16
            sendMulti(view, MotionEvent.ACTION_POINTER_UP or pointer1, downTime, time, listOf(300f, 700f)); time += 16
            for (i in 1..10) {
                sendMulti(view, MotionEvent.ACTION_MOVE, downTime, time, listOf(300f + 10f * i)); time += 16
            }
            sendMulti(view, MotionEvent.ACTION_UP, downTime, time, listOf(400f))

            // El primer MOVE tras el cambio de dedos solo reancla: se pierden 10 px, no brinca.
            assertEquals(0.09f, log.committed.single().offsetX, 0.015f)
        }
    }

    @Test
    fun cancelCommitsWhatWasAlreadyMoved() {
        instrumentation.runOnMainSync {
            val (view, log) = createView()
            val t = SystemClock.uptimeMillis()
            send(view, MotionEvent.ACTION_DOWN, 500f, 300f, t, t)
            for (i in 1..10) send(view, MotionEvent.ACTION_MOVE, 500f + 10f * i, 300f, t, t + i * 16L)
            send(view, MotionEvent.ACTION_CANCEL, 600f, 300f, t, t + 200)
            assertEquals(0.1f, log.committed.single().offsetX, 0.01f)
        }
    }

    @Test
    fun aGestureThatEndsWhereItStartedLeavesNoHistoryEntry() {
        instrumentation.runOnMainSync {
            val (view, log) = createView()
            val t = SystemClock.uptimeMillis()
            send(view, MotionEvent.ACTION_DOWN, 500f, 300f, t, t)
            for (i in 1..10) send(view, MotionEvent.ACTION_MOVE, 500f + 10f * i, 300f, t, t + i * 16L)
            for (i in 9 downTo 0) send(view, MotionEvent.ACTION_MOVE, 500f + 10f * i, 300f, t, t + (20 - i) * 16L)
            send(view, MotionEvent.ACTION_UP, 500f, 300f, t, t + 400)
            assertTrue("volver al punto de partida no es un cambio", log.committed.isEmpty())
        }
    }

    @Test
    fun gestureStartIsReportedOncePerTransform() {
        instrumentation.runOnMainSync {
            val (view, log) = createView()
            var starts = 0
            view.onTransformStart = { starts++ }
            drag(view, from = 500f to 300f, to = 700f to 300f)
            tap(view, 500f, 300f)
            assertEquals("solo el arrastre avisa (para pausar el player), el toque no", 1, starts)
        }
    }

    @Test
    fun transformIsIgnoredWhenTheClipIsNotSelectedButTapStillWorks() {
        instrumentation.runOnMainSync {
            val (view, log) = createView(canTransform = false)
            drag(view, from = 500f to 300f, to = 700f to 300f)
            assertTrue(log.committed.isEmpty())
            tap(view, 500f, 300f)
            assertEquals(1, log.taps)
        }
    }

    @Test
    fun touchIsNotConsumedWhenItCannotHandle() {
        instrumentation.runOnMainSync {
            val (view, log) = createView()
            view.canHandle = { false }
            val t = SystemClock.uptimeMillis()
            val down = MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, 500f, 300f, 0)
            assertFalse(view.dispatchTouchEvent(down))
            down.recycle()
            assertEquals(0, log.taps)
        }
    }

    @Test
    fun scaleAndOffsetAreClamped() {
        instrumentation.runOnMainSync {
            val (view, log) = createView()
            drag(view, from = 0f to 300f, to = 3000f to 300f)
            assertEquals(ClipFrame.MAX_OFFSET, log.committed.single().offsetX, 0f)
            pinch(view, fromCenterX = 500f, toCenterX = 500f, fromSpan = 100f, toSpan = 990f)
            pinch(view, fromCenterX = 500f, toCenterX = 500f, fromSpan = 100f, toSpan = 990f)
            assertEquals(ClipFrame.MAX_SCALE, log.committed.last().scale, 0f)
        }
    }

    private class Log {
        var taps = 0
        var changing = 0
        val committed = mutableListOf<ClipFrame>()
    }

    private fun createView(current: ClipFrame? = null, canTransform: Boolean = true): Pair<ClipFrameGestureView, Log> {
        val log = Log()
        var frame = current
        val view = ClipFrameGestureView(context)
        view.canHandle = { true }
        view.canTransform = { canTransform }
        view.currentFrame = { frame }
        view.referenceSize = { WIDTH.toFloat() to HEIGHT.toFloat() }
        view.onTap = { log.taps++ }
        view.onFrameChanging = { log.changing++ }
        view.onFrameCommitted = { log.committed.add(it); frame = it }
        view.measure(
            View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(HEIGHT, View.MeasureSpec.EXACTLY)
        )
        view.layout(0, 0, WIDTH, HEIGHT)
        return view to log
    }

    private fun send(view: View, action: Int, x: Float, y: Float, downTime: Long, time: Long) {
        val e = MotionEvent.obtain(downTime, time, action, x, y, 0)
        view.dispatchTouchEvent(e)
        e.recycle()
    }

    private fun tap(view: View, x: Float, y: Float) {
        val t = SystemClock.uptimeMillis()
        send(view, MotionEvent.ACTION_DOWN, x, y, t, t)
        send(view, MotionEvent.ACTION_UP, x, y, t, t + 50)
    }

    private fun drag(view: View, from: Pair<Float, Float>, to: Pair<Float, Float>, steps: Int = 20) {
        val t = SystemClock.uptimeMillis()
        send(view, MotionEvent.ACTION_DOWN, from.first, from.second, t, t)
        for (i in 1..steps) {
            val x = from.first + (to.first - from.first) * i / steps
            val y = from.second + (to.second - from.second) * i / steps
            send(view, MotionEvent.ACTION_MOVE, x, y, t, t + i * 16L)
        }
        send(view, MotionEvent.ACTION_UP, to.first, to.second, t, t + (steps + 1) * 16L)
    }

    private fun pointers(n: Int) = Array(n) { MotionEvent.PointerProperties().apply { id = it; toolType = MotionEvent.TOOL_TYPE_FINGER } }

    private fun coords(xs: List<Float>) = Array(xs.size) {
        MotionEvent.PointerCoords().apply { x = xs[it]; y = HEIGHT / 2f; pressure = 1f; size = 1f }
    }

    private fun sendMulti(view: View, action: Int, downTime: Long, time: Long, xs: List<Float>) {
        val e = MotionEvent.obtain(downTime, time, action, xs.size, pointers(xs.size), coords(xs), 0, 0, 1f, 1f, 0, 0, 0, 0)
        view.dispatchTouchEvent(e)
        e.recycle()
    }

    /** Dos dedos simétricos respecto a un centro que puede desplazarse durante el gesto. */
    private fun pinch(view: View, fromCenterX: Float, toCenterX: Float, fromSpan: Float, toSpan: Float, steps: Int = 60) {
        val downTime = SystemClock.uptimeMillis()
        var time = downTime
        fun xs(cx: Float, span: Float) = listOf(cx - span / 2, cx + span / 2)
        val pointer1 = 1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT
        sendMulti(view, MotionEvent.ACTION_DOWN, downTime, time, xs(fromCenterX, fromSpan).take(1)); time += 16
        sendMulti(view, MotionEvent.ACTION_POINTER_DOWN or pointer1, downTime, time, xs(fromCenterX, fromSpan)); time += 16
        for (i in 1..steps) {
            val f = i.toFloat() / steps
            sendMulti(view, MotionEvent.ACTION_MOVE, downTime, time, xs(fromCenterX + (toCenterX - fromCenterX) * f, fromSpan + (toSpan - fromSpan) * f)); time += 16
        }
        sendMulti(view, MotionEvent.ACTION_POINTER_UP or pointer1, downTime, time, xs(toCenterX, toSpan)); time += 16
        sendMulti(view, MotionEvent.ACTION_UP, downTime, time, xs(toCenterX, toSpan).take(1))
    }

    /** Continúa un gesto cuyo primer dedo ya está en firstX: baja el segundo y separa ambos. */
    private fun twoFingerPinch(view: View, downTime: Long, startTime: Long, firstX: Float, fromSpan: Float, toSpan: Float, steps: Int = 30) {
        var time = startTime
        fun xs(span: Float) = listOf(firstX - (span - fromSpan) / 2, firstX + fromSpan + (span - fromSpan) / 2)
        val pointer1 = 1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT
        sendMulti(view, MotionEvent.ACTION_POINTER_DOWN or pointer1, downTime, time, xs(fromSpan)); time += 16
        for (i in 1..steps) {
            sendMulti(view, MotionEvent.ACTION_MOVE, downTime, time, xs(fromSpan + (toSpan - fromSpan) * i / steps)); time += 16
        }
        sendMulti(view, MotionEvent.ACTION_POINTER_UP or pointer1, downTime, time, xs(toSpan)); time += 16
        sendMulti(view, MotionEvent.ACTION_UP, downTime, time, xs(toSpan).take(1))
    }

    companion object {
        private const val WIDTH = 1000
        private const val HEIGHT = 600
    }
}
