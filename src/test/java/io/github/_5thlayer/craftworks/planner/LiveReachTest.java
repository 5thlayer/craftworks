// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.planner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

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
        // Red's conversion comes first in route order, its real recipe second: the real recipe's parts are the errand.
        AssemblingRecipeSet recipes = AssemblingRecipeSet.builder()
                .add(BigRecipes.recipe("dyeing", "item_red", Ingredient.of("item_base", 1), Ingredient.of("dye_red", 1)))
                .add(BigRecipes.recipe("a_recolour_red", "item_red", new Ingredient(List.of("item_blue", "item_base"), 1), Ingredient.of("dye_red", 1)))
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
        assertNotSame(recipes.reach(), BigRecipes.build(2, 3, 2, false).reach());
    }

    private static AssemblingRecipeSet redOrBlue(boolean realRecipe) {
        AssemblingRecipeSet.Builder builder = AssemblingRecipeSet.builder()
                .add(BigRecipes.recipe("a_recolour_red", "item_red", new Ingredient(List.of("item_blue"), 1), Ingredient.of("dye_red", 1)))
                .add(BigRecipes.recipe("top", "top", new Ingredient(List.of("item_red", "item_blue"), 1)));
        if (realRecipe) builder.add(BigRecipes.recipe("dyeing", "item_red", Ingredient.of("item_base", 1)));
        return builder.build();
    }

    @Test
    void aLockedConversionIsPassedOverAndMissingNamesThePreferredItem() {
        Resolver.Resolution plan = new Resolver(redOrBlue(false), id -> id.equals("a_recolour_red")).resolve("top", 1, new ItemBag());
        assertEquals(Map.of("item_red", 1), TestBags.asMap(plan.missing()));
        assertTrue(plan.locked().isEmpty());
    }

    @Test
    void aLockedRealTopRouteStillReportsLocked() {
        Resolver.Resolution plan = new Resolver(redOrBlue(true), id -> id.equals("dyeing") || id.equals("a_recolour_red"))
                .resolve("top", 1, new ItemBag());
        assertEquals(Map.of("item_red", 1), TestBags.asMap(plan.locked()));
        assertTrue(plan.missing().isEmpty());
    }

    private static Locks.Reasoned<String> lockedWithReasons(Map<String, String> reasons, String... reasonless) {
        List<Function<String, Optional<Locks.Lock<String>>>> reasoning = new ArrayList<>();
        reasoning.add(id -> reasons.containsKey(id) ? Optional.of(new Locks.Lock<>(reasons.get(id))) : Optional.empty());
        return Locks.reasoned(List.of(Set.of(reasonless)::contains), List.of(), reasoning);
    }

    @Test
    void aLockedRealRouteBehindAConversionKeepsItsOwnReason() {
        Locks.Reasoned<String> locks = lockedWithReasons(Map.of("dyeing", "Research: Dyeing"), "a_recolour_red");
        Resolver.Resolution plan = new Resolver(redOrBlue(true), locks).resolve("top", 1, new ItemBag());
        assertEquals(List.of("dyeing"), plan.lockedBy().get("item_red"));
        assertEquals(Map.of("item_red", "Research: Dyeing"), plan.lockReasons(locks.reasons()));
    }

    @Test
    void anItemLockedByTwoRecipesShowsTheReasonOfTheOneThatHasOne() {
        // Filled alone, red's top route is the plain one; filled beside blue, the recolouring is passed over and it is dyeing.
        AssemblingRecipeSet recipes = AssemblingRecipeSet.builder()
                .add(BigRecipes.recipe("a_recolour_red", "item_red", new Ingredient(List.of("item_blue"), 1), Ingredient.of("dye_red", 1)))
                .add(BigRecipes.recipe("dyeing", "item_red", Ingredient.of("item_base", 1)))
                .add(BigRecipes.recipe("top", "top", Ingredient.of("item_red", 1), new Ingredient(List.of("item_red", "item_blue"), 1)))
                .build();
        Locks.Reasoned<String> locks = lockedWithReasons(Map.of("dyeing", "Research: Dyeing"), "a_recolour_red");
        Resolver.Resolution plan = new Resolver(recipes, locks).resolve("top", 1, new ItemBag());
        assertEquals(List.of("a_recolour_red", "dyeing"), plan.lockedBy().get("item_red"));
        assertEquals(Map.of("item_red", "Research: Dyeing"), plan.lockReasons(locks.reasons()));
    }

    @Test
    void aLockedRootKeepsItsOwnReason() {
        AssemblingRecipeSet recipes = AssemblingRecipeSet.builder()
                .add(BigRecipes.recipe("top", "top", Ingredient.of("item_base", 1)))
                .add(BigRecipes.recipe("other_top", "top", Ingredient.of("item_base", 2)))
                .build();
        Locks.Reasoned<String> locks = lockedWithReasons(Map.of("top", "Research: Tops", "other_top", "Research: Others"));
        Resolver.Resolution plan = new Resolver(recipes, locks).resolve("top", 1, new ItemBag());
        assertEquals(Map.of("top", List.of("top")), plan.lockedBy());
        assertEquals(Map.of("top", "Research: Tops"), plan.lockReasons(locks.reasons()));
    }

    @Test
    void aLockedEntryFromAnAbandonedRouteIsPutBack() {
        // The ingredient takes x or y: x's route runs into a Locked part and is rolled back, y's resolves.
        AssemblingRecipeSet recipes = AssemblingRecipeSet.builder()
                .add(BigRecipes.recipe("make_x", "item_x", Ingredient.of("item_l", 1)))
                .add(BigRecipes.recipe("make_l", "item_l", Ingredient.of("item_base", 1)))
                .add(BigRecipes.recipe("make_y", "item_y", Ingredient.of("item_base", 1)))
                .add(BigRecipes.recipe("top", "top", new Ingredient(List.of("item_x", "item_y"), 1)))
                .build();
        Locks.Reasoned<String> locks = lockedWithReasons(Map.of("make_l", "Research: L"));
        ItemBag held = new ItemBag();
        held.add("item_base", 5);
        Resolver.Resolution plan = new Resolver(recipes, locks).resolve("top", 1, held);
        assertTrue(plan.complete());
        assertTrue(plan.lockedBy().isEmpty());
        assertTrue(plan.lockReasons(locks.reasons()).isEmpty());
    }
}
