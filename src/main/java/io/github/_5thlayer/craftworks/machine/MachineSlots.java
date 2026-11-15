// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.machine;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiPredicate;
import java.util.function.IntFunction;
import java.util.function.IntPredicate;
import java.util.function.ToIntFunction;

/**
 * A machine's item slots, and which Held recipe ingredient each input slot takes: the {@code n}th ingredient
 * goes in the {@code n}th input, and a slot the recipe doesn't use takes nothing. The inputs come first, then
 * the outputs, and whatever else the machine keeps. An input the recipe doesn't use is a Spare input: it takes a
 * variant of an ingredient whose own slot holds another, and a result the outputs have no room for (#87). The item
 * face, the menu's slots and the screen's ghosts all read this, so they cannot disagree. Generic, so it is tested without Minecraft.
 *
 * @param size    the machine's slots, before the player's in its menu
 * @param inputs  the input slots; a Held recipe names at most this many distinct ingredients
 * @param firstOutput the first output slot, where the product lands first
 * @param outputs the output slots, from {@code firstOutput}: every one takes anything the machine makes and extracts
 */
public record MachineSlots(int size, int inputs, int firstOutput, int outputs) {

    public boolean isOutput(int slot) {
        return slot >= firstOutput && slot < firstOutput + outputs;
    }

    /**
     * The output slots in the order a result is offered them: those that hold something first, so a stack grows
     * before an empty slot is taken, then the empty ones. {@code occupied} is asked of each output slot.
     */
    public List<Integer> outputOrder(IntPredicate occupied) {
        List<Integer> order = new ArrayList<>(outputs);
        for (int slot = firstOutput; slot < firstOutput + outputs; slot++) {
            if (occupied.test(slot)) {
                order.add(slot);
            }
        }
        for (int slot = firstOutput; slot < firstOutput + outputs; slot++) {
            if (!occupied.test(slot)) {
                order.add(slot);
            }
        }
        return order;
    }

    public boolean isInput(int slot) {
        return slot >= 0 && slot < inputs;
    }

    public <I> Optional<I> ingredientFor(int slot, List<I> ingredients) {
        if (!isInput(slot) || slot >= ingredients.size()) {
            return Optional.empty();
        }
        return Optional.of(ingredients.get(slot));
    }

    public <I, T> boolean accepts(int slot, List<I> ingredients, T item, BiPredicate<I, T> matches) {
        return ingredientFor(slot, ingredients).filter(ingredient -> matches.test(ingredient, item)).isPresent();
    }

    /** Whether a slot holding {@code held} items cannot cover one craft: the screen draws it red. */
    public <I> boolean isShort(int slot, List<I> ingredients, int held, ToIntFunction<I> count) {
        return ingredientFor(slot, ingredients).filter(ingredient -> held < count.applyAsInt(ingredient)).isPresent();
    }

    /** Whether {@code slot} is an input a recipe of {@code ingredients} ingredients doesn't use. */
    public boolean isSpare(int slot, int ingredients) {
        return isInput(slot) && slot >= ingredients;
    }

    /**
     * The ingredient spare input {@code slot} takes {@code item} as, if any: the first that accepts it while its
     * own slot holds another variant, which a slot cannot hold beside its own. Nothing while the own slot is
     * empty or holds the same, which takes it first, and nothing a spare holding another variant could hold.
     * {@code held} is what a slot holds, empty when it is empty; a variant is equal to itself alone.
     */
    public <I, T> Optional<I> spareFor(int slot, List<I> ingredients, T item, BiPredicate<I, T> matches,
            IntFunction<Optional<T>> held) {
        if (!isSpare(slot, ingredients.size())) {
            return Optional.empty();
        }
        Optional<T> here = held.apply(slot);
        if (here.isPresent() && !here.get().equals(item)) {
            return Optional.empty();
        }
        for (int n = 0; n < ingredients.size(); n++) {
            I ingredient = ingredients.get(n);
            if (!matches.test(ingredient, item)) {
                continue;
            }
            Optional<T> own = held.apply(n);
            if (own.isPresent() && !own.get().equals(item)) {
                return Optional.of(ingredient);
            }
        }
        return Optional.empty();
    }

    /** The slots the {@code n}th ingredient is taken from, in order: its own, then the spares. */
    public List<Integer> sources(int n, int ingredients) {
        List<Integer> sources = new ArrayList<>();
        sources.add(n);
        for (int slot = ingredients; slot < inputs; slot++) {
            sources.add(slot);
        }
        return sources;
    }

    /**
     * Where a result goes, in order: the outputs as {@link #outputOrder} has them, then the spare inputs, those
     * holding something first. {@code occupied} is asked of each.
     */
    public List<Integer> overflowOrder(int ingredients, IntPredicate occupied) {
        List<Integer> order = outputOrder(occupied);
        for (int slot = ingredients; slot < inputs; slot++) {
            if (occupied.test(slot)) {
                order.add(slot);
            }
        }
        for (int slot = ingredients; slot < inputs; slot++) {
            if (!occupied.test(slot)) {
                order.add(slot);
            }
        }
        return order;
    }
}
