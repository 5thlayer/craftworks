// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.planner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * Every live resolve skips what its inventory cannot reach (#73), and its report walk names the errand
 * rather than the cycle. Counted in recipe applications, never in time.
 */
class LiveReachTest {

    /** The most applications one live resolve of the recolouring set may try, nothing held. */
    private static final long RECOLOUR_WORK_PER_RESOLVE = 4;

    private static final AssemblingRecipeSet COLOURS = BigRecipes.build(0, 0, 0, false, 40, BigRecipes.COLOURS);

    @Test
    void anUnmakeableRecolouringIsNotCutShortAndNamesTheItemAndTheDye() {
        Resolver resolver = new Resolver(COLOURS, id -> false);
        Resolver.Resolution plan = resolver.resolve("rc_0_to_5", 1, BigRecipes.stock(36));
        assertEquals(0, resolver.searchesCutShort());
        assertTrue(resolver.applicationsTried() <= RECOLOUR_WORK_PER_RESOLVE, "tried " + resolver.applicationsTried());
        assertEquals(Map.of(BigRecipes.coloured(0, 0), 1, BigRecipes.dye(5), 1), TestBags.asMap(plan.missing()));
        assertTrue(plan.locked().isEmpty());
    }

    @Test
    void largestAffordableOfAnUnmakeableRecolouringIsZeroWithoutASearchCutShort() {
        Resolver resolver = new Resolver(COLOURS, id -> false);
        assertEquals(0, resolver.largestAffordable("rc_0_to_5", BigRecipes.stock(36)));
        assertEquals(0, resolver.searchesCutShort());
        assertTrue(resolver.applicationsTried() <= RECOLOUR_WORK_PER_RESOLVE, "tried " + resolver.applicationsTried());
    }

    @Test
    void aRecolouringStillResolvesWhenTheDyeAndABaseAreHeld() {
        ItemBag held = BigRecipes.stock(3);
        held.add(BigRecipes.base(0), 1);
        held.add(BigRecipes.dye(5), 1);
        Resolver resolver = new Resolver(COLOURS, id -> false);
        assertTrue(resolver.resolve("rc_0_to_5", 1, held).complete());
        assertEquals(1, resolver.largestAffordable("rc_0_to_5", held));
    }

    @Test
    void theReportWalkStillDescendsIntoARealRecipe() {
        // Colour 0 has a real recipe from parts nobody holds, beside the conversions: its parts are the errand.
        AssemblingRecipeSet recipes = AssemblingRecipeSet.builder()
                .add(BigRecipes.recipe("dyeing", "item_red", Ingredient.of("item_base", 1), Ingredient.of("dye_red", 1)))
                .add(BigRecipes.recipe("recolour_red", "item_red", new Ingredient(List.of("item_blue", "item_base"), 1), Ingredient.of("dye_red", 1)))
                .add(BigRecipes.recipe("recolour_blue", "item_blue", new Ingredient(List.of("item_red", "item_base"), 1), Ingredient.of("dye_blue", 1)))
                .add(BigRecipes.recipe("top", "top", new Ingredient(List.of("item_red", "item_blue"), 1)))
                .build();
        Resolver.Resolution plan = new Resolver(recipes, id -> false).resolve("top", 1, new ItemBag());
        assertEquals(List.of("dye_red", "item_base"), plan.missing().stream().map(ItemAmount::item).sorted().toList());
    }

    @Test
    void aSimpleUnreachableChainStillNamesItsLeaf() {
        AssemblingRecipeSet recipes = AssemblingRecipeSet.builder()
                .add(BigRecipes.recipe("stick", "stick", Ingredient.of("plank", 1)))
                .add(BigRecipes.recipe("planks", "plank", Ingredient.of("log", 1)))
                .build();
        Resolver.Resolution plan = new Resolver(recipes, id -> false).resolve("stick", 1, new ItemBag());
        assertEquals(List.of(new ItemAmount("log", 1)), plan.missing());
    }

    @Test
    void theIndexIsBuiltOncePerRecipeSet() {
        AssemblingRecipeSet recipes = BigRecipes.build(2, 3, 2, false);
        assertSame(recipes.reach(), recipes.reach());
        assertTrue(recipes.reach() != BigRecipes.build(2, 3, 2, false).reach());
    }
}
