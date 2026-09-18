package com.tharunbirla.librecuts

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tharunbirla.librecuts.models.EditOperation
import com.tharunbirla.librecuts.models.EditRecipe
import com.tharunbirla.librecuts.utils.ProjectSerializer
import com.tharunbirla.librecuts.viewmodels.VideoEditingViewModel
import com.tharunbirla.librecuts.viewmodels.deleteSequenceSegment
import com.tharunbirla.librecuts.viewmodels.splitVideoSegment
import com.tharunbirla.librecuts.viewmodels.updateClipFrame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Encuadre por clip (M-198): se guarda en el .lcprj sin romper proyectos viejos, se aplica al clip
 * correcto, sobrevive a dividir y borrar clips y entra en deshacer.
 */
@RunWith(AndroidJUnit4::class)
class ClipFrameModelTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val mainUri = Uri.parse("file:///sdcard/Movies/main.mp4")
    private val otherUri = Uri.parse("file:///sdcard/Movies/other.mp4")
    private val frame = EditOperation.ClipFrame(scale = 0.5f, offsetX = 0.2f, offsetY = -0.1f)

    @Test
    fun projectWithoutFrameLoadsWithNullFrame() {
        // Un proyecto guardado antes de M-198 no trae "frame": debe quedar null (sin cambio),
        // no un ClipFrame con escala 0 como pasaría con un Float suelto en Gson.
        val json = ProjectSerializer.serialize(recipe(EditOperation.Merge(listOf(EditOperation.MergeItem(otherUri, 5000L)))))
        assertTrue("el JSON viejo no debe traer frame", !json.contains("\"frame\""))

        val item = mergeItems(ProjectSerializer.deserialize(json)).single()
        assertNull(item.frame)
    }

    @Test
    fun framesRoundTrip() {
        val original = recipe(
            EditOperation.FrameMain(frame),
            EditOperation.Merge(listOf(EditOperation.MergeItem(otherUri, 5000L, frame = frame)))
        )
        val loaded = ProjectSerializer.deserialize(ProjectSerializer.serialize(original))

        assertEquals(frame, loaded.operations.filterIsInstance<EditOperation.FrameMain>().single().frame)
        assertEquals(frame, mergeItems(loaded).single().frame)
    }

    @Test
    fun frameWithMissingFieldsTakesDefaults() {
        val json = ProjectSerializer.serialize(recipe(EditOperation.FrameMain(frame)))
            .replace(Regex("\"offsetX\":[-0-9.]+,?"), "")
        val loaded = ProjectSerializer.deserialize(json).operations.filterIsInstance<EditOperation.FrameMain>().single()
        assertEquals(0.5f, loaded.frame.scale, 0f)
        assertEquals(0f, loaded.frame.offsetX, 0f)
    }

    @Test
    fun updateClipFrameTargetsTheRightClipAndIdentityClearsIt() = onMain {
        val vm = viewModelWithTwoClips()

        vm.updateClipFrame(0, frame)
        assertEquals(frame, vm.ops().filterIsInstance<EditOperation.FrameMain>().single().frame)

        vm.updateClipFrame(1, frame)
        assertEquals(frame, mergeItemsOf(vm).single().frame)

        // La identidad no se guarda: un clip sin encuadrar no debe dejar basura en el proyecto.
        vm.updateClipFrame(0, EditOperation.ClipFrame())
        vm.updateClipFrame(1, EditOperation.ClipFrame())
        assertTrue(vm.ops().none { it is EditOperation.FrameMain })
        assertNull(mergeItemsOf(vm).single().frame)
    }

    @Test
    fun undoRestoresThePreviousFrame() = onMain {
        val vm = viewModelWithTwoClips()
        vm.updateClipFrame(0, frame)
        vm.undo()
        assertTrue(vm.ops().none { it is EditOperation.FrameMain })
    }

    @Test
    fun splittingTheMainClipKeepsTheFrameOnBothHalves() = onMain {
        val vm = VideoEditingViewModel()
        vm.initializeProject(mainUri, "main.mp4")
        vm.updateClipFrame(0, frame)

        vm.splitVideoSegment(0, 2000L, mainUri, 5000L)

        assertEquals(frame, vm.ops().filterIsInstance<EditOperation.FrameMain>().single().frame)
        assertEquals(frame, mergeItemsOf(vm).first().frame)
    }

    @Test
    fun deletingTheMainClipPromotesTheNextClipFrame() = onMain {
        val vm = viewModelWithTwoClips()
        vm.updateClipFrame(0, EditOperation.ClipFrame(scale = 2f))
        vm.updateClipFrame(1, frame)

        vm.deleteSequenceSegment(0)

        assertEquals(frame, vm.ops().filterIsInstance<EditOperation.FrameMain>().single().frame)
    }

    private fun viewModelWithTwoClips(): VideoEditingViewModel {
        val vm = VideoEditingViewModel()
        vm.initializeProject(mainUri, "main.mp4")
        vm.executeCommand(com.tharunbirla.librecuts.commands.AddOperationCommand(
            EditOperation.Merge(listOf(EditOperation.MergeItem(otherUri, 5000L))), "Merge"
        ))
        return vm
    }

    private fun VideoEditingViewModel.ops() = project.value!!.operations

    private fun mergeItemsOf(vm: VideoEditingViewModel) =
        vm.ops().filterIsInstance<EditOperation.Merge>().single().items

    private fun mergeItems(recipe: EditRecipe) =
        recipe.operations.filterIsInstance<EditOperation.Merge>().single().items

    private fun recipe(vararg ops: EditOperation) =
        EditRecipe(projectName = "p", sourceUri = mainUri, sourceName = "main.mp4", operations = ops.toList())

    private fun onMain(block: () -> Unit) = instrumentation.runOnMainSync(block)
}
