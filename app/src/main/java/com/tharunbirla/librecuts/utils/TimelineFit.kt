package com.tharunbirla.librecuts.utils

/**
 * Cómo cabe el timeline en la ventana (M-254, `specs/ventana-baja.md`). El preview conserva
 * [minPreviewFraction] del alto; lo que quede después de [chromePx] (barras, controles) es para el
 * timeline. Si no alcanza ni el mínimo usable, se colapsa y se despliega aparte.
 */
sealed interface TimelineFit {
    data object Full : TimelineFit
    data class Compact(val timelinePx: Int) : TimelineFit
    data object Collapsed : TimelineFit
}

fun timelineFitFor(
    windowPx: Int,
    chromePx: Int,
    fullTimelinePx: Int,
    minTimelinePx: Int,
    minPreviewFraction: Float = 0.4f
): TimelineFit {
    val budget = windowPx * (1f - minPreviewFraction) - chromePx
    return when {
        budget >= fullTimelinePx -> TimelineFit.Full
        budget >= minTimelinePx -> TimelineFit.Compact(budget.toInt())
        else -> TimelineFit.Collapsed
    }
}
