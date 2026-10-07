// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.machine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github._5thlayer.craftworks.machine.HoldVerdict.Checks;
import org.junit.jupiter.api.Test;

/** Whether Fill Recipe on an open Assembler is taken (#21), and which refusal comes first. */
class HoldVerdictTest {

    @Test
    void aRecipeItCanRunAndIsNotLockedIsHeld() {
        HoldVerdict verdict = HoldVerdict.of(Checks.passing());
        assertTrue(verdict.held());
        assertNull(verdict.messageKey());
    }

    @Test
    void aRecipeNothingNamesIsRefusedFirst() {
        Checks everythingWrong = Checks.passing().resolves(false).categoryHeld(false)
                .fluidsHaveBoxes(false).fluidVolumeFits(false).fitsSlots(false).remaindersFit(false).locked(true);
        assertEquals(HoldVerdict.NOT_ASSEMBLING, HoldVerdict.of(everythingWrong));
    }

    @Test
    void aRecipeItCouldNeverRunIsRefusedBeforeItsLockIsAsked() {
        Checks locked = Checks.passing().locked(true);
        assertEquals(HoldVerdict.TOO_MANY_FLUIDS, HoldVerdict.of(locked.fluidsHaveBoxes(false)));
        assertEquals(HoldVerdict.FLUID_TOO_LARGE, HoldVerdict.of(locked.fluidVolumeFits(false)));
        assertEquals(HoldVerdict.TOO_MANY_INGREDIENTS, HoldVerdict.of(locked.fitsSlots(false)));
        assertEquals(HoldVerdict.REMAINDERS_DONT_FIT, HoldVerdict.of(locked.remaindersFit(false)));
    }

    @Test
    void theFluidRefusalsComeInOrderAfterTheCategoryAndBeforeTheSlots() {
        Checks failing = Checks.passing().fitsSlots(false).fluidVolumeFits(false);
        assertEquals(HoldVerdict.FLUID_TOO_LARGE, HoldVerdict.of(failing));
        assertEquals(HoldVerdict.TOO_MANY_FLUIDS, HoldVerdict.of(failing.fluidsHaveBoxes(false)));
        assertEquals(HoldVerdict.WRONG_CATEGORY,
                HoldVerdict.of(failing.fluidsHaveBoxes(false).categoryHeld(false)));
    }

    @Test
    void aFluidRefusalCarriesItsOwnMessage() {
        assertEquals("craftworks.assembler.refused.too_many_fluids", HoldVerdict.TOO_MANY_FLUIDS.messageKey());
        assertEquals("craftworks.assembler.refused.fluid_too_large", HoldVerdict.FLUID_TOO_LARGE.messageKey());
    }

    @Test
    void aCategoryTheTierDoesNotHoldIsRefusedBeforeAnythingElseAboutTheRecipe() {
        Checks wrongInEveryWay = Checks.passing().categoryHeld(false).fitsSlots(false).locked(true);
        HoldVerdict verdict = HoldVerdict.of(wrongInEveryWay);
        assertEquals(HoldVerdict.WRONG_CATEGORY, verdict);
        assertEquals("craftworks.assembler.refused.wrong_category", verdict.messageKey());
    }

    @Test
    void aLockedRecipeIsRefusedWithAMessage() {
        HoldVerdict verdict = HoldVerdict.of(Checks.passing().locked(true));
        assertEquals(HoldVerdict.LOCKED, verdict);
        assertEquals("craftworks.assembler.refused.locked", verdict.messageKey());
    }

    @Test
    void aRefusalIsToldInTheWordsOfTheMachineThatRefused() {
        assertEquals("craftworks.assembler.refused.wrong_category", HoldVerdict.WRONG_CATEGORY.messageKey());
        assertEquals("craftworks.assembler.refused.locked", HoldVerdict.LOCKED.messageKey());
        assertNull(HoldVerdict.HELD.messageKey());
    }
}
