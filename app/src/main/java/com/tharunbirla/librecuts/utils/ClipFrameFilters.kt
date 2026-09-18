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
 *
 * Con zoom (s > 1) no se escala el cuadro completo: a ×5 sobre 1080p serían cuadros de 9600x5400.
 * Se recorta primero la región de la fuente que queda sobre el lienzo y se escala solo esa, así
 * el resultado nunca pasa del tamaño del lienzo.
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
        if (frame.scale > 1f) {
            // Se lleva el clip ajustado a un lienzo transparente del mismo tamaño: así la región
            // visible se calcula igual que en el camino de un clip, sin conocer el aspecto del clip.
            return "scale=$canvasW:$canvasH:force_original_aspect_ratio=decrease,format=rgba," +
                "pad=$canvasW:$canvasH:(ow-iw)/2:(oh-ih)/2:color=black@0," +
                zoomCropAndScale(frame)
        }
        // Caja par: yuv420p no admite dimensiones impares.
        val boxW = even(canvasW * frame.scale)
        val boxH = even(canvasH * frame.scale)
        return "scale=$boxW:$boxH:force_original_aspect_ratio=decrease:force_divisible_by=2"
    }

    /** Posición para `overlay`: centrado y desplazado offset * tamaño del lienzo. */
    fun overlayPosition(frame: ClipFrame?): String {
        if (frame != null && frame.scale > 1f) {
            // Tras recortar, la pieza empieza donde el clip entra al lienzo (0 si ya empezaba fuera).
            return "${edge(frame.scale, frame.offsetX, "W")}:${edge(frame.scale, frame.offsetY, "H")}"
        }
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
        val clip = if (frame.scale > 1f) {
            zoomCropAndScale(frame)
        } else {
            val s = fmt(frame.scale)
            "scale=trunc(iw*$s/2)*2:trunc(ih*$s/2)*2"
        }
        return listOf(
            "${inLabel}split[frame_bg][frame_fg]",
            "[frame_bg]drawbox=c=black:t=fill[frame_canvas]",
            "[frame_fg]$clip[frame_clip]",
            "[frame_canvas][frame_clip]overlay=${overlayPosition(frame)}$outLabel"
        )
    }

    /**
     * Para una entrada del tamaño del lienzo: recorta la región que sigue visible con zoom s y
     * desplazamiento offset, y la escala por s. Las fracciones son constantes calculadas aquí.
     */
    private fun zoomCropAndScale(frame: ClipFrame): String {
        val (x0, x1) = visibleRange(frame.scale, frame.offsetX)
        val (y0, y1) = visibleRange(frame.scale, frame.offsetY)
        val s = fmt(frame.scale)
        return "crop=w=iw*${fmt(x1 - x0)}:h=ih*${fmt(y1 - y0)}:x=iw*${fmt(x0)}:y=ih*${fmt(y0)}," +
            "scale=trunc(iw*$s/2)*2:trunc(ih*$s/2)*2"
    }

    /**
     * Rango de la fuente (en fracción de su tamaño) que cae sobre el lienzo. El borde del clip
     * escalado queda en lead = (1-s)/2 + offset del lienzo; el lienzo [0, 1] corresponde a la
     * fuente [-lead/s, (1-lead)/s], acotado a [0, 1].
     */
    private fun visibleRange(scale: Float, offset: Float): Pair<Float, Float> {
        val lead = (1f - scale) / 2f + offset
        val start = (-lead / scale).coerceIn(0f, 1f)
        val end = ((1f - lead) / scale).coerceIn(0f, 1f)
        // maxOffsetFor garantiza que algo del clip quede visible; el mínimo evita un crop de 0 px.
        return start to maxOf(end, start + 0.01f).coerceAtMost(1f)
    }

    /** Dónde empieza la pieza recortada: el borde del clip si cae dentro del lienzo, si no 0. */
    private fun edge(scale: Float, offset: Float, dimension: String): String {
        val lead = (1f - scale) / 2f + offset
        return if (lead <= 0f) "0" else "$dimension*${fmt(lead)}"
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
