package com.tharunbirla.librecuts

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tharunbirla.librecuts.models.EditRecipe
import com.tharunbirla.librecuts.utils.ProjectSerializer
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Las Activities simples apartan su contenido de las barras del sistema (M-239,
 * `specs/target-sdk-36.md` regla 1).
 *
 * Los insets se entregan a mano a la vista raíz del layout: en la tablet (Android 13) el sistema no
 * dibuja de borde a borde y consume las barras antes de que lleguen al contenido, así que con los
 * insets reales el test no probaría nada.
 */
@RunWith(AndroidJUnit4::class)
class EdgeToEdgeActivitiesTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun mainActivityKeepsItsContentOffTheSystemBars() = assertRootIsPushed(MainActivity::class.java)

    @Test
    fun projectImportActivityKeepsItsContentOffTheSystemBars() {
        // Sin un proyecto que abrir, la Activity se cierra en onCreate.
        val project = File(context.cacheDir, "edge_to_edge_test.lcprj").apply {
            writeText(
                ProjectSerializer.serialize(
                    EditRecipe(
                        projectName = "edge-to-edge",
                        sourceUri = Uri.fromFile(File(context.cacheDir, "missing.mp4")),
                        sourceName = "missing.mp4"
                    )
                )
            )
        }
        val intent = Intent(context, ProjectImportActivity::class.java)
            .putExtra("PROJECT_URI", Uri.fromFile(project))

        assertRootIsPushed(ActivityScenario.launch<ProjectImportActivity>(intent))
    }

    @Test
    fun errorDisplayActivityKeepsItsContentOffTheSystemBars() =
        assertRootIsPushed(ErrorDisplayActivity::class.java)

    private fun <A : Activity> assertRootIsPushed(activityClass: Class<A>) =
        assertRootIsPushed(ActivityScenario.launch(activityClass))

    private fun <A : Activity> assertRootIsPushed(launched: ActivityScenario<A>) {
        launched.use { scenario ->
            scenario.onActivity { activity ->
                val root = activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0)
                val before = padding(root)

                ViewCompat.dispatchApplyWindowInsets(
                    root,
                    WindowInsetsCompat.Builder()
                        .setInsets(WindowInsetsCompat.Type.systemBars(), Insets.of(10, 20, 30, 40))
                        .build()
                )

                assertEquals(
                    listOf(before[0] + 10, before[1] + 20, before[2] + 30, before[3] + 40),
                    padding(root)
                )
            }
        }
    }

    private fun padding(view: View) =
        listOf(view.paddingLeft, view.paddingTop, view.paddingRight, view.paddingBottom)
}
