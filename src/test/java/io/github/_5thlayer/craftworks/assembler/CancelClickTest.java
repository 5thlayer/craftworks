// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.assembler;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github._5thlayer.craftworks.planner.AssemblerQueue;
import org.junit.jupiter.api.Test;

/** A click on a queue icon cancels crafts of that row's final recipe, Factorio's way (ADR-0005). */
class CancelClickTest {

    @Test
    void leftClickCancelsOne() {
        assertEquals(1, CancelClick.crafts(0, false));
    }

    @Test
    void rightClickCancelsFive() {
        assertEquals(5, CancelClick.crafts(1, false));
    }

    @Test
    void shiftCancelsAllWhicheverButton() {
        assertEquals(AssemblerQueue.ALL, CancelClick.crafts(0, true));
        assertEquals(AssemblerQueue.ALL, CancelClick.crafts(1, true));
    }

    @Test
    void anyOtherButtonCancelsNothing() {
        assertEquals(0, CancelClick.crafts(2, false));
        assertEquals(0, CancelClick.crafts(2, true));
        assertEquals(0, CancelClick.crafts(FillRequest.NO_BUTTON, false));
    }
}
