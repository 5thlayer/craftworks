// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.planner;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The Assembling recipes, in the two directions the Resolver walks them: by recipe id, which is what a
 * request names, and by the item a recipe makes, which is what chain-crafting needs.
 *
 * <p>An item's recipes are its routes, in the order they were added. The Resolver plans with the
 * first route only, PlanetaryFactory's one-recipe-per-item rule; Route priority and fallback replace
 * that ordering and that rule (ADR-0006).
 */
public final class AssemblingRecipeSet {

    private static final AssemblingRecipeSet EMPTY = new AssemblingRecipeSet(Map.of(), Map.of());

    private final Map<String, AssemblingRecipe> byId;
    private final Map<String, List<AssemblingRecipe>> byResult;

    private AssemblingRecipeSet(Map<String, AssemblingRecipe> byId, Map<String, List<AssemblingRecipe>> byResult) {
        this.byId = byId;
        this.byResult = byResult;
    }

    public static AssemblingRecipeSet empty() {
        return EMPTY;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** The recipe with this id, or null. */
    public AssemblingRecipe byId(String recipeId) {
        return byId.get(recipeId);
    }

    /** The recipes that make this item, preferred first; empty when nothing makes it, a plan's leaf. */
    public List<AssemblingRecipe> routes(String item) {
        return byResult.getOrDefault(item, List.of());
    }

    public int size() {
        return byId.size();
    }

    /** Every recipe id in the set. */
    public Set<String> ids() {
        return byId.keySet();
    }

    public static final class Builder {

        private final Map<String, AssemblingRecipe> byId = new LinkedHashMap<>();
        private final Map<String, List<AssemblingRecipe>> byResult = new LinkedHashMap<>();

        private Builder() {
        }

        /** Adds a recipe; a second recipe under an id already added is ignored. */
        public Builder add(AssemblingRecipe recipe) {
            if (byId.putIfAbsent(recipe.id(), recipe) != null) return this;
            byResult.computeIfAbsent(recipe.result().item(), item -> new ArrayList<>()).add(recipe);
            return this;
        }

        public AssemblingRecipeSet build() {
            Map<String, List<AssemblingRecipe>> routes = new LinkedHashMap<>();
            byResult.forEach((item, recipes) -> routes.put(item, List.copyOf(recipes)));
            return new AssemblingRecipeSet(Map.copyOf(byId), Map.copyOf(routes));
        }
    }
}
