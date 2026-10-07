// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.machine;

import static io.github._5thlayer.craftworks.recipe.AssemblingCategory.ADVANCED_CRAFTING;
import static io.github._5thlayer.craftworks.recipe.AssemblingCategory.CRAFTING;
import static io.github._5thlayer.craftworks.recipe.AssemblingCategory.CRAFTING_WITH_FLUID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.github._5thlayer.craftworks.recipe.AssemblingCategory;
import org.junit.jupiter.api.Test;

/** Which recipes the Assembler's Recipe viewer tab takes, from the categories its tiers hold (#38). */
class AssemblerTabTest {

    /** The tab takes the union of its tiers' categories. */
    private static AssemblerTab tab(List<List<AssemblingCategory>> tiers) {
        return AssemblerTab.of(tiers.stream().flatMap(List::stream).toList());
    }

    /** The real defaults, as {@code ConfiguredTabs} reads them with nothing in the config. */
    @Test
    void underTheDefaultConfigTheTabTakesAnyCategoryAnyTierHolds() {
        List<AssemblingCategory> held = Arrays.stream(AssemblerTier.values())
                .flatMap(tier -> tier.defaultCategories().stream()).distinct().toList();
        AssemblerTab byDefault = AssemblerTab.of(held);
        assertTrue(byDefault.holds(CRAFTING));
        assertTrue(byDefault.holds(ADVANCED_CRAFTING));
        assertTrue(byDefault.holds(CRAFTING_WITH_FLUID));
    }

    @Test
    void aCategoryOnlyAHigherTierHoldsIsInTheTab() {
        AssemblerTab fluid = tab(List.of(List.of(CRAFTING), List.of(CRAFTING, CRAFTING_WITH_FLUID)));
        assertTrue(fluid.holds(CRAFTING_WITH_FLUID));
    }

    @Test
    void aCategoryNoTierHoldsIsInNoTab() {
        AssemblerTab inert = tab(List.of(List.of(CRAFTING)));
        assertFalse(inert.holds(CRAFTING_WITH_FLUID));
    }

    @Test
    void tiersHoldingNothingHaveNoRecipes() {
        AssemblerTab none = tab(List.of(List.of(), List.of()));
        assertFalse(none.holds(CRAFTING));
    }

    @Test
    void sortingFillsTheTabInOrderAndCountsTheRecipesLeftOut() {
        Map<String, AssemblingCategory> recipes = new LinkedHashMap<>();
        recipes.put("plank", CRAFTING);
        recipes.put("bucket", CRAFTING_WITH_FLUID);
        recipes.put("gear", ADVANCED_CRAFTING);
        recipes.put("pipe", CRAFTING_WITH_FLUID);

        AssemblerTab.Sorted<String> sorted = tab(List.of(List.of(CRAFTING, ADVANCED_CRAFTING)))
                .sort(recipes.keySet(), recipes::get);

        assertEquals(List.of("plank", "gear"), sorted.recipes());
        assertEquals(2, sorted.leftOut());
    }

    @Test
    void sortingLeavesNoneOutWhenTheTierHoldsEveryCategory() {
        AssemblerTab.Sorted<String> sorted = tab(List.of(List.of(CRAFTING, ADVANCED_CRAFTING, CRAFTING_WITH_FLUID)))
                .sort(List.of("pipe"), recipe -> CRAFTING_WITH_FLUID);
        assertEquals(List.of("pipe"), sorted.recipes());
        assertEquals(0, sorted.leftOut());
    }
}
