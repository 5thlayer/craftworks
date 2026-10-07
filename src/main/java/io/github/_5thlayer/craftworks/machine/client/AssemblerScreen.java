// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.machine.client;

import java.util.Optional;

import io.github._5thlayer.craftworks.machine.AssemblerMenu;
import io.github._5thlayer.craftworks.machine.FluidGaugeLayout;
import io.github._5thlayer.craftworks.machine.FluidLayout;
import io.github._5thlayer.craftworks.machine.MachineGhosts;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The Assembler's screen: the Held recipe at its head as its product's icon and name, its five inputs, the
 * product and the remainders and how far the craft is, with a gauge for each fluid box (the inputs, then the
 * outputs, as {@link FluidGaugeLayout} lays them across) and the energy in the buffer below. A stand-in drawn
 * from fills (real art is 5thlayer/craftworks#22).
 *
 * <p>No recipe is picked here and none cleared; the recipe viewer's Fill Recipe is the only picker. The Held
 * recipe is ghosted in the slots: each ingredient in the input slot that takes it, with the count one craft
 * needs, and the product in its slot. A tag ingredient cycles through its members. An input holding less than
 * one craft is red. What is ghosted, and where, is {@link MachineGhosts}'s; {@link #ghostAt} is how the recipe
 * viewers ask what is under the mouse.
 */
public final class AssemblerScreen extends AbstractContainerScreen<AssemblerMenu> {

    private static final int GAUGE_Y = 57;
    private static final int ENERGY_Y = 72;
    private static final int ENERGY_WIDTH = 160;

    private final FluidGaugeLayout gauges = FluidGaugeLayout.of(FluidLayout.ASSEMBLER);

    /** The progress bar, between the last input and the product. */
    private final int barX;
    private final int barWidth;

    public AssemblerScreen(AssemblerMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, 176, menu.inventoryY() + 82);
        inventoryLabelY = menu.inventoryY() - 11;
        barX = AssemblerMenu.INPUT_X + menu.layout().inputs() * 18 + 4;
        barWidth = AssemblerMenu.PRODUCT_X - 6 - barX;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        // The world dims behind the panel as it does behind the inventory.
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, MachineScreens.PANEL);
        menu.held().filter(recipe -> !recipe.product().isEmpty()).ifPresent(recipe -> {
            ItemStack product = recipe.product();
            graphics.item(product, leftPos + MachineGhosts.HEAD_X, topPos + MachineGhosts.HEAD_Y);
            graphics.text(font, product.getHoverName(), leftPos + 28, topPos + 20, MachineScreens.TEXT, false);
        });
        for (Slot slot : menu.slots) {
            int x = leftPos + slot.x;
            int y = topPos + slot.y;
            MachineScreens.recess(graphics, x, y, 16, 16);
            // The player's inventory follows the Assembler's slots, and takes only its recess.
            if (slot.index >= menu.layout().size()) {
                continue;
            }
            if (menu.layout().isInput(slot.index) && menu.isShort(slot.index)) {
                graphics.fill(x, y, x + 16, y + 16, MachineScreens.SHORT);
            }
            ghostAt(x, y).ifPresent(shown -> MachineScreens.ghost(graphics, font, shown.stack(), x, y));
        }
        int x = leftPos + barX;
        int y = topPos + AssemblerMenu.INPUT_Y;
        MachineScreens.recess(graphics, x, y, barWidth, 16);
        graphics.fill(x, y, x + Math.round(barWidth * menu.progress()), y + 16, MachineScreens.BAR);
        for (int box = 0; box < FluidLayout.ASSEMBLER.boxes(); box++) {
            MachineScreens.fluidGauge(graphics, menu.fluid(box), leftPos + gauges.x(box), topPos + GAUGE_Y, gauges.width(), menu.fluidCapacity(box));
        }
        MachineScreens.energyBar(graphics, font, leftPos + 8, topPos + ENERGY_Y, ENERGY_WIDTH, menu.energy(), menu.energyCapacity());
    }

    /**
     * The ghost under the mouse, or empty over a real stack, an empty slot with nothing to ghost, and the
     * rest of the panel: what the slots and the head show, and what EMI's and JEI's shortcuts ask of it.
     */
    public Optional<MachineGhosts.Ghost> ghostAt(double mouseX, double mouseY) {
        return MachineGhosts.at(menu, leftPos, topPos, (int) Math.floor(mouseX), (int) Math.floor(mouseY), Util.getMillis());
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
