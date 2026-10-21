// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.planner;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What a recipe set can reach from an inventory, ignoring quantities, remainders and the Lock source, every
 * recipe counting as open (#70, #73).
 *
 * <p>It starts from the inventory's keys and runs the recipes forward: a recipe fires once each ingredient
 * lists an item that is reached, or that a held key matches by item id, which over-approximates the
 * Data-carrying draws that match by item id (ADR-0022), and its result is then reached. An item it leaves out
 * cannot be made in any amount, so a walk skips it wherever it meets it: it would fail every route anyway, and
 * a failed route is rolled back whole. It catches what no search can afford to: a recolouring cycle that
 * nothing held leads into, whose failing search walks every simple path.
 *
 * <p>The index, from each item to the recipes that list it, is linear in the set and does not depend on any
 * inventory, so it is built once per set ({@link AssemblingRecipeSet#reach()}) and a new set, as a datapack
 * reload makes, builds its own. {@link #unreachableFrom} is the worklist, linear in the set too, and runs once
 * per inventory: whoever resolves several times against one inventory keeps its answer.
 *
 * <p>The index is flat: every ingredient of every recipe is numbered, every distinct item listed gets a slot,
 * and the ingredients listing slot {@code s} are {@code listedBy[listedFrom[s]]} up to but not including
 * {@code listedBy[listedFrom[s + 1]]}.
 */
final class Reach {

    private final String[] results;
    /** The recipe each ingredient belongs to. */
    private final int[] recipeOf;
    /** Per recipe, how many of its ingredients there are. */
    private final int[] ingredientCount;
    private final Map<String, Integer> slotOf = new HashMap<>();
    private final int[] listedFrom;
    private final int[] listedBy;

    Reach(AssemblingRecipeSet recipes) {
        List<AssemblingRecipe> all = new ArrayList<>(recipes.size());
        for (String id : recipes.ids()) all.add(recipes.byId(id));
        results = new String[all.size()];
        ingredientCount = new int[all.size()];
        int ingredients = 0;
        int listings = 0;
        for (int r = 0; r < all.size(); r++) {
            results[r] = all.get(r).result().item();
            ingredientCount[r] = all.get(r).ingredients().size();
            ingredients += ingredientCount[r];
            for (Ingredient ingredient : all.get(r).ingredients()) listings += ingredient.items().size();
        }
        // First pass: number the ingredients and the items, and count the listings per item.
        recipeOf = new int[ingredients];
        int[] ingredientOfListing = new int[listings];
        int[] slotOfListing = new int[listings];
        int[] counts = new int[listings];
        int ingredientNumber = 0;
        int listing = 0;
        for (int r = 0; r < all.size(); r++) {
            for (Ingredient ingredient : all.get(r).ingredients()) {
                recipeOf[ingredientNumber] = r;
                for (String item : ingredient.items()) {
                    Integer known = slotOf.get(item);
                    int slot = known != null ? known : slotOf.size();
                    if (known == null) slotOf.put(item, slot);
                    counts[slot]++;
                    ingredientOfListing[listing] = ingredientNumber;
                    slotOfListing[listing++] = slot;
                }
                ingredientNumber++;
            }
        }
        // Second pass: lay the listings out by item.
        listedFrom = new int[slotOf.size() + 1];
        for (int slot = 0; slot < slotOf.size(); slot++) listedFrom[slot + 1] = listedFrom[slot] + counts[slot];
        listedBy = new int[listings];
        int[] filled = new int[slotOf.size()];
        for (int i = 0; i < listings; i++) {
            int slot = slotOfListing[i];
            listedBy[listedFrom[slot] + filled[slot]++] = ingredientOfListing[i];
        }
    }

    /** The results of the set that no chain of its recipes reaches from {@code held}. */
    Set<String> unreachableFrom(Set<String> held) {
        Set<String> reached = new HashSet<>();
        Deque<String> pending = new ArrayDeque<>();
        for (String key : held) if (reached.add(key)) pending.add(key);
        int[] unmet = ingredientCount.clone();
        // A recipe that wants nothing needs nothing reached.
        for (int r = 0; r < unmet.length; r++) {
            if (unmet[r] == 0 && reached.add(results[r])) pending.add(results[r]);
        }
        // An item a held key matches by id is met without being reached, as a Data-carrying draw would be.
        Set<String> heldIds = new HashSet<>();
        for (String key : held) heldIds.add(ItemKey.itemId(key));
        for (String item : slotOf.keySet()) if (heldIds.contains(ItemKey.itemId(item))) pending.add(item);
        boolean[] met = new boolean[recipeOf.length];
        while (!pending.isEmpty()) {
            Integer slot = slotOf.get(pending.poll());
            if (slot == null) continue;
            for (int k = listedFrom[slot]; k < listedFrom[slot + 1]; k++) {
                int ingredient = listedBy[k];
                if (met[ingredient]) continue;
                met[ingredient] = true;
                int recipe = recipeOf[ingredient];
                if (--unmet[recipe] == 0 && reached.add(results[recipe])) pending.add(results[recipe]);
            }
        }
        Set<String> unreached = new HashSet<>();
        for (String made : results) if (!reached.contains(made)) unreached.add(made);
        return unreached;
    }
}
