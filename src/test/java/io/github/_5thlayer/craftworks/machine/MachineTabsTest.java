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

    /**
     * The defaults the machines will have once the Oil Refinery's config section lands: tier 1 holds two categories,
     * tiers 2 and 3 add one, the plant holds chemistry and the refinery oil-processing. Today
     * {@code ConfiguredTabs} gives the refinery none, so its tab is inert.
     */
    private static final MachineTabs DEFAULTS_WITH_REFINERY = tabs(
            List.of(List.of(CRAFTING, ADVANCED_CRAFTING), List.of(CRAFTING, ADVANCED_CRAFTING, CRAFTING_WITH_FLUID),
                    List.of(CRAFTING, ADVANCED_CRAFTING, CRAFTING_WITH_FLUID)),
            List.of(CHEMISTRY),
            List.of(OIL_PROCESSING));

    /** The Assembler's tab takes the union of its tiers' categories. */
    private static MachineTabs tabs(List<List<AssemblingCategory>> assemblerTiers, List<AssemblingCategory> chemicalPlant,
            List<AssemblingCategory> oilRefinery) {
        return MachineTabs.of(Map.of(
                MachineKind.ASSEMBLER, assemblerTiers.stream().flatMap(List::stream).toList(),
                MachineKind.CHEMICAL_PLANT, chemicalPlant,
                MachineKind.OIL_REFINERY, oilRefinery));
    }

    @Test
    void underTheDefaultConfigTheAssemblerTabTakesAnyCategoryAnyTierHolds() {
        assertEquals(List.of(MachineKind.ASSEMBLER), DEFAULTS_WITH_REFINERY.tabsFor(CRAFTING));
        assertEquals(List.of(MachineKind.ASSEMBLER), DEFAULTS_WITH_REFINERY.tabsFor(ADVANCED_CRAFTING));
        assertEquals(List.of(MachineKind.ASSEMBLER), DEFAULTS_WITH_REFINERY.tabsFor(CRAFTING_WITH_FLUID));
    }

    @Test
    void underTheDefaultConfigChemistryIsTheChemicalPlantsAlone() {
        assertEquals(List.of(MachineKind.CHEMICAL_PLANT), DEFAULTS_WITH_REFINERY.tabsFor(CHEMISTRY));
    }

    @Test
    void anOilProcessingRecipeLandsInTheOilRefinerysTabWhenItHoldsTheCategory() {
        assertEquals(List.of(MachineKind.OIL_REFINERY), DEFAULTS_WITH_REFINERY.tabsFor(OIL_PROCESSING));
    }

    @Test
    void aCategoryNoMachineHoldsIsInNoTab() {
        MachineTabs inert = tabs(List.of(List.of(CRAFTING)), List.of(CHEMISTRY), List.of());
        assertEquals(List.of(), inert.tabsFor(OIL_PROCESSING));
    }

    @Test
    void aCategoryBothMachinesHoldIsInBothTabs() {
        MachineTabs both = tabs(List.of(List.of(CRAFTING), List.of(CHEMISTRY)), List.of(CHEMISTRY, CRAFTING), List.of());
        assertEquals(List.of(MachineKind.ASSEMBLER, MachineKind.CHEMICAL_PLANT), both.tabsFor(CHEMISTRY));
        assertEquals(List.of(MachineKind.ASSEMBLER, MachineKind.CHEMICAL_PLANT), both.tabsFor(CRAFTING));
    }

    @Test
    void aCategoryTheRefineryAndTheAssemblerBothHoldIsInBothTabs() {
        MachineTabs both = tabs(List.of(List.of(OIL_PROCESSING)), List.of(), List.of(OIL_PROCESSING));
        assertEquals(List.of(MachineKind.ASSEMBLER, MachineKind.OIL_REFINERY), both.tabsFor(OIL_PROCESSING));
    }

    @Test
    void aMachineHoldingNothingHasNoRecipes() {
        MachineTabs none = tabs(List.of(List.of(), List.of()), List.of(), List.of());
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

        MachineTabs.Sorted<String> sorted = tabs(List.of(List.of(CRAFTING, ADVANCED_CRAFTING)), List.of(CHEMISTRY), List.of())
                .sort(recipes.keySet(), recipes::get);

        assertEquals(List.of("plank", "gear"), sorted.in(MachineKind.ASSEMBLER));
        assertEquals(List.of("acid"), sorted.in(MachineKind.CHEMICAL_PLANT));
        assertEquals(2, sorted.leftOut());
    }

    @Test
    void sortingPutsAnOilProcessingRecipeInTheRefinerysTabAndLeavesNoneOut() {
        MachineTabs.Sorted<String> sorted = DEFAULTS_WITH_REFINERY.sort(List.of("crude"), recipe -> OIL_PROCESSING);
        assertEquals(List.of("crude"), sorted.in(MachineKind.OIL_REFINERY));
        assertEquals(List.of(), sorted.in(MachineKind.ASSEMBLER));
        assertEquals(0, sorted.leftOut());
    }

    @Test
    void aRecipeInBothTabsIsCountedInNeitherLeftOut() {
        MachineTabs both = tabs(List.of(List.of(CHEMISTRY)), List.of(CHEMISTRY), List.of());
        MachineTabs.Sorted<String> sorted = both.sort(List.of("acid"), recipe -> CHEMISTRY);
        assertEquals(List.of("acid"), sorted.in(MachineKind.ASSEMBLER));
        assertEquals(List.of("acid"), sorted.in(MachineKind.CHEMICAL_PLANT));
        assertEquals(0, sorted.leftOut());
    }
}
