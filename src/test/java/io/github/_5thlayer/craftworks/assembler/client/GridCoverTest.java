// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.assembler.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import io.github._5thlayer.craftworks.assembler.client.GridCover.SlotAt;
import org.junit.jupiter.api.Test;

/** The rectangle that hides a copied 2x2 grid is worked out from where its slots are (ADR-0007). */
class GridCoverTest {

    /** Vanilla's inventory: inputs at x 98 and 116, y 18 and 36; the result at 154, 28. */
    private static final List<SlotAt> VANILLA = List.of(
            new SlotAt(98, 18), new SlotAt(116, 18), new SlotAt(98, 36), new SlotAt(116, 36), new SlotAt(154, 28));

    @Test
    void vanillasSlotsCoverWhatTheFixedRectangleDid() {
        assertEquals(new GridCover(97, 5, 171, 54), GridCover.around(VANILLA));
    }

    @Test
    void aShiftedLayoutShiftsTheCover() {
        List<SlotAt> shifted = VANILLA.stream().map(s -> new SlotAt(s.x() + 10, s.y() + 7)).toList();
        assertEquals(new GridCover(107, 12, 181, 61), GridCover.around(shifted));
    }

    @Test
    void aLayoutWithTheResultOnTheLeftCoversThatToo() {
        List<SlotAt> mirrored = List.of(
                new SlotAt(20, 28), new SlotAt(60, 18), new SlotAt(78, 18), new SlotAt(60, 36), new SlotAt(78, 36));
        assertEquals(new GridCover(19, 5, 95, 54), GridCover.around(mirrored));
    }
}
