package com.tharunbirla.librecuts.customviews

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import com.tharunbirla.librecuts.models.EditOperation

class VideoMaskOverlayView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var maskConfig = EditOperation.MaskConfig()
        set(value) {
            field = value
            invalidate()
            onMaskChanged?.invoke(value)
        }

    var isEditingMode = false
        set(value) {
            field = value
            visibility = if (value) VISIBLE else GONE
            invalidate()
        }

    var onMaskChanged: ((EditOperation.MaskConfig) -> Unit)? = null

    // La máscara vive en fracciones del contenedor que la dibuja (MaskedFrameLayout), no de este
    // overlay: ese contenedor mide solo el lienzo y además recibe el encuadre del clip. Sin
    // seguirlo, el contorno salía del tamaño del preview completo y fuera de lugar al encuadrar.
    private var container: View? = null
    private val toView = Matrix()
    private val fromView = Matrix()
    private var spaceWidth = 0f
    private var spaceHeight = 0f
    private val touchPoint = FloatArray(2)

    /** Dibuja y edita la máscara en el espacio de [container], con su tamaño y su transformación. */
    fun followContainer(container: View) {
        this.container = container
        invalidate()
    }

    /** Rectángulo de la forma (sin rotar) en coordenadas de este overlay. Es lo que se dibuja. */
    fun contourBounds(): RectF {
        updateSpace()
        val cx = spaceWidth * maskConfig.relativeX
        val cy = spaceHeight * maskConfig.relativeY
        val mw = spaceWidth * maskConfig.relativeWidth
        val mh = spaceHeight * maskConfig.relativeHeight
        val rect = RectF(cx - mw / 2, cy - mh / 2, cx + mw / 2, cy + mh / 2)
        toView.mapRect(rect)
        return rect
    }

    /**
     * Recalcula el paso del espacio del contenedor al de este overlay. Se hace en cada dibujo y
     * toque, no al conectar: el lienzo cambia de tamaño al recortar y el encuadre al arrastrar.
     */
    private fun updateSpace() {
        val c = container
        if (c == null || c.width == 0 || c.height == 0) {
            toView.reset()
            spaceWidth = width.toFloat()
            spaceHeight = height.toFloat()
        } else {
            val overlayToRoot = toRoot(this)
            toView.set(toRoot(c))
            val rootToOverlay = Matrix()
            overlayToRoot.invert(rootToOverlay)
            toView.postConcat(rootToOverlay)
            spaceWidth = c.width.toFloat()
            spaceHeight = c.height.toFloat()
        }
        toView.invert(fromView)
    }

    /**
     * Transformación acumulada de [view] hasta la raíz de la jerarquía. Se arma a mano porque
     * `View.transformMatrixToGlobal` es API 29 y el minSdk es 26; y no se usa
     * `getLocationInWindow` porque solo da la esquina, no la escala del encuadre.
     */
    private fun toRoot(view: View): Matrix {
        val m = Matrix()
        var v: View? = view
        while (v != null) {
            m.postConcat(v.matrix)
            val parent = v.parent as? View
            m.postTranslate(
                (v.left - (parent?.scrollX ?: 0)).toFloat(),
                (v.top - (parent?.scrollY ?: 0)).toFloat()
            )
            v = parent
        }
        return m
    }

    /** Pasa un toque del overlay al espacio del contenedor, en [touchPoint]. */
    private fun toSpace(x: Float, y: Float): FloatArray {
        touchPoint[0] = x
        touchPoint[1] = y
        fromView.mapPoints(touchPoint)
        return touchPoint
    }

    private val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00E5FF")
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    
    private val handleFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF2A6D")
        style = Paint.Style.FILL
    }
    
    private val handleStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFFFFF")
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    private var isDragging = false
    private var dragOffsetX = 0f
    private var dragOffsetY = 0f

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val scaleFactor = detector.scaleFactor
            maskConfig = maskConfig.copy(
                relativeWidth = (maskConfig.relativeWidth * scaleFactor).coerceIn(0.01f, 5.0f),
                relativeHeight = (maskConfig.relativeHeight * scaleFactor).coerceIn(0.01f, 5.0f)
            )
            return true
        }
    })

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEditingMode) return false

        scaleDetector.onTouchEvent(event)

        if (scaleDetector.isInProgress || event.pointerCount > 1) {
            isDragging = false
            return true
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                isDragging = true
                updateSpace()
                val p = toSpace(event.x, event.y)
                dragOffsetX = p[0]
                dragOffsetY = p[1]
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (isDragging) {
                    updateSpace()
                    // El delta se mide ya dentro del contenedor: con el clip a escala s, un dedo
                    // que avanza d px equivale a d/s px del lienzo, y la máscara se ve moverse d.
                    val p = toSpace(event.x, event.y)
                    val dx = p[0] - dragOffsetX
                    val dy = p[1] - dragOffsetY
                    dragOffsetX = p[0]
                    dragOffsetY = p[1]

                    if (spaceWidth > 0 && spaceHeight > 0) {
                        maskConfig = maskConfig.copy(
                            relativeX = maskConfig.relativeX + dx / spaceWidth,
                            relativeY = maskConfig.relativeY + dy / spaceHeight
                        )
                    }
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isDragging = false
            }
        }
        return false
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!isEditingMode || maskConfig.shape == EditOperation.MaskShape.NONE) return

        updateSpace()
        canvas.save()
        canvas.concat(toView)
        // Grosor y asas en px de pantalla aunque el contenedor esté escalado por el encuadre.
        val screenPx = 1f / toView.mapRadius(1f).coerceAtLeast(0.01f)
        maskPaint.strokeWidth = 4f * screenPx
        handleStrokePaint.strokeWidth = 3f * screenPx
        val handleRadius = 12f * screenPx

        val cx = spaceWidth * maskConfig.relativeX
        val cy = spaceHeight * maskConfig.relativeY
        val mw = spaceWidth * maskConfig.relativeWidth
        val mh = spaceHeight * maskConfig.relativeHeight

        canvas.rotate(maskConfig.rotationAngle, cx, cy)

        when (maskConfig.shape) {
            EditOperation.MaskShape.RECTANGLE -> canvas.drawRect(cx - mw/2, cy - mh/2, cx + mw/2, cy + mh/2, maskPaint)
            EditOperation.MaskShape.ELLIPSE -> canvas.drawOval(cx - mw/2, cy - mh/2, cx + mw/2, cy + mh/2, maskPaint)
            EditOperation.MaskShape.SPLIT -> canvas.drawLine(-spaceWidth, cy, spaceWidth * 2f, cy, maskPaint)
            EditOperation.MaskShape.SHUTTER -> {
                canvas.drawLine(-spaceWidth, cy - mh/2, spaceWidth * 2f, cy - mh/2, maskPaint)
                canvas.drawLine(-spaceWidth, cy + mh/2, spaceWidth * 2f, cy + mh/2, maskPaint)
            }
            EditOperation.MaskShape.HEART -> {
                val path = android.graphics.Path()
                createHeartPath(path, cx, cy, mw, mh)
                canvas.drawPath(path, maskPaint)
            }
            EditOperation.MaskShape.STAR -> {
                val path = android.graphics.Path()
                createStarPath(path, cx, cy, mw / 2f, mw / 4f)
                canvas.drawPath(path, maskPaint)
            }
            else -> {}
        }

        // Draw mask center handle
        canvas.drawCircle(cx, cy, handleRadius, handleFillPaint)
        canvas.drawCircle(cx, cy, handleRadius, handleStrokePaint)

        canvas.restore()
    }

    private fun createHeartPath(path: android.graphics.Path, cx: Float, cy: Float, width: Float, height: Float) {
        path.reset()
        val topCurveHeight = height * 0.3f
        path.moveTo(cx, cy + height * 0.4f)
        path.cubicTo(
            cx - width * 0.5f, cy + height * 0.1f,
            cx - width * 0.5f, cy - topCurveHeight,
            cx, cy - topCurveHeight * 0.4f
        )
        path.cubicTo(
            cx + width * 0.5f, cy - topCurveHeight,
            cx + width * 0.5f, cy + height * 0.1f,
            cx, cy + height * 0.4f
        )
        path.close()
    }

    private fun createStarPath(path: android.graphics.Path, cx: Float, cy: Float, radiusOuter: Float, radiusInner: Float) {
        path.reset()
        val points = 5
        val angle = Math.PI / points
        for (i in 0 until 2 * points) {
            val r = if (i % 2 == 0) radiusOuter else radiusInner
            val currAngle = i * angle - Math.PI / 2
            val x = (cx + r * Math.cos(currAngle)).toFloat()
            val y = (cy + r * Math.sin(currAngle)).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
    }
}
