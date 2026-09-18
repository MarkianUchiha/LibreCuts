// Movie está deprecado, pero es la única API del framework que dibuja un GIF en un instante
// arbitrario (setTime). AnimatedImageDrawable solo reproduce en tiempo real y no se puede
// sincronizar con el timeline. Se aísla aquí para que el resto del código no dependa de Movie
// y, si se cambia a una librería, solo se toque este archivo.
@file:Suppress("DEPRECATION")

package com.tharunbirla.librecuts.utils

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Movie

class GifFrameSource private constructor(private val movie: Movie) {

    val width: Int get() = movie.width()
    val height: Int get() = movie.height()
    val durationMs: Int get() = movie.duration()

    /**
     * Dibuja el frame de [timeMs]. Reutiliza [reuse] si tiene el tamaño del GIF
     * para no crear un bitmap por frame durante la reproducción.
     */
    fun renderFrame(timeMs: Int, reuse: Bitmap?): Bitmap {
        val bitmap = if (reuse != null && reuse.width == width && reuse.height == height) {
            reuse
        } else {
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        }
        bitmap.eraseColor(Color.TRANSPARENT)
        movie.setTime(timeMs)
        movie.draw(Canvas(bitmap), 0f, 0f)
        return bitmap
    }

    companion object {
        fun decode(path: String): GifFrameSource? = Movie.decodeFile(path)?.let { GifFrameSource(it) }
    }
}
