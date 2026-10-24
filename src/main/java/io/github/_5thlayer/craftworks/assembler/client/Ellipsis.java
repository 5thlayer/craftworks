// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.assembler.client;

import java.util.function.ToIntFunction;

/**
 * Cuts a line of text to a width with a trailing "…", the web's ellipsis: the full text goes in a
 * tooltip beside it. Measured by the font, not by counting characters, so the cut follows the GUI scale's
 * glyph widths and any translation. No Minecraft here, so it is a unit test.
 */
final class Ellipsis {

    static final String MARK = "…";

    private Ellipsis() {
    }

    /** {@code text} when it fits {@code width}, else its longest prefix that fits with the mark after it. */
    static String fit(String text, int width, ToIntFunction<String> measure) {
        if (measure.applyAsInt(text) <= width) return text;
        int budget = width - measure.applyAsInt(MARK);
        if (budget < 0) return "";
        int end = 0;
        while (end < text.length()) {
            int next = text.offsetByCodePoints(end, 1);
            if (measure.applyAsInt(text.substring(0, next)) > budget) break;
            end = next;
        }
        return text.substring(0, end).stripTrailing() + MARK;
    }
}
