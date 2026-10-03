// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.planner;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static io.github._5thlayer.craftworks.planner.TestBags.have;
import static io.github._5thlayer.craftworks.planner.TestBags.recipe;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A recipe that is not hand-craftable is never in a Crafting Plan (ADR-0109). A recipe with a fluid in or
 * out reads as not hand-craftable on the Minecraft side, which needs registries: the game tests cover that
 * and the decoding of the new fields.
 */
class HandCraftableTest {

    private static AssemblingRecipe planned(String id, boolean handCraftable) {
        return new AssemblingRecipe(
                id, List.of(Ingredient.of("plate", 1)), new ItemAmount("gear", 1), 10, 0, handCraftable);
    }

    @Test
    void aRecipeThatIsNotHandCraftableIsNotInTheSetSoNoPlanNamesIt() {
        AssemblingRecipeSet set = AssemblingRecipeSet.builder()
                .add(planned("by_hand", true))
                .add(planned("machine_only", false))
                .build();

        assertEquals(Set.of("by_hand"), set.ids());
        assertNull(set.byId("machine_only"));
        assertEquals(List.of("by_hand"), set.routes("gear").stream().map(AssemblingRecipe::id).toList());
    }

    @Test
    void whenOnlyAMachineMakesAnItemItsPlanIsMissing() {
        AssemblingRecipeSet set = AssemblingRecipeSet.builder()
                .add(planned("machine_only", false))
                .add(recipe("clock", "clock", 1, 10, Ingredient.of("gear", 1)))
                .build();

        Resolver.Resolution resolution = new Resolver(set, Set.<String>of()::contains)
                .resolve("clock", 1, have("plate", 5));

        assertFalse(resolution.complete());
        assertTrue(resolution.steps().stream().noneMatch(step -> step.recipe().equals("machine_only")));
    }
}
