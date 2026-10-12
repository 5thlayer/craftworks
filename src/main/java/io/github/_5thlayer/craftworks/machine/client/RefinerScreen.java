// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.machine.client;

import io.github._5thlayer.craftworks.machine.RefinerMenu;
import io.github._5thlayer.libworks.ghost.Ghosts;
import io.github._5thlayer.libworks.machine.client.MachineDraw;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** The Refiner's screen: its input, the smelt's progress, its output and the energy in the buffer. A stand-in drawn from fills (5thlayer/craftworks#22). */
public final class RefinerScreen extends AbstractContainerScreen<RefinerMenu> {

    private static final int ENERGY_WIDTH = 160;
    private static final int BAR_X = RefinerMenu.INPUT_X + 16 + 6;
    private static final int BAR_WIDTH = RefinerMenu.OUTPUT_X - 6 - BAR_X;
    /** The Refiner ghosts nothing: any smeltable item goes in its input. */
    private static final Ghosts NO_GHOSTS = new Ghosts();

    public RefinerScreen(RefinerMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, 176, RefinerMenu.INVENTORY_Y + 82);
        inventoryLabelY = RefinerMenu.INVENTORY_Y - 11;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        MachineDraw.panel(graphics, font, leftPos, topPos, imageWidth, imageHeight, menu.slots, NO_GHOSTS, 0);
        MachineScreens.progressBar(graphics, font, leftPos + BAR_X, topPos + RefinerMenu.SLOT_Y, BAR_WIDTH, menu.progress());
        MachineScreens.energyBar(graphics, font, leftPos + 8, topPos + RefinerMenu.ENERGY_Y, ENERGY_WIDTH,
                menu.energy(), menu.energyCapacity());
    }
}
