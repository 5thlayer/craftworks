// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.machine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import io.github._5thlayer.craftworks.machine.HoldVerdict.Checks;
import org.junit.jupiter.api.Test;

/** Whether Fill Recipe on an open Assembler is taken (#21), and which refusal comes first. */
class HoldVerdictTest {

    @Test
    void aRecipeItCanRunAndIsNotLockedIsHeld() {
        HoldVerdict verdict = HoldVerdict.of(Checks.passing());
        assertTrue(verdict.held());
        assertNull(verdict.messageKey(MachineKind.ASSEMBLER));
    }

    @Test
    void aRecipeNothingNamesIsRefusedFirst() {
        Checks everythingWrong = Checks.passing().resolves(false).categoryHeld(false).takesItems(false).takesFluids(false)
                .oneFluid(false).fluidFits(false).fitsSlots(false).remaindersFit(false).locked(true);
        assertEquals(HoldVerdict.NOT_ASSEMBLING, HoldVerdict.of(everythingWrong));
    }

    @Test
    void aRecipeItCouldNeverRunIsRefusedBeforeItsLockIsAsked() {
        Checks locked = Checks.passing().locked(true);
        assertEquals(HoldVerdict.HAS_FLUID, HoldVerdict.of(locked.takesFluids(false)));
        assertEquals(HoldVerdict.TOO_MANY_FLUIDS, HoldVerdict.of(locked.oneFluid(false)));
        assertEquals(HoldVerdict.FLUID_TOO_LARGE, HoldVerdict.of(locked.fluidFits(false)));
        assertEquals(HoldVerdict.TOO_MANY_INGREDIENTS, HoldVerdict.of(locked.fitsSlots(false)));
        assertEquals(HoldVerdict.REMAINDERS_DONT_FIT, HoldVerdict.of(locked.remaindersFit(false)));
    }

    @Test
    void theFluidRefusalsComeInOrderAfterTheCategoryAndBeforeTheSlots() {
        Checks failing = Checks.passing().fitsSlots(false).fluidFits(false);
        assertEquals(HoldVerdict.FLUID_TOO_LARGE, HoldVerdict.of(failing));
        assertEquals(HoldVerdict.TOO_MANY_FLUIDS, HoldVerdict.of(failing.oneFluid(false)));
        assertEquals(HoldVerdict.HAS_FLUID, HoldVerdict.of(failing.oneFluid(false).takesFluids(false)));
        assertEquals(HoldVerdict.WRONG_CATEGORY,
                HoldVerdict.of(failing.takesFluids(false).categoryHeld(false)));
    }

    @Test
    void aFluidRefusalCarriesItsOwnMessage() {
        assertEquals("craftworks.assembler.refused.too_many_fluids", HoldVerdict.TOO_MANY_FLUIDS.messageKey(MachineKind.ASSEMBLER));
        assertEquals("craftworks.assembler.refused.fluid_too_large", HoldVerdict.FLUID_TOO_LARGE.messageKey(MachineKind.ASSEMBLER));
    }

    @Test
    void aCategoryTheTierDoesNotHoldIsRefusedBeforeAnythingElseAboutTheRecipe() {
        Checks wrongInEveryWay = Checks.passing().categoryHeld(false).takesFluids(false).fitsSlots(false).locked(true);
        HoldVerdict verdict = HoldVerdict.of(wrongInEveryWay);
        assertEquals(HoldVerdict.WRONG_CATEGORY, verdict);
        assertEquals("craftworks.assembler.refused.wrong_category", verdict.messageKey(MachineKind.ASSEMBLER));
    }

    @Test
    void aLockedRecipeIsRefusedWithAMessage() {
        HoldVerdict verdict = HoldVerdict.of(Checks.passing().locked(true));
        assertEquals(HoldVerdict.LOCKED, verdict);
        assertEquals("craftworks.assembler.refused.locked", verdict.messageKey(MachineKind.ASSEMBLER));
    }

    @Test
    void aRefusalIsToldInTheWordsOfTheMachineThatRefused() {
        assertEquals("craftworks.assembler.refused.wrong_category", HoldVerdict.WRONG_CATEGORY.messageKey(MachineKind.ASSEMBLER));
        assertEquals("craftworks.chemical_plant.refused.wrong_category", HoldVerdict.WRONG_CATEGORY.messageKey(MachineKind.CHEMICAL_PLANT));
        assertEquals("craftworks.chemical_plant.refused.locked", HoldVerdict.LOCKED.messageKey(MachineKind.CHEMICAL_PLANT));
        assertNull(HoldVerdict.HELD.messageKey(MachineKind.CHEMICAL_PLANT));
    }

    @Test
    void aRecipeWithItemsOnAMachineWithNoItemSlotsIsRefusedAfterItsCategoryAndBeforeTheRest() {
        Checks failing = Checks.passing().takesItems(false).takesFluids(false).fitsSlots(false).locked(true);
        HoldVerdict verdict = HoldVerdict.of(failing);
        assertEquals(HoldVerdict.HAS_ITEMS, verdict);
        assertEquals("craftworks.assembler.refused.has_items", verdict.messageKey(MachineKind.ASSEMBLER));
        assertEquals(HoldVerdict.WRONG_CATEGORY, HoldVerdict.of(failing.categoryHeld(false)));
    }

    @Test
    void aMachineWithItemSlotsNeverRefusesForHavingItems() {
        assertTrue(FluidMachine.CHEMICAL_PLANT.hasItemSlots());
        assertTrue(HoldVerdict.of(Checks.passing()).held());
    }

    @Test
    void aMachineWithNeitherInputsNorAProductHasNoItemSlots() {
        FluidMachine fluidsOnly = new FluidMachine(MachineKind.CHEMICAL_PLANT, ChemicalPlantDefaults.INSTANCE, 0, false, 2, 3, List.of());
        assertFalse(fluidsOnly.hasItemSlots());
        assertTrue(new FluidMachine(MachineKind.CHEMICAL_PLANT, ChemicalPlantDefaults.INSTANCE, 0, true, 2, 3, List.of()).hasItemSlots());
    }
}
