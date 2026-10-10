// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.machine.client;

import io.github._5thlayer.craftworks.machine.RefinerMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/** The Refiner's screen: its input, the smelt's progress, its output and the energy in the buffer. A stand-in drawn from fills (5thlayer/craftworks#22). */
public final class RefinerScreen extends AbstractContainerScreen<RefinerMenu> {

    private static final int ENERGY_WIDTH = 160;
    private static final int BAR_X = RefinerMenu.INPUT_X + 16 + 6;
    private static final int BAR_WIDTH = RefinerMenu.OUTPUT_X - 6 - BAR_X;

    public RefinerScreen(RefinerMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, 176, RefinerMenu.INVENTORY_Y + 82);
        inventoryLabelY = RefinerMenu.INVENTORY_Y - 11;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, MachineScreens.PANEL);
        for (Slot slot : menu.slots) {
            MachineScreens.recess(graphics, leftPos + slot.x, topPos + slot.y, 16, 16);
        }
        int x = leftPos + BAR_X;
        int y = topPos + RefinerMenu.SLOT_Y;
        MachineScreens.recess(graphics, x, y, BAR_WIDTH, 16);
        graphics.fill(x, y, x + Math.round(BAR_WIDTH * menu.progress()), y + 16, MachineScreens.BAR);
        MachineScreens.energyBar(graphics, font, leftPos + 8, topPos + RefinerMenu.ENERGY_Y, ENERGY_WIDTH,
                menu.energy(), menu.energyCapacity());
    }
}
