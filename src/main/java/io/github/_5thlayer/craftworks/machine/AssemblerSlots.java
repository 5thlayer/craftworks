// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.machine;

import java.util.List;

/**
 * An Assembler's slots ({@link MachineSlots}): five inputs, four shared outputs, then the drain and fill slots of
 * tiers 2 and 3 (ADR-0021).
 *
 * <p>The outputs follow the inputs where the product and the remainders once did, so a save from before them
 * keeps its product in the first output and its remainders in the second.
 */
public final class AssemblerSlots {

    /** The input slots; a Held recipe names at most this many distinct ingredients. */
    public static final int INPUTS = 5;

    /** The shared output slots: the product, the ingredients' remainders and the containers all land in any with room. */
    public static final int OUTPUTS = 4;

    /** The first output slot, after the inputs. */
    public static final int OUTPUT = INPUTS;

    /** The drain slot: a filled fluid container, poured into the input box bound to its fluid. */
    public static final int DRAIN = OUTPUT + OUTPUTS;

    /** The fill slot: an empty fluid container, filled from the first output box with a whole container's worth. */
    public static final int FILL = DRAIN + 1;

    public static final int SIZE = FILL + 1;

    /** The drain and fill slots. */
    public static final List<Integer> CONTAINER_SLOTS = List.of(DRAIN, FILL);

    public static final MachineSlots LAYOUT = new MachineSlots(SIZE, INPUTS, OUTPUT, OUTPUTS);

    private AssemblerSlots() {
    }

    /** The {@code n}th output slot, from 0. */
    public static int output(int n) {
        return OUTPUT + n;
    }

    public static boolean isContainerSlot(int slot) {
        return slot == DRAIN || slot == FILL;
    }
}
