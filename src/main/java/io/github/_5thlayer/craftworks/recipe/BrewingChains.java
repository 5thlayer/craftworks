// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.recipe;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The brewing stand's mixes as a graph, and every chain of brews that starts from a water bottle (ADR-0026):
 * what the Brewed recipes fold. No Minecraft types, so the rules are unit-tested; the converter names the
 * type parameters, {@code P} a potion, {@code I} an ingredient, {@code C} a bottle's container and {@code R} a
 * brew's result that is not a potion.
 *
 * <p>A chain takes one mix at each step from the water and never visits a potion twice, so a graph with loops
 * has finitely many. The container mixes (potion to splash, splash to lingering) come only at its end, as
 * the stand has them, each container once. A brew whose result is not a potion ends its chain. A chain with no
 * step is none: a water bottle alone is no recipe.
 */
public final class BrewingChains {

    private BrewingChains() {
    }

    /** A potion mix: {@code from} brewed with {@code ingredient} becomes the potion {@code to}. */
    public record PotionMix<P, I>(P from, I ingredient, P to) {
    }

    /** A brew whose result is no potion, such as a mod's Bioresin: {@code from} with {@code ingredient} makes {@code result}. */
    public record ItemMix<P, I, R>(P from, I ingredient, R result) {
    }

    /** A container mix: a potion in container {@code from} brewed with {@code ingredient} moves to container {@code to}. */
    public record ContainerMix<C, I>(C from, I ingredient, C to) {
    }

    /**
     * The brewing table. {@code water} is the potion of a water bottle and {@code plain} its container, the one
     * the potion mixes work in.
     */
    public record Table<P, I, C, R>(P water, C plain, List<PotionMix<P, I>> potionMixes,
            List<ItemMix<P, I, R>> itemMixes, List<ContainerMix<C, I>> containerMixes) {

        public Table {
            potionMixes = List.copyOf(potionMixes);
            itemMixes = List.copyOf(itemMixes);
            containerMixes = List.copyOf(containerMixes);
        }
    }

    /** One chain: the ingredients of its steps, in order, and what it ends in. */
    public sealed interface Chain<P, I, C, R> {

        List<I> ingredients();
    }

    /** A chain ending in a bottled potion: {@code potion} in {@code container}. */
    public record PotionChain<P, I, C, R>(P potion, C container, List<I> ingredients) implements Chain<P, I, C, R> {
    }

    /** A chain ending in a brew's own item. */
    public record ItemChain<P, I, C, R>(R result, List<I> ingredients) implements Chain<P, I, C, R> {
    }

    /** Every chain from water, in the order the table lists the mixes it takes. */
    public static <P, I, C, R> List<Chain<P, I, C, R>> chains(Table<P, I, C, R> table) {
        List<Chain<P, I, C, R>> chains = new ArrayList<>();
        Set<P> visited = new HashSet<>();
        visited.add(table.water());
        walk(table, table.water(), visited, new ArrayList<>(), chains);
        return chains;
    }

    /** The potions some chain from water passes through or ends in, the water itself included. */
    public static <P, I, C, R> Set<P> reachable(Table<P, I, C, R> table) {
        Set<P> reached = new HashSet<>();
        reached.add(table.water());
        boolean grew = true;
        while (grew) {
            grew = false;
            for (PotionMix<P, I> mix : table.potionMixes()) {
                if (reached.contains(mix.from()) && reached.add(mix.to())) grew = true;
            }
        }
        return reached;
    }

    private static <P, I, C, R> void walk(Table<P, I, C, R> table, P at, Set<P> visited, List<I> taken,
            List<Chain<P, I, C, R>> out) {
        if (!taken.isEmpty()) out.add(new PotionChain<>(at, table.plain(), List.copyOf(taken)));
        containers(table, at, table.plain(), new HashSet<>(Set.of(table.plain())), taken, out);
        for (ItemMix<P, I, R> mix : table.itemMixes()) {
            if (!mix.from().equals(at)) continue;
            taken.add(mix.ingredient());
            out.add(new ItemChain<>(mix.result(), List.copyOf(taken)));
            taken.removeLast();
        }
        for (PotionMix<P, I> mix : table.potionMixes()) {
            if (!mix.from().equals(at) || !visited.add(mix.to())) continue;
            taken.add(mix.ingredient());
            walk(table, mix.to(), visited, taken, out);
            taken.removeLast();
            visited.remove(mix.to());
        }
    }

    /** The container steps after a chain reached {@code potion} in {@code in}: each ends a chain of its own. */
    private static <P, I, C, R> void containers(Table<P, I, C, R> table, P potion, C in, Set<C> used, List<I> taken,
            List<Chain<P, I, C, R>> out) {
        for (ContainerMix<C, I> mix : table.containerMixes()) {
            if (!mix.from().equals(in) || !used.add(mix.to())) continue;
            taken.add(mix.ingredient());
            out.add(new PotionChain<>(potion, mix.to(), List.copyOf(taken)));
            containers(table, potion, mix.to(), used, taken, out);
            taken.removeLast();
            used.remove(mix.to());
        }
    }
}
