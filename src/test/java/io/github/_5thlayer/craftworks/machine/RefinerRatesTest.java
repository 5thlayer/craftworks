// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.machine;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * The Refiner's rates (#46): Factorio's electric furnace at speed 2 and 90 FE a tick, over vanilla's cooking times
 * of 200 for smelting and 100 for blasting, through the Assembler's own rate and Overload Limit arithmetic.
 */
class RefinerRatesTest {

    private static final double SPEED = 2.0;
    private static final double POWER = 90.0;
    private static final int SMELTING = 200;
    private static final int BLASTING = 100;

    @Test
    void aSmeltTakesTheCookingTimeOverTheSpeed() {
        assertEquals(100, CraftRates.durationTicks(SPEED, SMELTING));
        assertEquals(50, CraftRates.durationTicks(SPEED, BLASTING));
    }

    @Test
    void aSmeltCostsThePowerTimesItsTicks() {
        assertEquals(9000, CraftRates.fePerCraft(POWER, SPEED, SMELTING));
        assertEquals(4500, CraftRates.fePerCraft(POWER, SPEED, BLASTING));
    }

    @Test
    void theTicksOfASmeltSumToItsPriceExactly() {
        int duration = CraftRates.durationTicks(SPEED, SMELTING);
        int price = CraftRates.fePerCraft(POWER, SPEED, SMELTING);
        int paid = 0;
        for (int tick = 0; tick < duration; tick++) {
            paid += CraftRates.feForTick(tick, duration, price);
        }
        assertEquals(price, paid);
        assertEquals(90, CraftRates.feForTick(0, duration, price));
    }

    @Test
    void theOverloadLimitIsTwoSmeltsOfOneItemForBothRecipeKinds() {
        assertEquals(2, OverloadLimit.crafts(SPEED, SMELTING));
        assertEquals(2, OverloadLimit.crafts(SPEED, BLASTING));
        assertEquals(2, OverloadLimit.room(1, OverloadLimit.crafts(SPEED, BLASTING), 0));
        assertEquals(1, OverloadLimit.room(1, OverloadLimit.crafts(SPEED, BLASTING), 1));
        assertEquals(0, OverloadLimit.room(1, OverloadLimit.crafts(SPEED, BLASTING), 5));
    }

    @Test
    void theSlotsAreAnInputThenAnOutput() {
        assertEquals(2, RefinerSlots.LAYOUT.size());
        assertEquals(true, RefinerSlots.LAYOUT.isInput(RefinerSlots.INPUT));
        assertEquals(false, RefinerSlots.LAYOUT.isInput(RefinerSlots.OUTPUT));
        assertEquals(RefinerSlots.OUTPUT, RefinerSlots.LAYOUT.product());
    }
}
