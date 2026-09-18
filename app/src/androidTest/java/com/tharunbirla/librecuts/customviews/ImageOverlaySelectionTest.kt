package com.tharunbirla.librecuts.customviews

import android.net.Uri
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tharunbirla.librecuts.models.EditOperation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Imagen seleccionada: arrastrar tocando encima la mueve, tocar fuera avisa para guardar o
 * cambiar de selección y el pellizco escala y la mueve siguiendo el punto medio de los dedos.
 */
@RunWith(AndroidJUnit4::class)
class ImageOverlaySelectionTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test
    fun dragStartingOnTheImageMovesIt() {
        instrumentation.runOnMainSync {
            val overlay = createSelectedOverlay()
            drag(overlay, from = 500f to 300f, to = 700f to 300f)
            assertEquals(0.7f, overlay.getRelativeX(), 0.02f)
            assertEquals(0.5f, overlay.getRelativeY(), 0.02f)
        }
    }

    @Test
    fun dragStartingOutsideTheImageDoesNotMoveIt() {
        instrumentation.runOnMainSync {
            val overlay = createSelectedOverlay()
            drag(overlay, from = 100f to 100f, to = 300f to 100f)
            assertEquals(0.5f, overlay.getRelativeX(), 0.01f)
            assertEquals(0.5f, overlay.getRelativeY(), 0.01f)
        }
    }

    @Test
    fun tapOutsideTheImageReportsIt() {
        instrumentation.runOnMainSync {
            val overlay = createSelectedOverlay()
            var tappedAt: Pair<Float, Float>? = null
            overlay.onTapOutside = { x, y -> tappedAt = x to y }

            tap(overlay, 100f, 100f)

            assertNotNull("tocar fuera debe avisar", tappedAt)
            assertEquals(100f, tappedAt!!.first, 1f)
        }
    }

    @Test
    fun tapOnTheImageDoesNotReportTapOutside() {
        instrumentation.runOnMainSync {
            val overlay = createSelectedOverlay()
            var tappedAt: Pair<Float, Float>? = null
            overlay.onTapOutside = { x, y -> tappedAt = x to y }

            tap(overlay, 500f, 300f)

            assertNull(tappedAt)
        }
    }

    @Test
    fun tapOutsideInMaskModeDoesNotReportIt() {
        instrumentation.runOnMainSync {
            val overlay = createSelectedOverlay()
            overlay.isMaskEditingMode = true
            var tappedAt: Pair<Float, Float>? = null
            overlay.onTapOutside = { x, y -> tappedAt = x to y }

            tap(overlay, 100f, 100f)

            assertNull("en modo máscara tocar fuera no debe guardar", tappedAt)
        }
    }

    @Test
    fun pinchMovingTheFingersScalesAndMovesTheImage() {
        instrumentation.runOnMainSync {
            val overlay = createSelectedOverlay()
            // Los dedos se separan y a la vez se desplazan 150 px a la derecha.
            pinch(overlay, fromCenterX = 500f, toCenterX = 650f, fromSpan = 400f, toSpan = 600f)
            assertEquals(0.65f, overlay.getRelativeX(), 0.03f)
            assertEquals(0.3f, overlay.getRelativeWidth(), 0.03f)
        }
    }

    @Test
    fun symmetricPinchScalesWithoutMoving() {
        instrumentation.runOnMainSync {
            val overlay = createSelectedOverlay()
            var reported: Pair<Float, Float>? = null
            overlay.onPositionChanged = { rx, ry -> reported = rx to ry }

            pinch(overlay, fromCenterX = 500f, toCenterX = 500f, fromSpan = 400f, toSpan = 800f)

            assertEquals(0.4f, overlay.getRelativeWidth(), 0.04f)
            assertEquals(0.5f, overlay.getRelativeX(), 0.02f)
            assertTrue("al terminar el pellizco se avisa la posición (keyframes)", reported != null)
        }
    }

    /**
     * Imagen cuadrada de 200x200 px centrada en (500, 300). El archivo no existe: el aspecto cae
     * a 1.0 y no hace falta decodificar nada para probar los gestos.
     */
    private fun createSelectedOverlay(): DraggableImageOverlayView {
        val overlay = DraggableImageOverlayView(context)
        overlay.setVideoSize(WIDTH, HEIGHT)
        overlay.activateForEdit(
            EditOperation.AddImageOverlay(
                imageUri = Uri.fromFile(File(context.cacheDir, "no_existe.png")),
                relativeX = 0.5f, relativeY = 0.5f, relativeWidth = 0.2f, relativeHeight = 1f / 3f,
                rotationAngle = 0f, id = "i1"
            )
        )
        // Los post {} de activateForEdit no corren en una vista sin ventana, así que el tamaño y la
        // posición se aplican a mano. Dos pasadas: la primera cambia el tamaño del ImageView y
        // lo recoloca con el layout; la segunda fija la posición sobre ese layout.
        repeat(2) {
            layout(overlay)
            overlay.setProperties(0.5f, 0.5f, 0.2f, 1f / 3f, 0f, 1f, false)
        }
        layout(overlay)
        return overlay
    }

    private fun layout(overlay: View) {
        overlay.measure(
            View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(HEIGHT, View.MeasureSpec.EXACTLY)
        )
        overlay.layout(0, 0, WIDTH, HEIGHT)
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
