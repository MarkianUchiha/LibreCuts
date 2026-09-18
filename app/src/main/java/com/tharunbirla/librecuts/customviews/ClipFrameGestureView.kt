package com.tharunbirla.librecuts.customviews

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import com.tharunbirla.librecuts.models.EditOperation.ClipFrame

/**
 * Capa invisible sobre el video principal (M-198), debajo de los overlays: solo recibe los toques
 * que ningún texto o imagen reclamó. Un toque avisa para seleccionar el clip; con el clip
 * seleccionado, arrastrar lo mueve y pellizcar lo escala siguiendo el punto medio de los dedos.
 *
 * No guarda estado del proyecto: lee el encuadre actual al empezar el gesto, avisa en vivo con
 * onFrameChanging (solo preview) y una sola vez con onFrameCommitted al soltar, para que cada
 * gesto sea una sola entrada del historial de deshacer.
 */
class ClipFrameGestureView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    /** Si esta capa atiende el toque (otro modo de edición puede ser dueño). Se consulta en cada DOWN. */
    var canHandle: () -> Boolean = { false }

    /** Si el gesto puede encuadrar (clip seleccionado y bajo el playhead). Se consulta en cada DOWN. */
    var canTransform: () -> Boolean = { false }

    var currentFrame: () -> ClipFrame? = { null }

    /** Tamaño en px del video completo en el preview: los offsets son fracciones de él. */
    var referenceSize: () -> Pair<Float, Float> = { width.toFloat() to height.toFloat() }

    var onTap: (() -> Unit)? = null
    /** Primer cambio de un gesto de encuadre; la Activity pausa el player para que no pelee con el dedo. */
    var onTransformStart: (() -> Unit)? = null
    var onFrameChanging: ((ClipFrame) -> Unit)? = null
    var onFrameCommitted: ((ClipFrame) -> Unit)? = null

    private var isTransforming = false
    private var startFrame = ClipFrame()
    private var working = ClipFrame()
    private var changed = false

    // Anclas del arrastre de un dedo. Se reinician cuando cambia la cantidad de dedos para que el
    // dedo que queda tras un pellizco no haga brincar el clip.
    private var lastX = 0f
    private var lastY = 0f
    private var resetDragAnchor = false

    // Hasta pasar el touch slop no se arrastra: un toque con temblor no debe mover el clip ni
    // dejar una entrada en el historial.
    private val touchSlop = android.view.ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var isDragging = false

    private var lastFocusX = 0f
    private var lastFocusY = 0f

    private val tapDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean = true
        override fun onSingleTapUp(e: MotionEvent): Boolean {
            onTap?.invoke()
            return true
        }
    })

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            lastFocusX = detector.focusX
            lastFocusY = detector.focusY
            return isTransforming
        }

        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val (refW, refH) = referenceSize()
            update(
                working.copy(
                    scale = working.scale * detector.scaleFactor,
                    offsetX = working.offsetX + safeDiv(detector.focusX - lastFocusX, refW),
                    offsetY = working.offsetY + safeDiv(detector.focusY - lastFocusY, refH)
                )
            )
            lastFocusX = detector.focusX
            lastFocusY = detector.focusY
            return true
        }
    })

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            if (!canHandle()) return false
            // Se decide una vez por gesto: si el clip se selecciona a mitad del gesto, este no encuadra.
            isTransforming = canTransform()
            startFrame = currentFrame() ?: ClipFrame()
            working = startFrame
            changed = false
            isDragging = false
            resetDragAnchor = false
            downX = event.x
            downY = event.y
            lastX = event.x
            lastY = event.y
        }

        tapDetector.onTouchEvent(event)
        if (isTransforming) scaleDetector.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_POINTER_UP -> resetDragAnchor = true
            MotionEvent.ACTION_MOVE -> if (isTransforming && event.pointerCount == 1 && !scaleDetector.isInProgress) {
                dragTo(event.x, event.y)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                // Comparar contra el inicio: ir y volver al mismo punto no debe dejar una entrada vacía de deshacer.
                if (isTransforming && changed && !sameFrame(working, startFrame)) onFrameCommitted?.invoke(working)
                isTransforming = false
            }
        }
        return true
    }

    private fun dragTo(x: Float, y: Float) {
        if (resetDragAnchor) {
            // Cambió la cantidad de dedos: el dedo que queda sigue arrastrando desde donde está.
            resetDragAnchor = false
            isDragging = true
        } else {
            if (!isDragging) {
                if (Math.hypot((x - downX).toDouble(), (y - downY).toDouble()) <= touchSlop) return
                // lastX sigue en el DOWN: el arrastre cuenta desde ahí, no desde que se cruzó el slop.
                isDragging = true
            }
            val (refW, refH) = referenceSize()
            update(
                working.copy(
                    offsetX = working.offsetX + safeDiv(x - lastX, refW),
                    offsetY = working.offsetY + safeDiv(y - lastY, refH)
                )
            )
        }
        lastX = x
        lastY = y
    }

    private fun update(frame: ClipFrame) {
        val scale = frame.scale.coerceIn(ClipFrame.MIN_SCALE, ClipFrame.MAX_SCALE)
        val maxOffset = ClipFrame.maxOffsetFor(scale)
        working = ClipFrame(
            scale = scale,
            offsetX = frame.offsetX.coerceIn(-maxOffset, maxOffset),
            offsetY = frame.offsetY.coerceIn(-maxOffset, maxOffset)
        )
        if (!changed) onTransformStart?.invoke()
        changed = true
        onFrameChanging?.invoke(working)
    }

    // Con tolerancia: ir y volver suma y resta floats y puede dejar residuos como 1e-8.
    private fun sameFrame(a: ClipFrame, b: ClipFrame): Boolean =
        kotlin.math.abs(a.scale - b.scale) < 1e-4f &&
            kotlin.math.abs(a.offsetX - b.offsetX) < 1e-4f &&
            kotlin.math.abs(a.offsetY - b.offsetY) < 1e-4f

    private fun safeDiv(delta: Float, size: Float): Float = if (size > 0f) delta / size else 0f
}
