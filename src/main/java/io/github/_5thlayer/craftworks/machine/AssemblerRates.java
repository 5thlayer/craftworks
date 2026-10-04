// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.machine;

/**
 * An Assembler's rate: how long a craft takes at a tier's speed and what it costs in FE.
 *
 * <p>The recipe carries its time in ticks and nothing else; the Assembler divides by its speed. A tier's
 * power is not a whole number of FE a tick (37.5, 187.5), so energy is priced per craft, as power times
 * the craft's time, and spread over the craft's ticks so that they sum to the price exactly. A tick that
 * cannot be paid in full makes no progress, and an Assembler draws nothing when it is not crafting.
 *
 * <p>Pure: no Minecraft types.
 */
public final class AssemblerRates {

    private AssemblerRates() {
    }

    /** The ticks one craft takes at {@code speed}: the recipe's time over it, never below one tick. */
    public static int durationTicks(double speed, int recipeTicks) {
        return Math.max(1, (int) Math.ceil(recipeTicks / speed - 1e-6));
    }

    /** FE for one whole craft: {@code power} FE a tick over the recipe's time at {@code speed}, unrounded. */
    public static int fePerCraft(double power, double speed, int recipeTicks) {
        return (int) Math.round(power * (recipeTicks / speed));
    }

    /**
     * The FE the tick at {@code progress} draws: the share of {@code totalFe} that tick completes, so a
     * craft's ticks sum to its price exactly and a tick past the end draws nothing.
     */
    public static int feForTick(int progress, int durationTicks, int totalFe) {
        if (progress < 0 || progress >= durationTicks) {
            return 0;
        }
        return share(progress + 1, durationTicks, totalFe) - share(progress, durationTicks, totalFe);
    }

    private static int share(int ticks, int durationTicks, int totalFe) {
        return (int) ((long) totalFe * ticks / durationTicks);
    }
}
