// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.machine;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiPredicate;
import java.util.function.IntPredicate;
import java.util.function.ToIntFunction;

/**
 * A machine's item slots, and which Held recipe ingredient each input slot takes: the {@code n}th ingredient
 * goes in the {@code n}th input, and a slot the recipe doesn't use takes nothing. The inputs come first, then
 * the outputs, and whatever else the machine keeps. The item face, the menu's slots and the screen's
 * ghosts all read this, so they cannot disagree. Generic, so it is tested without Minecraft.
 *
 * @param size    the machine's slots, before the player's in its menu
 * @param inputs  the input slots; a Held recipe names at most this many distinct ingredients
 * @param firstOutput the first output slot, where the product lands first
 * @param outputs the output slots, from {@code product}: every one takes anything the machine makes and extracts
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
}
