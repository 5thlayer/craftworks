// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.assembler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import io.github._5thlayer.craftworks.planner.AssemblingRecipe;
import io.github._5thlayer.craftworks.planner.AssemblingRecipeSet;
import io.github._5thlayer.craftworks.planner.Ingredient;
import io.github._5thlayer.craftworks.planner.ItemAmount;
import io.github._5thlayer.craftworks.planner.ItemBag;
import io.github._5thlayer.craftworks.planner.Resolver;
import org.junit.jupiter.api.Test;

/**
 * Ready is the Resolver's {@code complete()} for one craft, per recipe: intermediates count, Missing and
 * Locked do not, and two routes to one item are both Ready when both resolve.
 */
class ReadyPassTest {

    private static AssemblingRecipe recipe(String id, String result, int priority, Ingredient... ingredients) {
        return new AssemblingRecipe(id, List.of(ingredients), new ItemAmount(result, 1), 10, priority);
    }

    private static final AssemblingRecipeSet RECIPES = AssemblingRecipeSet.builder()
            .add(recipe("planks", "planks", 0, Ingredient.of("log", 1)))
            .add(recipe("sticks", "stick", 0, Ingredient.of("planks", 2)))
            .add(recipe("sticks_from_bamboo", "stick", 0, Ingredient.of("bamboo", 2)))
            .add(recipe("torch", "torch", 0, Ingredient.of("stick", 1), Ingredient.of("coal", 1)))
            .build();

    private static Set<String> ready(ItemBag stock, Predicate<String> locked) {
        ReadyPass pass = new ReadyPass(new Resolver(RECIPES, locked), stock, List.copyOf(RECIPES.ids()));
        while (!pass.done()) pass.resolveNext();
        return pass.ready();
    }

    private static ItemBag have(Object... pairs) {
        ItemBag bag = new ItemBag();
        for (int i = 0; i < pairs.length; i += 2) bag.add((String) pairs[i], (Integer) pairs[i + 1]);
        return bag;
    }

    @Test
    void anEmptyInventoryHasNothingReady() {
        assertEquals(Set.of(), ready(new ItemBag(), recipe -> false));
    }

    @Test
    void logsAndCoalMakePlanksSticksAndTorchesReadyThroughIntermediates() {
        assertEquals(Set.of("planks", "sticks", "torch"), ready(have("log", 2, "coal", 1), recipe -> false));
    }

    @Test
    void aRecipeWithAnythingMissingIsNotReady() {
        assertEquals(Set.of("planks", "sticks"), ready(have("log", 2), recipe -> false));
    }

    @Test
    void aLockedRecipeIsNotReadyNorAreTheOnesThatNeedIt() {
        assertEquals(Set.of("planks"), ready(have("log", 2, "coal", 1), recipe -> recipe.equals("sticks")));
    }

    @Test
    void bothRoutesToTheSameItemAreListedWhenBothResolve() {
        Set<String> ready = ready(have("log", 2, "bamboo", 2), recipe -> false);
        assertTrue(ready.containsAll(Set.of("sticks", "sticks_from_bamboo")));
    }

    @Test
    void aRouteThatOnlyResolvesThroughItsSiblingIsStillReadyOnItsOwnTerms() {
        // Bamboo alone: the bamboo route resolves; the planks route has no logs to start from.
        Set<String> ready = ready(have("bamboo", 2), recipe -> false);
        assertTrue(ready.contains("sticks_from_bamboo"));
        assertFalse(ready.contains("planks"));
    }

    @Test
    void thePassResolvesEveryCandidateAndSaysHowMany() {
        ReadyPass pass = new ReadyPass(new Resolver(RECIPES, recipe -> false), new ItemBag(), List.copyOf(RECIPES.ids()));
        assertEquals(4, pass.total());
        assertFalse(pass.done());
    }
}
