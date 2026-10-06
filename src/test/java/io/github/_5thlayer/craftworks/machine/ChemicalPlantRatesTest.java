// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.machine;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import io.github._5thlayer.craftworks.recipe.AssemblingCategory;
import org.junit.jupiter.api.Test;

/** The Chemical Plant's figures before a config says otherwise (#26): Factorio's chemical plant, at 1 FE = 100 J. */
class ChemicalPlantRatesTest {

    private static final MachineRates PLANT = ChemicalPlantRates.INSTANCE;

    @Test
    void itRunsAtSpeedOneDrawing105FeATickWithAFiftyThousandFeBuffer() {
        assertEquals(1.0, PLANT.defaultSpeed());
        assertEquals(105.0, PLANT.defaultPower());
        assertEquals(50_000, PLANT.defaultBuffer());
    }

    @Test
    void itHoldsTheChemistryCategoryOnly() {
        assertEquals(List.of(AssemblingCategory.CHEMISTRY), PLANT.defaultCategories());
    }

    @Test
    void itsConfigSectionIsNamedForItsBlock() {
        assertEquals("chemical_plant", PLANT.blockName());
    }

    @Test
    void aCraftCostsItsPowerTimesItsTicksWithNoEnergyLostAcrossThem() {
        // A 60 tick craft at speed 1 is 6,300 FE.
        int duration = AssemblerRates.durationTicks(PLANT.defaultSpeed(), 60);
        int price = AssemblerRates.fePerCraft(PLANT.defaultPower(), PLANT.defaultSpeed(), 60);
        assertEquals(60, duration);
        assertEquals(6300, price);
        int sum = 0;
        for (int tick = 0; tick < duration; tick++) {
            sum += AssemblerRates.feForTick(tick, duration, price);
        }
        assertEquals(price, sum);
    }
}
