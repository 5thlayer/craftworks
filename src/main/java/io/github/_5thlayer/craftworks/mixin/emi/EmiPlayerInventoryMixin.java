// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.mixin.emi;

import java.util.List;
import java.util.function.Predicate;

import dev.emi.emi.api.recipe.EmiPlayerInventory;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.stack.EmiIngredient;
import io.github._5thlayer.craftworks.compat.emi.ReadyCraftables;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Adds the Ready Assembling recipes EMI would not have considered to its Craftables list (#17); see
 * {@link ReadyCraftables}.
 *
 * <p>The one injection is at the method's return, which always matches while the method is there; the
 * mixin config's plugin checks that it, and the shadowed {@code getPredicate}, are before this is applied.
 */
@Mixin(value = EmiPlayerInventory.class, remap = false)
public abstract class EmiPlayerInventoryMixin {

    @Shadow
    public abstract Predicate<EmiRecipe> getPredicate();

    @Inject(method = "getCraftables()Ljava/util/List;", at = @At("RETURN"), cancellable = true)
    private void craftworks$addReady(CallbackInfoReturnable<List<EmiIngredient>> cir) {
        cir.setReturnValue(ReadyCraftables.widen(cir.getReturnValue(), getPredicate()));
    }
}
