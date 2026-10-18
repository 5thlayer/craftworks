// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.mixin.emi;

import java.util.function.Function;
import java.util.function.Supplier;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStackInteraction;
import dev.emi.emi.input.EmiBind;
import dev.emi.emi.screen.EmiScreenManager;
import io.github._5thlayer.craftworks.compat.emi.CraftClick;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Lets a click on a Craftables entry or a favorite reach the Assembler's handler whatever the synced
 * Ready set says (ADR-0012, amended by #66).
 *
 * <p>{@code craftInteraction} serves mouse clicks on those entries and EMI's craft hotkeys on a hovered
 * stack. It asks the handler's {@code canCraft} in {@code EmiRecipeFiller.performFill} and sends nothing
 * on false, and a favorite with no recipe of its own picks one through {@code getPreferredRecipe(..,
 * true)}, which skips every recipe {@code canCraft} refuses. {@link CraftClick} is set around the whole
 * call so the handler answers yes to both.
 *
 * <p><b>The seam.</b> The recipe supplier is evaluated twice at most: first with the handler still
 * answering from Ready, so a favorite whose item has a Ready recipe gets one; and only when that finds
 * none, again with the handler answering yes, so EMI's own preference (the player's chosen default, held
 * ingredients, category order) picks among the rest. Answering yes throughout would hand every recipe
 * the same +16 in EMI's scoring and a recipe that isn't Ready could beat a Ready sibling. A Craftables
 * entry carries its recipe, so its first evaluation already succeeds.
 *
 * <p>It only records; the click is never altered or rerouted. The tooltip's {@code getPreferredRecipe}
 * call is outside {@code craftInteraction} and is untouched. {@code try/finally} clears the flag on any
 * exit, and on any screen but the Assembler's nothing reads it.
 */
@Mixin(value = EmiScreenManager.class, remap = false)
public abstract class CraftInteractionMixin {

    @WrapMethod(method = "craftInteraction(Ldev/emi/emi/api/stack/EmiIngredient;Ljava/util/function/Supplier;Ldev/emi/emi/api/stack/EmiStackInteraction;Ljava/util/function/Function;)Z")
    private static boolean craftworks$flagClick(EmiIngredient ingredient, Supplier<EmiRecipe> recipe,
            EmiStackInteraction interaction, Function<EmiBind, Boolean> bind, Operation<Boolean> original) {
        CraftClick.begin();
        try {
            return original.call(ingredient, recipe, interaction, bind);
        } finally {
            CraftClick.end();
        }
    }

    @WrapOperation(method = "craftInteraction(Ldev/emi/emi/api/stack/EmiIngredient;Ljava/util/function/Supplier;Ldev/emi/emi/api/stack/EmiStackInteraction;Ljava/util/function/Function;)Z",
            at = @At(value = "INVOKE", target = "Ljava/util/function/Supplier;get()Ljava/lang/Object;"))
    private static Object craftworks$readyFirst(Supplier<?> supplier, Operation<Object> original) {
        CraftClick.pickingFavoriteRecipe(true);
        Object ready;
        try {
            ready = original.call(supplier);
        } finally {
            CraftClick.pickingFavoriteRecipe(false);
        }
        return ready != null ? ready : original.call(supplier);
    }
}
