// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.mixin.emi;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.emi.emi.api.stack.EmiStackInteraction;
import dev.emi.emi.config.EmiConfig;
import dev.emi.emi.input.EmiBind;
import dev.emi.emi.input.EmiInput;
import dev.emi.emi.screen.EmiScreenManager;
import io.github._5thlayer.craftworks.assembler.FillRequest;
import io.github._5thlayer.craftworks.assembler.StackClickFill;
import io.github._5thlayer.craftworks.compat.emi.FillClick;
import io.github._5thlayer.craftworks.compat.emi.RightClickCraft;
import java.util.function.Function;
import net.minecraft.client.input.MouseButtonEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * A plain right click on a Craftables entry, a favorite or an index stack queues five, when no bind of
 * EMI's took the press (#77, ADR-0004).
 *
 * <p><b>On release.</b> EMI's {@code mouseClicked} only claims the press; {@code mouseReleased} calls
 * {@code stackInteraction} with the button, which tries every bind in turn and returns false when none
 * matched. This wraps that call: when it comes back false for a plain right button, it is run once more
 * with a bind function that matches only {@code craftOne}. That goes through EMI's own recipe picking
 * and {@code craftInteraction}, so {@link CraftInteractionMixin}'s Ready-first pick and its one Fill a
 * press (#75) apply unchanged, and {@link FillClick} holds button 1 so the handler reads it as the
 * right click of {@code + Fill Recipe}: five, or the Crafting Plan when the inventory doesn't cover them.
 *
 * <p>A press an EMI bind took (view-uses on right click, by default) returns true first and is never
 * looked at again. With any modifier held, or on another button, nothing is added. A recipe that the
 * Personal Assembler's handler is not the one to craft on the open screen is refused ({@link
 * RightClickCraft#refuses}), so no other screen's click, and no recipe of another handler, moves ingredients.
 * {@code try/finally} clears both flags on any exit.
 */
@Mixin(value = EmiScreenManager.class, remap = false)
public abstract class StackClickMixin {

    private static int craftworks$released = FillRequest.NO_BUTTON;

    @WrapMethod(method = "mouseReleased(Lnet/minecraft/client/input/MouseButtonEvent;)Z")
    private static boolean craftworks$noteRelease(MouseButtonEvent event, Operation<Boolean> original) {
        craftworks$released = event.button();
        try {
            return original.call(event);
        } finally {
            craftworks$released = FillRequest.NO_BUTTON;
        }
    }

    @WrapOperation(method = "mouseReleased(Lnet/minecraft/client/input/MouseButtonEvent;)Z",
            at = @At(value = "INVOKE", target = "Ldev/emi/emi/screen/EmiScreenManager;stackInteraction(Ldev/emi/emi/api/stack/EmiStackInteraction;Ljava/util/function/Function;)Z"))
    private static boolean craftworks$rightClickCrafts(EmiStackInteraction interaction, Function<EmiBind, Boolean> bind,
            Operation<Boolean> original) {
        boolean consumed = original.call(interaction, bind);
        if (!StackClickFill.fallsThroughToFive(craftworks$released, EmiInput.getCurrentModifiers(), consumed)
                || interaction == null || interaction.getStack().isEmpty()) {
            return consumed;
        }
        FillClick.press(craftworks$released);
        RightClickCraft.begin();
        try {
            return EmiScreenManager.stackInteraction(interaction, pressed -> pressed == EmiConfig.craftOne);
        } finally {
            RightClickCraft.end();
            FillClick.release();
        }
    }
}
