// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.machine;

import java.util.Locale;

/**
 * What an Assembler is doing this tick, as {@link AssemblerBlockEntity#state} reports it: the first of the
 * checks its tick makes that it fails, in the order the tick makes them (#28).
 */
public enum MachineState {

    /** No Held recipe: the machine idles and draws nothing. */
    NO_RECIPE,
    /**
     * The Held recipe cannot run here: it names a fluid this machine can't take, is outside its categories, has
     * too many ingredients, or is gone after a reload.
     */
    CANT_RUN,
    /** An input slot holds less than one craft of its ingredient. */
    MISSING_INGREDIENTS,
    /** An output cannot take a craft's result: the product slot, the remainder slot or a fluid output box. */
    OUTPUT_FULL,
    /** The buffer cannot pay this tick's FE. */
    NEEDS_POWER,
    /** Everything is in place: this tick counts towards the craft. */
    CRAFTING;

    /** The lang key of what a machine in this state is doing, such as "Needs power", for its screen and Jade. */
    public String langKey() {
        return "craftworks.jade.state." + name().toLowerCase(Locale.ROOT);
    }

    /** The state with {@code ordinal}, as a menu sends it, or {@link #NO_RECIPE} for one out of range. */
    public static MachineState of(int ordinal) {
        MachineState[] states = values();
        return ordinal >= 0 && ordinal < states.length ? states[ordinal] : NO_RECIPE;
    }
}
