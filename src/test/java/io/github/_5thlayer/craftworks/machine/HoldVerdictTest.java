// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.machine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** What Fill Recipe on an open Assembler tells the player (#21). Which refusal comes first is the game tests'. */
class HoldVerdictTest {

    @Test
    void aHeldRecipeCarriesNoMessage() {
        assertTrue(HoldVerdict.HELD.held());
        assertNull(HoldVerdict.HELD.messageKey());
    }

    @Test
    void aFluidRefusalCarriesItsOwnMessage() {
        assertEquals("craftworks.assembler.refused.too_many_fluids", HoldVerdict.TOO_MANY_FLUIDS.messageKey());
        assertEquals("craftworks.assembler.refused.fluid_too_large", HoldVerdict.FLUID_TOO_LARGE.messageKey());
    }

    @Test
    void aRefusalIsToldInTheWordsOfTheMachineThatRefused() {
        assertEquals("craftworks.assembler.refused.wrong_category", HoldVerdict.WRONG_CATEGORY.messageKey());
        assertEquals("craftworks.assembler.refused.locked", HoldVerdict.LOCKED.messageKey());
    }
}
