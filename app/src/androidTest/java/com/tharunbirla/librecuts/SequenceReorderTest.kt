package com.tharunbirla.librecuts

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tharunbirla.librecuts.models.EditOperation
import com.tharunbirla.librecuts.models.EditRecipe
import com.tharunbirla.librecuts.utils.ProjectSerializer
import com.tharunbirla.librecuts.viewmodels.VideoEditingViewModel
import com.tharunbirla.librecuts.viewmodels.reorderSequence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Reordenar clips (M-210): la secuencia conserva su cantidad de clips porque el primero se
 * promueve a principal en vez de copiarse dentro de `Merge.items`.
 */
@RunWith(AndroidJUnit4::class)
class SequenceReorderTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val mainUri = Uri.parse("file:///sdcard/Movies/main.mp4")
    private val otherUri = Uri.parse("file:///sdcard/Movies/other.mp4")
    private val thirdUri = Uri.parse("file:///sdcard/Movies/third.mp4")
    private val frame = EditOperation.ClipFrame(scale = 0.5f, offsetX = 0.2f, offsetY = -0.1f)

    @Test
    fun reorderingTwoClipsKeepsTwoClips() = onMain {
        val vm = viewModelWithTwoClips()
        val items = sequenceOf(vm)

        vm.reorderSequence(listOf(items[1], items[0]))

        // El síntoma de M-210 era que aquí quedaban 3: el principal en sourceUri y otra vez adentro
        // de Merge.items.
        assertEquals(2, clipCount(vm))
        assertEquals(otherUri, vm.sourceUri())
        assertEquals(mainUri, mergeItemsOf(vm).single().uri)
    }

    @Test
    fun movingTheMainClipCarriesItsPropertiesIntoMerge() = onMain {
        val vm = viewModelWithTwoClips()
        val main = sequenceOf(vm)[0].copy(
            trimStartMs = 1000L, trimEndMs = 4000L, speed = 2.0f, isMirrored = true, frame = frame
        )
        val other = sequenceOf(vm)[1]

        vm.reorderSequence(listOf(other, main))

        val moved = mergeItemsOf(vm).single()
        assertEquals(1000L, moved.trimStartMs)
        assertEquals(4000L, moved.trimEndMs)
        assertEquals(2.0f, moved.speed, 0f)
        assertTrue(moved.isMirrored)
        assertEquals(frame, moved.frame)
    }

    @Test
    fun promotedClipPropertiesBecomeMainOperations() = onMain {
        val vm = viewModelWithTwoClips()
        val main = sequenceOf(vm)[0]
        val other = sequenceOf(vm)[1].copy(
            trimStartMs = 500L, trimEndMs = 3500L, speed = 0.5f, isMirrored = true, frame = frame
        )

        vm.reorderSequence(listOf(other, main))

        val trim = vm.ops().filterIsInstance<EditOperation.Trim>().single()
        assertEquals(500L, trim.startMs)
        assertEquals(3500L, trim.endMs)
        assertEquals(0.5f, vm.ops().filterIsInstance<EditOperation.SpeedMain>().single().speed, 0f)
        assertTrue(vm.ops().filterIsInstance<EditOperation.MirrorMain>().single().isMirrored)
        assertEquals(frame, vm.ops().filterIsInstance<EditOperation.FrameMain>().single().frame)
    }

    @Test
    fun propertiesOfTheOldMainClipDoNotStayBehind() = onMain {
        val vm = viewModelWithTwoClips()
        val main = sequenceOf(vm)[0].copy(speed = 3.0f, isMirrored = true, frame = frame)
        val other = sequenceOf(vm)[1]

        vm.reorderSequence(listOf(other, main))

        // El clip que dejó de ser principal se lleva sus operaciones *Main: si quedaran, se
        // aplicarían al clip promovido.
        assertTrue(vm.ops().none { it is EditOperation.SpeedMain })
        assertTrue(vm.ops().none { it is EditOperation.MirrorMain })
        assertTrue(vm.ops().none { it is EditOperation.FrameMain })
    }

    @Test
    fun reorderingThreeClipsRespectsTheChosenOrder() = onMain {
        val vm = viewModelWithTwoClips()
        vm.executeCommand(com.tharunbirla.librecuts.commands.MutateListCommand("Add") { ops ->
            val idx = ops.indexOfFirst { it is EditOperation.Merge }
            val merge = ops[idx] as EditOperation.Merge
            ops.toMutableList().also {
                it[idx] = merge.copy(items = merge.items + EditOperation.MergeItem(thirdUri, 5000L))
            }
        })
        val items = sequenceOf(vm)

        vm.reorderSequence(listOf(items[2], items[0], items[1]))

        assertEquals(3, clipCount(vm))
        assertEquals(thirdUri, vm.sourceUri())
        assertEquals(listOf(mainUri, otherUri), mergeItemsOf(vm).map { it.uri })
    }

    @Test
    fun reorderingDownToASingleClipDropsTheMergeOperation() = onMain {
        val vm = viewModelWithTwoClips()

        vm.reorderSequence(listOf(sequenceOf(vm)[1]))

        assertEquals(otherUri, vm.sourceUri())
        assertTrue(vm.ops().none { it is EditOperation.Merge })
    }

    @Test
    fun undoRestoresTheOriginalOrder() = onMain {
        val vm = viewModelWithTwoClips()
        val items = sequenceOf(vm)

        vm.reorderSequence(listOf(items[1], items[0]))
        vm.undo()

        assertEquals(mainUri, vm.sourceUri())
        assertEquals(otherUri, mergeItemsOf(vm).single().uri)
    }

    @Test
    fun savedProjectDoesNotCarryTheMainClipTwice() = onMain {
        val vm = viewModelWithTwoClips()
        val items = sequenceOf(vm)
        vm.reorderSequence(listOf(items[1], items[0]))

        val recipe = EditRecipe(
            projectName = "p",
            sourceUri = vm.sourceUri(),
            sourceName = "other.mp4",
            operations = vm.ops()
        )
        val loaded = ProjectSerializer.deserialize(ProjectSerializer.serialize(recipe))

        assertEquals(otherUri, loaded.sourceUri)
        val items2 = loaded.operations.filterIsInstance<EditOperation.Merge>().single().items
        assertEquals(listOf(mainUri), items2.map { it.uri })
    }

    /**
     * Réplica de `VideoEditingActivity.getSequenceItems()`: el clip principal como `MergeItem`
     * virtual, seguido de los items de `Merge`. Es la lista que recibe `reorderSequence`.
     */
    private fun sequenceOf(vm: VideoEditingViewModel): List<EditOperation.MergeItem> {
        val ops = vm.ops()
        val trim = ops.filterIsInstance<EditOperation.Trim>().lastOrNull()
        val speed = ops.filterIsInstance<EditOperation.SpeedMain>().lastOrNull()
        val mirror = ops.filterIsInstance<EditOperation.MirrorMain>().lastOrNull()
        val mask = ops.filterIsInstance<EditOperation.MaskMain>().lastOrNull()
        val main = EditOperation.MergeItem(
            uri = vm.sourceUri(),
            durationMs = 5000L,
            trimStartMs = trim?.startMs ?: 0L,
            trimEndMs = trim?.endMs ?: 5000L,
            speed = speed?.speed ?: 1.0f,
            isMirrored = mirror?.isMirrored ?: false,
            maskConfig = mask?.maskConfig ?: EditOperation.MaskConfig(),
            frame = ops.filterIsInstance<EditOperation.FrameMain>().lastOrNull()?.frame
        )
        return listOf(main) + (ops.filterIsInstance<EditOperation.Merge>().firstOrNull()?.items ?: emptyList())
    }

    private fun clipCount(vm: VideoEditingViewModel) = 1 + mergeItemsOf(vm).size

    private fun viewModelWithTwoClips(): VideoEditingViewModel {
        val vm = VideoEditingViewModel()
        vm.initializeProject(mainUri, "main.mp4")
        vm.executeCommand(com.tharunbirla.librecuts.commands.AddOperationCommand(
            EditOperation.Merge(listOf(EditOperation.MergeItem(otherUri, 5000L))), "Merge"
        ))
        return vm
    }

    private fun VideoEditingViewModel.ops() = project.value!!.operations

    private fun VideoEditingViewModel.sourceUri() = project.value!!.sourceUri

    private fun mergeItemsOf(vm: VideoEditingViewModel) =
        vm.ops().filterIsInstance<EditOperation.Merge>().single().items

    private fun onMain(block: () -> Unit) = instrumentation.runOnMainSync(block)
}
