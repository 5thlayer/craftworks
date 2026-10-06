// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.machine;

/**
 * The Overload Limit: how many crafts' worth of an ingredient automated insertion leaves in an
 * Assembler, Factorio's {@code clamp(ceil(factor * speed / energy_required) + 1, minimum, maximum)}, with
 * the constants the FactoryWorks Pack reads from the same Factorio data.
 *
 * <p>The hand is not held to it: only an insert through the item capability is. Pure: no Minecraft types.
 */
public final class OverloadLimit {

    static final double FACTOR = 1.166;
    static final int MINIMUM = 2;
    static final int MAXIMUM = 100;

    private static final double TICKS_PER_SECOND = 20.0;

    private OverloadLimit() {
    }

    /** Crafts' worth at {@code speed} of a recipe of {@code recipeTicks}, never below one tick. */
    public static int crafts(double speed, int recipeTicks) {
        double perSwing = FACTOR * speed * TICKS_PER_SECOND / Math.max(1, recipeTicks);
        // The epsilon keeps an exact quotient from rounding up past itself.
        int swing = (int) Math.ceil(perSwing - 1e-9);
        return Math.min(Math.max(swing + 1, MINIMUM), MAXIMUM);
    }

    /** How many more a slot holding {@code held} takes of an ingredient needing {@code perCraft}. */
    public static int room(int perCraft, int crafts, int held) {
        return Math.max(0, perCraft * crafts - held);
    }

    /**
     * The volume of a fluid output box: the larger of {@code box} and {@code crafts} crafts' worth of the
     * {@code amount} the Held recipe makes into it, so a machine holds what it makes for a few crafts before
     * it stalls. The Pack's {@code OutputTankVolume} rule, with every result pinned to its own box.
     */
    public static int outputBox(int box, int crafts, int amount) {
        return (int) Math.max(box, Math.min(Integer.MAX_VALUE, (long) crafts * amount));
    }
}
