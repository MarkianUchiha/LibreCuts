package com.tharunbirla.librecuts

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tharunbirla.librecuts.models.EditOperation
import com.tharunbirla.librecuts.viewmodels.VideoEditingViewModel
import com.tharunbirla.librecuts.viewmodels.insertFreezeFrame
import com.tharunbirla.librecuts.viewmodels.reorderSequence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Congelar cuadro (M-211, punto 2). Cada test nombra el criterio de `specs/congelar-cuadro.md`
 * que verifica.
 */
@RunWith(AndroidJUnit4::class)
class FreezeFrameTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val mainUri = Uri.parse("file:///sdcard/Movies/main.mp4")
    private val otherUri = Uri.parse("file:///sdcard/Movies/other.mp4")
    private val freezeUri = Uri.parse("file:///data/cache/freeze_vid.mp4")
    private val frame = EditOperation.ClipFrame(scale = 0.5f, offsetX = 0.2f, offsetY = -0.1f)

    private val main = EditOperation.MergeItem(mainUri, 10_000L, trimStartMs = 1000L, trimEndMs = 9000L)
    private val other = EditOperation.MergeItem(otherUri, 5000L)

    @Test
    fun ca1_freezeAtStartOfMainBecomesTheMainClip() = onMain {
        val vm = viewModelWith(main)

        vm.reorderSequence(insertFreezeFrame(listOf(main), 0, 0L, freezeUri))

        assertEquals(freezeUri, vm.sourceUri())
        val trim = vm.ops().filterIsInstance<EditOperation.Trim>().single()
        assertEquals(0L, trim.startMs)
        assertEquals(3000L, trim.endMs)
        // El principal anterior sigue entero: antes del arreglo se recortaba a los 3 s del freeze.
        val moved = mergeItemsOf(vm).single()
        assertEquals(mainUri, moved.uri)
        assertEquals(1000L, moved.trimStartMs)
        assertEquals(9000L, moved.trimEndMs)
    }

    @Test
    fun ca2_freezeInTheMiddleSplitsTheClipWithoutGaps() {
        val result = insertFreezeFrame(listOf(main, other), 0, 2000L, freezeUri)

        assertEquals(listOf(mainUri, freezeUri, mainUri, otherUri), result.map { it.uri })
        assertEquals(1000L, result[0].trimStartMs)
        assertEquals(3000L, result[0].trimEndMs)
        assertEquals(3000L, result[2].trimStartMs)
        assertEquals(9000L, result[2].trimEndMs)
    }

    @Test
    fun ca2_splitPointAccountsForSpeed() {
        val fast = main.copy(speed = 2.0f)

        val result = insertFreezeFrame(listOf(fast), 0, 1000L, freezeUri)

        // 1 s en el timeline a 2x son 2 s de la fuente.
        assertEquals(3000L, result[0].trimEndMs)
        assertEquals(3000L, result[2].trimStartMs)
    }

    @Test
    fun ca3_freezeOnALaterClipLeavesTheMainClipAlone() = onMain {
        val vm = viewModelWith(main, other)

        vm.reorderSequence(insertFreezeFrame(listOf(main, other), 1, 1000L, freezeUri))

        assertEquals(mainUri, vm.sourceUri())
        val trim = vm.ops().filterIsInstance<EditOperation.Trim>().single()
        assertEquals(1000L, trim.startMs)
        assertEquals(9000L, trim.endMs)
        assertEquals(listOf(otherUri, freezeUri, otherUri), mergeItemsOf(vm).map { it.uri })
    }

    @Test
    fun ca4_freezeInheritsFrameMirrorAndMask() {
        val mask = EditOperation.MaskConfig(shape = EditOperation.MaskShape.ELLIPSE, relativeX = 0.3f)
        val source = main.copy(frame = frame, isMirrored = true, maskConfig = mask)

        val freeze = insertFreezeFrame(listOf(source), 0, 2000L, freezeUri)[1]

        assertEquals(frame, freeze.frame)
        assertTrue(freeze.isMirrored)
        assertEquals(mask, freeze.maskConfig)
    }

    @Test
    fun ca4b_animatedMaskIsFrozenAtTheFreezeInstant() {
        val mask = EditOperation.MaskConfig(
            shape = EditOperation.MaskShape.ELLIPSE,
            relativeWidth = 0.4f,
            relativeHeight = 0.2f,
            positionKeyframes = listOf(
                EditOperation.KeyframePoint(0L, 0.0f, 0.0f),
                EditOperation.KeyframePoint(4000L, 1.0f, 0.5f)
            ),
            rotationKeyframes = listOf(
                EditOperation.KeyframePoint(0L, 0f),
                EditOperation.KeyframePoint(4000L, 90f)
            )
        )
        val source = main.copy(maskConfig = mask)

        val frozen = insertFreezeFrame(listOf(source), 0, 2000L, freezeUri)[1].maskConfig

        assertEquals(0.5f, frozen.relativeX, 0.0001f)
        assertEquals(0.25f, frozen.relativeY, 0.0001f)
        assertEquals(45f, frozen.rotationAngle, 0.0001f)
        // Sin keyframes de tamaño, ancho y alto se conservan tal cual; no se promedian.
        assertEquals(0.4f, frozen.relativeWidth, 0.0001f)
        assertEquals(0.2f, frozen.relativeHeight, 0.0001f)
        assertTrue(frozen.positionKeyframes.isEmpty())
        assertTrue(frozen.sizeKeyframes.isEmpty())
        assertTrue(frozen.rotationKeyframes.isEmpty())
        assertTrue(frozen.featherKeyframes.isEmpty())
    }

    @Test
    fun ca5_freezeDoesNotInheritSpeedOrReverse() {
        val source = main.copy(speed = 2.0f, isReversed = true)

        val freeze = insertFreezeFrame(listOf(source), 0, 1000L, freezeUri)[1]

        assertEquals(1.0f, freeze.speed, 0f)
        assertFalse(freeze.isReversed)
        assertEquals(3000L, freeze.trimmedDurationMs)
    }

    @Test
    fun ca6_singleUndoRestoresTheSequence() = onMain {
        val vm = viewModelWith(main, other)
        val before = vm.ops()

        vm.reorderSequence(insertFreezeFrame(listOf(main, other), 0, 0L, freezeUri))
        vm.undo()

        assertEquals(mainUri, vm.sourceUri())
        assertEquals(before, vm.ops())
    }

    @Test
    fun freezeAtTheEndOfAClipGoesAfterIt() {
        val result = insertFreezeFrame(listOf(main, other), 0, 8000L, freezeUri)

        assertEquals(listOf(mainUri, freezeUri, otherUri), result.map { it.uri })
        assertEquals(main, result[0])
    }

    /** Proyecto cuyo principal es `first` (con su recorte) y el resto va en `Merge`. */
    private fun viewModelWith(first: EditOperation.MergeItem, vararg rest: EditOperation.MergeItem): VideoEditingViewModel {
        val vm = VideoEditingViewModel()
        vm.initializeProject(first.uri, "main.mp4")
        vm.executeCommand(com.tharunbirla.librecuts.commands.AddOperationCommand(
            EditOperation.Trim(first.trimStartMs, first.trimEndMs), "Trim"
        ))
        if (rest.isNotEmpty()) {
            vm.executeCommand(com.tharunbirla.librecuts.commands.AddOperationCommand(
                EditOperation.Merge(rest.toList()), "Merge"
            ))
        }
        return vm
    }

    private fun VideoEditingViewModel.ops() = project.value!!.operations

    private fun VideoEditingViewModel.sourceUri() = project.value!!.sourceUri

    private fun mergeItemsOf(vm: VideoEditingViewModel) =
        vm.ops().filterIsInstance<EditOperation.Merge>().single().items

    private fun onMain(block: () -> Unit) = instrumentation.runOnMainSync(block)
}
