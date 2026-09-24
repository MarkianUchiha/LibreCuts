package com.tharunbirla.librecuts

import com.tharunbirla.librecuts.utils.TimelineFit
import com.tharunbirla.librecuts.utils.timelineFitFor
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Cuánto alto le toca al timeline para que el preview conserve el 40 % de la ventana (M-254,
 * `specs/ventana-baja.md`). Las medidas están en dp, como si la densidad fuera 1; salen del teléfono
 * de prueba (Honor ELP-NX9) y de la tablet (Honor ELN-W09).
 */
class TimelineFitTest {

    // Barra superior 56 + fila de controles 64 + padding 8 + divisor 4 + barra de herramientas 88.
    private val phoneChrome = 220
    private val full = 140
    private val min = 80

    private fun fit(window: Int, chrome: Int = phoneChrome) = timelineFitFor(window, chrome, full, min)

    @Test
    fun aTallPhoneKeepsTheFullTimeline() {
        assertEquals(TimelineFit.Full, fit(831))
    }

    @Test
    fun theTabletSidePanelLayoutKeepsTheFullTimeline() {
        // Sin barra inferior: las herramientas van al riel lateral.
        assertEquals(TimelineFit.Full, fit(685, chrome = 56 + 64 + 8 + 4))
    }

    @Test
    fun anIntermediateWindowCompactsTheTimeline() {
        // 550 × 0.6 − 220 = 110.
        assertEquals(TimelineFit.Compact(110), fit(550))
    }

    @Test
    fun theSplitScreenPhoneCollapsesTheTimeline() {
        assertEquals(TimelineFit.Collapsed, fit(415))
    }

    @Test
    fun theLandscapePhoneCollapsesTheTimeline() {
        assertEquals(TimelineFit.Collapsed, fit(376))
    }

    @Test
    fun thresholdsAreInclusive() {
        // (full + chrome) / 0.6 = 600 → justo el timeline completo.
        assertEquals(TimelineFit.Full, fit(600))
        // (min + chrome) / 0.6 = 500 → justo el compacto mínimo.
        assertEquals(TimelineFit.Compact(80), fit(500))
        assertEquals(TimelineFit.Collapsed, fit(499))
    }
}
