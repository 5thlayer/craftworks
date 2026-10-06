// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.machine.client;

import io.github._5thlayer.craftworks.machine.FluidGaugeLayout;
import io.github._5thlayer.craftworks.machine.FluidMachineMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * A fluid machine's screen, the Chemical Plant's or the Oil Refinery's: a {@link HeldMachineScreen} over the
 * machine's item slots, which it shows only if it has any, with a gauge for each of its fluid boxes (the inputs,
 * then the outputs, as {@link FluidGaugeLayout} lays them across) and the energy in the buffer below.
 */
public final class FluidMachineScreen extends HeldMachineScreen<FluidMachineMenu> {

    private static final int GAUGE_Y = 57;
    private static final int ENERGY_Y = 72;
    private static final int ENERGY_WIDTH = 160;

    private final FluidGaugeLayout gauges;

    public FluidMachineScreen(FluidMachineMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, 176, FluidMachineMenu.INVENTORY_Y + 82, FluidMachineMenu.INVENTORY_Y);
        gauges = FluidGaugeLayout.of(menu.description());
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        for (int box = 0; box < menu.description().boxes(); box++) {
            MachineScreens.fluidGauge(graphics, menu.fluid(box), leftPos + gauges.x(box), topPos + GAUGE_Y, gauges.width(), menu.fluidCapacity(box));
        }
        MachineScreens.energyBar(graphics, font, leftPos + 8, topPos + ENERGY_Y, ENERGY_WIDTH, menu.energy(), menu.energyCapacity());
    }

    /** Over a gauge, the fluid's name and how much of the box it fills. */
    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractTooltip(graphics, mouseX, mouseY);
        for (int box = 0; box < menu.description().boxes(); box++) {
            if (MachineScreens.overGauge(leftPos + gauges.x(box), topPos + GAUGE_Y, gauges.width(), mouseX, mouseY)) {
                MachineScreens.fluidTooltip(graphics, font, menu.fluid(box), menu.fluidCapacity(box), mouseX, mouseY);
                return;
            }
        }
    }
}
