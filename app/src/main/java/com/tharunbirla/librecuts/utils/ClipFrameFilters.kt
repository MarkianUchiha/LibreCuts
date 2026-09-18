package com.tharunbirla.librecuts.utils

import com.tharunbirla.librecuts.models.EditOperation.ClipFrame
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Piezas de filtro FFmpeg para el encuadre por clip (M-198). Son funciones puras, sin Android,
 * para poder probarlas en la JVM: VideoEditingViewModel depende de Context y no se puede.
 *
 * Sin encuadre (null o identidad) devuelven exactamente lo que el export generaba antes, para que
 * los proyectos sin encuadrar no cambien ni un byte en su comando.
 */
object ClipFrameFilters {

    /**
     * Escala del clip en la normalización del camino merge: el clip entra en una caja de
     * canvasW*s x canvasH*s conservando su aspecto (con s = 1 llena el lienzo como antes).
     */
    fun fitScale(canvasW: Int, canvasH: Int, frame: ClipFrame?): String {
        if (frame == null || frame.isIdentity) {
            return "scale=$canvasW:$canvasH:force_original_aspect_ratio=decrease"
        }
        // Caja par: yuv420p no admite dimensiones impares.
        val boxW = even(canvasW * frame.scale)
        val boxH = even(canvasH * frame.scale)
        return "scale=$boxW:$boxH:force_original_aspect_ratio=decrease:force_divisible_by=2"
    }

    /** Posición para `overlay`: centrado y desplazado offset * tamaño del lienzo. */
    fun overlayPosition(frame: ClipFrame?): String {
        val dx = frame?.offsetX ?: 0f
        val dy = frame?.offsetY ?: 0f
        return "(W-w)/2${term(dx, "W")}:(H-h)/2${term(dy, "H")}"
    }

    /**
     * Camino sin merge: el video es su propio lienzo, así que se dibuja escalado sobre una copia
     * pintada de negro del mismo tamaño. Con split + drawbox no hace falta conocer las dimensiones.
     * Devuelve null si no hay encuadre, para no agregar etapas.
     */
    fun framedStage(inLabel: String, outLabel: String, frame: ClipFrame?): List<String>? {
        if (frame == null || frame.isIdentity) return null
        val s = fmt(frame.scale)
        return listOf(
            "${inLabel}split[frame_bg][frame_fg]",
            "[frame_bg]drawbox=c=black:t=fill[frame_canvas]",
            "[frame_fg]scale=trunc(iw*$s/2)*2:trunc(ih*$s/2)*2[frame_clip]",
            "[frame_canvas][frame_clip]overlay=${overlayPosition(frame)}$outLabel"
        )
    }

    private fun term(offset: Float, dimension: String): String = when {
        offset == 0f -> ""
        offset > 0f -> "+${fmt(offset)}*$dimension"
        else -> "-${fmt(abs(offset))}*$dimension"
    }

    private fun even(value: Float): Int = maxOf(2, (value / 2f).roundToInt() * 2)

    // Locale.US: con el locale del teléfono en español saldría "0,5000" y FFmpeg lo tomaría como separador.
    private fun fmt(value: Float): String = String.format(Locale.US, "%.4f", value)
}
