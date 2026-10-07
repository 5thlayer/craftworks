// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.machine;

import static io.github._5thlayer.craftworks.recipe.AssemblingCategory.ADVANCED_CRAFTING;
import static io.github._5thlayer.craftworks.recipe.AssemblingCategory.CRAFTING;
import static io.github._5thlayer.craftworks.recipe.AssemblingCategory.CRAFTING_WITH_FLUID;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.github._5thlayer.craftworks.recipe.AssemblingCategory;
import org.junit.jupiter.api.Test;

/** Which recipe viewer tabs an Assembling recipe goes in, from the categories the Assembler's tiers hold (#38). */
class MachineTabsTest {

    /** The Assembler's tab takes the union of its tiers' categories. */
    private static MachineTabs tabs(List<List<AssemblingCategory>> assemblerTiers) {
        return MachineTabs.of(Map.of(MachineKind.ASSEMBLER, assemblerTiers.stream().flatMap(List::stream).toList()));
    }

    /** The real defaults, as {@code ConfiguredTabs} reads them with nothing in the config. */
    @Test
    void underTheDefaultConfigTheAssemblerTabTakesAnyCategoryAnyTierHolds() {
        List<AssemblingCategory> assembler = Arrays.stream(AssemblerTier.values())
                .flatMap(tier -> tier.defaultCategories().stream()).distinct().toList();
        MachineTabs byDefault = MachineTabs.of(Map.of(MachineKind.ASSEMBLER, assembler));
        assertEquals(List.of(MachineKind.ASSEMBLER), byDefault.tabsFor(CRAFTING));
        assertEquals(List.of(MachineKind.ASSEMBLER), byDefault.tabsFor(ADVANCED_CRAFTING));
        assertEquals(List.of(MachineKind.ASSEMBLER), byDefault.tabsFor(CRAFTING_WITH_FLUID));
    }

    @Test
    void aCategoryOnlyAHigherTierHoldsIsInTheAssemblersTab() {
        MachineTabs fluid = tabs(List.of(List.of(CRAFTING), List.of(CRAFTING, CRAFTING_WITH_FLUID)));
        assertEquals(List.of(MachineKind.ASSEMBLER), fluid.tabsFor(CRAFTING_WITH_FLUID));
    }

    @Test
    void aCategoryNoTierHoldsIsInNoTab() {
        MachineTabs inert = tabs(List.of(List.of(CRAFTING)));
        assertEquals(List.of(), inert.tabsFor(CRAFTING_WITH_FLUID));
    }

    @Test
    void aMachineHoldingNothingHasNoRecipes() {
        MachineTabs none = tabs(List.of(List.of(), List.of()));
        assertEquals(List.of(), none.tabsFor(CRAFTING));
    }

    @Test
    void aMachineMissingFromTheMapHoldsNothing() {
        assertEquals(List.of(), MachineTabs.of(Map.of()).tabsFor(CRAFTING));
    }

    @Test
    void sortingFillsTheTabInOrderAndCountsTheRecipesLeftOut() {
        Map<String, AssemblingCategory> recipes = new LinkedHashMap<>();
        recipes.put("plank", CRAFTING);
        recipes.put("bucket", CRAFTING_WITH_FLUID);
        recipes.put("gear", ADVANCED_CRAFTING);
        recipes.put("pipe", CRAFTING_WITH_FLUID);

        MachineTabs.Sorted<String> sorted = tabs(List.of(List.of(CRAFTING, ADVANCED_CRAFTING)))
                .sort(recipes.keySet(), recipes::get);

        assertEquals(List.of("plank", "gear"), sorted.in(MachineKind.ASSEMBLER));
        assertEquals(2, sorted.leftOut());
    }

    @Test
    void sortingLeavesNoneOutWhenTheTierHoldsEveryCategory() {
        MachineTabs.Sorted<String> sorted = tabs(List.of(List.of(CRAFTING, ADVANCED_CRAFTING, CRAFTING_WITH_FLUID)))
                .sort(List.of("pipe"), recipe -> CRAFTING_WITH_FLUID);
        assertEquals(List.of("pipe"), sorted.in(MachineKind.ASSEMBLER));
        assertEquals(0, sorted.leftOut());
    }
}
