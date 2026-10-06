// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.machine.client;

import io.github._5thlayer.craftworks.machine.AssemblerMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * The Assembler's screen: a {@link HeldMachineScreen} over five inputs, the product and the remainders, with
 * the energy in the buffer below; tiers 2 and 3 add the fluid box as a gauge beside the energy bar.
 */
public final class AssemblerScreen extends HeldMachineScreen<AssemblerMenu> {

    private static final int ENERGY_Y = 60;
    private static final int ENERGY_WIDTH = 160;
    // With a fluid box the energy bar gives up its right end to the gauge, the same height.
    private static final int ENERGY_WIDTH_WITH_FLUID = 104;
    private static final int FLUID_X = 8 + ENERGY_WIDTH_WITH_FLUID + 4;
    private static final int FLUID_WIDTH = 8 + ENERGY_WIDTH - FLUID_X;

    public AssemblerScreen(AssemblerMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, 176, 166, AssemblerMenu.INVENTORY_Y);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        int energyX = leftPos + 8;
        int energyY = topPos + ENERGY_Y;
        int energyWidth = menu.hasFluidBox() ? ENERGY_WIDTH_WITH_FLUID : ENERGY_WIDTH;
        MachineScreens.energyBar(graphics, font, energyX, energyY, energyWidth, menu.energy(), menu.energyCapacity());

        if (menu.hasFluidBox()) {
            MachineScreens.fluidGauge(graphics, menu.fluid(), leftPos + FLUID_X, energyY, FLUID_WIDTH, menu.fluidCapacity());
        }
    }

    /** Over the gauge, the fluid's name and how much of the box it fills. */
    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractTooltip(graphics, mouseX, mouseY);
        if (menu.hasFluidBox() && MachineScreens.overGauge(leftPos + FLUID_X, topPos + ENERGY_Y, FLUID_WIDTH, mouseX, mouseY)) {
            MachineScreens.fluidTooltip(graphics, font, menu.fluid(), menu.fluidCapacity(), mouseX, mouseY);
        }
    }
}
