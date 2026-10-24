// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.compat.emi;

import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.registry.EmiRecipeFiller;

/**
 * Whether EMI is inside the craft a plain right click on a stack falls through to (#77).
 *
 * <p>Set and cleared around that call on the render thread, which runs it synchronously, so no click can
 * see another's state. {@code StackClickMixin} sets it, and {@code CraftInteractionMixin} asks
 * {@link #refuses} of every recipe EMI picks while it is on.
 */
public final class RightClickCraft {

    private static boolean active;

    private RightClickCraft() {
    }

    public static void begin() {
        active = true;
    }

    public static void end() {
        active = false;
    }

    /**
     * True while this craft is in progress and {@code recipe} would not go to the Personal Assembler's
     * handler on the open screen.
     *
     * <p>A left click on such a recipe is EMI's own, say a vanilla recipe's ingredients moving into a
     * crafting grid; a right click must not do that, so the recipe is refused and EMI crafts nothing.
     */
    public static boolean refuses(Object recipe) {
        if (!active || !(recipe instanceof EmiRecipe emiRecipe)) return false;
        var screen = EmiApi.getHandledScreen();
        return screen == null || !(EmiRecipeFiller.getFirstValidHandler(emiRecipe, screen) instanceof PersonalAssemblerEmiHandler);
    }
}
