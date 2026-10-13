// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.mixin.minecraft;

import net.minecraft.client.gui.navigation.ScreenPosition;
import net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Reads where a recipe book screen puts its recipe book button, which is protected and which a screen
 * that copies the inventory overrides to put somewhere of its own. {@code InventoryGridBlank} finds the
 * button by that position.
 */
@Mixin(AbstractRecipeBookScreen.class)
public interface AbstractRecipeBookScreenAccessor {

    @Invoker("getRecipeBookButtonPosition")
    ScreenPosition craftworks$recipeBookButtonPosition();
}
