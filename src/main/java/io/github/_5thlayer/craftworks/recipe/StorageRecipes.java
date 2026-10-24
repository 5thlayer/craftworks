// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.recipe;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiPredicate;

/**
 * Which Converted recipes are Storage recipes (#78, GLOSSARY.md): the ones that pack an item into its storage
 * block or unpack it, known by their round trip. Decided over all the Converted recipes at once, since a recipe
 * is one only when the one that undoes it is there too.
 *
 * <p>A trade is a recipe of one ingredient and one plain result. It packs when it takes nine or four of its
 * ingredient and makes one; another unpacks it when it takes one of what the pack made and makes as many as the
 * pack took, of something the pack's ingredient accepts. Both then count. The vanilla pack's {@code time}s are
 * set by the same rule in {@code build.gradle}, which cannot call this.
 *
 * @param <I> an ingredient
 * @param <R> a result
 */
public final class StorageRecipes {

    private StorageRecipes() {
    }

    /** A recipe of one ingredient taken {@code ingredientCount} times and one result made {@code resultCount} times. */
    public record Trade<I, R>(String id, I ingredient, int ingredientCount, R result, int resultCount) {
    }

    /** The ids of the trades that have their round trip; {@code accepts} says whether an ingredient takes a result. */
    public static <I, R> Set<String> find(List<Trade<I, R>> trades, BiPredicate<I, R> accepts) {
        List<Trade<I, R>> packs = new ArrayList<>();
        List<Trade<I, R>> unpacks = new ArrayList<>();
        for (Trade<I, R> trade : trades) {
            if (trade.ingredientCount() > 1 && trade.resultCount() == 1 && isPackCount(trade.ingredientCount())) packs.add(trade);
            else if (trade.ingredientCount() == 1 && isPackCount(trade.resultCount())) unpacks.add(trade);
        }
        Set<String> found = new HashSet<>();
        for (Trade<I, R> pack : packs) {
            for (Trade<I, R> unpack : unpacks) {
                if (unpack.resultCount() == pack.ingredientCount()
                        && accepts.test(unpack.ingredient(), pack.result())
                        && accepts.test(pack.ingredient(), unpack.result())) {
                    found.add(pack.id());
                    found.add(unpack.id());
                }
            }
        }
        return found;
    }

    private static boolean isPackCount(int count) {
        return count == 9 || count == 4;
    }
}
