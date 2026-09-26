// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.planner;

import java.util.List;
import java.util.Map;

/**
 * One of an Assembling recipe's demands: {@code count} of any one of {@code items}.
 *
 * <p>An ingredient is a choice, not an item: a tag ingredient ({@code #minecraft:planks}) matches
 * several items, and a Resolver that read only the first match would refuse a plan the player can pay
 * for.
 *
 * <p>The order of {@code items} is the order the Resolver prefers them in. It comes from the
 * ingredient's own match list, which is an arbitrary order rather than a ranked one, so it decides
 * which of two interchangeable items a plan spends, and nothing more.
 *
 * <p>{@code remainders} names, for an item that leaves one, what one of it leaves behind when spent:
 * the bucket a milk bucket leaves. It is a fact about the item, which is why it is keyed by item
 * rather than held once for the ingredient.
 */
public record Ingredient(List<String> items, int count, Map<String, String> remainders) {

    public Ingredient {
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("an ingredient nothing satisfies is not an ingredient");
        }
        for (String item : items) {
            if (item == null || item.isBlank()) {
                throw new IllegalArgumentException("an ingredient needs item keys");
            }
        }
        if (count <= 0) {
            throw new IllegalArgumentException("an ingredient count of " + count + " is not a count");
        }
        items = List.copyOf(items);
        remainders = Map.copyOf(remainders);
        for (String item : remainders.keySet()) {
            if (!items.contains(item)) {
                throw new IllegalArgumentException("a remainder for " + item + ", which the ingredient does not take");
            }
        }
    }

    /** An ingredient none of whose items leaves anything behind. */
    public Ingredient(List<String> items, int count) {
        this(items, count, Map.of());
    }

    /** The single-item case, which is most of them. */
    public static Ingredient of(String item, int count) {
        return new Ingredient(List.of(item), count);
    }

    /** What a shortfall of this ingredient is reported as: the first thing that would satisfy it. */
    public String preferred() {
        return items.get(0);
    }

    /** What one spent {@code item} leaves behind, or null when it leaves nothing. */
    public String remainder(String item) {
        return remainders.get(item);
    }
}
