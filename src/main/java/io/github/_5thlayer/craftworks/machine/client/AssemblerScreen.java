// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.machine.client;

import java.util.Optional;

import io.github._5thlayer.craftworks.machine.AssemblerFluidBox;
import io.github._5thlayer.craftworks.machine.AssemblerGhosts;
import io.github._5thlayer.craftworks.machine.AssemblerMenu;
import io.github._5thlayer.craftworks.machine.AssemblerSlots;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The Assembler's screen: the Held recipe, its five inputs, the product and the remainders, how far the
 * craft is and the energy in the buffer; tiers 2 and 3 add the fluid box as a gauge beside the energy bar. A
 * stand-in drawn from fills (real art is 5thlayer/craftworks#22).
 *
 * <p>No recipe is picked here and none cleared; the recipe viewer's Fill Recipe is the only picker. The
 * Held recipe heads the screen as its product's icon and name, and is ghosted in the slots: each
 * ingredient in the input slot that takes it, with the count one craft needs, and the product in its slot.
 * A tag ingredient cycles through its members. An input holding less than one craft is red. What is ghosted,
 * and where, is {@link AssemblerGhosts}'s; {@link #ghostAt} is how the recipe viewers ask what is under the mouse.
 */
public final class AssemblerScreen extends AbstractContainerScreen<AssemblerMenu> {

    private static final int BAR_X = AssemblerMenu.INPUT_X + AssemblerSlots.INPUTS * 18 + 4;
    private static final int BAR_WIDTH = AssemblerMenu.PRODUCT_X - 6 - BAR_X;
    private static final int ENERGY_Y = 60;
    private static final int ENERGY_WIDTH = 160;
    private static final int ENERGY_HEIGHT = MachineScreens.ENERGY_HEIGHT;
    // With a fluid box the energy bar gives up its right end to the gauge, the same height.
    private static final int ENERGY_WIDTH_WITH_FLUID = 104;
    private static final int FLUID_X = 8 + ENERGY_WIDTH_WITH_FLUID + 4;
    private static final int FLUID_WIDTH = 8 + ENERGY_WIDTH - FLUID_X;

    public AssemblerScreen(AssemblerMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, 176, 166);
        inventoryLabelY = AssemblerMenu.INVENTORY_Y - 11;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        // The world dims behind the panel as it does behind the inventory.
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, MachineScreens.PANEL);
        Optional<AssemblerMenu.Held> held = menu.held();
        held.filter(recipe -> !recipe.product().isEmpty()).ifPresent(recipe -> {
            ItemStack product = recipe.product();
            graphics.item(product, leftPos + AssemblerGhosts.HEAD_X, topPos + AssemblerGhosts.HEAD_Y);
            graphics.text(font, product.getHoverName(), leftPos + 28, topPos + 20, MachineScreens.TEXT, false);
        });
        for (Slot slot : menu.slots) {
            int x = leftPos + slot.x;
            int y = topPos + slot.y;
            MachineScreens.recess(graphics, x, y, 16, 16);
            // The player's inventory follows the machine's slots, and takes only its recess.
            if (slot.index >= AssemblerSlots.SIZE) {
                continue;
            }
            if (AssemblerSlots.isInput(slot.index) && menu.isShort(slot.index)) {
                graphics.fill(x, y, x + 16, y + 16, MachineScreens.SHORT);
            }
            ghost(graphics, x, y);
        }
        int x = leftPos + BAR_X;
        int y = topPos + AssemblerMenu.INPUT_Y;
        MachineScreens.recess(graphics, x, y, BAR_WIDTH, 16);
        graphics.fill(x, y, x + Math.round(BAR_WIDTH * menu.progress()), y + 16, MachineScreens.BAR);

        int energyX = leftPos + 8;
        int energyY = topPos + ENERGY_Y;
        int energyWidth = menu.hasFluidBox() ? ENERGY_WIDTH_WITH_FLUID : ENERGY_WIDTH;
        MachineScreens.energyBar(graphics, font, energyX, energyY, energyWidth, menu.energy(), menu.capacity());

        if (menu.hasFluidBox()) {
            MachineScreens.fluidGauge(graphics, menu.fluid(), leftPos + FLUID_X, energyY, FLUID_WIDTH, AssemblerFluidBox.CAPACITY);
        }
    }

    /** Over the gauge, the fluid's name and how much of the box it fills. */
    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractTooltip(graphics, mouseX, mouseY);
        if (menu.hasFluidBox() && MachineScreens.overGauge(leftPos + FLUID_X, topPos + ENERGY_Y, FLUID_WIDTH, mouseX, mouseY)) {
            MachineScreens.fluidTooltip(graphics, font, menu.fluid(), AssemblerFluidBox.CAPACITY, mouseX, mouseY);
        }
    }

    /**
     * The ghost under the mouse, or empty over a real stack, an empty slot with nothing to ghost, and the
     * rest of the panel: what the slots and the head show, and what EMI's and JEI's shortcuts ask of it.
     */
    public Optional<AssemblerGhosts.Ghost> ghostAt(double mouseX, double mouseY) {
        return AssemblerGhosts.at(menu, leftPos, topPos, (int) Math.floor(mouseX), (int) Math.floor(mouseY), Util.getMillis());
    }

    /** What an empty slot would take under the Held recipe: the ingredient there, or the product. */
    private void ghost(GuiGraphicsExtractor graphics, int x, int y) {
        ghostAt(x, y).ifPresent(shown -> MachineScreens.ghost(graphics, font, shown.stack(), x, y));
    }
}
