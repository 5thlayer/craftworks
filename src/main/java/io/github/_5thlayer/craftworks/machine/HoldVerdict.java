// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.machine;

/**
 * Whether an Assembler takes a recipe it was asked to hold, by Fill Recipe on its open screen.
 *
 * <p>One rule and one message rather than a button that silently does nothing. A recipe the Assembler
 * could never run is refused before its Lock is asked, so it is never reported as merely Locked. Locked
 * is asked last, once, of the player who presses (ADR-0013), and never again once the recipe is held.
 *
 * <p>The refusals are declared in the order they are checked: the first that applies is the one told.
 */
public enum HoldVerdict {
    HELD(null),
    /** The id names no Assembling recipe the server has loaded. */
    NOT_ASSEMBLING("not_assembling"),
    /** The recipe's category is not one this tier holds, by the server config. */
    WRONG_CATEGORY("wrong_category"),
    /** The recipe has more fluid ingredients or results than the Assembler has input or output boxes. */
    TOO_MANY_FLUIDS("too_many_fluids"),
    /** The recipe needs more of a fluid a craft, in or out, than a box holds. */
    FLUID_TOO_LARGE("fluid_too_large"),
    /** The recipe names more distinct ingredients than the Assembler has input slots. */
    TOO_MANY_INGREDIENTS("too_many_ingredients"),
    /** One craft's remainders need more than the three outputs the product leaves, counting the stacks of every kind, so the shared outputs' leftover rule refuses them. */
    REMAINDERS_DONT_FIT("remainders_dont_fit"),
    /** The Lock source says the recipe is Locked for the player pressing. */
    LOCKED("locked");

    private final String refusal;

    HoldVerdict(String refusal) {
        this.refusal = refusal;
    }

    public boolean held() {
        return this == HELD;
    }

    /** The lang key the Assembler tells the player, say {@code craftworks.assembler.refused.locked}, or null when the recipe was held. */
    public String messageKey() {
        return refusal == null ? null : "craftworks.assembler.refused." + refusal;
    }
}
