// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.machine;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * An Assembler's rate (#21): the recipe's time over the tier's speed, and an energy price that is the
 * tier's power times that time, spread over the craft's ticks so they sum to it exactly.
 */
class CraftRatesTest {

    @Test
    void aCraftTakesTheRecipesTimeOverTheTiersSpeed() {
        assertEquals(20, CraftRates.durationTicks(0.5, 10));
        assertEquals(14, CraftRates.durationTicks(0.75, 10));
        assertEquals(8, CraftRates.durationTicks(1.25, 10));
    }

    @Test
    void anExactQuotientDoesNotRoundUpPastItself() {
        assertEquals(8, CraftRates.durationTicks(1.25, 10));
        assertEquals(16, CraftRates.durationTicks(1.25, 20));
    }

    @Test
    void aCraftIsNeverFasterThanOneTick() {
        assertEquals(1, CraftRates.durationTicks(1.25, 0));
        assertEquals(1, CraftRates.durationTicks(1000.0, 1));
    }

    @Test
    void theDefaultPowersPriceACraftAsPowerTimesCraftTime() {
        assertEquals(750, CraftRates.fePerCraft(37.5, 0.5, 10));
        assertEquals(1000, CraftRates.fePerCraft(75.0, 0.75, 10));
        assertEquals(1500, CraftRates.fePerCraft(187.5, 1.25, 10));
    }

    @Test
    void aFractionalPowerLosesNoEnergyAcrossACraftsTicks() {
        for (AssemblerTier tier : AssemblerTier.values()) {
            for (int time : new int[] {1, 10, 33, 160}) {
                int duration = CraftRates.durationTicks(tier.defaultSpeed(), time);
                int price = CraftRates.fePerCraft(tier.defaultPower(), tier.defaultSpeed(), time);
                int sum = 0;
                for (int tick = 0; tick < duration; tick++) {
                    sum += CraftRates.feForTick(tick, duration, price);
                }
                assertEquals(price, sum, tier + " at " + time + " ticks");
            }
        }
    }

    @Test
    void aTickPastTheEndDrawsNothing() {
        assertEquals(0, CraftRates.feForTick(20, 20, 750));
        assertEquals(0, CraftRates.feForTick(-1, 20, 750));
    }
}
