// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.machine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Where a machine's gauges stand across its screen. */
class FluidGaugeLayoutTest {

    @Test
    void theAssemblersFiveStandAcrossTheRow() {
        FluidGaugeLayout layout = FluidGaugeLayout.of(FluidLayout.ASSEMBLER);
        assertEquals(28, layout.width());
        assertEquals(8, layout.x(0));
        assertEquals(40, layout.x(1));
        assertEquals(76, layout.x(2));
        assertEquals(108, layout.x(3));
        assertEquals(140, layout.x(4));
    }

    @Test
    void twoInAndThreeOutFitTheRowWithoutOverlap() {
        FluidGaugeLayout layout = new FluidGaugeLayout(2, 3);
        assertEquals(28, layout.width());
        assertEquals(8, layout.x(0));
        assertEquals(140, layout.x(4));
        assertTrue(layout.x(4) + layout.width() <= FluidGaugeLayout.RIGHT);
        for (int box = 1; box < 5; box++) {
            assertTrue(layout.x(box) >= layout.x(box - 1) + layout.width() + FluidGaugeLayout.GAP);
        }
        assertTrue(layout.x(2) - (layout.x(1) + layout.width()) > layout.x(1) - (layout.x(0) + layout.width()));
    }
}
