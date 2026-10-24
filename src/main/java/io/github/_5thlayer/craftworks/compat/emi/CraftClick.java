// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.compat.emi;

/**
 * Whether EMI is inside {@code EmiScreenManager.craftInteraction}, a click or craft hotkey on a Craftables
 * entry or a favorite (ADR-0012, amended by #66).
 *
 * <p>Set and cleared around that call on the render thread, which runs it synchronously, so no click can
 * see another's state. It records, and the click is never rerouted; on any screen but the Assembler's
 * nothing reads it.
 *
 * <p>While {@link #pickingFavoriteRecipe} has it on, EMI is picking a favorite's recipe and the handler still
 * answers from the Ready set, so a Ready recipe is preferred over one that isn't.
 *
 * <p>{@link #sent} notes that the handler sent a Fill during the call, which its {@code craft()} reports to EMI
 * as false (see {@code PersonalAssemblerEmiHandler}); {@code CraftInteractionMixin} reads it to tell EMI the
 * click was handled (#75).
 */
public final class CraftClick {

    private static boolean inProgress;
    private static boolean pickingRecipe;
    private static boolean sent;

    private CraftClick() {
    }

    public static void begin() {
        inProgress = true;
        pickingRecipe = false;
        sent = false;
    }

    public static void end() {
        inProgress = false;
        pickingRecipe = false;
        sent = false;
    }

    public static void pickingFavoriteRecipe(boolean on) {
        pickingRecipe = on;
    }

    /** Notes that the handler sent a Fill; outside a craft interaction it notes nothing. */
    public static void markSent() {
        if (inProgress) sent = true;
    }

    /** True once the handler has sent a Fill in the craft interaction in progress. */
    public static boolean sent() {
        return sent;
    }

    /** True while a click's Fill must reach {@code craft()} whatever the Ready set says. */
    public static boolean bypassesReady() {
        return inProgress && !pickingRecipe;
    }
}
