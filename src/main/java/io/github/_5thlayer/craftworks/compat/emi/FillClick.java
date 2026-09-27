// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.compat.emi;

import io.github._5thlayer.craftworks.assembler.FillRequest;

/**
 * The mouse button of the {@code + Fill Recipe} press in progress, or {@link FillRequest#NO_BUTTON}.
 *
 * <p>Set and cleared around EMI's {@code mouseClicked}, which calls the handler synchronously on the
 * render thread, so no press can see another's button. EMI's craft hotkeys reach the handler outside
 * any click and read {@code NO_BUTTON}.
 */
public final class FillClick {

    private static int button = FillRequest.NO_BUTTON;

    private FillClick() {
    }

    public static void press(int pressed) {
        button = pressed;
    }

    public static void release() {
        button = FillRequest.NO_BUTTON;
    }

    public static int button() {
        return button;
    }
}
