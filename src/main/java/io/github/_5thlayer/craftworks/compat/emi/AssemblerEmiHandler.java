// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.compat.emi;

import java.util.List;

import dev.emi.emi.api.recipe.EmiPlayerInventory;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.handler.EmiCraftContext;
import dev.emi.emi.api.recipe.handler.EmiRecipeHandler;
import io.github._5thlayer.craftworks.machine.AssemblerMenu;
import io.github._5thlayer.craftworks.network.HoldRecipePacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * EMI's {@code + Fill Recipe}, pointed at an open Assembler: it sets the Held recipe and moves no items.
 *
 * <p>{@link EmiRecipeHandler} directly, as {@link PersonalAssemblerEmiHandler} is: holding a recipe takes
 * no items, so the button stays lit with an empty inventory. Every Assembling recipe gets it, Hand-craftable
 * or not and Locked or not; whether this Assembler takes it, and for this player, is server truth, and the
 * server refuses with a message rather than the button hiding the reason.
 */
public final class AssemblerEmiHandler implements EmiRecipeHandler<AssemblerMenu> {

    /** Built here, not asked for: {@code EmiPlayerInventory.of} dispatches back to this method. */
    @Override
    public EmiPlayerInventory getInventory(AbstractContainerScreen<AssemblerMenu> screen) {
        Player player = Minecraft.getInstance().player;
        return player == null ? new EmiPlayerInventory(List.of()) : new EmiPlayerInventory(player);
    }

    @Override
    public boolean supportsRecipe(EmiRecipe recipe) {
        return recipe.getCategory() == AssemblingEmiPlugin.ASSEMBLING && recipe.getId() != null;
    }

    @Override
    public boolean canCraft(EmiRecipe recipe, EmiCraftContext<AssemblerMenu> context) {
        return true;
    }

    @Override
    public List<ClientTooltipComponent> getTooltip(EmiRecipe recipe, EmiCraftContext<AssemblerMenu> context) {
        return List.of(ClientTooltipComponent.create(
                Component.translatable("craftworks.assembler.fill_recipe").getVisualOrderText()));
    }

    /** Sends the recipe and hands the screen back to the Assembler, where the Held recipe is shown. */
    @Override
    public boolean craft(EmiRecipe recipe, EmiCraftContext<AssemblerMenu> context) {
        Identifier id = recipe.getId();
        if (id == null) return false;
        ClientPacketDistributor.sendToServer(new HoldRecipePacket(id));
        return true;
    }
}
