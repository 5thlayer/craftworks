// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.planner;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.github._5thlayer.craftworks.planner.TestBags.asMap;
import static io.github._5thlayer.craftworks.planner.TestBags.have;
import static io.github._5thlayer.craftworks.planner.TestBags.recipe;
import static io.github._5thlayer.craftworks.planner.TestBags.route;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Route priority: of several Assembling recipes making one item, the highest-priority route that
 * resolves with nothing Missing or Locked wins, ties going to the lowest recipe id.
 */
class RoutePriorityTest {

    /** torch = 1 stick + 1 coal; stick from planks (priority 5) or from bamboo (priority 1). */
    private static AssemblingRecipeSet sticks() {
        return AssemblingRecipeSet.builder()
                .add(recipe("torch", "torch", 1, 10, Ingredient.of("stick", 1), Ingredient.of("coal", 1)))
                .add(route("sticks_from_bamboo", "stick", 1, 1, Ingredient.of("bamboo", 2)))
                .add(route("sticks_from_planks", "stick", 4, 5, Ingredient.of("planks", 2)))
                .build();
    }

    private static List<String> recipes(Resolver.Resolution resolution) {
        return resolution.steps().stream().map(CraftStep::recipe).toList();
    }

    @Test
    void routesAreOrderedByPriorityThenByLowestRecipeId() {
        AssemblingRecipeSet set = AssemblingRecipeSet.builder()
                .add(route("c", "stick", 1, 0))
                .add(route("b", "stick", 1, 7))
                .add(route("a", "stick", 1, 0))
                .add(route("d", "stick", 1, 7))
                .build();

        assertEquals(List.of("b", "d", "a", "c"), set.routes("stick").stream().map(AssemblingRecipe::id).toList());
    }

    @Test
    void thePreferredRouteIsUsedWheneverItResolves() {
        Resolver.Resolution resolution = new Resolver(sticks(), Set.of()::contains)
                .resolve("torch", 1, have("planks", 2, "bamboo", 2, "coal", 1));

        assertTrue(resolution.complete());
        assertEquals(List.of("sticks_from_planks", "torch"), recipes(resolution));
    }

    @Test
    void aLowerRouteIsUsedWhenThePreferredOneHasSomethingMissing() {
        Resolver.Resolution resolution = new Resolver(sticks(), Set.of()::contains)
                .resolve("torch", 1, have("bamboo", 2, "coal", 1));

        assertTrue(resolution.complete());
        assertEquals(List.of("sticks_from_bamboo", "torch"), recipes(resolution));
        assertEquals(Map.of("bamboo", 2, "coal", 1), asMap(resolution.rawCost()));
    }

    @Test
    void aLowerRouteIsUsedWhenThePreferredOneIsLocked() {
        Resolver.Resolution resolution = new Resolver(sticks(), Set.of("sticks_from_planks")::contains)
                .resolve("torch", 1, have("planks", 2, "bamboo", 2, "coal", 1));

        assertTrue(resolution.complete());
        assertEquals(List.of("sticks_from_bamboo", "torch"), recipes(resolution));
    }

    @Test
    void aLowerRouteIsUsedWhenThePreferredOnesSubtreeHasSomethingLocked() {
        AssemblingRecipeSet set = AssemblingRecipeSet.builder()
                .add(recipe("torch", "torch", 1, 10, Ingredient.of("stick", 1)))
                .add(route("sticks_from_planks", "stick", 4, 5, Ingredient.of("planks", 2)))
                .add(route("planks", "planks", 4, 0, Ingredient.of("log", 1)))
                .add(route("sticks_from_bamboo", "stick", 1, 1, Ingredient.of("bamboo", 2)))
                .build();

        Resolver.Resolution resolution = new Resolver(set, Set.of("planks")::contains)
                .resolve("torch", 1, have("log", 1, "bamboo", 2));

        assertTrue(resolution.complete());
        assertEquals(List.of("sticks_from_bamboo", "torch"), recipes(resolution));
        assertEquals(Map.of("bamboo", 2), asMap(resolution.rawCost()));
    }

    @Test
    void tiesGoToTheLowestRecipeId() {
        AssemblingRecipeSet set = AssemblingRecipeSet.builder()
                .add(recipe("torch", "torch", 1, 10, Ingredient.of("stick", 1)))
                .add(route("sticks_z", "stick", 1, 3, Ingredient.of("bamboo", 1)))
                .add(route("sticks_a", "stick", 1, 3, Ingredient.of("bamboo", 1)))
                .build();

        Resolver.Resolution resolution = new Resolver(set, Set.of()::contains).resolve("torch", 1, have("bamboo", 1));

        assertEquals(List.of("sticks_a", "torch"), recipes(resolution));
    }

    @Test
    void aPreferredRouteThatFailsPartWayLeavesNothingSpentForALaterStep() {
        // The planks route takes the player's planks for its first ingredient and then finds no iron.
        // Falling back must hand the planks back, or the fence's own planks come up Missing.
        AssemblingRecipeSet set = AssemblingRecipeSet.builder()
                .add(recipe("fence", "fence", 1, 10, Ingredient.of("stick", 1), Ingredient.of("planks", 2)))
                .add(route("sticks_from_planks", "stick", 4, 5, Ingredient.of("planks", 2), Ingredient.of("iron", 1)))
                .add(route("sticks_from_bamboo", "stick", 1, 1, Ingredient.of("bamboo", 2)))
                .build();

        Resolver.Resolution resolution = new Resolver(set, Set.of()::contains)
                .resolve("fence", 1, have("planks", 2, "bamboo", 2));

        assertTrue(resolution.complete());
        assertEquals(List.of("sticks_from_bamboo", "fence"), recipes(resolution));
        assertEquals(Map.of("planks", 2, "bamboo", 2), asMap(resolution.rawCost()));
        assertEquals(Map.of("stick", 1, "fence", 1), asMap(resolution.toCraft()));
    }

    @Test
    void whenNoRouteResolvesThePlanReportsTheTopRoutesMissingAndLocked() {
        AssemblingRecipeSet set = AssemblingRecipeSet.builder()
                .add(recipe("torch", "torch", 1, 10, Ingredient.of("stick", 1)))
                .add(route("sticks_from_planks", "stick", 4, 5, Ingredient.of("planks", 2), Ingredient.of("resin", 1)))
                .add(route("resin", "resin", 1, 0, Ingredient.of("sap", 1)))
                .add(route("sticks_from_bamboo", "stick", 1, 1, Ingredient.of("bamboo", 2)))
                .build();

        Resolver.Resolution resolution = new Resolver(set, Set.of("resin")::contains).resolve("torch", 1, have());

        assertFalse(resolution.complete());
        assertEquals(Map.of("planks", 2), asMap(resolution.missing()));
        assertEquals(Map.of("resin", 1), asMap(resolution.locked()));
    }
}
