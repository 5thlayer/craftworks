// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.assembler;

import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AbstractCraftingMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;

/**
 * Another mod's copy of the inventory's 2x2 crafting grid (ADR-0005, ADR-0007).
 *
 * <p>A mod that draws its own inventory screen, such as one that adds slots beside the player's, copies
 * vanilla's: an {@code AbstractCraftingMenu} with a 2x2 grid of its own. {@code InventoryMenuMixin} never
 * reaches it, so it is recognised by what vanilla's API says of it and never by whose it is -- Craftworks
 * names no other mod. The crafting table is a 3x3 and is not one.
 *
 * <p>Its grid already crafts nothing, since crafting recipes are converted away at load (ADR-0011), but
 * it would take items, and give them back on close. So the server removes its slots as it does the
 * inventory's, in place with every index kept ({@link RemovedGridSlot#replace}), when a player opens it.
 * The client's copy of the menu is removed too ({@link #deaden}, from {@code InventoryGridBlank}), or the
 * client would predict a placement the server refuses.
 */
public final class CopiedInventoryGrid {

    private CopiedInventoryGrid() {
    }

    static void register() {
        NeoForge.EVENT_BUS.addListener(CopiedInventoryGrid::onOpen);
    }

    /** Whether {@code menu} is a copy of the inventory's grid: any crafting menu with a 2x2 grid, vanilla's included. */
    public static boolean isCopy(AbstractContainerMenu menu) {
        return menu instanceof AbstractCraftingMenu crafting
                && crafting.getGridWidth() == 2
                && crafting.getGridHeight() == 2;
    }

    /**
     * Removes the result slot and the grid's input slots of {@code menu}, in place. Does nothing to a menu
     * that is not a copy, or to the inventory menu, which {@code InventoryMenuMixin} has already done.
     * Safe to repeat.
     */
    public static void deaden(AbstractContainerMenu menu) {
        if (!isCopy(menu) || menu instanceof InventoryMenu) return;
        AbstractCraftingMenu crafting = (AbstractCraftingMenu) menu;
        // Each slot names its own index, which stays the same whichever list the menu answers from.
        RemovedGridSlot.replace(menu, crafting.getResultSlot().index);
        for (Slot input : crafting.getInputGridSlots()) {
            RemovedGridSlot.replace(menu, input.index);
        }
    }

    private static void onOpen(PlayerContainerEvent.Open event) {
        deaden(event.getContainer());
    }
}
