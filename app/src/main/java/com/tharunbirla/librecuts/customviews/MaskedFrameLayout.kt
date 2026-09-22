package com.tharunbirla.librecuts.customviews

import android.content.Context
import android.graphics.Canvas
import android.graphics.Path
import android.util.AttributeSet
import android.widget.FrameLayout
import com.tharunbirla.librecuts.models.EditOperation

class MaskedFrameLayout @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    var maskConfig: EditOperation.MaskConfig = EditOperation.MaskConfig()
        set(value) {
            field = value
            invalidate()
        }

    override fun dispatchDraw(canvas: Canvas) {
        if (maskConfig.shape != EditOperation.MaskShape.NONE) {
            val path = MaskPath.silhouette(maskConfig, width.toFloat(), height.toFloat())

            if (maskConfig.feather > 0f) {
                val featherPx = (maskConfig.feather * 0.4f).coerceIn(1f, 80f)
                val count = canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null)
                super.dispatchDraw(canvas)
                
                val maskPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    color = -0x1
                    xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.DST_IN)
                    maskFilter = android.graphics.BlurMaskFilter(featherPx, android.graphics.BlurMaskFilter.Blur.NORMAL)
                }
                
                if (maskConfig.isInverted) {
                    val fullPath = Path().apply { addRect(0f, 0f, width.toFloat(), height.toFloat(), Path.Direction.CW) }
                    fullPath.op(path, Path.Op.DIFFERENCE)
                    canvas.drawPath(fullPath, maskPaint)
                } else {
                    canvas.drawPath(path, maskPaint)
                }
                canvas.restoreToCount(count)
            } else {
                canvas.save()
                if (maskConfig.isInverted) {
                    canvas.clipOutPath(path)
                } else {
                    canvas.clipPath(path)
                }
                super.dispatchDraw(canvas)
                canvas.restore()
            }
        } else {
            super.dispatchDraw(canvas)
        }
    }
}
