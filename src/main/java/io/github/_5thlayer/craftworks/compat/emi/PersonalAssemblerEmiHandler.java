// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.compat.emi;

import java.util.List;

import dev.emi.emi.api.recipe.EmiPlayerInventory;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.handler.EmiCraftContext;
import dev.emi.emi.api.recipe.handler.EmiRecipeHandler;
import io.github._5thlayer.craftworks.assembler.AssemblingRecipeIds;
import io.github._5thlayer.craftworks.assembler.FillRequest;
import io.github._5thlayer.craftworks.assembler.ReadyRecipeIds;
import io.github._5thlayer.craftworks.network.FillRecipePacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.InventoryMenu;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * EMI's {@code + Fill Recipe}, pointed at the Personal Assembler (ADR-0004, ADR-0005).
 *
 * <p><b>{@link EmiRecipeHandler} directly, never {@code StandardRecipeHandler}.</b> A standard handler
 * moves ingredients into a crafting grid, and its default {@code canCraft} checks the player's inventory
 * against the recipe, so EMI greys the button out when an ingredient is missing. The Assembler has no
 * grid and moves nothing, and a missing ingredient is exactly the case the Crafting Plan exists to name.
 *
 * <p>{@code RecipeFillButtonWidget} sets {@code canFill = supportsRecipe(recipe) && canCraft(recipe,
 * ctx)}, and that single field drives both the greyed texture and the click gate. {@code canCraft} is
 * ours, so returning true unconditionally keeps the button lit with the ingredients absent.
 *
 * <p>{@code craft()} sends and returns. It opens no screen, because {@code EmiRecipeFiller.performFill}
 * calls {@code Minecraft.setScreen(handledScreen)} the moment it returns true, and anything opened
 * synchronously here loses that race. That same {@code setScreen} is why a queueing click returns
 * <b>false</b>: true would close EMI's recipe screen after every craft, and the player would reopen it
 * to queue the next one. False skips the {@code setScreen} and EMI's button sound with it, so the sound
 * is played here.
 */
public final class PersonalAssemblerEmiHandler implements EmiRecipeHandler<InventoryMenu> {

    /**
     * The player's stacks, built here rather than asked for.
     *
     * <p>{@code EmiPlayerInventory.of(player)} would be the obvious call and is a stack overflow: it is
     * EMI's dispatcher, which looks up the handlers registered for the open screen and returns
     * {@code handlers.get(0).getInventory(...)}, which is this method. EMI builds the inventory to work
     * out what is craftable as soon as the inventory opens, so the crash would come then, not on a press.
     */
    @Override
    public EmiPlayerInventory getInventory(AbstractContainerScreen<InventoryMenu> screen) {
        Player player = Minecraft.getInstance().player;
        return player == null ? new EmiPlayerInventory(List.of()) : new EmiPlayerInventory(player);
    }

    /**
     * An Assembling recipe, and no other.
     *
     * <p>Which recipes are Assembling recipes is a fact about the loaded recipes and therefore server
     * truth, so it is synced as ids and read here from {@link AssemblingRecipeIds}. EMI's own 2x2 handler
     * shares the inventory's key but claims only vanilla's crafting category.
     */
    @Override
    public boolean supportsRecipe(EmiRecipe recipe) {
        Identifier id = recipe.getId();
        return id != null && AssemblingRecipeIds.contains(id.toString());
    }

    /**
     * Fill Recipe is always possible; EMI's craftables list is possible only for a Ready recipe.
     *
     * <p>The button must stay enabled when the player lacks the ingredients, because showing what is
     * missing is the Crafting Plan's entire job. So every context but one answers true. The exception is
     * {@code Type.CRAFTABLE}, which is EMI asking whether to list the recipe's result under Craftables,
     * and that is answered from the Ready set the server syncs (ADR-0012): what Fill Recipe would queue
     * right now, intermediates included. Before the first sync it is empty, so nothing is listed.
     *
     * <p>Which recipes get a button at all is {@link #supportsRecipe}'s question, and a different one.
     */
    @Override
    public boolean canCraft(EmiRecipe recipe, EmiCraftContext<InventoryMenu> context) {
        if (context.getType() != EmiCraftContext.Type.CRAFTABLE) return true;
        Identifier id = recipe.getId();
        return id != null && ReadyRecipeIds.contains(id.toString());
    }

    /**
     * Names the four clicks under EMI's own "Fill Recipe" line, or the shortcut cannot be discovered.
     *
     * <p>EMI asks this only of the handler for the open screen, so the lines appear on the inventory
     * screen and nowhere else.
     */
    @Override
    public List<ClientTooltipComponent> getTooltip(EmiRecipe recipe, EmiCraftContext<InventoryMenu> context) {
        return List.of("left", "right", "shift", "middle").stream()
                .map(key -> ClientTooltipComponent.create(
                        Component.translatable("craftworks.fill_recipe." + key).getVisualOrderText()))
                .toList();
    }

    /**
     * Sends the recipe and what the click asked for, and gets out of EMI's way (ADR-0004).
     *
     * <p>Left queues one, right five, Shift all, middle asks for the Crafting Plan. The button comes from
     * {@link FillClick}; EMI reports Shift itself, as an amount of {@code Integer.MAX_VALUE}. Whether the
     * inventory covers the request is the server's call.
     *
     * <p>Only a request for the plan returns true and hands the screen back to the inventory; a queueing
     * click returns false so EMI's recipe screen stays open for the next one (see the class doc).
     */
    @Override
    public boolean craft(EmiRecipe recipe, EmiCraftContext<InventoryMenu> context) {
        Identifier id = recipe.getId();
        if (id == null) return false;
        FillRequest request = FillRequest.of(FillClick.button(), context.getAmount() == Integer.MAX_VALUE);
        ClientPacketDistributor.sendToServer(new FillRecipePacket(id, request));
        if (request == FillRequest.PLAN) return true;
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
        return false;
    }
}
