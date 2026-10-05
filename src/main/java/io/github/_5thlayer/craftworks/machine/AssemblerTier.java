// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.machine;

import java.util.List;

/**
 * The Assembler's three tiers, each a block of its own, with the figures the server config starts from.
 *
 * <p>Speed divides a recipe's time into the ticks a craft takes; power is FE a tick while crafting, at
 * 1 FE = 100 J: 75, 150 and 375 kW. The categories are the recipe kinds a tier may hold, Factorio's: tier 1
 * takes {@code crafting} and {@code advanced-crafting}, and tiers 2 and 3 add {@code crafting-with-fluid}. Pure: no Minecraft types, so the rates are unit-tested.
 */
public enum AssemblerTier {
    ONE("assembler_1", 0.5, 37.5, 50_000, List.of("crafting", "advanced-crafting")),
    TWO("assembler_2", 0.75, 75.0, 50_000, List.of("crafting", "advanced-crafting", "crafting-with-fluid")),
    THREE("assembler_3", 1.25, 187.5, 50_000, List.of("crafting", "advanced-crafting", "crafting-with-fluid"));

    private final String blockName;
    private final double defaultSpeed;
    private final double defaultPower;
    private final int defaultBuffer;
    private final List<String> defaultCategories;

    AssemblerTier(String blockName, double defaultSpeed, double defaultPower, int defaultBuffer,
            List<String> defaultCategories) {
        this.blockName = blockName;
        this.defaultSpeed = defaultSpeed;
        this.defaultPower = defaultPower;
        this.defaultBuffer = defaultBuffer;
        this.defaultCategories = defaultCategories;
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

    /** The recipe categories this tier holds until the server config says otherwise. */
    public List<String> defaultCategories() {
        return defaultCategories;
    }
}
