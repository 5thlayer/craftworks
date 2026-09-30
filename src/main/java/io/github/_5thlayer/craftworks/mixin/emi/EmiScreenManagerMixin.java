// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.mixin.emi;

import java.util.List;

import dev.emi.emi.api.widget.Bounds;
import dev.emi.emi.config.SidebarSettings;
import dev.emi.emi.runtime.EmiDrawContext;
import dev.emi.emi.screen.EmiScreenManager;
import dev.emi.emi.screen.EmiScreenManager.SidebarPanel;
import io.github._5thlayer.craftworks.compat.emi.IndexTabRow;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Puts the index tab row above EMI's item index (#15); see {@link IndexTabRow}.
 *
 * <p>Every injection is at a method's head or return, which always match while the method is there; the
 * mixin config's plugin checks the methods are before any is applied.
 */
@Mixin(value = EmiScreenManager.class, remap = false)
public abstract class EmiScreenManagerMixin {

    /** Set while the index's panel is laid out again in its smaller bounds, so that call is let through. */
    @Unique
    private static boolean craftworks$layingOutBelowRow;

    @Shadow
    private static void createScreenSpace(SidebarPanel panel, Screen screen, List<Bounds> exclusion, boolean rtl,
            Bounds bounds, SidebarSettings settings) {
        throw new AssertionError();
    }

    /** Lays the panel showing the index out a row shorter at the top, leaving the row its room. */
    @Inject(method = "createScreenSpace(Ldev/emi/emi/screen/EmiScreenManager$SidebarPanel;Lnet/minecraft/client/gui/screens/Screen;Ljava/util/List;ZLdev/emi/emi/api/widget/Bounds;Ldev/emi/emi/config/SidebarSettings;)V",
            at = @At("HEAD"), cancellable = true)
    private static void craftworks$leaveRoomForTabs(SidebarPanel panel, Screen screen, List<Bounds> exclusion, boolean rtl,
            Bounds bounds, SidebarSettings settings, CallbackInfo ci) {
        if (craftworks$layingOutBelowRow) return;
        boolean room = IndexTabRow.wantsRoom(panel);
        IndexTabRow.laidOut(panel, room);
        if (!room) return;
        craftworks$layingOutBelowRow = true;
        try {
            createScreenSpace(panel, screen, exclusion, rtl, IndexTabRow.belowRow(bounds), settings);
        } finally {
            craftworks$layingOutBelowRow = false;
        }
        ci.cancel();
    }

    @Inject(method = "render(Ldev/emi/emi/runtime/EmiDrawContext;IIF)V", at = @At("HEAD"))
    private static void craftworks$keepRoomForTabs(EmiDrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        IndexTabRow.keepRoom();
    }

    @Inject(method = "render(Ldev/emi/emi/runtime/EmiDrawContext;IIF)V", at = @At("RETURN"))
    private static void craftworks$renderTabs(EmiDrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        IndexTabRow.render(context, mouseX, mouseY);
    }

    @Inject(method = "drawForeground(Ldev/emi/emi/runtime/EmiDrawContext;IIF)V", at = @At("RETURN"))
    private static void craftworks$renderTabTooltip(EmiDrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        IndexTabRow.renderTooltip(context, mouseX, mouseY);
    }

    @Inject(method = "mouseClicked(Lnet/minecraft/client/input/MouseButtonEvent;)Z", at = @At("HEAD"), cancellable = true)
    private static void craftworks$clickTab(MouseButtonEvent event, CallbackInfoReturnable<Boolean> cir) {
        if (IndexTabRow.click(event)) cir.setReturnValue(true);
    }
}
