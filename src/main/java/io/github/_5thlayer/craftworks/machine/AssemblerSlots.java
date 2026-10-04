// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.machine;

import java.util.List;
import java.util.Optional;
import java.util.function.BiPredicate;
import java.util.function.ToIntFunction;

/**
 * An Assembler's slots, and which Held recipe ingredient each input slot takes: the {@code n}th
 * ingredient goes in the {@code n}th slot, and a slot the recipe doesn't use takes nothing. The item
 * face, the menu's slots and the screen's ghosts all read this, so they cannot disagree. Generic, so it
 * is tested without Minecraft.
 */
public final class AssemblerSlots {

    /** The input slots; a Held recipe names at most this many distinct ingredients. */
    public static final int INPUTS = 5;

    /** The product's slot, after the inputs. */
    public static final int PRODUCT = INPUTS;

    /** The ingredients' remainders' slot (cake's buckets, honey block's bottles). */
    public static final int REMAINDERS = INPUTS + 1;

    public static final int SIZE = INPUTS + 2;

    private AssemblerSlots() {
    }

    public static boolean isInput(int slot) {
        return slot >= 0 && slot < INPUTS;
    }

    public static <I> Optional<I> ingredientFor(int slot, List<I> ingredients) {
        if (!isInput(slot) || slot >= ingredients.size()) {
            return Optional.empty();
        }
        return Optional.of(ingredients.get(slot));
    }

    public static <I, T> boolean accepts(int slot, List<I> ingredients, T item, BiPredicate<I, T> matches) {
        return ingredientFor(slot, ingredients).filter(ingredient -> matches.test(ingredient, item)).isPresent();
    }

    /** Whether a slot holding {@code held} items cannot cover one craft: the screen draws it red. */
    public static <I> boolean isShort(int slot, List<I> ingredients, int held, ToIntFunction<I> count) {
        return ingredientFor(slot, ingredients).filter(ingredient -> held < count.applyAsInt(ingredient)).isPresent();
    }
}
