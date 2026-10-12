// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.machine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Where a machine's gauges stand on its screen. */
class FluidGaugeLayoutTest {

    @Test
    void theAssemblersInputsStandLeftAndItsOutputsRight() {
        FluidGaugeLayout layout = FluidGaugeLayout.of(FluidLayout.ASSEMBLER);
        assertEquals(76, layout.width());
        assertEquals(8, layout.x(0));
        assertEquals(8, layout.x(1));
        assertEquals(92, layout.x(2));
        assertEquals(92, layout.x(3));
        assertEquals(92, layout.x(4));
    }

    @Test
    void eachColumnRunsDownFromTheTopRow() {
        FluidGaugeLayout layout = new FluidGaugeLayout(2, 3);
        assertEquals(0, layout.row(0));
        assertEquals(1, layout.row(1));
        assertEquals(0, layout.row(2));
        assertEquals(1, layout.row(3));
        assertEquals(2, layout.row(4));
        assertEquals(3, layout.rows());
    }

    @Test
    void theColumnsFitThePanelWithAGapBetween() {
        FluidGaugeLayout layout = new FluidGaugeLayout(2, 3);
        assertTrue(layout.x(2) + layout.width() <= FluidGaugeLayout.RIGHT);
        assertEquals(FluidGaugeLayout.GROUP_GAP, layout.x(2) - (layout.x(0) + layout.width()));
    }

    @Test
    void aMachineWithOnlyOutputsTakesTheWholeWidth() {
        FluidGaugeLayout layout = new FluidGaugeLayout(0, 2);
        assertEquals(160, layout.width());
        assertEquals(8, layout.x(0));
        assertEquals(1, layout.row(1));
        assertEquals(2, layout.rows());
    }
}
