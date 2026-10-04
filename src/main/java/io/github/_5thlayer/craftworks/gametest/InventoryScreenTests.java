// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.gametest;

import java.util.UUID;

import io.github._5thlayer.craftworks.assembler.FillRequest;
import io.github._5thlayer.craftworks.assembler.PersonalAssembler;
import io.github._5thlayer.craftworks.assembler.RemovedGridSlot;
import io.github._5thlayer.craftworks.planner.PlanQueue;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The inventory screen is the Assembler (ADR-0005, ADR-0007): the 2x2 grid is gone from a real
 * player's menu with every slot index kept, and a cancel from the queue reaches the server's queue.
 */
final class InventoryScreenTests {

    private InventoryScreenTests() {
    }

    static void register(CraftworksGameTests.Registrar tests) {
        tests.test("the_inventory_menu_keeps_its_slot_indices", 20, InventoryScreenTests::keepsSlotIndices);
        tests.test("the_2x2_grid_takes_nothing_and_crafts_nothing", 20, InventoryScreenTests::gridIsDead);
        tests.test("a_partial_cancel_refunds_and_keeps_the_row", 20, InventoryScreenTests::partialCancel);
    }

    /**
     * Other mods address the inventory menu by index, so every one of them still points where vanilla
     * put it: result 0, grid 1-4, armour 5-8, main inventory 9-35, hotbar 36-44, offhand 45.
     */
    private static void keepsSlotIndices(GameTestHelper helper) {
        ServerPlayer player = AssemblerTests.playerHolding(helper, ItemStack.EMPTY);
        InventoryMenu menu = player.inventoryMenu;
        helper.assertTrue(menu.slots.size() == InventoryMenu.SHIELD_SLOT + 1,
                "the menu has " + menu.slots.size() + " slots, not vanilla's 46");
        for (int i = 0; i < menu.slots.size(); i++) {
            helper.assertTrue(menu.slots.get(i).index == i, "slot " + i + " carries index " + menu.slots.get(i).index);
        }
        helper.assertTrue(menu.getSlot(InventoryMenu.RESULT_SLOT).container != player.getInventory(),
                "slot 0 is no longer the crafting result");
        for (int i = InventoryMenu.CRAFT_SLOT_START; i < InventoryMenu.CRAFT_SLOT_END; i++) {
            helper.assertTrue(menu.getSlot(i).container != player.getInventory(), "slot " + i + " is no longer a grid slot");
        }
        // Armour runs head to feet from 5.
        EquipmentSlot[] armour = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
        for (int i = 0; i < armour.length; i++) {
            player.setItemSlot(armour[i], new ItemStack(Items.DIRT, i + 1));
            helper.assertTrue(menu.getSlot(InventoryMenu.ARMOR_SLOT_START + i).getItem().getCount() == i + 1,
                    "slot " + (InventoryMenu.ARMOR_SLOT_START + i) + " is not the " + armour[i] + " slot");
        }
        // The main inventory is container slots 9-35 at menu slots 9-35, the hotbar 0-8 at 36-44.
        for (int i = InventoryMenu.INV_SLOT_START; i < InventoryMenu.INV_SLOT_END; i++) {
            assertBacksContainerSlot(helper, player, menu.getSlot(i), i);
        }
        for (int i = InventoryMenu.USE_ROW_SLOT_START; i < InventoryMenu.USE_ROW_SLOT_END; i++) {
            assertBacksContainerSlot(helper, player, menu.getSlot(i), i - InventoryMenu.USE_ROW_SLOT_START);
        }
        player.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.STICK, 7));
        helper.assertTrue(menu.getSlot(InventoryMenu.SHIELD_SLOT).getItem().is(Items.STICK),
                "slot 45 is not the offhand");
        helper.succeed();
    }

    private static void assertBacksContainerSlot(GameTestHelper helper, ServerPlayer player, Slot slot, int containerSlot) {
        helper.assertTrue(slot.container == player.getInventory() && slot.getContainerSlot() == containerSlot,
                "menu slot " + slot.index + " is not inventory slot " + containerSlot);
    }

    /** The five grid slots stay, inactive and refusing; nothing put in the grid behind them crafts. */
    private static void gridIsDead(GameTestHelper helper) {
        ServerPlayer player = AssemblerTests.playerHolding(helper, ItemStack.EMPTY);
        InventoryMenu menu = player.inventoryMenu;
        for (int i = InventoryMenu.RESULT_SLOT; i < InventoryMenu.CRAFT_SLOT_END; i++) {
            Slot slot = menu.getSlot(i);
            helper.assertTrue(slot instanceof RemovedGridSlot, "slot " + i + " is still a live grid slot");
            helper.assertFalse(slot.isActive(), "slot " + i + " is drawn");
            helper.assertFalse(slot.mayPlace(new ItemStack(Items.OAK_LOG)), "slot " + i + " takes items");
            helper.assertFalse(slot.mayPickup(player), "slot " + i + " gives items");
        }
        // What the recipe book would do: write straight into the grid's container, past the slots.
        menu.getSlot(InventoryMenu.CRAFT_SLOT_START).container.setItem(0, new ItemStack(Items.OAK_LOG));
        menu.slotsChanged(menu.getSlot(InventoryMenu.CRAFT_SLOT_START).container);
        helper.assertTrue(menu.getSlot(InventoryMenu.RESULT_SLOT).getItem().isEmpty(),
                "a log in the grid made " + menu.getSlot(InventoryMenu.RESULT_SLOT).getItem());
        menu.getSlot(InventoryMenu.CRAFT_SLOT_START).container.setItem(0, ItemStack.EMPTY);
        helper.succeed();
    }

    /** A left click's cancel on a real player: one craft's cost back, the rest still queued under its id. */
    private static void partialCancel(GameTestHelper helper) {
        ServerPlayer player = AssemblerTests.playerHolding(helper, new ItemStack(Items.OAK_LOG, 10));
        PersonalAssembler.fill(player, AssemblerTests.OAK_SAPLING, FillRequest.FIVE);
        PlanQueue queue = PersonalAssembler.queueOf(player);
        UUID row = queue.entries().get(0).plan().id();

        helper.assertTrue(PersonalAssembler.cancel(player, row, 1), "the cancel was refused");

        queue = PersonalAssembler.queueOf(player);
        helper.assertTrue(AssemblerTests.count(player, Items.OAK_LOG) == 2,
                "one craft's two logs should be back, and " + AssemblerTests.count(player, Items.OAK_LOG) + " are");
        helper.assertTrue(queue.entries().size() == 1 && queue.entries().get(0).plan().id().equals(row),
                "the row did not keep its id");
        helper.assertTrue(queue.entries().get(0).remainingRootCrafts() == 4,
                "4 crafts should be left, and " + queue.entries().get(0).remainingRootCrafts() + " are");
        helper.succeed();
    }
}
