package com.tharunbirla.librecuts

import com.tharunbirla.librecuts.models.EditOperation.ClipFrame
import com.tharunbirla.librecuts.utils.ClipFrameFilters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
