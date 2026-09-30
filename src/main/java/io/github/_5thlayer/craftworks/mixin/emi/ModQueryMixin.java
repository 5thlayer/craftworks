// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.mixin.emi;

import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.search.ModQuery;
import io.github._5thlayer.craftworks.compat.emi.AssembledItemsQuery;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Makes {@code @craftworks} in EMI's search find every item an Assembling recipe makes (#15); see
 * {@link AssembledItemsQuery}.
 *
 * <p>It only ever adds a match: a stack EMI's own mod search finds is left alone. A search EMI runs
 * before the stacks are baked asks {@code matchesUnbaked}, so both are widened alike.
 */
@Mixin(value = ModQuery.class, remap = false)
public abstract class ModQueryMixin {

    @Unique
    private boolean craftworks$namesCraftworks;

    @Inject(method = "<init>(Ljava/lang/String;)V", at = @At("TAIL"))
    private void craftworks$readTerm(String name, CallbackInfo ci) {
        craftworks$namesCraftworks = AssembledItemsQuery.namesCraftworks(name);
    }

    @Inject(method = {"matches(Ldev/emi/emi/api/stack/EmiStack;)Z", "matchesUnbaked(Ldev/emi/emi/api/stack/EmiStack;)Z"},
            at = @At("RETURN"), cancellable = true)
    private void craftworks$matchAssembled(EmiStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ() && craftworks$namesCraftworks && AssembledItemsQuery.assembled(stack)) cir.setReturnValue(true);
    }
}
