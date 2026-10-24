// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.recipe;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Set;

import io.github._5thlayer.craftworks.recipe.StorageRecipes.Trade;
import org.junit.jupiter.api.Test;

/** Which Converted recipes are Storage recipes: the ones with a round trip (#78), decided over all of them at once. */
class StorageRecipesTest {

    private static Set<String> found(List<Trade<String, String>> trades) {
        return StorageRecipes.find(trades, String::equals);
    }

    @Test
    void aPackAndItsUnpackAreBothStorageRecipes() {
        var trades = List.of(
                new Trade<>("pack", "ingot", 9, "block", 1),
                new Trade<>("unpack", "block", 1, "ingot", 9));
        assertEquals(Set.of("pack", "unpack"), found(trades));
    }

    @Test
    void fourMakeOneRoundTripsToo() {
        var trades = List.of(
                new Trade<>("pack", "ball", 4, "block", 1),
                new Trade<>("unpack", "block", 1, "ball", 4));
        assertEquals(Set.of("pack", "unpack"), found(trades));
    }

    @Test
    void aPackWithNoWayBackIsNot() {
        var trades = List.of(new Trade<>("planks", "plank", 4, "table", 1));
        assertEquals(Set.of(), found(trades));
    }

    @Test
    void anUnpackWithNoPackIsNot() {
        var trades = List.of(new Trade<>("unpack", "block", 1, "ingot", 9));
        assertEquals(Set.of(), found(trades));
    }

    @Test
    void theWayBackMustMakeAsManyAsThePackTook() {
        var trades = List.of(
                new Trade<>("pack", "meal", 9, "bone_block", 1),
                new Trade<>("unpack", "bone_block", 1, "meal", 3));
        assertEquals(Set.of(), found(trades));
    }

    @Test
    void theWayBackMustTakeWhatThePackMadeAndMakeWhatItTook() {
        var trades = List.of(
                new Trade<>("pack", "ingot", 9, "block", 1),
                new Trade<>("other", "stone", 1, "ingot", 9),
                new Trade<>("elsewhere", "block", 1, "dust", 9));
        assertEquals(Set.of(), found(trades));
    }

    @Test
    void countsOtherThanNineAndFourAreNot() {
        var trades = List.of(
                new Trade<>("pack", "ingot", 3, "block", 1),
                new Trade<>("unpack", "block", 1, "ingot", 3),
                new Trade<>("pack2", "nugget", 9, "bundle", 2),
                new Trade<>("unpack2", "bundle", 1, "nugget", 9));
        assertEquals(Set.of(), found(trades));
    }

    @Test
    void anItemInTwoPairsIsInBoth() {
        var trades = List.of(
                new Trade<>("nuggets", "nugget", 9, "ingot", 1),
                new Trade<>("to_nuggets", "ingot", 1, "nugget", 9),
                new Trade<>("ingots", "ingot", 9, "block", 1),
                new Trade<>("to_ingots", "block", 1, "ingot", 9));
        assertEquals(Set.of("nuggets", "to_nuggets", "ingots", "to_ingots"), found(trades));
    }

    @Test
    void anIngredientThatAcceptsTheItemCounts() {
        var trades = List.of(
                new Trade<>("pack", "#coals", 9, "block", 1),
                new Trade<>("unpack", "block", 1, "coal", 9));
        assertEquals(Set.of("pack", "unpack"), StorageRecipes.find(trades,
                (ingredient, item) -> ingredient.equals(item) || ingredient.equals("#coals") && item.equals("coal")));
    }
}
