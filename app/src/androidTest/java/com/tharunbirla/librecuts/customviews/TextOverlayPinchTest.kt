package com.tharunbirla.librecuts.customviews

import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.widget.EditText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Pellizco de dos dedos sobre el texto en edición. El ADB del dispositivo no permite inyectar
 * multitoque (sendevent requiere root), así que se simula con MotionEvent sobre la vista.
 */
@RunWith(AndroidJUnit4::class)
class TextOverlayPinchTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test
    fun pinchOutToDoubleSpanRoughlyDoublesFontSize() {
        instrumentation.runOnMainSync {
            val overlay = createOverlay(fontSize = 36)
            pinch(overlay, fromSpan = 400f, toSpan = 800f)
            val size = overlay.getCurrentFontSize()
            assertTrue("esperado ~72, fue $size", size in 60..84)
        }
    }

    @Test
    fun pinchInToHalfSpanRoughlyHalvesFontSize() {
        instrumentation.runOnMainSync {
            val overlay = createOverlay(fontSize = 72)
            pinch(overlay, fromSpan = 800f, toSpan = 400f)
            val size = overlay.getCurrentFontSize()
            assertTrue("esperado ~36, fue $size", size in 30..42)
        }
    }

    @Test
    fun pinchKeepsTextCenteredWhereItWas() {
        instrumentation.runOnMainSync {
            val overlay = createOverlay(fontSize = 36)
            val text = overlay.getChildAt(0) as EditText
            val centerBefore = text.x + text.width / 2f

            pinch(overlay, fromSpan = 400f, toSpan = 800f)
            layout(overlay)

            val centerAfter = text.x + text.width / 2f
            assertEquals("el centro del texto se movió", centerBefore, centerAfter, 2f)
            assertEquals(0.5f, overlay.getRelativeX(), 0.01f)
        }
    }

    private fun createOverlay(fontSize: Int): DraggableTextOverlayView {
        val overlay = DraggableTextOverlayView(context)
        overlay.setVideoSize(WIDTH, HEIGHT)
        overlay.activate("Hola", fontSize)
        layout(overlay)
        return overlay
    }

    private fun layout(view: View) {
        view.measure(
            View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(HEIGHT, View.MeasureSpec.EXACTLY)
        )
        view.layout(0, 0, WIDTH, HEIGHT)
    }

    /** Dos dedos simétricos respecto al centro, movidos en pasos chicos como un gesto real. */
    private fun pinch(view: View, fromSpan: Float, toSpan: Float, steps: Int = 60) {
        val cx = WIDTH / 2f
        val cy = HEIGHT / 2f
        val downTime = SystemClock.uptimeMillis()
        var time = downTime

        fun send(action: Int, span: Float, pointers: Int) {
            val props = Array(pointers) { MotionEvent.PointerProperties().apply { id = it; toolType = MotionEvent.TOOL_TYPE_FINGER } }
            val coords = Array(pointers) {
                MotionEvent.PointerCoords().apply {
                    x = if (it == 0) cx - span / 2 else cx + span / 2
                    y = cy
                    pressure = 1f
                    size = 1f
                }
            }
            val event = MotionEvent.obtain(downTime, time, action, pointers, props, coords, 0, 0, 1f, 1f, 0, 0, 0, 0)
            view.dispatchTouchEvent(event)
            event.recycle()
            time += 16
        }

        val pointer1 = 1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT
        send(MotionEvent.ACTION_DOWN, fromSpan, 1)
        send(MotionEvent.ACTION_POINTER_DOWN or pointer1, fromSpan, 2)
        for (i in 1..steps) {
            send(MotionEvent.ACTION_MOVE, fromSpan + (toSpan - fromSpan) * i / steps, 2)
        }
        send(MotionEvent.ACTION_POINTER_UP or pointer1, toSpan, 2)
        send(MotionEvent.ACTION_UP, toSpan, 1)
    }

    companion object {
        private const val WIDTH = 1000
        private const val HEIGHT = 600
    }
}
