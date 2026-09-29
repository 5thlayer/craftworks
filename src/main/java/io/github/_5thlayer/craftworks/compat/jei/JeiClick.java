// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.compat.jei;

import io.github._5thlayer.craftworks.assembler.FillRequest;

/**
 * What a click on JEI's Assembling recipe button asks for: the button and modifier bits of JEI's user
 * input, put through {@link FillRequest#of}, the function EMI's Fill Recipe uses (ADR-0004).
 *
 * <p>Plain ints, so it is tested without Minecraft or JEI.
 */
public final class JeiClick {

    /** GLFW's {@code GLFW_MOD_SHIFT}, the bit JEI reports in {@code getModifiers()}. */
    static final int MOD_SHIFT = 0x1;

    private JeiClick() {
    }

    public static FillRequest request(int button, int modifiers) {
        return FillRequest.of(button, (modifiers & MOD_SHIFT) != 0);
    }
}
