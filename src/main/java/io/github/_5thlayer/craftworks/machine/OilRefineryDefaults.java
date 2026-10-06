// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.machine;

import static io.github._5thlayer.craftworks.recipe.AssemblingCategory.OIL_PROCESSING;

import java.util.List;

import io.github._5thlayer.craftworks.recipe.AssemblingCategory;

/**
 * The Oil Refinery's figures, Factorio's oil refinery: speed 1, 420 kW, which at 1 FE = 100 J is 210 FE a
 * tick, and the {@code oil-processing} category. Its buffer is the Assemblers' 50,000 FE.
 */
public final class OilRefineryDefaults implements MachineDefaults {

    public static final OilRefineryDefaults INSTANCE = new OilRefineryDefaults();

    private OilRefineryDefaults() {
    }

    @Override
    public String blockName() {
        return "oil_refinery";
    }

    @Override
    public double defaultSpeed() {
        return 1.0;
    }

    @Override
    public double defaultPower() {
        return 210.0;
    }

    @Override
    public int defaultBuffer() {
        return 50_000;
    }

    @Override
    public List<AssemblingCategory> defaultCategories() {
        return List.of(OIL_PROCESSING);
    }
}
