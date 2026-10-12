// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.machine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The fluid box rule (#57, ADR-0021): a craft is refused if it needs more of a fluid than a flat 2,000 mB box holds. */
class FluidBoxesTest {

    @Test
    void aCraftNeedingNoMoreThanABoxFits() {
        assertTrue(FluidBoxes.fits(1));
        assertTrue(FluidBoxes.fits(1_000));
        assertTrue(FluidBoxes.fits(2_000));
    }

    @Test
    void aCraftNeedingMoreThanABoxIsRefused() {
        assertFalse(FluidBoxes.fits(2_001));
        assertFalse(FluidBoxes.fits(Integer.MAX_VALUE));
    }
}
