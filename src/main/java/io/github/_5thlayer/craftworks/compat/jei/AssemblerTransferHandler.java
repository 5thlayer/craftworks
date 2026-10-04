// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.compat.jei;

import java.util.Optional;

import io.github._5thlayer.craftworks.machine.Assemblers;
import io.github._5thlayer.craftworks.machine.AssemblerMenu;
import io.github._5thlayer.craftworks.network.HoldRecipePacket;
import io.github._5thlayer.craftworks.recipe.AssemblingRecipe;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import mezz.jei.api.recipe.types.IRecipeType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * JEI's {@code +} on an Assembling recipe with an Assembler open: it sets the Held recipe and moves no
 * items, as EMI's Fill Recipe does there. Always offered, whatever the inventory holds, and whether the
 * Assembler takes the recipe for this player is the server's call, which answers in chat.
 */
final class AssemblerTransferHandler implements IRecipeTransferHandler<AssemblerMenu, RecipeHolder<AssemblingRecipe>> {

    @Override
    public Class<? extends AssemblerMenu> getContainerClass() {
        return AssemblerMenu.class;
    }

    @Override
    public Optional<MenuType<AssemblerMenu>> getMenuType() {
        return Optional.of(Assemblers.MENU.get());
    }

    @Override
    public IRecipeType<RecipeHolder<AssemblingRecipe>> getRecipeType() {
        return AssemblingJeiPlugin.ASSEMBLING;
    }

    @Override
    public IRecipeTransferError transferRecipe(AssemblerMenu menu, RecipeHolder<AssemblingRecipe> recipe,
            IRecipeSlotsView slots, Player player, boolean maxTransfer, boolean doTransfer) {
        if (doTransfer) {
            ClientPacketDistributor.sendToServer(new HoldRecipePacket(recipe.id().identifier()));
        }
        return null;
    }
}
