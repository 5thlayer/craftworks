// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.assembler.client;

import java.util.Collection;

/**
 * The rectangle that hides a copied 2x2 crafting grid, in the screen's own coordinates, right and bottom
 * exclusive (ADR-0007).
 *
 * <p>It is worked out from where the menu's grid and result slots stand, so a screen that moves the grid
 * is covered where it moved to. Each slot is a 16 by 16 well drawn one pixel out, with the result well's
 * white highlight at its right edge, and the {@code container.crafting} title sits 13 pixels above the
 * topmost slot. For vanilla's inventory, whose inputs stand at x 98 and 116, y 18 and 36 and whose result
 * stands at 154, 28, that is 97, 5 to 171, 54. No Minecraft here, so it is a unit test.
 */
record GridCover(int left, int top, int right, int bottom) {

    /** A slot's well is drawn one pixel out on the left. */
    private static final int WELL_LEFT = 1;
    /** The {@code container.crafting} title sits this far above the topmost slot. */
    private static final int TITLE_ABOVE = 13;
    /** A 16 pixel slot, plus the well's white highlight on its right edge. */
    private static final int WELL_RIGHT = 17;
    /** A 16 pixel slot, plus the well's highlight below it. */
    private static final int WELL_BELOW = 18;

    /** Where a slot's top left corner stands. */
    record SlotAt(int x, int y) {
    }

    /** The smallest cover that holds every slot's well and the title above them. {@code slots} must not be empty. */
    static GridCover around(Collection<SlotAt> slots) {
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (SlotAt slot : slots) {
            minX = Math.min(minX, slot.x());
            minY = Math.min(minY, slot.y());
            maxX = Math.max(maxX, slot.x());
            maxY = Math.max(maxY, slot.y());
        }
        return new GridCover(minX - WELL_LEFT, minY - TITLE_ABOVE, maxX + WELL_RIGHT, maxY + WELL_BELOW);
    }
}
