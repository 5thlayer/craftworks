// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.assembler;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * A right click on an EMI stack on the Assembler's screen queues five only when nothing else took the
 * press (#77): which presses fall through.
 */
class StackClickFillTest {

    private static final int LEFT = 0;
    private static final int RIGHT = 1;
    private static final int MIDDLE = 2;
    private static final int NO_MODIFIERS = 0;
    private static final int SHIFT = 1;

    @Test
    void aPlainRightClickNothingTookQueuesFive() {
        assertTrue(StackClickFill.fallsThroughToFive(RIGHT, NO_MODIFIERS, false));
    }

    @Test
    void aRightClickAnEmiBindTookIsLeftToThatBind() {
        assertFalse(StackClickFill.fallsThroughToFive(RIGHT, NO_MODIFIERS, true));
    }

    @Test
    void shiftRightClickIsNotAdded() {
        assertFalse(StackClickFill.fallsThroughToFive(RIGHT, SHIFT, false));
    }

    @Test
    void anyModifierIsNotAdded() {
        for (int modifiers = 1; modifiers < 16; modifiers++) {
            assertFalse(StackClickFill.fallsThroughToFive(RIGHT, modifiers, false));
        }
    }

    @Test
    void otherButtonsAreNotAdded() {
        assertFalse(StackClickFill.fallsThroughToFive(LEFT, NO_MODIFIERS, false));
        assertFalse(StackClickFill.fallsThroughToFive(MIDDLE, NO_MODIFIERS, false));
        assertFalse(StackClickFill.fallsThroughToFive(FillRequest.NO_BUTTON, NO_MODIFIERS, false));
    }
}
