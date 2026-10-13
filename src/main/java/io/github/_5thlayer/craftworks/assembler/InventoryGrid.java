// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.assembler;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AbstractCraftingMenu;
import net.minecraft.world.inventory.Slot;

/**
 * The inventory's 2x2 crafting grid, wherever a menu has one (ADR-0005, ADR-0007).
 *
 * <p>Vanilla's inventory is one such menu; another mod's inventory screen copies it with a 2x2
 * {@code AbstractCraftingMenu} of its own. Both are recognised by what vanilla's API says of them and
 * never by whose they are -- Craftworks names no other mod. The crafting table is a 3x3 and is not one.
 */
public final class InventoryGrid {

    private InventoryGrid() {
    }

    /** Whether {@code menu} is a crafting menu with a 2x2 grid, vanilla's inventory menu included. */
    public static boolean is2x2(AbstractContainerMenu menu) {
        return menu instanceof AbstractCraftingMenu crafting
                && crafting.getGridWidth() == 2
                && crafting.getGridHeight() == 2;
    }

    /** The grid's result slot and then its input slots, as the menu holds them now. */
    public static List<Slot> slotsOf(AbstractCraftingMenu menu) {
        List<Slot> slots = new ArrayList<>();
        slots.add(menu.getResultSlot());
        slots.addAll(menu.getInputGridSlots());
        return slots;
    }
}
