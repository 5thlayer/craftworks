// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.machine;

/**
 * The Assembler's three tiers, each a block of its own, with the figures the server config starts from.
 *
 * <p>Speed divides a recipe's time into the ticks a craft takes; power is FE a tick while crafting, at
 * 1 FE = 100 J: 75, 150 and 375 kW. Pure: no Minecraft types, so the rates are unit-tested.
 */
public enum AssemblerTier {
    ONE("assembler_1", 0.5, 37.5, 50_000),
    TWO("assembler_2", 0.75, 75.0, 50_000),
    THREE("assembler_3", 1.25, 187.5, 50_000);

    private final String blockName;
    private final double defaultSpeed;
    private final double defaultPower;
    private final int defaultBuffer;

    AssemblerTier(String blockName, double defaultSpeed, double defaultPower, int defaultBuffer) {
        this.blockName = blockName;
        this.defaultSpeed = defaultSpeed;
        this.defaultPower = defaultPower;
        this.defaultBuffer = defaultBuffer;
    }

    public String blockName() {
        return blockName;
    }

    public String partBlockName() {
        return blockName + "_part";
    }

    public double defaultSpeed() {
        return defaultSpeed;
    }

    /** FE a tick while crafting. */
    public double defaultPower() {
        return defaultPower;
    }

    /** FE the energy buffer holds. */
    public int defaultBuffer() {
        return defaultBuffer;
    }
}
