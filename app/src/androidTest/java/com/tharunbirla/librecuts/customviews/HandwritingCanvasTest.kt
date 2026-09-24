package com.tharunbirla.librecuts.customviews

import android.graphics.Bitmap
import android.graphics.Rect
import android.graphics.RectF
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

/**
 * Un trazo sigue sobre el mismo punto del video aunque cambie dónde se dibuja el video (M-244). El
 * rect cambia al abrir el panel lateral de la tablet, que angosta el preview, y al girar con el
 * dibujo abierto. Los trazos se guardan en coordenadas de la vista, así que sin transformarlos el
 * PNG exportado cambiaría.
 */
@RunWith(AndroidJUnit4::class)
class HandwritingCanvasTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    /** Video 16:9 a lo ancho de la vista, como en vertical. */
    private val wideRect = RectF(0f, 100f, 1000f, 662.5f)

    /** El mismo video más chico y desplazado, como al aparecer el panel lateral. */
    private val narrowRect = RectF(100f, 200f, 740f, 560f)

    @Test
    fun aStrokeKeepsItsPlaceOnTheVideoWhenTheVideoRectChanges() = onMain {
        val canvas = canvasWith(wideRect)
        drawStroke(canvas, 200f, 300f, 600f, 400f)
        val before = inkBounds(canvas.exportToBitmap(640, 360)!!)

        canvas.setVideoRect(narrowRect)

        assertSameBounds(before, inkBounds(canvas.exportToBitmap(640, 360)!!))
    }

    @Test
    fun anUndoneStrokeComesBackInItsPlaceAfterTheRectChanges() = onMain {
        val canvas = canvasWith(wideRect)
        drawStroke(canvas, 200f, 300f, 600f, 400f)
        val before = inkBounds(canvas.exportToBitmap(640, 360)!!)
        canvas.undo()

        canvas.setVideoRect(narrowRect)
        canvas.redo()

        assertSameBounds(before, inkBounds(canvas.exportToBitmap(640, 360)!!))
    }

    private fun canvasWith(rect: RectF) = HandwritingCanvasView(context).apply {
        measure(exactly(1000), exactly(800))
        layout(0, 0, 1000, 800)
        setVideoRect(rect)
    }

    private fun drawStroke(view: View, x0: Float, y0: Float, x1: Float, y1: Float) {
        val t = SystemClock.uptimeMillis()
        fun send(action: Int, x: Float, y: Float) {
            val e = MotionEvent.obtain(t, t, action, x, y, 0)
            view.dispatchTouchEvent(e)
            e.recycle()
        }
        send(MotionEvent.ACTION_DOWN, x0, y0)
        for (i in 1..10) send(MotionEvent.ACTION_MOVE, x0 + (x1 - x0) * i / 10, y0 + (y1 - y0) * i / 10)
        send(MotionEvent.ACTION_UP, x1, y1)
    }

    /** El cuadro que ocupa la tinta en el PNG; comparar píxel a píxel falla por el antialiasing. */
    private fun inkBounds(bitmap: Bitmap): Rect {
        var left = bitmap.width; var top = bitmap.height; var right = -1; var bottom = -1
        for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
            if ((bitmap.getPixel(x, y) ushr 24) > 128) {
                if (x < left) left = x
                if (x > right) right = x
                if (y < top) top = y
                if (y > bottom) bottom = y
            }
        }
        return Rect(left, top, right, bottom)
    }

    private fun assertSameBounds(expected: Rect, actual: Rect) {
        val close = abs(expected.left - actual.left) <= 1 && abs(expected.top - actual.top) <= 1 &&
            abs(expected.right - actual.right) <= 1 && abs(expected.bottom - actual.bottom) <= 1
        assertTrue("el trazo se movió en el video: antes $expected, después $actual", close)
    }

    private fun exactly(px: Int) = View.MeasureSpec.makeMeasureSpec(px, View.MeasureSpec.EXACTLY)

    private fun onMain(block: () -> Unit) = instrumentation.runOnMainSync(block)
}
