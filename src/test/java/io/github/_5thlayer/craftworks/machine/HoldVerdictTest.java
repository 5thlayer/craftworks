// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.machine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Whether Fill Recipe on an open Assembler is taken (#21), and which refusal comes first. */
class HoldVerdictTest {

    @Test
    void aRecipeItCanRunAndIsNotLockedIsHeld() {
        HoldVerdict verdict = HoldVerdict.of(true, true, true, true, true, true, true, false);
        assertTrue(verdict.held());
        assertNull(verdict.messageKey());
    }

    @Test
    void aRecipeNothingNamesIsRefusedFirst() {
        assertEquals(HoldVerdict.NOT_ASSEMBLING, HoldVerdict.of(false, false, false, false, false, false, false, true));
    }

    @Test
    void aRecipeItCouldNeverRunIsRefusedBeforeItsLockIsAsked() {
        assertEquals(HoldVerdict.HAS_FLUID, HoldVerdict.of(true, true, false, false, false, false, false, true));
        assertEquals(HoldVerdict.TOO_MANY_FLUIDS, HoldVerdict.of(true, true, true, false, false, false, false, true));
        assertEquals(HoldVerdict.FLUID_TOO_LARGE, HoldVerdict.of(true, true, true, true, false, false, false, true));
        assertEquals(HoldVerdict.TOO_MANY_INGREDIENTS, HoldVerdict.of(true, true, true, true, true, false, false, true));
        assertEquals(HoldVerdict.REMAINDERS_DONT_FIT, HoldVerdict.of(true, true, true, true, true, true, false, true));
    }

    @Test
    void aFluidRefusalCarriesItsOwnMessage() {
        assertEquals("craftworks.assembler.refused.too_many_fluids", HoldVerdict.TOO_MANY_FLUIDS.messageKey());
        assertEquals("craftworks.assembler.refused.fluid_too_large", HoldVerdict.FLUID_TOO_LARGE.messageKey());
    }

    @Test
    void aCategoryTheTierDoesNotHoldIsRefusedBeforeAnythingElseAboutTheRecipe() {
        HoldVerdict verdict = HoldVerdict.of(true, false, false, false, false, false, false, true);
        assertEquals(HoldVerdict.WRONG_CATEGORY, verdict);
        assertEquals("craftworks.assembler.refused.wrong_category", verdict.messageKey());
    }

    @Test
    void aLockedRecipeIsRefusedWithAMessage() {
        HoldVerdict verdict = HoldVerdict.of(true, true, true, true, true, true, true, true);
        assertEquals(HoldVerdict.LOCKED, verdict);
        assertEquals("craftworks.assembler.refused.locked", verdict.messageKey());
    }
}
