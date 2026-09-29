// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.mixin.jei;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import io.github._5thlayer.craftworks.compat.jei.AssemblingRecipeButton;
import mezz.jei.api.gui.buttons.IIconButtonController;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Lets the Assembler's JEI recipe button take a right and a middle click (#12, ADR-0004).
 *
 * <p>JEI's icon buttons are vanilla buttons underneath, and a vanilla button's
 * {@code isValidClickButton} accepts the left button alone, so a right or middle click never reaches
 * {@code onPress}, where JEI's user input would carry it. This widens that check for
 * {@link AssemblingRecipeButton} and nothing else: JEI's own buttons keep their left-only clicks.
 */
@Mixin(targets = "mezz.jei.gui.elements.IconButton$UserInputHandler", remap = false)
public abstract class IconButtonInputMixin {

    @Shadow
    @Final
    private IIconButtonController controller;

    @ModifyExpressionValue(method = "lambda$handleUserInput$0", at = @At(value = "INVOKE",
            target = "Lmezz/jei/gui/elements/InternalIconButton;isValidClickButton(Lnet/minecraft/client/input/MouseButtonInfo;)Z"))
    private boolean craftworks$anyMouseButton(boolean valid) {
        return valid || AssemblingRecipeButton.is(controller);
    }
}
