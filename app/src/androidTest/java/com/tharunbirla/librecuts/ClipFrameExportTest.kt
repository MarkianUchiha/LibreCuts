package com.tharunbirla.librecuts

import android.graphics.Bitmap
import android.graphics.Color
import android.media.MediaMetadataRetriever
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.antonkarpenko.ffmpegkit.FFmpegKit
import com.antonkarpenko.ffmpegkit.ReturnCode
import android.net.Uri
import com.tharunbirla.librecuts.models.EditOperation
import com.tharunbirla.librecuts.models.EditOperation.ClipFrame
import com.tharunbirla.librecuts.utils.ClipFrameFilters
import com.tharunbirla.librecuts.viewmodels.VideoEditingViewModel
import com.tharunbirla.librecuts.viewmodels.addMergeOperation
import com.tharunbirla.librecuts.viewmodels.updateClipFrame
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

    @Test
    fun singleClipZoomShowsTheRightRegionOfTheSource() {
        // Fuente mitad roja (izq) y mitad azul (der). Con s = 2 y dx = 0.25 el lienzo muestra la
        // fuente de 0.125 a 0.625: rojo hasta el 75 % del ancho de salida y azul después.
        val stage = ClipFrameFilters.framedStage("[src]", "[out]", ClipFrame(scale = 2f, offsetX = 0.25f))!!
        val frame = render("[0:v]$TWO_TONE[src];" + stage.joinToString(";"))
        assertRedAt(frame, 0.4f, 0.5f)
        assertBlueAt(frame, 0.85f, 0.5f)
        assertTrue("con zoom la salida conserva el tamaño del lienzo", frame.width == W && frame.height == H)
    }

    @Test
    fun mergeZoomPlacesTheClipWhereItEntersTheCanvas() {
        // s = 2, dx = 0.75: el borde izquierdo del clip queda en 0.25 W; antes, fondo negro.
        val clipFrame = ClipFrame(scale = 2f, offsetX = 0.75f)
        val graph = "color=c=black:s=${W}x$H:d=1:r=10[bg];" +
            "[0:v]$TWO_TONE,${ClipFrameFilters.fitScale(W, H, clipFrame)}[fg];" +
            "[bg][fg]overlay=${ClipFrameFilters.overlayPosition(clipFrame)}:shortest=1[out]"
        val frame = render(graph)
        assertBlackAt(frame, 0.1f, 0.5f)
        assertRedAt(frame, 0.5f, 0.5f)
    }

    @Test
    fun consolidatedMergeCommandFramesTheSecondClip() {
        // El clip 1 (azul) va encuadrado a media escala; el clip 0 (rojo) queda completo.
        val source = syntheticClip("main", "red")
        val second = syntheticClip("second", "blue")
        val out = exportFile(source) { vm ->
            vm.addMergeOperation(listOf(EditOperation.MergeItem(Uri.fromFile(second), 1000L)))
            vm.updateClipFrame(1, ClipFrame(scale = 0.5f))
        }
        val first = frameAt(out, 300_000L)
        val later = frameAt(out, 1_500_000L)
        out.delete()
        assertRedAt(first, 0.05f, 0.05f)
        assertBlueAt(later, 0.5f, 0.5f)
        assertBlackAt(later, 0.05f, 0.05f)
    }

    @Test
    fun consolidatedCommandAppliesTheMainClipFrame() {
        // End-to-end con el comando real del export: sin textos, imágenes ni recorte, que es
        // justo el caso donde el comando se saltaba el encuadre.
        val source = syntheticClip("main")
        val frame = exportWith(source) { vm ->
            vm.updateClipFrame(0, ClipFrame(scale = 0.5f, offsetX = 0.25f))
        }
        // Clip a media escala centrado en x = 0.75 del ancho.
        assertRedAt(frame, 0.75f, 0.5f)
        assertBlackAt(frame, 0.3f, 0.5f)
        assertBlackAt(frame, 0.1f, 0.1f)
    }

    @Test
    fun consolidatedMergeCommandAppliesTheMainClipFrame() {
        val source = syntheticClip("main")
        val second = syntheticClip("second")
        val frame = exportWith(source) { vm ->
            vm.addMergeOperation(listOf(EditOperation.MergeItem(Uri.fromFile(second), 1000L)))
            vm.updateClipFrame(0, ClipFrame(scale = 0.5f, offsetX = -0.25f))
        }
        assertRedAt(frame, 0.25f, 0.5f)
        assertBlackAt(frame, 0.7f, 0.5f)
    }

    private fun exportWith(source: File, edit: (VideoEditingViewModel) -> Unit): Bitmap =
        firstFrame(exportFile(source, edit))

    private fun frameAt(file: File, timeUs: Long): Bitmap {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            return retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST)!!
        } finally {
            retriever.release()
        }
    }

    private fun exportFile(source: File, edit: (VideoEditingViewModel) -> Unit): File {
        val out = File(context.cacheDir, "clip_frame_export_${System.nanoTime()}.mp4")
        var cmd: String? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val vm = VideoEditingViewModel()
            vm.initializeProject(Uri.fromFile(source), source.name)
            edit(vm)
            cmd = vm.buildConsolidatedFFmpegCommand(source.absolutePath, out.absolutePath, null, context)
        }
        assertTrue("el comando debe incluir el encuadre: $cmd", cmd!!.contains("overlay="))
        // Mismo fallback que FFmpegRenderEngine: si el encoder de hardware falla, software.
        var session = FFmpegKit.execute(cmd)
        if (!ReturnCode.isSuccess(session.returnCode)) {
            session = FFmpegKit.execute(cmd!!.replace("-c:v h264_mediacodec", "-c:v libx264"))
        }
        assertTrue("FFmpeg falló: ${session.allLogsAsString.takeLast(800)}", ReturnCode.isSuccess(session.returnCode))
        return out
    }

    /** Clip de 1 s de un color, con audio, generado con el FFmpeg de la app. */
    private fun syntheticClip(name: String, color: String = "red"): File {
        val file = File(context.cacheDir, "clip_frame_src_${name}_${System.nanoTime()}.mp4")
        val session = FFmpegKit.execute(
            "-y -f lavfi -i color=c=$color:s=${W}x$H:d=1:r=10 -f lavfi -i anullsrc=r=44100:cl=stereo " +
                "-t 1 -c:v libx264 -pix_fmt yuv420p -c:a aac -shortest \"${file.absolutePath}\""
        )
        assertTrue("no se pudo crear el clip de prueba", ReturnCode.isSuccess(session.returnCode))
        return file
    }

    private fun firstFrame(file: File): Bitmap {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            return retriever.getFrameAtTime(0)!!
        } finally {
            retriever.release()
            file.delete()
        }
    }

    private fun assertRedAt(bmp: Bitmap, fx: Float, fy: Float) = assertRed(bmp, (bmp.width * fx).toInt(), (bmp.height * fy).toInt())
    private fun assertBlackAt(bmp: Bitmap, fx: Float, fy: Float) = assertBlack(bmp, (bmp.width * fx).toInt(), (bmp.height * fy).toInt())

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

    private fun assertBlueAt(bmp: Bitmap, fx: Float, fy: Float) {
        val c = bmp.getPixel((bmp.width * fx).toInt(), (bmp.height * fy).toInt())
        assertTrue("($fx,$fy) debía ser azul y es #${Integer.toHexString(c)}", Color.blue(c) > 180 && Color.red(c) < 80 && Color.green(c) < 80)
    }

    private fun assertBlack(bmp: Bitmap, x: Int, y: Int) {
        val c = bmp.getPixel(x, y)
        assertTrue("($x,$y) debía ser negro y es #${Integer.toHexString(c)}", Color.red(c) < 40 && Color.green(c) < 40 && Color.blue(c) < 40)
    }

    companion object {
        private const val W = 320
        private const val H = 240
        // Mitad derecha azul sobre la fuente roja: permite ver qué región de la fuente quedó en el lienzo.
        private const val TWO_TONE = "drawbox=x=${W / 2}:y=0:w=${W / 2}:h=$H:c=blue:t=fill"
    }
}
