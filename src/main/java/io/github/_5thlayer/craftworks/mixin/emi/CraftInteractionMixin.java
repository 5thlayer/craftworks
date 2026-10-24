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
import io.github._5thlayer.craftworks.compat.emi.RightClickCraft;
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
 * <p><b>One Fill a press (#75).</b> The handler's {@code craft()} sends and returns false, so EMI's recipe
 * screen stays open, and {@code craftInteraction} hands that false back to {@code stackInteraction}, which
 * takes the press as unhandled and tries its next binds: view recipes, then {@code craftInteraction} again
 * with the item's preferred recipe, which sends a second Fill whenever the press matches no bind in
 * between, as a keyboard craft key or a left click with view recipes unbound does. So a call in which the
 * handler sent answers true, and {@code stackInteraction} stops there. {@code performFill} has already
 * returned false by then, so no {@code setScreen} follows.
 *
 * <p>The right click that falls through to a craft (#77, {@code StackClickMixin}) runs this same call, and
 * there the picked recipe is dropped unless the Personal Assembler's handler is the one that would craft it
 * ({@link RightClickCraft#refuses}), so EMI crafts nothing rather than fill a grid.
 *
 * <p>Beyond those it only records; the click is never rerouted. The tooltip's {@code getPreferredRecipe}
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
            return original.call(ingredient, recipe, interaction, bind) || CraftClick.sent();
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
        Object recipe = ready != null ? ready : original.call(supplier);
        return RightClickCraft.refuses(recipe) ? null : recipe;
    }
}
