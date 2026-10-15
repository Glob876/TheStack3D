package com.stackgame.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TowerRulesTest {
    @Test fun perfectPlacementKeepsSize() {
        val result = TowerRules.place(-1.5f, 3f, -1.4f, 3f)!!
        assertTrue(result.perfect)
        assertEquals(-1.5f, result.start)
        assertEquals(3f, result.size)
    }
    @Test fun trimsOverhangOnEitherSide() {
        val left = TowerRules.place(-1.5f, 3f, -2.5f, 3f)!!
        val right = TowerRules.place(-1.5f, 3f, -0.5f, 3f)!!
        assertEquals(-1.5f, left.start)
        assertEquals(-0.5f, right.start)
        assertEquals(2f, left.size)
        assertEquals(2f, right.size)
        assertFalse(left.perfect)
    }
    @Test fun missesAndTouchingEdgesEndTheRun() {
        assertNull(TowerRules.place(-1.5f, 3f, 1.5f, 3f))
        assertNull(TowerRules.place(-1.5f, 3f, -4.5f, 3f))
        assertNull(TowerRules.place(-1.5f, 3f, 4f, 3f))
    }
    @Test fun repeatedCutsUseTheRemainingSize() {
        val first = TowerRules.place(0f, 3f, 1f, 3f)!!
        val second = TowerRules.place(first.start, first.size, 2f, first.size)!!
        assertEquals(1f, second.size)
        assertEquals(2f, second.start)
    }
}
