// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.machine;

import static io.github._5thlayer.craftworks.recipe.AssemblingCategory.ADVANCED_CRAFTING;
import static io.github._5thlayer.craftworks.recipe.AssemblingCategory.CHEMISTRY;
import static io.github._5thlayer.craftworks.recipe.AssemblingCategory.CRAFTING;
import static io.github._5thlayer.craftworks.recipe.AssemblingCategory.CRAFTING_WITH_FLUID;
import static io.github._5thlayer.craftworks.recipe.AssemblingCategory.OIL_PROCESSING;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;

import io.github._5thlayer.craftworks.recipe.AssemblingCategory;
import org.junit.jupiter.api.Test;

/** Which recipe viewer tabs an Assembling recipe goes in, from the categories each machine holds (#38). */
class MachineTabsTest {

    /** The default config: tier 1 holds two categories, tiers 2 and 3 add one, the plant holds chemistry. */
    private static final MachineTabs DEFAULTS = MachineTabs.of(
            List.of(List.of(CRAFTING, ADVANCED_CRAFTING),
                    List.of(CRAFTING, ADVANCED_CRAFTING, CRAFTING_WITH_FLUID),
                    List.of(CRAFTING, ADVANCED_CRAFTING, CRAFTING_WITH_FLUID)),
            List.of(CHEMISTRY));

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
    void aCategoryNoMachineHoldsIsInNoTab() {
        assertEquals(List.of(), DEFAULTS.tabsFor(OIL_PROCESSING));
    }

    @Test
    void aCategoryBothMachinesHoldIsInBothTabs() {
        MachineTabs both = MachineTabs.of(List.of(List.of(CRAFTING), List.of(CHEMISTRY)), List.of(CHEMISTRY, CRAFTING));
        assertEquals(List.of(MachineKind.ASSEMBLER, MachineKind.CHEMICAL_PLANT), both.tabsFor(CHEMISTRY));
        assertEquals(List.of(MachineKind.ASSEMBLER, MachineKind.CHEMICAL_PLANT), both.tabsFor(CRAFTING));
    }

    @Test
    void aMachineHoldingNothingHasNoRecipes() {
        MachineTabs none = MachineTabs.of(List.of(List.of(), List.of()), List.of());
        assertEquals(List.of(), none.tabsFor(CRAFTING));
    }

    @Test
    void sortingFillsEachTabInOrderAndCountsTheRecipesLeftOut() {
        Map<String, AssemblingCategory> recipes = new java.util.LinkedHashMap<>();
        recipes.put("plank", CRAFTING);
        recipes.put("acid", CHEMISTRY);
        recipes.put("crude", OIL_PROCESSING);
        recipes.put("gear", ADVANCED_CRAFTING);
        recipes.put("fuel", OIL_PROCESSING);

        MachineTabs.Sorted<String> sorted = DEFAULTS.sort(recipes.keySet(), recipes::get);

        assertEquals(List.of("plank", "gear"), sorted.in(MachineKind.ASSEMBLER));
        assertEquals(List.of("acid"), sorted.in(MachineKind.CHEMICAL_PLANT));
        assertEquals(2, sorted.leftOut());
    }

    @Test
    void aRecipeInBothTabsIsCountedInNeitherLeftOut() {
        MachineTabs both = MachineTabs.of(List.of(List.of(CHEMISTRY)), List.of(CHEMISTRY));
        MachineTabs.Sorted<String> sorted = both.sort(List.of("acid"), recipe -> CHEMISTRY);
        assertEquals(List.of("acid"), sorted.in(MachineKind.ASSEMBLER));
        assertEquals(List.of("acid"), sorted.in(MachineKind.CHEMICAL_PLANT));
        assertEquals(0, sorted.leftOut());
    }
}
