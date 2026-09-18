package com.tharunbirla.librecuts.customviews

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tharunbirla.librecuts.models.EditOperation
import com.tharunbirla.librecuts.models.TextPosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/** Saber qué texto hay bajo un toque en el preview, para seleccionarlo sin pasar por la pista. */
@RunWith(AndroidJUnit4::class)
class TextOverlayHitTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test
    fun findTextAtReturnsTheTextUnderThePoint() {
        instrumentation.runOnMainSync {
            val view = createView(
                text("izq", "Uno", 0.25f, 0.5f),
                text("der", "Dos", 0.75f, 0.5f)
            )
            assertEquals("izq", view.findTextAt(250f, 300f))
            assertEquals("der", view.findTextAt(750f, 300f))
            assertNull(view.findTextAt(500f, 300f))
            assertNull(view.findTextAt(250f, 50f))
        }
    }

    @Test
    fun findTextAtIgnoresTextsOutsideCurrentTimeAndHiddenOnes() {
        instrumentation.runOnMainSync {
            val view = createView(
                text("tarde", "Tarde", 0.25f, 0.5f, startMs = 5_000L),
                text("oculto", "Oculto", 0.75f, 0.5f)
            )
            view.hiddenOperationId = "oculto"
            redraw(view)
            assertNull(view.findTextAt(250f, 300f))
            assertNull(view.findTextAt(750f, 300f))
        }
    }

    @Test
    fun tapOnTextReportsItsIdAndTapOnEmptyAreaIsNotConsumed() {
        instrumentation.runOnMainSync {
            val view = createView(text("izq", "Uno", 0.25f, 0.5f))
            var tapped: String? = null
            view.onTextTapped = { tapped = it }

            tap(view, 250f, 300f)
            assertEquals("izq", tapped)

            tapped = null
            val consumed = tap(view, 600f, 300f)
            assertNull(tapped)
            assertFalse("un toque sin texto debe seguir su camino", consumed)
        }
    }

    @Test
    fun tapIsNotConsumedWhenSelectionByTapIsDisabled() {
        instrumentation.runOnMainSync {
            val view = createView(text("izq", "Uno", 0.25f, 0.5f))
            var tapped: String? = null
            view.onTextTapped = { tapped = it }
            view.canSelectByTap = { false }

            val consumed = tap(view, 250f, 300f)
            assertNull(tapped)
            assertFalse("con recorte o audio abiertos el toque sigue su camino", consumed)
        }
    }

    private fun text(id: String, value: String, rx: Float, ry: Float, startMs: Long = 0L) =
        EditOperation.AddText(
            text = value, fontSize = 40, position = TextPosition.CENTER,
            relativeX = rx, relativeY = ry, startTimeMs = startMs, endTimeMs = 10_000L, id = id
        )

    private fun createView(vararg ops: EditOperation.AddText): TextOverlayView {
        val view = TextOverlayView(context)
        view.setVideoSize(WIDTH, HEIGHT)
        view.setTextOperations(ops.toList())
        view.currentPositionMs = 1_000L
        view.measure(
            View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(HEIGHT, View.MeasureSpec.EXACTLY)
        )
        view.layout(0, 0, WIDTH, HEIGHT)
        redraw(view)
        return view
    }

    private fun redraw(view: View) {
        view.draw(Canvas(Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)))
    }

    /** Devuelve si la vista consumió el DOWN. */
    private fun tap(view: View, x: Float, y: Float): Boolean {
        val t = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, x, y, 0)
        val consumed = view.dispatchTouchEvent(down)
        down.recycle()
        if (consumed) {
            val up = MotionEvent.obtain(t, t + 50, MotionEvent.ACTION_UP, x, y, 0)
            view.dispatchTouchEvent(up)
            up.recycle()
        }
        return consumed
    }

    companion object {
        private const val WIDTH = 1000
        private const val HEIGHT = 600
    }
}
