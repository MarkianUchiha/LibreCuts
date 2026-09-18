package com.tharunbirla.librecuts.customviews

import android.content.Context
import android.view.View
import android.view.ViewGroup

/**
 * Acomoda a sus hijos en filas que se parten al llegar al ancho disponible, cada fila centrada.
 * Se usa en el panel lateral de tablet para que los botones de un toolbar no queden escondidos
 * en un scroll horizontal (el "Listo" quedaba fuera de vista).
 */
class WrapLayout(context: Context) : ViewGroup(context) {

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // Dentro de un HorizontalScrollView el modo llega UNSPECIFIED, pero el tamaño trae el ancho
        // visible (makeSafeMeasureSpec); si viene 0 no hay límite y todo queda en una sola fila.
        val available = MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight
        val maxLineWidth = if (available > 0) available else Int.MAX_VALUE

        var lineWidth = 0
        var lineHeight = 0
        var usedHeight = 0
        var widestLine = 0
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child.visibility == View.GONE) continue
            measureChildWithMargins(child, widthMeasureSpec, 0, heightMeasureSpec, 0)
            val lp = child.layoutParams as MarginLayoutParams
            val childWidth = child.measuredWidth + lp.leftMargin + lp.rightMargin
            val childHeight = child.measuredHeight + lp.topMargin + lp.bottomMargin
            if (lineWidth > 0 && lineWidth + childWidth > maxLineWidth) {
                usedHeight += lineHeight
                widestLine = maxOf(widestLine, lineWidth)
                lineWidth = 0
                lineHeight = 0
            }
            lineWidth += childWidth
            lineHeight = maxOf(lineHeight, childHeight)
        }
        usedHeight += lineHeight
        widestLine = maxOf(widestLine, lineWidth)

        val width = if (available > 0) available else widestLine
        setMeasuredDimension(
            resolveSize(width + paddingLeft + paddingRight, widthMeasureSpec),
            resolveSize(usedHeight + paddingTop + paddingBottom, heightMeasureSpec)
        )
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val maxLineWidth = r - l - paddingLeft - paddingRight
        var top = paddingTop
        var lineStart = 0
        while (lineStart < childCount) {
            // Primero se decide qué hijos caben en la fila para poder centrarla.
            var lineEnd = lineStart
            var lineWidth = 0
            var lineHeight = 0
            while (lineEnd < childCount) {
                val child = getChildAt(lineEnd)
                if (child.visibility == View.GONE) { lineEnd++; continue }
                val lp = child.layoutParams as MarginLayoutParams
                val childWidth = child.measuredWidth + lp.leftMargin + lp.rightMargin
                if (lineWidth > 0 && lineWidth + childWidth > maxLineWidth) break
                lineWidth += childWidth
                lineHeight = maxOf(lineHeight, child.measuredHeight + lp.topMargin + lp.bottomMargin)
                lineEnd++
            }
            var left = paddingLeft + maxOf(0, (maxLineWidth - lineWidth) / 2)
            for (i in lineStart until lineEnd) {
                val child = getChildAt(i)
                if (child.visibility == View.GONE) continue
                val lp = child.layoutParams as MarginLayoutParams
                val childLeft = left + lp.leftMargin
                val childTop = top + lp.topMargin
                child.layout(childLeft, childTop, childLeft + child.measuredWidth, childTop + child.measuredHeight)
                left += child.measuredWidth + lp.leftMargin + lp.rightMargin
            }
            top += lineHeight
            lineStart = lineEnd
        }
    }

    // Se aceptan los LayoutParams originales (LinearLayout.LayoutParams es MarginLayoutParams) para
    // que los hijos puedan regresar a su fila original sin perder márgenes ni tamaño.
    override fun checkLayoutParams(p: LayoutParams?): Boolean = p is MarginLayoutParams
    override fun generateLayoutParams(p: LayoutParams?): LayoutParams = MarginLayoutParams(p)
    override fun generateDefaultLayoutParams(): LayoutParams =
        MarginLayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
}
