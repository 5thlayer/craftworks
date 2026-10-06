// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.machine;

import static io.github._5thlayer.craftworks.recipe.AssemblingCategory.CHEMISTRY;

import java.util.List;

import io.github._5thlayer.craftworks.recipe.AssemblingCategory;

/**
 * The Chemical Plant's figures, Factorio's chemical plant: speed 1, 210 kW, which at 1 FE = 100 J is 105 FE a
 * tick, and the {@code chemistry} category. Its buffer is the Assemblers' 50,000 FE.
 */
public final class ChemicalPlantRates implements MachineRates {

    public static final ChemicalPlantRates INSTANCE = new ChemicalPlantRates();

    private ChemicalPlantRates() {
    }

    @Override
    public String blockName() {
        return "chemical_plant";
    }

    @Override
    public double defaultSpeed() {
        return 1.0;
    }

    @Override
    public double defaultPower() {
        return 105.0;
    }

    @Override
    public int defaultBuffer() {
        return 50_000;
    }

    @Override
    public List<AssemblingCategory> defaultCategories() {
        return List.of(CHEMISTRY);
    }
}
