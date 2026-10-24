// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.assembler.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.function.ToIntFunction;

import org.junit.jupiter.api.Test;

/** The empty-queue hint is cut to its width by measure, not by character count. */
class EllipsisTest {

    /** Six pixels a character, the mark two: a font where "…" is narrower than a letter. */
    private static final ToIntFunction<String> FONT =
            s -> s.codePoints().map(c -> c == '…' ? 2 : 6).sum();

    @Test
    void textThatFitsIsLeftWhole() {
        assertEquals("Queue empty.", Ellipsis.fit("Queue empty.", 72, FONT));
    }

    @Test
    void textThatDoesNotFitEndsInTheMarkWithinTheWidth() {
        String cut = Ellipsis.fit("Queue empty. Find an item", 72, FONT);
        assertEquals("Queue empty…", cut);
        assertTrue(FONT.applyAsInt(cut) <= 72);
    }

    @Test
    void aWiderFontCutsSooner() {
        ToIntFunction<String> wide = s -> 2 * FONT.applyAsInt(s);
        assertEquals("Queue…", Ellipsis.fit("Queue empty. Find an item", 72, wide));
    }

    @Test
    void noRoomForTheMarkLeavesNothing() {
        assertEquals("", Ellipsis.fit("Queue empty.", 1, FONT));
    }
}
