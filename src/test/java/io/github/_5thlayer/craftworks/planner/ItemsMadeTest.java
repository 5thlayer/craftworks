// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.planner;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static io.github._5thlayer.craftworks.planner.TestBags.recipe;
import static io.github._5thlayer.craftworks.planner.TestBags.route;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** What {@code @craftworks} lists in EMI: every item some Assembling recipe makes (#15). */
class ItemsMadeTest {

    @Test
    void anEmptySetMakesNothing() {
        assertEquals(Set.of(), AssemblingRecipeSet.empty().itemsMade());
    }

    @Test
    void everyRecipesResultIsListedOnceHoweverManyRoutesMakeIt() {
        AssemblingRecipeSet set = AssemblingRecipeSet.builder()
                .add(route("sticks_from_planks", "minecraft:stick", 4, 5, Ingredient.of("minecraft:oak_planks", 2)))
                .add(route("sticks_from_bamboo", "minecraft:stick", 1, 1, Ingredient.of("minecraft:bamboo", 2)))
                .add(recipe("flint_and_steel", "minecraft:flint_and_steel", 1, 10,
                        Ingredient.of("minecraft:flint", 1), Ingredient.of("minecraft:iron_ingot", 1)))
                .build();

        assertEquals(Set.of("minecraft:stick", "minecraft:flint_and_steel"), set.itemsMade());
    }

    /** EMI's index lists an item once, not once per component patch, so the set names bare item ids. */
    @Test
    void aResultWithComponentsIsListedByItsItemId() {
        AssemblingRecipeSet set = AssemblingRecipeSet.builder()
                .add(recipe("swiftness", "minecraft:potion[minecraft:potion_contents={potion:\"minecraft:swiftness\"}]", 1, 10))
                .build();

        assertEquals(Set.of("minecraft:potion"), set.itemsMade());
    }

    /** Locks are per player and the index is the same for everyone, so a Locked route still lists its item. */
    @Test
    void itemsAreListedWhateverTheIngredients() {
        AssemblingRecipeSet set = AssemblingRecipeSet.builder()
                .add(recipe("diamond_block", "minecraft:diamond_block", 1, 10, Ingredient.of("minecraft:diamond", 9)))
                .build();

        assertEquals(Set.of("minecraft:diamond_block"), set.itemsMade());
    }
}
