// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.machine.client;

import io.github._5thlayer.craftworks.machine.AssemblerMenu;
import io.github._5thlayer.craftworks.machine.FluidGaugeLayout;
import io.github._5thlayer.craftworks.machine.FluidLayout;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * The Assembler's screen: a {@link HeldMachineScreen} over five inputs, the product and the remainders, with
 * the energy in the buffer below, and a gauge for each fluid box (the inputs, then the outputs, as
 * {@link FluidGaugeLayout} lays them across) between the slots and the energy bar.
 */
public final class AssemblerScreen extends HeldMachineScreen<AssemblerMenu> {

    private static final int GAUGE_Y = 57;
    private static final int ENERGY_Y = 72;
    private static final int ENERGY_WIDTH = 160;

    private final FluidGaugeLayout gauges = FluidGaugeLayout.of(FluidLayout.ASSEMBLER);

    public AssemblerScreen(AssemblerMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, 176, menu.inventoryY() + 82, menu.inventoryY());
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        for (int box = 0; box < FluidLayout.ASSEMBLER.boxes(); box++) {
            MachineScreens.fluidGauge(graphics, menu.fluid(box), leftPos + gauges.x(box), topPos + GAUGE_Y, gauges.width(), menu.fluidCapacity(box));
        }
        MachineScreens.energyBar(graphics, font, leftPos + 8, topPos + ENERGY_Y, ENERGY_WIDTH, menu.energy(), menu.energyCapacity());
    }

    /** Over a gauge, the fluid's name and how much of the box it fills. */
    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractTooltip(graphics, mouseX, mouseY);
        for (int box = 0; box < FluidLayout.ASSEMBLER.boxes(); box++) {
            if (MachineScreens.overGauge(leftPos + gauges.x(box), topPos + GAUGE_Y, gauges.width(), mouseX, mouseY)) {
                MachineScreens.fluidTooltip(graphics, font, menu.fluid(box), menu.fluidCapacity(box), mouseX, mouseY);
                return;
            }
        }
    }
}
