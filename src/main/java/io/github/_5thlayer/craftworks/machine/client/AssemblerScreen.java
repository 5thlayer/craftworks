// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.machine.client;

import java.util.Optional;

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
 * craft is and the energy in the buffer. A stand-in drawn from fills (real art is 5thlayer/craftworks#22).
 *
 * <p>No recipe is picked here and none cleared; the recipe viewer's Fill Recipe is the only picker. The
 * Held recipe heads the screen as its product's icon and name, and is ghosted in the slots: each
 * ingredient in the input slot that takes it, with the count one craft needs, and the product in its slot.
 * A tag ingredient cycles through its members. An input holding less than one craft is red. What is ghosted,
 * and where, is {@link AssemblerGhosts}'s; {@link #ghostAt} is how the recipe viewers ask what is under the mouse.
 */
public final class AssemblerScreen extends AbstractContainerScreen<AssemblerMenu> {

    private static final int PANEL = 0xFFC6C6C6;
    private static final int SLOT = 0xFF8B8B8B;
    private static final int SLOT_DARK = 0xFF373737;
    private static final int SLOT_LIGHT = 0xFFFFFFFF;
    private static final int BAR = 0xFF5DA05D;
    private static final int ENERGY = 0xFFB8392B;
    private static final int TEXT = 0xFF404040;
    private static final int SHORT = 0xFFB84C4C;
    // Half the slot's own colour over a ghost, so it reads as a placeholder rather than an item.
    private static final int GHOST_VEIL = 0x808B8B8B;

    private static final int BAR_X = AssemblerMenu.INPUT_X + AssemblerSlots.INPUTS * 18 + 4;
    private static final int BAR_WIDTH = AssemblerMenu.PRODUCT_X - 6 - BAR_X;
    private static final int ENERGY_Y = 60;
    private static final int ENERGY_WIDTH = 160;
    private static final int ENERGY_HEIGHT = 12;

    public AssemblerScreen(AssemblerMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, 176, 166);
        inventoryLabelY = AssemblerMenu.INVENTORY_Y - 11;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        // The world dims behind the panel as it does behind the inventory.
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, PANEL);
        Optional<AssemblerMenu.Held> held = menu.held();
        held.filter(recipe -> !recipe.product().isEmpty()).ifPresent(recipe -> {
            ItemStack product = recipe.product();
            graphics.item(product, leftPos + AssemblerGhosts.HEAD_X, topPos + AssemblerGhosts.HEAD_Y);
            graphics.text(font, product.getHoverName(), leftPos + 28, topPos + 20, TEXT, false);
        });
        for (Slot slot : menu.slots) {
            int x = leftPos + slot.x;
            int y = topPos + slot.y;
            recess(graphics, x, y, 16, 16);
            // The player's inventory follows the machine's slots, and takes only its recess.
            if (slot.index >= AssemblerSlots.SIZE) {
                continue;
            }
            if (AssemblerSlots.isInput(slot.index) && menu.isShort(slot.index)) {
                graphics.fill(x, y, x + 16, y + 16, SHORT);
            }
            ghost(graphics, x, y);
        }
        int x = leftPos + BAR_X;
        int y = topPos + AssemblerMenu.INPUT_Y;
        recess(graphics, x, y, BAR_WIDTH, 16);
        graphics.fill(x, y, x + Math.round(BAR_WIDTH * menu.progress()), y + 16, BAR);

        int energyX = leftPos + 8;
        int energyY = topPos + ENERGY_Y;
        recess(graphics, energyX, energyY, ENERGY_WIDTH, ENERGY_HEIGHT);
        float charge = menu.capacity() <= 0 ? 0f : Math.min(1f, (float) menu.energy() / menu.capacity());
        graphics.fill(energyX, energyY, energyX + Math.round(ENERGY_WIDTH * charge), energyY + ENERGY_HEIGHT, ENERGY);
        Component stored = Component.translatable("craftworks.assembler.energy", menu.energy(), menu.capacity());
        graphics.text(font, stored, energyX + (ENERGY_WIDTH - font.width(stored)) / 2, energyY + 2, 0xFFFFFFFF, true);
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
        Optional<AssemblerGhosts.Ghost> shown = ghostAt(x, y);
        if (shown.isEmpty()) {
            return;
        }
        ItemStack ghost = shown.get().stack();
        graphics.item(ghost, x, y);
        graphics.fill(x, y, x + 16, y + 16, GHOST_VEIL);
        if (ghost.getCount() > 1) {
            String count = Integer.toString(ghost.getCount());
            graphics.text(font, count, x + 17 - font.width(count), y + 9, 0xFFFFFFFF, true);
        }
    }

    /** A slot's bevel: dark above and left, light below and right, grey inside. */
    private static void recess(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        graphics.fill(x - 1, y - 1, x + width, y, SLOT_DARK);
        graphics.fill(x - 1, y, x, y + height, SLOT_DARK);
        graphics.fill(x, y + height, x + width + 1, y + height + 1, SLOT_LIGHT);
        graphics.fill(x + width, y, x + width + 1, y + height, SLOT_LIGHT);
        graphics.fill(x, y, x + width, y + height, SLOT);
    }
}
