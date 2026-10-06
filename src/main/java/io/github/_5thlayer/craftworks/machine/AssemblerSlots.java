// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.machine;

/** An Assembler's slots: five inputs, then the product and the remainders ({@link MachineSlots}). */
public final class AssemblerSlots {

    /** The input slots; a Held recipe names at most this many distinct ingredients. */
    public static final int INPUTS = 5;

    /** The product's slot, after the inputs. */
    public static final int PRODUCT = INPUTS;

    /** The ingredients' remainders' slot (cake's buckets, honey block's bottles). */
    public static final int REMAINDERS = INPUTS + 1;

    public static final int SIZE = INPUTS + 2;

    public static final MachineSlots LAYOUT = new MachineSlots(SIZE, INPUTS, PRODUCT);

    private AssemblerSlots() {
    }
}
