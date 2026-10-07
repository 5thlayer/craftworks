// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.machine;

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
    CRAFTING
}
