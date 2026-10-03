// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.planner;

import java.util.List;

/**
 * An Assembling recipe as the Resolver sees it: what it eats, the one item it makes, how long one
 * craft takes in ticks, and its Route priority among the recipes making the same item. A recipe that is
 * not {@code handCraftable} is never planned through: {@link AssemblingRecipeSet} refuses it (ADR-0109).
 *
 * <p>Not the recipe the recipe manager holds: the Resolver plans over a set of these, and keeping the
 * set free of any Minecraft type is what lets the recursion be checked by an ordinary unit test. The
 * Minecraft side reads each loaded {@code craftworks:assembling} recipe into one of these.
 */
public record AssemblingRecipe(
        String id, List<Ingredient> ingredients, ItemAmount result, int time, int priority, boolean handCraftable) {

    public AssemblingRecipe(String id, List<Ingredient> ingredients, ItemAmount result, int time, int priority) {
        this(id, ingredients, result, time, priority, true);
    }

    /** A recipe at the default Route priority, 0. */
    public AssemblingRecipe(String id, List<Ingredient> ingredients, ItemAmount result, int time) {
        this(id, ingredients, result, time, 0);
    }

    public AssemblingRecipe {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("a recipe needs an id");
        if (result == null) throw new IllegalArgumentException("recipe " + id + " makes nothing");
        if (time < 0) throw new IllegalArgumentException("recipe " + id + " cannot take " + time + " ticks");
        ingredients = List.copyOf(ingredients);
    }
}
