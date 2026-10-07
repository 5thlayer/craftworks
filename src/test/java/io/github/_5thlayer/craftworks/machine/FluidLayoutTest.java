// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.machine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import io.github._5thlayer.craftworks.machine.FluidLayout.Face;
import io.github._5thlayer.craftworks.machine.FluidLayout.Site;
import org.junit.jupiter.api.Test;

/**
 * How a machine's fluid boxes are counted and where its Fluid Connections stand, as sites: where they land in a
 * world, for each facing, is the game tests' to check, since Minecraft is not on this classpath.
 */
class FluidLayoutTest {

    private static final FluidLayout ASSEMBLER = FluidLayout.ASSEMBLER;

    @Test
    void anAssemblerHasTwoInputBoxesThreeOutputBoxesAndSixConnections() {
        assertEquals(2, ASSEMBLER.fluidInputs());
        assertEquals(3, ASSEMBLER.fluidOutputs());
        assertEquals(5, ASSEMBLER.boxes());
        assertEquals(6, ASSEMBLER.connections().size());
    }

    @Test
    void theBoxesAreBoundByOrderInputsFirst() {
        assertTrue(ASSEMBLER.isInput(0) && ASSEMBLER.isInput(1));
        assertFalse(ASSEMBLER.isInput(2));
        assertFalse(ASSEMBLER.isInput(-1));
        assertEquals(0, ASSEMBLER.binding(0));
        assertEquals(1, ASSEMBLER.binding(1));
        assertEquals(0, ASSEMBLER.binding(2));
        assertEquals(2, ASSEMBLER.binding(4));
        assertEquals(2, ASSEMBLER.outputBox(0));
        assertEquals(4, ASSEMBLER.outputBox(2));
    }

    @Test
    void anAssemblersConnectionsAreTheThreeBottomBlocksOfBothEdges() {
        assertEquals(List.of(
                new Site(1, -1, Face.AHEAD), new Site(1, 0, Face.AHEAD), new Site(1, 1, Face.AHEAD),
                new Site(-1, -1, Face.BEHIND), new Site(-1, 0, Face.BEHIND), new Site(-1, 1, Face.BEHIND)),
                ASSEMBLER.connections());
    }

    /** A half turn takes each site to the one opposite, so the blockstate's two ring rotations cover all four facings. */
    @Test
    void theSixSitesAreSymmetricUnderAHalfTurn() {
        List<Site> turned = ASSEMBLER.connections().stream()
                .map(site -> new Site(-site.ahead(), -site.right(), site.face() == Face.AHEAD ? Face.BEHIND : Face.AHEAD))
                .toList();
        assertTrue(turned.containsAll(ASSEMBLER.connections()) && ASSEMBLER.connections().containsAll(turned));
    }
}
