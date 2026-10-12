// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.machine;

/**
 * How big a fluid box is: a flat {@link #VOLUME} mB, input or output, whatever the recipe (ADR-0021). The
 * Assembler's machine pull and a pipe fill an input box to the brim, an output box holds as much as it can be
 * made to, and a bucket's worth always swaps whole. A constant beside {@link OverloadLimit}'s, not config.
 *
 * <p>Pure: no Minecraft types.
 */
public final class FluidBoxes {

    /** mB every fluid box holds; a recipe needing more of one fluid in or out of a craft is refused. */
    public static final int VOLUME = 2_000;

    private FluidBoxes() {
    }

    /** Whether one craft's {@code amount} mB of a fluid fits a box. */
    public static boolean fits(int amount) {
        return amount <= VOLUME;
    }
}
