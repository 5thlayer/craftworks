// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.machine;

import java.util.List;
import java.util.Optional;
import java.util.function.BiPredicate;
import java.util.function.ToIntFunction;

/**
 * A machine's item slots, and which Held recipe ingredient each input slot takes: the {@code n}th ingredient
 * goes in the {@code n}th input, and a slot the recipe doesn't use takes nothing. The inputs come first, then
 * the product's slot and whatever else the machine keeps. The item face, the menu's slots and the screen's
 * ghosts all read this, so they cannot disagree. Generic, so it is tested without Minecraft.
 *
 * @param size    the machine's slots, before the player's in its menu
 * @param inputs  the input slots; a Held recipe names at most this many distinct ingredients
 * @param product the product's slot
 */
public record MachineSlots(int size, int inputs, int product) {

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
