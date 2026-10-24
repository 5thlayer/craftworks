// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.assembler;

/**
 * Whether a click on an EMI stack on the Personal Assembler's screen queues five (#77, ADR-0012).
 *
 * <p>Craftworks adds plain right click and nothing else. EMI's binds come first and are never overridden:
 * with EMI's defaults view-uses holds right click and keeps it, and a player who wants five unbinds it.
 * So the press counts only when EMI's own handling of it found nothing to do.
 */
public final class StackClickFill {

    private static final int NO_MODIFIERS = 0;

    private StackClickFill() {
    }

    /**
     * @param button     the mouse button released
     * @param modifiers  the modifier keys held, in EMI's own encoding, 0 for none
     * @param consumed   whether EMI's stack interaction took the press
     */
    public static boolean fallsThroughToFive(int button, int modifiers, boolean consumed) {
        return !consumed && button == FillRequest.RIGHT_BUTTON && modifiers == NO_MODIFIERS;
    }
}
