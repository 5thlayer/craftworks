// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.machine;

/**
 * Whether an Assembler or a Chemical Plant takes a recipe it was asked to hold, by Fill Recipe on its open screen.
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
    NOT_ASSEMBLING("not_assembling"),
    /** The recipe's category is not one this tier holds, by the server config. */
    WRONG_CATEGORY("wrong_category"),
    /** The recipe names a fluid this tier can't take: any on tier 1, and a fluid result on every tier. */
    HAS_FLUID("has_fluid"),
    /** The recipe needs two or more fluids, and the Assembler has one fluid box. */
    TOO_MANY_FLUIDS("too_many_fluids"),
    /** The recipe needs more of its fluid a craft than the fluid box holds. */
    FLUID_TOO_LARGE("fluid_too_large"),
    /** The recipe names more distinct ingredients than the Assembler has input slots. */
    TOO_MANY_INGREDIENTS("too_many_ingredients"),
    /** One craft's remainders are two items, or more than a stack, so the one remainder slot can't take them. */
    REMAINDERS_DONT_FIT("remainders_dont_fit"),
    /** The Lock source says the recipe is Locked for the player pressing. */
    LOCKED("locked");

    private final String refusal;

    HoldVerdict(String refusal) {
        this.refusal = refusal;
    }

    /**
     * What the world answered about a recipe, one flag for each refusal: every one true is a recipe that
     * passes, and {@link #locked} is the Lock source's answer. Read with a name, never by position:
     * {@code Checks.passing().fitsSlots(false)} is a recipe that fails only that.
     */
    public record Checks(boolean resolves, boolean categoryHeld, boolean takesFluids, boolean oneFluid,
            boolean fluidFits, boolean fitsSlots, boolean remaindersFit, boolean locked) {

        /** A recipe that resolves, is in a category the tier holds, takes its fluids and fits, and is not Locked. */
        public static Checks passing() {
            return new Checks(true, true, true, true, true, true, true, false);
        }

        public Checks resolves(boolean resolves) {
            return new Checks(resolves, categoryHeld, takesFluids, oneFluid, fluidFits, fitsSlots,
                    remaindersFit, locked);
        }

        public Checks categoryHeld(boolean categoryHeld) {
            return new Checks(resolves, categoryHeld, takesFluids, oneFluid, fluidFits, fitsSlots,
                    remaindersFit, locked);
        }

        /** Whether the tier takes the recipe's fluids at all: no fluid result, and one only with a fluid box. */
        public Checks takesFluids(boolean takesFluids) {
            return new Checks(resolves, categoryHeld, takesFluids, oneFluid, fluidFits, fitsSlots,
                    remaindersFit, locked);
        }

        public Checks oneFluid(boolean oneFluid) {
            return new Checks(resolves, categoryHeld, takesFluids, oneFluid, fluidFits, fitsSlots,
                    remaindersFit, locked);
        }

        public Checks fluidFits(boolean fluidFits) {
            return new Checks(resolves, categoryHeld, takesFluids, oneFluid, fluidFits, fitsSlots,
                    remaindersFit, locked);
        }

        public Checks fitsSlots(boolean fitsSlots) {
            return new Checks(resolves, categoryHeld, takesFluids, oneFluid, fluidFits, fitsSlots,
                    remaindersFit, locked);
        }

        public Checks remaindersFit(boolean remaindersFit) {
            return new Checks(resolves, categoryHeld, takesFluids, oneFluid, fluidFits, fitsSlots,
                    remaindersFit, locked);
        }

        public Checks locked(boolean locked) {
            return new Checks(resolves, categoryHeld, takesFluids, oneFluid, fluidFits, fitsSlots,
                    remaindersFit, locked);
        }
    }

    /** The first refusal that applies, in the order of this enum, or {@link #HELD}. */
    public static HoldVerdict of(Checks checks) {
        if (!checks.resolves()) {
            return NOT_ASSEMBLING;
        }
        if (!checks.categoryHeld()) {
            return WRONG_CATEGORY;
        }
        if (!checks.takesFluids()) {
            return HAS_FLUID;
        }
        if (!checks.oneFluid()) {
            return TOO_MANY_FLUIDS;
        }
        if (!checks.fluidFits()) {
            return FLUID_TOO_LARGE;
        }
        if (!checks.fitsSlots()) {
            return TOO_MANY_INGREDIENTS;
        }
        if (!checks.remaindersFit()) {
            return REMAINDERS_DONT_FIT;
        }
        return checks.locked() ? LOCKED : HELD;
    }

    public boolean held() {
        return this == HELD;
    }

    /** The lang key an Assembler tells the player, or null when the recipe was held. */
    public String messageKey() {
        return messageKey("assembler");
    }

    /** The lang key {@code machine} (its lang name, say {@code chemical_plant}) tells the player, or null when the recipe was held. */
    public String messageKey(String machine) {
        return refusal == null ? null : "craftworks." + machine + ".refused." + refusal;
    }
}
