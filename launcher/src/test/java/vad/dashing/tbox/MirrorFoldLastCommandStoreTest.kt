package vad.dashing.tbox

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class MirrorFoldLastCommandStoreTest {

    @Before
    fun setUp() {
        MirrorFoldLastCommandStore.resetForTests()
    }

    @Test
    fun defaultStateIsUnfolded() {
        assertEquals(MIRROR_FOLD_SWITCH_VALUE_UNFOLD, MirrorFoldLastCommandStore.readLastValue())
    }

    @Test
    fun firstSingleTapSendsFold() {
        val next = MirrorFoldLastCommandStore.nextSingleTapValue()
        assertEquals(MIRROR_FOLD_SWITCH_VALUE_FOLD, next)
    }

    @Test
    fun togglesSequentiallyBetweenFoldAndUnfold() {
        assertEquals(MIRROR_FOLD_SWITCH_VALUE_FOLD, MirrorFoldLastCommandStore.nextSingleTapValue())

        MirrorFoldLastCommandStore.rememberSent(MIRROR_FOLD_SWITCH_VALUE_FOLD)
        assertEquals(MIRROR_FOLD_SWITCH_VALUE_FOLD, MirrorFoldLastCommandStore.readLastValue())
        assertEquals(MIRROR_FOLD_SWITCH_VALUE_UNFOLD, MirrorFoldLastCommandStore.nextSingleTapValue())

        MirrorFoldLastCommandStore.rememberSent(MIRROR_FOLD_SWITCH_VALUE_UNFOLD)
        assertEquals(MIRROR_FOLD_SWITCH_VALUE_UNFOLD, MirrorFoldLastCommandStore.readLastValue())
        assertEquals(MIRROR_FOLD_SWITCH_VALUE_FOLD, MirrorFoldLastCommandStore.nextSingleTapValue())
    }

    @Test
    fun toggleLogicNormalizesUnknownValues() {
        assertEquals(MIRROR_FOLD_SWITCH_VALUE_UNFOLD, MirrorFoldToggleLogic.normalizeStoredValue(0))
        assertEquals(MIRROR_FOLD_SWITCH_VALUE_UNFOLD, MirrorFoldToggleLogic.normalizeStoredValue(99))
        assertEquals(MIRROR_FOLD_SWITCH_VALUE_FOLD, MirrorFoldToggleLogic.normalizeStoredValue(MIRROR_FOLD_SWITCH_VALUE_FOLD))
    }
}
