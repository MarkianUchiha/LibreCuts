package com.tharunbirla.librecuts

import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.widget.ScrollView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * El riel de herramientas de la tablet en horizontal muestra que continúa (M-242,
 * `specs/rotacion-horizontal.md` regla 1, CA1). Se desplazaba, pero sin ninguna señal, y parecía
 * que las herramientas de abajo no existían.
 */
@RunWith(AndroidJUnit4::class)
class ToolRailTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test
    fun theRailFadesAtTheEdgeWhereMoreToolsContinue() {
        instrumentation.runOnMainSync {
            // El layout usa atributos del tema (?attr/...): sin el tema del editor no se infla.
            val themed = ContextThemeWrapper(context, R.style.Editor)
            val root = LayoutInflater.from(themed).inflate(R.layout.activity_video_editing, null)
            val rail = root.findViewById<ScrollView>(R.id.toolRail)

            // Mismo recurso que la barra inferior (editingControlsScroll): el borde se desvanece solo
            // mientras queda contenido de ese lado, así que al llegar al final desaparece.
            assertTrue("el riel no desvanece sus bordes", rail.isVerticalFadingEdgeEnabled)
            assertTrue("el desvanecido no mide nada", rail.verticalFadingEdgeLength > 0)
        }
    }
}
