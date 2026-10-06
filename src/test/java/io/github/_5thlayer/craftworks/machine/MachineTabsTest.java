// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.machine;

import static io.github._5thlayer.craftworks.recipe.AssemblingCategory.ADVANCED_CRAFTING;
import static io.github._5thlayer.craftworks.recipe.AssemblingCategory.CHEMISTRY;
import static io.github._5thlayer.craftworks.recipe.AssemblingCategory.CRAFTING;
import static io.github._5thlayer.craftworks.recipe.AssemblingCategory.CRAFTING_WITH_FLUID;
import static io.github._5thlayer.craftworks.recipe.AssemblingCategory.OIL_PROCESSING;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.github._5thlayer.craftworks.recipe.AssemblingCategory;
import org.junit.jupiter.api.Test;

/** Which recipe viewer tabs an Assembling recipe goes in, from the categories each machine holds (#38). */
class MachineTabsTest {

    /** The default config: tier 1 holds two categories, tiers 2 and 3 add one, the plant holds chemistry and the refinery oil-processing. */
    private static final MachineTabs DEFAULTS = MachineTabs.of(
            List.of(List.of(CRAFTING, ADVANCED_CRAFTING),
                    List.of(CRAFTING, ADVANCED_CRAFTING, CRAFTING_WITH_FLUID),
                    List.of(CRAFTING, ADVANCED_CRAFTING, CRAFTING_WITH_FLUID)),
            List.of(CHEMISTRY),
            List.of(OIL_PROCESSING));

    @Test
    void underTheDefaultConfigTheAssemblerTabTakesAnyCategoryAnyTierHolds() {
        assertEquals(List.of(MachineKind.ASSEMBLER), DEFAULTS.tabsFor(CRAFTING));
        assertEquals(List.of(MachineKind.ASSEMBLER), DEFAULTS.tabsFor(ADVANCED_CRAFTING));
        assertEquals(List.of(MachineKind.ASSEMBLER), DEFAULTS.tabsFor(CRAFTING_WITH_FLUID));
    }

    @Test
    void underTheDefaultConfigChemistryIsTheChemicalPlantsAlone() {
        assertEquals(List.of(MachineKind.CHEMICAL_PLANT), DEFAULTS.tabsFor(CHEMISTRY));
    }

    @Test
    void anOilProcessingRecipeLandsInTheOilRefinerysTabWhenItHoldsTheCategory() {
        assertEquals(List.of(MachineKind.OIL_REFINERY), DEFAULTS.tabsFor(OIL_PROCESSING));
    }

    @Test
    void aCategoryNoMachineHoldsIsInNoTab() {
        MachineTabs inert = MachineTabs.of(List.of(List.of(CRAFTING)), List.of(CHEMISTRY), List.of());
        assertEquals(List.of(), inert.tabsFor(OIL_PROCESSING));
    }

    @Test
    void aCategoryBothMachinesHoldIsInBothTabs() {
        MachineTabs both = MachineTabs.of(List.of(List.of(CRAFTING), List.of(CHEMISTRY)), List.of(CHEMISTRY, CRAFTING), List.of());
        assertEquals(List.of(MachineKind.ASSEMBLER, MachineKind.CHEMICAL_PLANT), both.tabsFor(CHEMISTRY));
        assertEquals(List.of(MachineKind.ASSEMBLER, MachineKind.CHEMICAL_PLANT), both.tabsFor(CRAFTING));
    }

    @Test
    void aMachineHoldingNothingHasNoRecipes() {
        MachineTabs none = MachineTabs.of(List.of(List.of(), List.of()), List.of(), List.of());
        assertEquals(List.of(), none.tabsFor(CRAFTING));
    }

    @Test
    void sortingFillsEachTabInOrderAndCountsTheRecipesLeftOut() {
        Map<String, AssemblingCategory> recipes = new LinkedHashMap<>();
        recipes.put("plank", CRAFTING);
        recipes.put("acid", CHEMISTRY);
        recipes.put("crude", OIL_PROCESSING);
        recipes.put("gear", ADVANCED_CRAFTING);
        recipes.put("fuel", OIL_PROCESSING);

        MachineTabs.Sorted<String> sorted = MachineTabs.of(List.of(List.of(CRAFTING, ADVANCED_CRAFTING)), List.of(CHEMISTRY), List.of())
                .sort(recipes.keySet(), recipes::get);

        assertEquals(List.of("plank", "gear"), sorted.in(MachineKind.ASSEMBLER));
        assertEquals(List.of("acid"), sorted.in(MachineKind.CHEMICAL_PLANT));
        assertEquals(2, sorted.leftOut());
    }

    @Test
    void sortingPutsAnOilProcessingRecipeInTheRefinerysTabAndLeavesNoneOut() {
        MachineTabs.Sorted<String> sorted = DEFAULTS.sort(List.of("crude"), recipe -> OIL_PROCESSING);
        assertEquals(List.of("crude"), sorted.in(MachineKind.OIL_REFINERY));
        assertEquals(List.of(), sorted.in(MachineKind.ASSEMBLER));
        assertEquals(0, sorted.leftOut());
    }

    @Test
    void aRecipeInBothTabsIsCountedInNeitherLeftOut() {
        MachineTabs both = MachineTabs.of(List.of(List.of(CHEMISTRY)), List.of(CHEMISTRY), List.of());
        MachineTabs.Sorted<String> sorted = both.sort(List.of("acid"), recipe -> CHEMISTRY);
        assertEquals(List.of("acid"), sorted.in(MachineKind.ASSEMBLER));
        assertEquals(List.of("acid"), sorted.in(MachineKind.CHEMICAL_PLANT));
        assertEquals(0, sorted.leftOut());
    }
}
