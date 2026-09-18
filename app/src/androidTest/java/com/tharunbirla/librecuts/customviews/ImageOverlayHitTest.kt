package com.tharunbirla.librecuts.customviews

import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tharunbirla.librecuts.models.EditOperation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Saber qué imagen hay bajo un toque en el preview, para seleccionarla sin pasar por la pista. */
@RunWith(AndroidJUnit4::class)
class ImageOverlayHitTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test
    fun findImageAtReturnsTheImageUnderThePoint() {
        instrumentation.runOnMainSync {
            val view = createView(
                image("izq", 0.25f, 0.5f),
                image("der", 0.75f, 0.5f)
            )
            assertEquals("izq", view.findImageAt(250f, 300f))
            assertEquals("der", view.findImageAt(750f, 300f))
            assertNull(view.findImageAt(500f, 300f))
            assertNull(view.findImageAt(250f, 30f))
        }
    }

    @Test
    fun findImageAtIgnoresImagesOutsideCurrentTimeAndHiddenOnes() {
        instrumentation.runOnMainSync {
            val view = createView(
                image("tarde", 0.25f, 0.5f, startMs = 5_000L),
                image("oculta", 0.75f, 0.5f)
            )
            view.hiddenOperationId = "oculta"
            redraw(view)
            assertNull(view.findImageAt(250f, 300f))
            assertNull(view.findImageAt(750f, 300f))
        }
    }

    @Test
    fun topmostImageWinsWhenTheyOverlap() {
        instrumentation.runOnMainSync {
            val view = createView(
                image("abajo", 0.5f, 0.5f),
                image("arriba", 0.55f, 0.5f)
            )
            assertEquals("arriba", view.findImageAt(520f, 300f))
        }
    }

    @Test
    fun rotatedImageIsHitInsideItsRotatedShapeOnly() {
        instrumentation.runOnMainSync {
            // Cuadrado de 300x300 px centrado en (500, 300), girado 45°: la esquina del rect
            // sin girar (650, 150) queda a 212 px del centro en el eje local, fuera del rombo
            // aun con el margen táctil; la punta derecha del rombo (700, 300) queda dentro.
            val view = createView(image("girada", 0.5f, 0.5f, w = 0.3f, h = 0.5f, rotation = 45f))
            assertNull(view.findImageAt(650f, 150f))
            assertEquals("girada", view.findImageAt(700f, 300f))
        }
    }

    @Test
    fun tapOnImageReportsItsIdAndTapOnEmptyAreaIsNotConsumed() {
        instrumentation.runOnMainSync {
            val view = createView(image("izq", 0.25f, 0.5f))
            var tapped: String? = null
            view.onImageTapped = { tapped = it }

            tap(view, 250f, 300f)
            assertEquals("izq", tapped)

            tapped = null
            val consumed = tap(view, 600f, 300f)
            assertNull(tapped)
            assertFalse("un toque sin imagen debe seguir su camino", consumed)
        }
    }

    @Test
    fun tapIsNotConsumedWhenTapToSelectIsDisabled() {
        instrumentation.runOnMainSync {
            val view = createView(image("izq", 0.25f, 0.5f))
            var tapped: String? = null
            view.onImageTapped = { tapped = it }
            view.isTapToSelectEnabled = false

            val consumed = tap(view, 250f, 300f)
            assertNull(tapped)
            assertFalse("con un texto en edición el toque es para el texto", consumed)
        }
    }

    // El archivo no existe: el hit test no depende de que el bitmap ya esté decodificado.
    private fun image(
        id: String, rx: Float, ry: Float, w: Float = 0.2f, h: Float = 0.2f,
        rotation: Float = 0f, startMs: Long = 0L
    ) = EditOperation.AddImageOverlay(
        imageUri = Uri.fromFile(File(context.cacheDir, "no_existe_$id.png")),
        relativeX = rx, relativeY = ry, relativeWidth = w, relativeHeight = h,
        rotationAngle = rotation, startTimeMs = startMs, endTimeMs = 10_000L, id = id
    )

    private fun createView(vararg ops: EditOperation.AddImageOverlay): ImageOverlayView {
        val view = ImageOverlayView(context)
        view.setVideoSize(WIDTH, HEIGHT)
        view.setImageOperations(ops.toList())
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
