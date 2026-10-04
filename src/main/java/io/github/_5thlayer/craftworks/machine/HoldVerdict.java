// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.machine;

/**
 * Whether an Assembler takes a recipe it was asked to hold, by Fill Recipe on its open screen.
 *
 * <p>One rule and one message rather than a button that silently does nothing. A recipe the Assembler
 * could never run is refused before its Lock is asked, so it is never reported as merely Locked. Locked
 * is asked here, once, of the player who presses (ADR-0013), and never again once the recipe is held.
 *
 * <p>Pure: the menu asks the world and hands the answers in.
 */
public enum HoldVerdict {
    HELD(null),
    /** The id names no Assembling recipe the server has loaded. */
    NOT_ASSEMBLING("craftworks.assembler.refused.not_assembling"),
    /** The recipe names a fluid, which tiers 2 and 3 take in 5thlayer/factoryworks#580. */
    HAS_FLUID("craftworks.assembler.refused.has_fluid"),
    /** The recipe names more distinct ingredients than the Assembler has input slots. */
    TOO_MANY_INGREDIENTS("craftworks.assembler.refused.too_many_ingredients"),
    /** One craft's remainders are two items, or more than a stack, so the one remainder slot can't take them. */
    REMAINDERS_DONT_FIT("craftworks.assembler.refused.remainders_dont_fit"),
    /** The Lock source says the recipe is Locked for the player pressing. */
    LOCKED("craftworks.assembler.refused.locked");

    private final String messageKey;

    HoldVerdict(String messageKey) {
        this.messageKey = messageKey;
    }

    public static HoldVerdict of(boolean resolves, boolean namesFluid, boolean fitsSlots, boolean remaindersFit,
            boolean locked) {
        if (!resolves) {
            return NOT_ASSEMBLING;
        }
        if (namesFluid) {
            return HAS_FLUID;
        }
        if (!fitsSlots) {
            return TOO_MANY_INGREDIENTS;
        }
        if (!remaindersFit) {
            return REMAINDERS_DONT_FIT;
        }
        return locked ? LOCKED : HELD;
    }

    public boolean held() {
        return this == HELD;
    }

    /** The lang key the player is told, or null when the recipe was held. */
    public String messageKey() {
        return messageKey;
    }
}
