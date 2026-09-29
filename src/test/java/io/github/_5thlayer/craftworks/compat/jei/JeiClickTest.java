// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.compat.jei;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github._5thlayer.craftworks.assembler.FillRequest;
import org.junit.jupiter.api.Test;

/**
 * JEI's recipe button asks for what EMI's Fill Recipe asks for: its input's button and modifier bits
 * go through the same {@link FillRequest#of}.
 */
class JeiClickTest {

    private static final int SHIFT = 0x1;
    private static final int CONTROL = 0x2;

    @Test
    void buttonsMapAsEmisDo() {
        assertEquals(FillRequest.ONE, JeiClick.request(0, 0));
        assertEquals(FillRequest.FIVE, JeiClick.request(1, 0));
        assertEquals(FillRequest.PLAN, JeiClick.request(2, 0));
    }

    @Test
    void shiftBitAsksForAll() {
        assertEquals(FillRequest.ALL, JeiClick.request(0, SHIFT));
        assertEquals(FillRequest.ALL, JeiClick.request(2, SHIFT | CONTROL));
    }

    @Test
    void otherModifiersAreNotShift() {
        assertEquals(FillRequest.ONE, JeiClick.request(0, CONTROL));
    }
}
