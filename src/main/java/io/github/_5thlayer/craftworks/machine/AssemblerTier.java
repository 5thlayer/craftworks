// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.machine;

import static io.github._5thlayer.craftworks.recipe.AssemblingCategory.ADVANCED_CRAFTING;
import static io.github._5thlayer.craftworks.recipe.AssemblingCategory.CRAFTING;
import static io.github._5thlayer.craftworks.recipe.AssemblingCategory.CRAFTING_WITH_FLUID;

import java.util.List;

import io.github._5thlayer.craftworks.recipe.AssemblingCategory;

/**
 * The Assembler's three tiers, each a block of its own, with the figures the server config starts from.
 *
 * <p>Speed divides a recipe's time into the ticks a craft takes; power is FE a tick while crafting, at
 * 1 FE = 100 J: 75, 150 and 375 kW. The categories are the recipe kinds a tier may hold, and every tier holds all
 * three, {@code crafting}, {@code advanced-crafting} and {@code crafting-with-fluid}, until the server config says
 * otherwise. Every tier has the fluid boxes and Fluid Connections of {@link FluidLayout#ASSEMBLER}, the Assembler's
 * one layout, so the tiers differ only in speed, power and buffer.
 * The rates are pure, so they are unit-tested.
 */
public enum AssemblerTier implements MachineDefaults {
    ONE("assembler_1", 0.5, 37.5, 50_000, List.of(CRAFTING, ADVANCED_CRAFTING, CRAFTING_WITH_FLUID)),
    TWO("assembler_2", 0.75, 75.0, 50_000, List.of(CRAFTING, ADVANCED_CRAFTING, CRAFTING_WITH_FLUID)),
    THREE("assembler_3", 1.25, 187.5, 50_000, List.of(CRAFTING, ADVANCED_CRAFTING, CRAFTING_WITH_FLUID));

    private final String blockName;
    private final double defaultSpeed;
    private final double defaultPower;
    private final int defaultBuffer;
    private final List<AssemblingCategory> defaultCategories;

    AssemblerTier(String blockName, double defaultSpeed, double defaultPower, int defaultBuffer,
            List<AssemblingCategory> defaultCategories) {
        this.blockName = blockName;
        this.defaultSpeed = defaultSpeed;
        this.defaultPower = defaultPower;
        this.defaultBuffer = defaultBuffer;
        this.defaultCategories = defaultCategories;
    }

    @Override
    public String blockName() {
        return blockName;
    }

    public String partBlockName() {
        return blockName + "_part";
    }

    @Override
    public double defaultSpeed() {
        return defaultSpeed;
    }

    /** FE a tick while crafting. */
    @Override
    public double defaultPower() {
        return defaultPower;
    }

    /** FE the energy buffer holds. */
    @Override
    public int defaultBuffer() {
        return defaultBuffer;
    }

    /** The recipe categories this tier holds until the server config says otherwise. */
    @Override
    public List<AssemblingCategory> defaultCategories() {
        return defaultCategories;
    }
}
