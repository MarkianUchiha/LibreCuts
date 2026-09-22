package com.tharunbirla.librecuts.customviews

import android.graphics.Matrix
import android.graphics.Path
import com.tharunbirla.librecuts.models.EditOperation

/**
 * Las formas de la máscara, medidas sobre el lienzo. Viven aparte porque las dibujan tres vistas
 * distintas: el contenedor que recorta el video, el contorno que se edita y la captura del clip
 * saliente durante una transición.
 *
 * Quién recorta decide si invierte (`clipPath` o `clipOutPath`) y qué hace con el difuminado: aquí
 * solo sale la silueta.
 */
object MaskPath {

    /** Silueta de [mask] sobre un lienzo de [width] x [height]. Vacía si la máscara no tiene forma. */
    fun silhouette(mask: EditOperation.MaskConfig, width: Float, height: Float): Path {
        val path = Path()
        if (mask.shape == EditOperation.MaskShape.NONE) return path

        val cx = width * mask.relativeX
        val cy = height * mask.relativeY
        val mw = width * mask.relativeWidth
        val mh = height * mask.relativeHeight

        when (mask.shape) {
            EditOperation.MaskShape.RECTANGLE ->
                path.addRect(cx - mw / 2, cy - mh / 2, cx + mw / 2, cy + mh / 2, Path.Direction.CW)
            EditOperation.MaskShape.ELLIPSE ->
                path.addOval(cx - mw / 2, cy - mh / 2, cx + mw / 2, cy + mh / 2, Path.Direction.CW)
            // Split y shutter se extienden más allá del lienzo a propósito: son cortes, no figuras.
            EditOperation.MaskShape.SPLIT ->
                path.addRect(-width, cy, width * 2f, height * 2f, Path.Direction.CW)
            EditOperation.MaskShape.SHUTTER ->
                path.addRect(-width, cy - mh / 2, width * 2f, cy + mh / 2, Path.Direction.CW)
            EditOperation.MaskShape.HEART -> heart(path, cx, cy, mw, mh)
            EditOperation.MaskShape.STAR -> star(path, cx, cy, mw / 2f, mw / 4f)
            else -> {}
        }

        if (mask.rotationAngle != 0f) {
            path.transform(Matrix().apply { postRotate(mask.rotationAngle, cx, cy) })
        }
        return path
    }

    fun heart(path: Path, cx: Float, cy: Float, width: Float, height: Float) {
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

    fun star(path: Path, cx: Float, cy: Float, radiusOuter: Float, radiusInner: Float) {
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
