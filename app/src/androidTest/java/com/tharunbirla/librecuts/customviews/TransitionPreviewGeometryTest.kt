package com.tharunbirla.librecuts.customviews

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.RectF
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tharunbirla.librecuts.models.EditOperation
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * El clip saliente conserva su geometría durante toda la transición (M-211, punto 3). Cada test
 * nombra el criterio de `specs/transicion-preview.md` que verifica.
 *
 * Se arma la jerarquía de `activity_video_editing.xml`: el preview, la caja del lienzo centrada
 * dentro, y ahí el contenedor del video —que ya lleva el encuadre del clip **entrante**— con el
 * overlay de la transición como hermano suyo.
 */
@RunWith(AndroidJUnit4::class)
class TransitionPreviewGeometryTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    /** El encuadre del clip que entra. Nada de lo que se dibuja del saliente debería seguirlo. */
    private val incomingFrame = EditOperation.ClipFrame(scale = 1.4f, offsetX = -0.2f, offsetY = -0.2f)

    /** La mascara del clip que entra: la mitad derecha del lienzo. Tampoco debe alcanzar al saliente. */
    private val incomingMask = EditOperation.MaskConfig(
        shape = EditOperation.MaskShape.RECTANGLE,
        relativeX = 0.75f, relativeY = 0.5f, relativeWidth = 0.5f, relativeHeight = 1f
    )

    @Test
    fun ca1_snapshotKeepsTheOutgoingFrameWhileTheContainerHasTheIncomingOne() = onMain {
        val outgoing = EditOperation.ClipFrame(scale = 0.5f, offsetX = 0.1f, offsetY = 0.1f)
        val scene = scene(geometry(frame = outgoing))

        // Escala 0.5 con pivote en el centro del lienzo, desplazada (100, 56.2).
        assertRect(RectF(350f, 196.7f, 850f, 477.7f), scene.overlay.snapshotBounds())
    }

    @Test
    fun ca1_withoutFrameTheSnapshotFillsTheCanvas() = onMain {
        val scene = scene(geometry(frame = EditOperation.ClipFrame()))

        assertRect(RectF(0f, 0f, PREVIEW_W.toFloat(), CANVAS_H.toFloat()), scene.overlay.snapshotBounds())
    }

    @Test
    fun ca3_snapshotKeepsTheVideoRectWhenCropped() = onMain {
        // Recorte a la mitad central: el lienzo mide 500, pero el video sigue midiendo 1000 y
        // arranca 250 a la izquierda, igual que el playerView en VideoEditingActivity.
        val cropped = RectF(-250f, 0f, 750f, CANVAS_H.toFloat())
        val scene = scene(geometry(videoRect = cropped, reference = cropped), canvasW = 500)

        val bounds = scene.overlay.snapshotBounds()

        assertRect(cropped, bounds)
        assertEquals("proporción del video", 1000f / CANVAS_H, bounds.width() / bounds.height(), 0.01f)
    }

    @Test
    fun ca2_mirroredSnapshotIsDrawnMirrored() = onMain {
        val plain = scene(geometry(frame = EditOperation.ClipFrame()))
        val mirrored = scene(geometry(frame = EditOperation.ClipFrame(), isMirrored = true))

        // La captura es mitad roja (izquierda) y mitad azul (derecha).
        assertEquals("sin espejo, a la izquierda va el rojo", Color.RED, leftColor(plain))
        assertEquals("con espejo, a la izquierda va el azul", Color.BLUE, leftColor(mirrored))
    }

    @Test
    fun rule1_snapshotIsClippedByTheOutgoingMaskNotTheIncomingOne() = onMain {
        // Del saliente: la mitad izquierda del lienzo. El contenedor lleva la del entrante, opuesta.
        val outgoingMask = EditOperation.MaskConfig(
            shape = EditOperation.MaskShape.RECTANGLE,
            relativeX = 0.25f, relativeY = 0.5f, relativeWidth = 0.5f, relativeHeight = 1f
        )
        val scene = scene(geometry(mask = outgoingMask))

        val drawn = draw(scene)
        assertEquals("la mitad del saliente se ve", 255, Color.alpha(drawn.getPixel(250, CANVAS_H / 2)))
        assertEquals("la otra mitad no", 0, Color.alpha(drawn.getPixel(750, CANVAS_H / 2)))
    }

    private fun geometry(
        frame: EditOperation.ClipFrame = EditOperation.ClipFrame(),
        isMirrored: Boolean = false,
        mask: EditOperation.MaskConfig = EditOperation.MaskConfig(),
        videoRect: RectF = RectF(0f, 0f, PREVIEW_W.toFloat(), CANVAS_H.toFloat()),
        reference: RectF = RectF(videoRect)
    ) = TransitionPreviewOverlayView.SnapshotGeometry(
        videoRect = videoRect,
        frame = frame,
        isMirrored = isMirrored,
        mask = mask,
        reference = reference
    )

    private class Scene(val overlay: TransitionPreviewOverlayView)

    private fun scene(
        geometry: TransitionPreviewOverlayView.SnapshotGeometry,
        canvasW: Int = PREVIEW_W
    ): Scene {
        val preview = FrameLayout(context)
        val canvas = FrameLayout(context)
        val maskContainer = MaskedFrameLayout(context)
        canvas.addView(maskContainer, FrameLayout.LayoutParams(MATCH, MATCH))

        val overlay = TransitionPreviewOverlayView(context)
        canvas.addView(overlay, FrameLayout.LayoutParams(MATCH, MATCH))
        preview.addView(canvas, FrameLayout.LayoutParams(canvasW, CANVAS_H, Gravity.CENTER))

        preview.measure(exactly(PREVIEW_W), exactly(PREVIEW_H))
        preview.layout(0, 0, PREVIEW_W, PREVIEW_H)

        // Igual que applyClipFramePreview: el contenedor ya adoptó el encuadre del clip entrante.
        maskContainer.pivotX = canvasW / 2f
        maskContainer.pivotY = CANVAS_H / 2f
        maskContainer.scaleX = incomingFrame.scale
        maskContainer.scaleY = incomingFrame.scale
        maskContainer.translationX = incomingFrame.offsetX * canvasW
        maskContainer.translationY = incomingFrame.offsetY * CANVAS_H
        maskContainer.maskConfig = incomingMask

        // Wipe apenas empezado: dibuja la captura entera y sin transparencia.
        overlay.updateTransition("wipeleft", 0.05f, halvesBitmap(), geometry)
        return Scene(overlay)
    }

    /** Captura de prueba: mitad izquierda roja, mitad derecha azul. */
    private fun halvesBitmap(): Bitmap {
        val bmp = Bitmap.createBitmap(2, 1, Bitmap.Config.ARGB_8888)
        bmp.setPixel(0, 0, Color.RED)
        bmp.setPixel(1, 0, Color.BLUE)
        return bmp
    }

    /** Color cerca del borde izquierdo de la captura ya dibujada. */
    private fun leftColor(scene: Scene): Int {
        val bounds = scene.overlay.snapshotBounds()
        return draw(scene).getPixel((bounds.left + 10).toInt(), bounds.centerY().toInt())
    }

    private fun draw(scene: Scene): Bitmap {
        val overlay = scene.overlay
        val out = Bitmap.createBitmap(overlay.width, overlay.height, Bitmap.Config.ARGB_8888)
        overlay.draw(Canvas(out))
        return out
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
