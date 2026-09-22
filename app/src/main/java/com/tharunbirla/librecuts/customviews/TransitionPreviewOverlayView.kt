package com.tharunbirla.librecuts.customviews

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.tharunbirla.librecuts.models.EditOperation

class TransitionPreviewOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var transitionType: String = "none"
    var progress: Float = 0f
        set(value) {
            field = value.coerceIn(0f, 1f)
            invalidate()
        }

    /**
     * Geometria del clip saliente, en coordenadas del lienzo. Llega resuelta desde el modelo y no
     * se deduce del padre: durante la transicion el contenedor del video ya adopto el encuadre del
     * clip entrante, y un seek puede caer a mitad de la ventana (M-211, punto 3).
     */
    data class SnapshotGeometry(
        /** Donde cae el cuadro del video dentro del lienzo, antes del encuadre. */
        val videoRect: RectF,
        val frame: EditOperation.ClipFrame = EditOperation.ClipFrame(),
        val isMirrored: Boolean = false,
        val mask: EditOperation.MaskConfig = EditOperation.MaskConfig(),
        /** Rectangulo del video completo: da el pivote del encuadre, igual que en el preview. */
        val reference: RectF = RectF(videoRect)
    )

    private var snapshotBitmap: Bitmap? = null
    private var geometry: SnapshotGeometry? = null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val path = Path()
    private val srcRect = RectF()
    private val dstRect = RectF()
    private val snapshotMatrix = Matrix()
    private val frameMatrix = Matrix()

    fun updateTransition(type: String, prog: Float, bitmap: Bitmap?, geometry: SnapshotGeometry? = null) {
        this.transitionType = type.lowercase()
        this.progress = prog.coerceIn(0f, 1f)
        if (this.snapshotBitmap != bitmap) {
            this.snapshotBitmap = bitmap
        }
        this.geometry = geometry
        invalidate()
    }

    fun clearSnapshot() {
        this.snapshotBitmap = null
        this.geometry = null
        this.transitionType = "none"
        this.progress = 0f
        invalidate()
    }

    /** Rectangulo donde se dibuja la captura del saliente. Es lo que verifican los tests. */
    fun snapshotBounds(): RectF {
        val g = geometry ?: return RectF(0f, 0f, width.toFloat(), height.toFloat())
        val bounds = RectF(g.videoRect)
        buildFrameMatrix(g).mapRect(bounds)
        return bounds
    }

    /**
     * Espejo y encuadre del clip saliente, en coordenadas del lienzo. El pivote es el centro del
     * video completo, igual que `applyClipFramePreview`, que es lo que hace que preview y export
     * coincidan cuando el clip esta recortado.
     */
    private fun buildFrameMatrix(g: SnapshotGeometry): Matrix {
        frameMatrix.reset()
        if (g.isMirrored) {
            frameMatrix.postScale(-1f, 1f, g.videoRect.centerX(), g.videoRect.centerY())
        }
        frameMatrix.postScale(g.frame.scale, g.frame.scale, g.reference.centerX(), g.reference.centerY())
        frameMatrix.postTranslate(g.frame.offsetX * g.reference.width(), g.frame.offsetY * g.reference.height())
        return frameMatrix
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val bmp = snapshotBitmap
        if (bmp == null || bmp.isRecycled || transitionType == "none" || progress <= 0f || progress >= 1f) {
            return
        }

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        // El lienzo: los efectos (cortinas, deslizamientos) se miden aqui, igual que xfade en el
        // export. Solo la captura del saliente lleva su propia geometria.
        dstRect.set(0f, 0f, w, h)
        srcRect.set(0f, 0f, bmp.width.toFloat(), bmp.height.toFloat())
        val g = geometry
        snapshotMatrix.setRectToRect(srcRect, g?.videoRect ?: dstRect, Matrix.ScaleToFit.FILL)
        if (g != null) snapshotMatrix.postConcat(buildFrameMatrix(g))

        // La mascara del clip saliente, ya encuadrada. El difuminado se ignora a proposito: aqui
        // solo recorta (la spec lo deja fuera; el difuminado tiene su propio bug, M-228).
        val mask = g?.mask
        val maskPath = if (mask != null && mask.shape != EditOperation.MaskShape.NONE) {
            MaskPath.silhouette(mask, w, h).apply { transform(buildFrameMatrix(g)) }
        } else {
            null
        }
        val maskInverted = mask?.isInverted == true

        /** El clip saliente con su geometria; el efecto lo dibuja donde y como le toque. */
        fun drawSnapshot(target: Canvas) {
            if (maskPath == null) {
                target.drawBitmap(bmp, snapshotMatrix, paint)
                return
            }
            target.save()
            if (maskInverted) target.clipOutPath(maskPath) else target.clipPath(maskPath)
            target.drawBitmap(bmp, snapshotMatrix, paint)
            target.restore()
        }

        paint.reset()
        paint.isAntiAlias = true
        paint.isFilterBitmap = true

        when (transitionType) {
            "fade", "dissolve" -> {
                paint.alpha = ((1f - progress) * 255).toInt()
                drawSnapshot(canvas)
            }
            "fadeblack" -> {
                val alpha = if (progress < 0.5f) progress * 2f else (1f - progress) * 2f
                paint.color = Color.BLACK
                paint.alpha = (alpha * 255).toInt()
                canvas.drawRect(dstRect, paint)
            }
            "fadewhite" -> {
                val alpha = if (progress < 0.5f) progress * 2f else (1f - progress) * 2f
                paint.color = Color.WHITE
                paint.alpha = (alpha * 255).toInt()
                canvas.drawRect(dstRect, paint)
            }
            "wipeleft" -> {
                canvas.save()
                canvas.clipRect(0f, 0f, w * (1f - progress), h)
                drawSnapshot(canvas)
                canvas.restore()
            }
            "wiperight" -> {
                canvas.save()
                canvas.clipRect(w * progress, 0f, w, h)
                drawSnapshot(canvas)
                canvas.restore()
            }
            "wipeup" -> {
                canvas.save()
                canvas.clipRect(0f, 0f, w, h * (1f - progress))
                drawSnapshot(canvas)
                canvas.restore()
            }
            "wipedown" -> {
                canvas.save()
                canvas.clipRect(0f, h * progress, w, h)
                drawSnapshot(canvas)
                canvas.restore()
            }
            "slideleft", "coverleft" -> {
                canvas.save()
                canvas.translate(-w * progress, 0f)
                drawSnapshot(canvas)
                canvas.restore()
            }
            "slideright", "coverright" -> {
                canvas.save()
                canvas.translate(w * progress, 0f)
                drawSnapshot(canvas)
                canvas.restore()
            }
            "slideup" -> {
                canvas.save()
                canvas.translate(0f, -h * progress)
                drawSnapshot(canvas)
                canvas.restore()
            }
            "slidedown" -> {
                canvas.save()
                canvas.translate(0f, h * progress)
                drawSnapshot(canvas)
                canvas.restore()
            }
            "circlecrop" -> {
                canvas.save()
                val maxRadius = Math.hypot((w / 2).toDouble(), (h / 2).toDouble()).toFloat()
                val radius = maxRadius * (1f - progress)
                path.reset()
                path.addCircle(w / 2f, h / 2f, radius, Path.Direction.CW)
                canvas.clipPath(path)
                drawSnapshot(canvas)
                canvas.restore()
            }
            "rectcrop" -> {
                canvas.save()
                val halfW = (w / 2f) * (1f - progress)
                val halfH = (h / 2f) * (1f - progress)
                canvas.clipRect(w / 2f - halfW, h / 2f - halfH, w / 2f + halfW, h / 2f + halfH)
                drawSnapshot(canvas)
                canvas.restore()
            }
            "zoomin" -> {
                canvas.save()
                val scale = 1f + progress * 0.4f
                paint.alpha = ((1f - progress) * 255).toInt()
                canvas.scale(scale, scale, w / 2f, h / 2f)
                drawSnapshot(canvas)
                canvas.restore()
            }
            else -> {
                paint.alpha = ((1f - progress) * 255).toInt()
                drawSnapshot(canvas)
            }
        }
    }
}
