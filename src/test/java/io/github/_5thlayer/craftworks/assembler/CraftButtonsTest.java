// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.assembler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Which of the Crafting Plan's three queueing buttons are live (#7).
 *
 * <p>A press queues at once, so a lit button is a promise that the inventory covers it. The ceiling
 * is the Resolver's {@code largestAffordable}; this is only what the screen does with it.
 */
class CraftButtonsTest {

    @Test
    void nothingAffordableGreysAllThree() {
        CraftButtons buttons = CraftButtons.of(0);
        assertFalse(buttons.one());
        assertFalse(buttons.five());
        assertFalse(buttons.all());
    }

    @Test
    void fewerThanFiveLeavesOnlyFiveGreyed() {
        CraftButtons buttons = CraftButtons.of(4);
        assertTrue(buttons.one());
        assertFalse(buttons.five());
        assertTrue(buttons.all());
    }

    @Test
    void exactlyFiveLightsFive() {
        assertTrue(CraftButtons.of(5).five());
    }

    @Test
    void allQueuesTheCeilingItself() {
        assertEquals(12, CraftButtons.of(12).allCount());
    }

    @Test
    void aNegativeCeilingIsNothingAffordable() {
        CraftButtons buttons = CraftButtons.of(-1);
        assertFalse(buttons.all());
        assertEquals(0, buttons.allCount());
    }
}
