package com.tharunbirla.librecuts

import com.tharunbirla.librecuts.models.EditOperation.ClipFrame
import com.tharunbirla.librecuts.utils.ClipFrameFilters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/** Piezas de filtro del encuadre por clip (M-198). Sin encuadre, el comando no debe cambiar ni un byte. */
class ClipFrameFiltersTest {

    @Test
    fun identityKeepsTheCurrentMergeFilters() {
        for (frame in listOf(null, ClipFrame())) {
            assertEquals("scale=1280:720:force_original_aspect_ratio=decrease", ClipFrameFilters.fitScale(1280, 720, frame))
            assertEquals("(W-w)/2:(H-h)/2", ClipFrameFilters.overlayPosition(frame))
        }
    }

    @Test
    fun mergeScaleShrinksTheFitBoxAndKeepsEvenSizes() {
        assertEquals(
            "scale=640:360:force_original_aspect_ratio=decrease:force_divisible_by=2",
            ClipFrameFilters.fitScale(1280, 720, ClipFrame(scale = 0.5f))
        )
        // 1280 * 0.33 = 422.4 → la caja se redondea a par para que yuv420p no falle.
        assertEquals(
            "scale=422:238:force_original_aspect_ratio=decrease:force_divisible_by=2",
            ClipFrameFilters.fitScale(1280, 720, ClipFrame(scale = 0.33f))
        )
    }

    @Test
    fun overlayPositionShiftsTheCenterByAFractionOfTheCanvas() {
        assertEquals(
            "(W-w)/2+0.2500*W:(H-h)/2-0.1000*H",
            ClipFrameFilters.overlayPosition(ClipFrame(offsetX = 0.25f, offsetY = -0.1f))
        )
    }

    @Test
    fun numbersUseADotEvenWithACommaLocale() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            val frame = ClipFrame(scale = 0.75f, offsetX = 0.5f, offsetY = 0.5f)
            val stage = ClipFrameFilters.framedStage("[in]", "[out]", frame)!!.joinToString(";")
            assertFalse("una coma decimal rompe FFmpeg: $stage", Regex("\\d,\\d").containsMatchIn(stage))
            assertFalse(ClipFrameFilters.overlayPosition(frame).contains(","))
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun zoomCropsTheVisibleRegionBeforeScaling() {
        // s = 2 centrado: se ve el 50 % central de la fuente. Recortar antes de escalar evita
        // cuadros intermedios de W*s x H*s (9600x5400 con s = 5 en 1080p).
        val stage = ClipFrameFilters.framedStage("[in]", "[out]", ClipFrame(scale = 2f))!!
        assertEquals(
            "[frame_fg]crop=w=iw*0.5000:h=ih*0.5000:x=iw*0.2500:y=ih*0.2500," +
                "scale=trunc(iw*2.0000/2)*2:trunc(ih*2.0000/2)*2[frame_clip]",
            stage[2]
        )
        assertEquals("[frame_canvas][frame_clip]overlay=0:0[out]", stage[3])
    }

    @Test
    fun zoomWithOffsetCropsOnlyWhatStaysOnCanvas() {
        // s = 2, dx = 0.75: el borde izquierdo del clip queda en 0.25 W; se ve la fuente de 0 a 0.375.
        val stage = ClipFrameFilters.framedStage("[in]", "[out]", ClipFrame(scale = 2f, offsetX = 0.75f))!!
        assertEquals(
            "[frame_fg]crop=w=iw*0.3750:h=ih*0.5000:x=iw*0.0000:y=ih*0.2500," +
                "scale=trunc(iw*2.0000/2)*2:trunc(ih*2.0000/2)*2[frame_clip]",
            stage[2]
        )
        assertEquals("[frame_canvas][frame_clip]overlay=W*0.2500:0[out]", stage[3])
    }

    @Test
    fun mergeZoomPadsTheFittedClipToTheCanvasAndCropsBeforeScaling() {
        val frame = ClipFrame(scale = 2f)
        assertEquals(
            "scale=1280:720:force_original_aspect_ratio=decrease,format=rgba," +
                "pad=1280:720:(ow-iw)/2:(oh-ih)/2:color=black@0," +
                "crop=w=iw*0.5000:h=ih*0.5000:x=iw*0.2500:y=ih*0.2500," +
                "scale=trunc(iw*2.0000/2)*2:trunc(ih*2.0000/2)*2",
            ClipFrameFilters.fitScale(1280, 720, frame)
        )
        assertEquals("0:0", ClipFrameFilters.overlayPosition(frame))
    }

    @Test
    fun maxOffsetGrowsWithZoomSoTheEdgesStayReachable() {
        assertEquals(ClipFrame.MAX_OFFSET, ClipFrame.maxOffsetFor(0.5f), 0f)
        assertEquals(ClipFrame.MAX_OFFSET, ClipFrame.maxOffsetFor(1f), 0f)
        // Con s = 5 hace falta |offset| = 2 para llevar el borde del clip al borde del lienzo.
        assertTrue(ClipFrame.maxOffsetFor(5f) >= 2f)
        // Pero nunca tanto que el clip salga por completo del lienzo.
        assertTrue(ClipFrame.maxOffsetFor(5f) < 3f)
    }

    @Test
    fun singleClipStageIsSkippedWithoutFrame() {
        assertNull(ClipFrameFilters.framedStage("[in]", "[out]", null))
        assertNull(ClipFrameFilters.framedStage("[in]", "[out]", ClipFrame()))
    }

    @Test
    fun singleClipStageDrawsTheScaledClipOverABlackCanvasOfTheSameSize() {
        val stage = ClipFrameFilters.framedStage("[cfv]", "[framed]", ClipFrame(scale = 0.5f, offsetX = 0.25f))!!
        assertEquals(
            listOf(
                "[cfv]split[frame_bg][frame_fg]",
                "[frame_bg]drawbox=c=black:t=fill[frame_canvas]",
                "[frame_fg]scale=trunc(iw*0.5000/2)*2:trunc(ih*0.5000/2)*2[frame_clip]",
                "[frame_canvas][frame_clip]overlay=(W-w)/2+0.2500*W:(H-h)/2[framed]"
            ),
            stage
        )
    }
}
