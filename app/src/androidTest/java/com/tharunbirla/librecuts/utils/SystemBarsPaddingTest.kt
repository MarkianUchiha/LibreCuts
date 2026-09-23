package com.tharunbirla.librecuts.utils

import android.view.View
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * El helper de insets de borde a borde (M-238, `specs/target-sdk-36.md` regla 1). Los insets se
 * entregan a mano con `dispatchApplyWindowInsets`, así el test no depende de las barras reales del
 * dispositivo donde corre.
 */
@RunWith(AndroidJUnit4::class)
class SystemBarsPaddingTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test
    fun paddingIsTheOriginalPlusTheSystemBars() = onMain {
        val view = viewWithPadding()
        view.applySystemBarsPadding()

        ViewCompat.dispatchApplyWindowInsets(view, insets(systemBars = Insets.of(10, 20, 30, 40)))

        assertPadding(15, 26, 37, 48, view)
    }

    @Test
    fun receivingInsetsTwiceDoesNotAccumulate() = onMain {
        val view = viewWithPadding()
        view.applySystemBarsPadding()
        val bars = insets(systemBars = Insets.of(10, 20, 30, 40))

        // Pasa en cada giro y cada vez que una vista cambia de padre en applyWorkspaceLayout.
        ViewCompat.dispatchApplyWindowInsets(view, bars)
        ViewCompat.dispatchApplyWindowInsets(view, bars)

        assertPadding(15, 26, 37, 48, view)
    }

    @Test
    fun theDisplayCutoutAlsoPushesTheContent() = onMain {
        val view = viewWithPadding()
        view.applySystemBarsPadding()

        // En horizontal, el recorte de la cámara cae a un costado donde no hay barra.
        ViewCompat.dispatchApplyWindowInsets(
            view,
            insets(systemBars = Insets.of(0, 20, 0, 40), cutout = Insets.of(50, 0, 0, 0))
        )

        assertPadding(55, 26, 7, 48, view)
    }

    @Test
    fun insetsAreNotConsumed() = onMain {
        val view = viewWithPadding()
        view.applySystemBarsPadding()
        val bars = Insets.of(10, 20, 30, 40)

        val result = ViewCompat.dispatchApplyWindowInsets(view, insets(systemBars = bars, imeVisible = true))

        // El editor detecta el teclado leyendo los insets de la raíz (VideoEditingActivity:846).
        assertFalse(result.isConsumed)
        assertTrue(result.isVisible(WindowInsetsCompat.Type.ime()))
        assertEquals(bars, result.getInsets(WindowInsetsCompat.Type.systemBars()))
    }

    private fun viewWithPadding() = View(context).apply { setPadding(5, 6, 7, 8) }

    private fun insets(
        systemBars: Insets,
        cutout: Insets = Insets.NONE,
        imeVisible: Boolean = false
    ): WindowInsetsCompat = WindowInsetsCompat.Builder()
        .setInsets(WindowInsetsCompat.Type.systemBars(), systemBars)
        .setInsets(WindowInsetsCompat.Type.displayCutout(), cutout)
        .setVisible(WindowInsetsCompat.Type.ime(), imeVisible)
        .build()

    private fun assertPadding(left: Int, top: Int, right: Int, bottom: Int, view: View) {
        assertEquals(
            listOf(left, top, right, bottom),
            listOf(view.paddingLeft, view.paddingTop, view.paddingRight, view.paddingBottom)
        )
    }

    private fun onMain(block: () -> Unit) = instrumentation.runOnMainSync(block)
}
