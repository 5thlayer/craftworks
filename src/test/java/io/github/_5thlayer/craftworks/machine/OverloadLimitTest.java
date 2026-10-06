// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.machine;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** The Overload Limit (#21): Factorio's clamp(ceil(1.166 * speed * 20 / ticks) + 1, 2, 100) crafts' worth. */
class OverloadLimitTest {

    @Test
    void aSlowRecipeIsHeldToTheMinimum() {
        assertEquals(3, OverloadLimit.crafts(0.5, 10));
        assertEquals(2, OverloadLimit.crafts(0.5, 600));
    }

    @Test
    void aFastRecipeScalesWithSpeed() {
        assertEquals(4, OverloadLimit.crafts(1.25, 10));
    }

    @Test
    void aVeryFastRecipeScalesUpToACap() {
        assertEquals(31, OverloadLimit.crafts(1.25, 1));
        assertEquals(100, OverloadLimit.crafts(10.0, 1));
    }

    @Test
    void roomIsWhatTheLimitLeavesOfWhatTheSlotHolds() {
        assertEquals(6, OverloadLimit.room(2, 3, 0));
        assertEquals(2, OverloadLimit.room(2, 3, 4));
        assertEquals(0, OverloadLimit.room(2, 3, 9));
    }
}
