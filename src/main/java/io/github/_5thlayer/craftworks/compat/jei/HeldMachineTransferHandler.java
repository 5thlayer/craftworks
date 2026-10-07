// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.compat.jei;

import java.util.Optional;

import io.github._5thlayer.craftworks.network.HoldRecipePacket;
import io.github._5thlayer.craftworks.recipe.AssemblingRecipe;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import mezz.jei.api.recipe.types.IRecipeType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * JEI's {@code +} on an Assembling recipe with an Assembler open: it sets the Held recipe and moves no
 * items, as EMI's Fill Recipe does there. Always offered, whatever the inventory holds, and whether the
 * open machine takes the recipe for this player is the server's call, which answers in chat.
 */
final class HeldMachineTransferHandler<M extends AbstractContainerMenu> implements IRecipeTransferHandler<M, RecipeHolder<AssemblingRecipe>> {

    private final Class<M> menuClass;
    private final MenuType<M> menuType;
    private final IRecipeType<RecipeHolder<AssemblingRecipe>> tab;

    /** @param tab the tab it is registered on */
    HeldMachineTransferHandler(Class<M> menuClass, MenuType<M> menuType, IRecipeType<RecipeHolder<AssemblingRecipe>> tab) {
        this.menuClass = menuClass;
        this.menuType = menuType;
        this.tab = tab;
    }

    @Override
    public Class<? extends M> getContainerClass() {
        return menuClass;
    }

    @Override
    public Optional<MenuType<M>> getMenuType() {
        return Optional.of(menuType);
    }

    @Override
    public IRecipeType<RecipeHolder<AssemblingRecipe>> getRecipeType() {
        return tab;
    }

    @Override
    public IRecipeTransferError transferRecipe(M menu, RecipeHolder<AssemblingRecipe> recipe,
            IRecipeSlotsView slots, Player player, boolean maxTransfer, boolean doTransfer) {
        if (doTransfer) {
            ClientPacketDistributor.sendToServer(new HoldRecipePacket(recipe.id().identifier()));
        }
        return null;
    }
}
