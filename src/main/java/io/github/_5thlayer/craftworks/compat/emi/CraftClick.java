// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.compat.emi;

/**
 * Whether EMI is inside {@code EmiScreenManager.craftInteraction}, a click or craft hotkey on a Craftables
 * entry or a favorite (ADR-0012, amended by #66).
 *
 * <p>Set and cleared around that call on the render thread, which runs it synchronously, so no click can
 * see another's state. It only records: the click is never altered, consumed or rerouted, and on any
 * screen but the Assembler's nothing reads it.
 *
 * <p>While {@link #pickingFavoriteRecipe} has it on, EMI is picking a favorite's recipe and the handler still
 * answers from the Ready set, so a Ready recipe is preferred over one that isn't.
 */
public final class CraftClick {

    private static boolean inProgress;
    private static boolean pickingRecipe;

    private CraftClick() {
    }

    public static void begin() {
        inProgress = true;
        pickingRecipe = false;
    }

    public static void end() {
        inProgress = false;
        pickingRecipe = false;
    }

    public static void pickingFavoriteRecipe(boolean on) {
        pickingRecipe = on;
    }

    /** True while a click's Fill must reach {@code craft()} whatever the Ready set says. */
    public static boolean bypassesReady() {
        return inProgress && !pickingRecipe;
    }
}
