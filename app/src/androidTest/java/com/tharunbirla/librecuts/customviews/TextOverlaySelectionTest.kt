package com.tharunbirla.librecuts.customviews

import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tharunbirla.librecuts.models.EditOperation
import com.tharunbirla.librecuts.models.TextPosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Texto seleccionado (sin teclado): arrastrar tocando encima lo mueve, tocar fuera avisa para
 * guardar o cambiar de selección, el doble toque entra a escribir y el pellizco escala y mueve.
 */
@RunWith(AndroidJUnit4::class)
class TextOverlaySelectionTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test
    fun dragStartingOnTheTextMovesIt() {
        instrumentation.runOnMainSync {
            val overlay = createSelectedOverlay()
            drag(overlay, from = 500f to 300f, to = 700f to 300f)
            assertEquals(0.7f, overlay.getRelativeX(), 0.02f)
            assertEquals(0.5f, overlay.getRelativeY(), 0.02f)
        }
    }

    @Test
    fun tapOutsideTheTextReportsItAndDoesNotMoveIt() {
        instrumentation.runOnMainSync {
            val overlay = createSelectedOverlay()
            var tappedAt: Pair<Float, Float>? = null
            overlay.onTapOutside = { x, y -> tappedAt = x to y }

            tap(overlay, 100f, 100f)

            assertNotNull("tocar fuera debe avisar", tappedAt)
            assertEquals(100f, tappedAt!!.first, 1f)
            assertEquals(0.5f, overlay.getRelativeX(), 0.01f)
        }
    }

    @Test
    fun singleTapOnTheTextKeepsItSelectedWithoutTextInput() {
        instrumentation.runOnMainSync {
            val overlay = createSelectedOverlay()
            var focused = false
            overlay.onEditingFocused = { focused = true }

            tap(overlay, 500f, 300f)

            assertTrue("un toque sencillo no debe abrir el teclado", !focused)
        }
    }

    @Test
    fun doubleTapOnTheTextEntersTextInput() {
        instrumentation.runOnMainSync {
            val overlay = createSelectedOverlay()
            var focused = false
            overlay.onEditingFocused = { focused = true }

            val t = SystemClock.uptimeMillis()
            send(overlay, MotionEvent.ACTION_DOWN, 500f, 300f, t, t)
            send(overlay, MotionEvent.ACTION_UP, 500f, 300f, t, t + 50)
            send(overlay, MotionEvent.ACTION_DOWN, 500f, 300f, t + 150, t + 150)
            send(overlay, MotionEvent.ACTION_UP, 500f, 300f, t + 150, t + 200)

            assertTrue("el doble toque debe abrir la escritura", focused)
        }
    }

    @Test
    fun pinchMovingTheFingersMovesTheText() {
        instrumentation.runOnMainSync {
            val overlay = createSelectedOverlay()
            // Los dedos se separan y a la vez se desplazan 150 px a la derecha.
            pinch(overlay, fromCenterX = 500f, toCenterX = 650f, fromSpan = 400f, toSpan = 600f)
            assertEquals(0.65f, overlay.getRelativeX(), 0.03f)
            assertTrue("también debe escalar", overlay.getCurrentFontSize() > 40)
        }
    }

    @Test
    fun pinchInSelectedModeScalesEvenIfItStartsOutsideTheText() {
        instrumentation.runOnMainSync {
            val overlay = createSelectedOverlay()
            pinch(overlay, fromCenterX = 500f, toCenterX = 500f, fromSpan = 400f, toSpan = 800f)
            assertTrue("esperado ~72, fue ${overlay.getCurrentFontSize()}", overlay.getCurrentFontSize() in 60..84)
            assertEquals(0.5f, overlay.getRelativeX(), 0.02f)
        }
    }

    private fun createSelectedOverlay(): DraggableTextOverlayView {
        val overlay = DraggableTextOverlayView(context)
        overlay.setVideoSize(WIDTH, HEIGHT)
        overlay.activateForEdit(
            EditOperation.AddText(
                text = "Hola", fontSize = 36, position = TextPosition.CENTER,
                relativeX = 0.5f, relativeY = 0.5f, id = "t1"
            )
        )
        overlay.measure(
            View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(HEIGHT, View.MeasureSpec.EXACTLY)
        )
        overlay.layout(0, 0, WIDTH, HEIGHT)
        return overlay
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

    /** Dos dedos simétricos respecto a un centro que puede desplazarse durante el gesto. */
    private fun pinch(view: View, fromCenterX: Float, toCenterX: Float, fromSpan: Float, toSpan: Float, steps: Int = 60) {
        val cy = HEIGHT / 2f
        val downTime = SystemClock.uptimeMillis()
        var time = downTime

        fun sendPinch(action: Int, cx: Float, span: Float, pointers: Int) {
            val props = Array(pointers) { MotionEvent.PointerProperties().apply { id = it; toolType = MotionEvent.TOOL_TYPE_FINGER } }
            val coords = Array(pointers) {
                MotionEvent.PointerCoords().apply {
                    x = if (it == 0) cx - span / 2 else cx + span / 2
                    y = cy
                    pressure = 1f
                    size = 1f
                }
            }
            val e = MotionEvent.obtain(downTime, time, action, pointers, props, coords, 0, 0, 1f, 1f, 0, 0, 0, 0)
            view.dispatchTouchEvent(e)
            e.recycle()
            time += 16
        }

        val pointer1 = 1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT
        sendPinch(MotionEvent.ACTION_DOWN, fromCenterX, fromSpan, 1)
        sendPinch(MotionEvent.ACTION_POINTER_DOWN or pointer1, fromCenterX, fromSpan, 2)
        for (i in 1..steps) {
            val f = i.toFloat() / steps
            sendPinch(MotionEvent.ACTION_MOVE, fromCenterX + (toCenterX - fromCenterX) * f, fromSpan + (toSpan - fromSpan) * f, 2)
        }
        sendPinch(MotionEvent.ACTION_POINTER_UP or pointer1, toCenterX, toSpan, 2)
        sendPinch(MotionEvent.ACTION_UP, toCenterX, toSpan, 1)
    }

    companion object {
        private const val WIDTH = 1000
        private const val HEIGHT = 600
    }
}
