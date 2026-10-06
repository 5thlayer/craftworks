// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.compat.emi;

import java.util.List;

import dev.emi.emi.api.recipe.EmiPlayerInventory;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.handler.EmiCraftContext;
import dev.emi.emi.api.recipe.handler.EmiRecipeHandler;
import io.github._5thlayer.craftworks.machine.MachineKind;
import io.github._5thlayer.craftworks.network.HoldRecipePacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * EMI's {@code + Fill Recipe}, pointed at an open Assembler or Chemical Plant: it sets the Held recipe and moves
 * no items. One handler for either menu, told which it is for the text it shows.
 *
 * <p>{@link EmiRecipeHandler} directly, as {@link PersonalAssemblerEmiHandler} is: holding a recipe takes
 * no items, so the button stays lit with an empty inventory. Every Assembling recipe gets it, Hand-craftable
 * or not and Locked or not; whether the open machine takes it, and for this player, is server truth, and the
 * server refuses with a message rather than the button hiding the reason. It shows on both machines' tabs,
 * whichever the open machine holds (ADR-0016).
 */
public final class HeldMachineEmiHandler<M extends AbstractContainerMenu> implements EmiRecipeHandler<M> {

    private final MachineKind machine;

    /** @param machine which machine the button's tooltip says the recipe is set on */
    public HeldMachineEmiHandler(MachineKind machine) {
        this.machine = machine;
    }

    /** Built here, not asked for: {@code EmiPlayerInventory.of} dispatches back to this method. */
    @Override
    public EmiPlayerInventory getInventory(AbstractContainerScreen<M> screen) {
        Player player = Minecraft.getInstance().player;
        return player == null ? new EmiPlayerInventory(List.of()) : new EmiPlayerInventory(player);
    }

    @Override
    public boolean supportsRecipe(EmiRecipe recipe) {
        return recipe instanceof AssemblingEmiRecipe;
    }

    @Override
    public boolean canCraft(EmiRecipe recipe, EmiCraftContext<M> context) {
        return true;
    }

    @Override
    public List<ClientTooltipComponent> getTooltip(EmiRecipe recipe, EmiCraftContext<M> context) {
        return List.of(ClientTooltipComponent.create(
                Component.translatable(machine.langKey("fill_recipe")).getVisualOrderText()));
    }

    /** Sends the recipe and hands the screen back to the Assembler or Chemical Plant, where the Held recipe is shown. */
    @Override
    public boolean craft(EmiRecipe recipe, EmiCraftContext<M> context) {
        ClientPacketDistributor.sendToServer(new HoldRecipePacket(AssemblingEmiRecipe.recipeIdOf(recipe)));
        return true;
    }
}
