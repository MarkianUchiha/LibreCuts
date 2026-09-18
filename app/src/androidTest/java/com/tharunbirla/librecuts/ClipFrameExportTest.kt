package com.tharunbirla.librecuts

import android.graphics.Bitmap
import android.graphics.Color
import android.media.MediaMetadataRetriever
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.antonkarpenko.ffmpegkit.FFmpegKit
import com.antonkarpenko.ffmpegkit.ReturnCode
import com.tharunbirla.librecuts.models.EditOperation.ClipFrame
import com.tharunbirla.librecuts.utils.ClipFrameFilters
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Corre los filtros del encuadre con el FFmpeg real de la app sobre un clip rojo sintético y mira
 * los pixeles del resultado: así se comprueba el export sin depender de un ffmpeg en la PC.
 */
@RunWith(AndroidJUnit4::class)
class ClipFrameExportTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun singleClipFrameShrinksAndShiftsOverBlack() {
        val stage = ClipFrameFilters.framedStage("[0:v]", "[out]", ClipFrame(scale = 0.5f, offsetX = 0.25f))!!
        val frame = render(stage.joinToString(";"))

        // Clip de 160x120 centrado en x = 160 + 0.25*320 = 240.
        assertRed(frame, 240, 120)
        assertBlack(frame, 100, 120)
        assertBlack(frame, 10, 10)
    }

    @Test
    fun mergeClipFrameShrinksAndShiftsInsideTheCanvas() {
        val clipFrame = ClipFrame(scale = 0.5f, offsetX = -0.25f)
        val graph = "color=c=black:s=${W}x$H:d=1:r=10[bg];" +
            "[0:v]${ClipFrameFilters.fitScale(W, H, clipFrame)}[fg];" +
            "[bg][fg]overlay=${ClipFrameFilters.overlayPosition(clipFrame)}:shortest=1[out]"
        val frame = render(graph)

        // Centro en x = 160 - 0.25*320 = 80.
        assertRed(frame, 80, 120)
        assertBlack(frame, 240, 120)
    }

    private fun render(filterGraph: String): Bitmap {
        val out = File(context.cacheDir, "clip_frame_${System.nanoTime()}.mp4")
        val cmd = "-y -f lavfi -i color=c=red:s=${W}x$H:d=1:r=10 -filter_complex \"$filterGraph\" " +
            "-map \"[out]\" -c:v libx264 -pix_fmt yuv420p \"${out.absolutePath}\""
        val session = FFmpegKit.execute(cmd)
        assertTrue("FFmpeg falló: ${session.allLogsAsString.takeLast(800)}", ReturnCode.isSuccess(session.returnCode))

        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(out.absolutePath)
            return retriever.getFrameAtTime(0)!!
        } finally {
            retriever.release()
            out.delete()
        }
    }

    private fun assertRed(bmp: Bitmap, x: Int, y: Int) {
        val c = bmp.getPixel(x, y)
        assertTrue("($x,$y) debía ser rojo y es #${Integer.toHexString(c)}", Color.red(c) > 180 && Color.green(c) < 80 && Color.blue(c) < 80)
    }

    private fun assertBlack(bmp: Bitmap, x: Int, y: Int) {
        val c = bmp.getPixel(x, y)
        assertTrue("($x,$y) debía ser negro y es #${Integer.toHexString(c)}", Color.red(c) < 40 && Color.green(c) < 40 && Color.blue(c) < 40)
    }

    companion object {
        private const val W = 320
        private const val H = 240
    }
}
