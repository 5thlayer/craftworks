// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.machine.client;

import java.util.Optional;

import io.github._5thlayer.craftworks.machine.AssemblerGhosts;
import io.github._5thlayer.craftworks.machine.ChemicalPlantFluids;
import io.github._5thlayer.craftworks.machine.ChemicalPlantMenu;
import io.github._5thlayer.craftworks.machine.ChemicalPlantSlots;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The Chemical Plant's screen: the Held recipe, its two inputs and the product, how far the craft is, a gauge for
 * each of its four fluid boxes (the inputs, then the outputs) and the energy in the buffer. A stand-in drawn from
 * fills (real art is 5thlayer/craftworks#22), laid out as the Assembler's is and ghosted the same way, over
 * {@link AssemblerGhosts}; {@link #ghostAt} is how the recipe viewers ask what is under the mouse.
 *
 * <p>No recipe is picked here and none cleared; the recipe viewer's Fill Recipe is the only picker.
 */
public final class ChemicalPlantScreen extends AbstractContainerScreen<ChemicalPlantMenu> {

    private static final int BAR_X = ChemicalPlantMenu.INPUT_X + ChemicalPlantSlots.INPUTS * 18 + 4;
    private static final int BAR_WIDTH = ChemicalPlantMenu.PRODUCT_X - 6 - BAR_X;
    private static final int GAUGE_Y = 57;
    private static final int GAUGE_WIDTH = 36;
    /** The two input gauges, then the two outputs, a little apart. */
    private static final int[] GAUGE_X = {8, 48, 92, 132};
    private static final int ENERGY_Y = 72;
    private static final int ENERGY_WIDTH = 160;

    public ChemicalPlantScreen(ChemicalPlantMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, 176, ChemicalPlantMenu.INVENTORY_Y + 82);
        inventoryLabelY = ChemicalPlantMenu.INVENTORY_Y - 11;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        // The world dims behind the panel as it does behind the inventory.
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, MachineScreens.PANEL);
        menu.held().filter(recipe -> !recipe.product().isEmpty()).ifPresent(recipe -> {
            ItemStack product = recipe.product();
            graphics.item(product, leftPos + AssemblerGhosts.HEAD_X, topPos + AssemblerGhosts.HEAD_Y);
            graphics.text(font, product.getHoverName(), leftPos + 28, topPos + 20, MachineScreens.TEXT, false);
        });
        for (Slot slot : menu.slots) {
            int x = leftPos + slot.x;
            int y = topPos + slot.y;
            MachineScreens.recess(graphics, x, y, 16, 16);
            // The player's inventory follows the machine's slots, and takes only its recess.
            if (slot.index >= ChemicalPlantSlots.SIZE) {
                continue;
            }
            if (slot.index < ChemicalPlantSlots.INPUTS && menu.isShort(slot.index)) {
                graphics.fill(x, y, x + 16, y + 16, MachineScreens.SHORT);
            }
            ghostAt(x, y).ifPresent(shown -> MachineScreens.ghost(graphics, font, shown.stack(), x, y));
        }
        int x = leftPos + BAR_X;
        int y = topPos + ChemicalPlantMenu.INPUT_Y;
        MachineScreens.recess(graphics, x, y, BAR_WIDTH, 16);
        graphics.fill(x, y, x + Math.round(BAR_WIDTH * menu.progress()), y + 16, MachineScreens.BAR);

        for (int box = 0; box < ChemicalPlantFluids.SIZE; box++) {
            MachineScreens.fluidGauge(graphics, menu.fluid(box), leftPos + GAUGE_X[box], topPos + GAUGE_Y, GAUGE_WIDTH, menu.fluidCapacity(box));
        }
        MachineScreens.energyBar(graphics, font, leftPos + 8, topPos + ENERGY_Y, ENERGY_WIDTH, menu.energy(), menu.energyCapacity());
    }

    /** Over a gauge, the fluid's name and how much of the box it fills. */
    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractTooltip(graphics, mouseX, mouseY);
        for (int box = 0; box < ChemicalPlantFluids.SIZE; box++) {
            if (MachineScreens.overGauge(leftPos + GAUGE_X[box], topPos + GAUGE_Y, GAUGE_WIDTH, mouseX, mouseY)) {
                MachineScreens.fluidTooltip(graphics, font, menu.fluid(box), menu.fluidCapacity(box), mouseX, mouseY);
                return;
            }
        }
    }

    /**
     * The ghost under the mouse, or empty over a real stack, an empty slot with nothing to ghost, and the
     * rest of the panel: what the slots and the head show, and what EMI's and JEI's shortcuts ask of it.
     */
    public Optional<AssemblerGhosts.Ghost> ghostAt(double mouseX, double mouseY) {
        return AssemblerGhosts.at(menu.held(), menu.slots, AssemblerGhosts.Layout.CHEMICAL_PLANT, leftPos, topPos,
                (int) Math.floor(mouseX), (int) Math.floor(mouseY), Util.getMillis());
    }
}
