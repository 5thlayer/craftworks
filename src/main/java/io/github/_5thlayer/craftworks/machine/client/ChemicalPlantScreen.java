// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.machine.client;

import io.github._5thlayer.craftworks.machine.FluidMachine;
import io.github._5thlayer.craftworks.machine.FluidMachineMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * The Chemical Plant's screen: a {@link HeldMachineScreen} over two inputs and the product, with a gauge for each
 * of its four fluid boxes (the inputs, then the outputs) and the energy in the buffer below.
 */
public final class ChemicalPlantScreen extends HeldMachineScreen<FluidMachineMenu> {

    private static final int GAUGE_Y = 57;
    private static final int GAUGE_WIDTH = 36;
    /** The two input gauges, then the two outputs, a little apart. */
    private static final int[] GAUGE_X = {8, 48, 92, 132};
    private static final int ENERGY_Y = 72;
    private static final int ENERGY_WIDTH = 160;

    public ChemicalPlantScreen(FluidMachineMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, 176, FluidMachineMenu.INVENTORY_Y + 82, FluidMachineMenu.INVENTORY_Y);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        for (int box = 0; box < FluidMachine.CHEMICAL_PLANT.boxes(); box++) {
            MachineScreens.fluidGauge(graphics, menu.fluid(box), leftPos + GAUGE_X[box], topPos + GAUGE_Y, GAUGE_WIDTH, menu.fluidCapacity(box));
        }
        MachineScreens.energyBar(graphics, font, leftPos + 8, topPos + ENERGY_Y, ENERGY_WIDTH, menu.energy(), menu.energyCapacity());
    }

    /** Over a gauge, the fluid's name and how much of the box it fills. */
    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractTooltip(graphics, mouseX, mouseY);
        for (int box = 0; box < FluidMachine.CHEMICAL_PLANT.boxes(); box++) {
            if (MachineScreens.overGauge(leftPos + GAUGE_X[box], topPos + GAUGE_Y, GAUGE_WIDTH, mouseX, mouseY)) {
                MachineScreens.fluidTooltip(graphics, font, menu.fluid(box), menu.fluidCapacity(box), mouseX, mouseY);
                return;
            }
        }
    }
}
