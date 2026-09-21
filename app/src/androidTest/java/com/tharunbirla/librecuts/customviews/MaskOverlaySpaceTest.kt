package com.tharunbirla.librecuts.customviews

import android.graphics.RectF
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tharunbirla.librecuts.models.EditOperation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Editar la máscara con el contorno donde de verdad está la máscara (M-211, punto 1). Cada test
 * nombra el criterio de `specs/mascara-edicion.md` que verifica.
 *
 * Se arma la misma jerarquía que `activity_video_editing.xml`: el preview (más alto que ancho), la
 * caja del lienzo 16:9 centrada con el contenedor de la máscara adentro, y el overlay de edición
 * como hermano de la caja, ocupando todo el preview.
 */
@RunWith(AndroidJUnit4::class)
class MaskOverlaySpaceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    private val halfRect = EditOperation.MaskConfig(
        shape = EditOperation.MaskShape.RECTANGLE,
        relativeX = 0.5f, relativeY = 0.5f, relativeWidth = 0.5f, relativeHeight = 0.5f
    )

    @Test
    fun ca1_contourMatchesTheCanvasNotTheWholePreview() = onMain {
        val scene = scene(frame = null)

        val bounds = scene.overlay.contourBounds()

        // Mitad del lienzo (1000x562.5) centrada en él; el lienzo empieza en y = 368.75.
        assertRect(RectF(250f, 509.375f, 750f, 790.625f), bounds)
    }

    @Test
    fun ca2_contourFollowsTheClipFrame() = onMain {
        val scene = scene(frame = EditOperation.ClipFrame(scale = 0.5f, offsetX = 0.1f, offsetY = 0.1f))

        val bounds = scene.overlay.contourBounds()

        // Escala 0.5 con pivote en el centro del lienzo y desplazamiento de (100, 56.25).
        assertRect(RectF(475f, 635.3125f, 725f, 775.9375f), bounds)
    }

    @Test
    fun ca3_dragMovesTheContourOneToOneWithoutFrame() = onMain {
        val scene = scene(frame = null)
        val before = scene.overlay.contourBounds()

        drag(scene.overlay, 500f, 650f, dx = 100f, dy = 100f)

        val after = scene.overlay.contourBounds()
        assertEquals(100f, after.centerX() - before.centerX(), 0.5f)
        assertEquals(100f, after.centerY() - before.centerY(), 0.5f)
    }

    @Test
    fun ca3_dragMovesTheContourOneToOneWithFrame() = onMain {
        val scene = scene(frame = EditOperation.ClipFrame(scale = 0.5f, offsetX = 0.1f, offsetY = 0.1f))
        val before = scene.overlay.contourBounds()

        drag(scene.overlay, 600f, 700f, dx = 100f, dy = 100f)

        val after = scene.overlay.contourBounds()
        assertEquals(100f, after.centerX() - before.centerX(), 0.5f)
        assertEquals(100f, after.centerY() - before.centerY(), 0.5f)
    }

    @Test
    fun ca3_dragUpdatesTheSavedMaskInCanvasFractions() = onMain {
        val scene = scene(frame = EditOperation.ClipFrame(scale = 0.5f))

        drag(scene.overlay, 500f, 650f, dx = 100f, dy = 0f)

        // 100 px en pantalla con escala 0.5 son 200 px del lienzo de 1000: una quinta parte.
        assertEquals(0.7f, scene.overlay.maskConfig.relativeX, 0.001f)
    }

    @Test
    fun ca4_pinchScalesTheContourTheSameWithAndWithoutFrame() = onMain {
        val withoutFrame = pinchRatio(scene(frame = null))
        val withFrame = pinchRatio(scene(frame = EditOperation.ClipFrame(scale = 0.5f)))

        // ScaleGestureDetector arranca tarde por la tolerancia del toque y se queda algo corto del
        // doble (igual que en TextOverlayPinchTest); lo que no puede cambiar es que el encuadre
        // altere la proporción.
        assertTrue("esperado ~2, fue $withFrame", withFrame in 1.7f..2.1f)
        assertEquals(withoutFrame, withFrame, 0.01f)
    }

    private fun pinchRatio(scene: Scene): Float {
        val before = scene.overlay.contourBounds()
        pinch(scene.overlay, fromSpan = 400f, toSpan = 800f)
        val after = scene.overlay.contourBounds()
        assertEquals("ancho y alto crecen igual", after.width() / before.width(), after.height() / before.height(), 0.01f)
        return after.width() / before.width()
    }

    @Test
    fun withoutAContainerItBehavesAsBefore() = onMain {
        val overlay = VideoMaskOverlayView(context)
        overlay.isEditingMode = true
        overlay.maskConfig = halfRect
        overlay.measure(exactly(PREVIEW_W), exactly(PREVIEW_H))
        overlay.layout(0, 0, PREVIEW_W, PREVIEW_H)

        assertRect(RectF(250f, 325f, 750f, 975f), overlay.contourBounds())
    }

    private class Scene(val overlay: VideoMaskOverlayView)

    private fun scene(frame: EditOperation.ClipFrame?): Scene {
        val preview = FrameLayout(context)
        val canvas = FrameLayout(context)
        val maskContainer = MaskedFrameLayout(context)
        canvas.addView(maskContainer, FrameLayout.LayoutParams(MATCH, MATCH))
        preview.addView(canvas, FrameLayout.LayoutParams(PREVIEW_W, CANVAS_H, Gravity.CENTER))
        val overlay = VideoMaskOverlayView(context)
        preview.addView(overlay, FrameLayout.LayoutParams(MATCH, MATCH))

        preview.measure(exactly(PREVIEW_W), exactly(PREVIEW_H))
        preview.layout(0, 0, PREVIEW_W, PREVIEW_H)

        // Igual que VideoEditingActivity.applyClipFramePreview, con la referencia = todo el lienzo.
        if (frame != null) {
            maskContainer.pivotX = PREVIEW_W / 2f
            maskContainer.pivotY = CANVAS_H / 2f
            maskContainer.scaleX = frame.scale
            maskContainer.scaleY = frame.scale
            maskContainer.translationX = frame.offsetX * PREVIEW_W
            maskContainer.translationY = frame.offsetY * CANVAS_H
        }

        overlay.followContainer(maskContainer)
        overlay.isEditingMode = true
        overlay.maskConfig = halfRect
        return Scene(overlay)
    }

    private fun drag(view: View, x: Float, y: Float, dx: Float, dy: Float, steps: Int = 10) {
        val downTime = SystemClock.uptimeMillis()
        var time = downTime
        fun send(action: Int, px: Float, py: Float) {
            val event = MotionEvent.obtain(downTime, time, action, px, py, 0)
            view.dispatchTouchEvent(event)
            event.recycle()
            time += 16
        }
        send(MotionEvent.ACTION_DOWN, x, y)
        for (i in 1..steps) {
            send(MotionEvent.ACTION_MOVE, x + dx * i / steps, y + dy * i / steps)
        }
        send(MotionEvent.ACTION_UP, x + dx, y + dy)
    }

    /** Dos dedos simétricos respecto al centro del preview, como en TextOverlayPinchTest. */
    private fun pinch(view: View, fromSpan: Float, toSpan: Float, steps: Int = 60) {
        val cx = PREVIEW_W / 2f
        val cy = PREVIEW_H / 2f
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

    private fun assertRect(expected: RectF, actual: RectF) {
        assertEquals("left", expected.left, actual.left, 1f)
        assertEquals("top", expected.top, actual.top, 1f)
        assertEquals("right", expected.right, actual.right, 1f)
        assertEquals("bottom", expected.bottom, actual.bottom, 1f)
    }

    private fun exactly(size: Int) = View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY)

    private fun onMain(block: () -> Unit) = instrumentation.runOnMainSync(block)

    companion object {
        private const val PREVIEW_W = 1000
        private const val PREVIEW_H = 1300
        // Lienzo 16:9 de 1000 de ancho: 562.5, redondeado por el layout.
        private const val CANVAS_H = 562
        private const val MATCH = FrameLayout.LayoutParams.MATCH_PARENT
    }
}
