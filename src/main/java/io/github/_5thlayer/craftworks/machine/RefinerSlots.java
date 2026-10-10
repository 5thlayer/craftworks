// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.machine;

/** The Refiner's slots: the input it smelts and the output it fills ({@link MachineSlots}). */
public final class RefinerSlots {

    public static final int INPUT = 0;
    public static final int OUTPUT = 1;
    public static final int SIZE = 2;

    public static final MachineSlots LAYOUT = new MachineSlots(SIZE, 1, OUTPUT);

    private RefinerSlots() {
    }
}
