// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.machine.client;

import java.text.NumberFormat;
import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * What the screens of the machines that craft draw, an Assembler's: the grey panel's
 * bevelled slots, the energy bar, a fluid gauge and its tooltip, and an empty slot's ghost. A stand-in drawn
 * from fills (real art is 5thlayer/craftworks#22).
 */
final class MachineScreens {

    static final int PANEL = 0xFFC6C6C6;
    static final int BAR = 0xFF5DA05D;
    static final int TEXT = 0xFF404040;
    static final int SHORT = 0xFFB84C4C;

    private static final int SLOT = 0xFF8B8B8B;
    private static final int SLOT_DARK = 0xFF373737;
    private static final int SLOT_LIGHT = 0xFFFFFFFF;
    private static final int ENERGY = 0xFFB8392B;
    // Half the slot's own colour over a ghost, so it reads as a placeholder rather than an item.
    private static final int GHOST_VEIL = 0x808B8B8B;
    private static final int FLUID_TILE = 16;

    static final int ENERGY_HEIGHT = 12;

    private MachineScreens() {
    }

    /** A slot's bevel: dark above and left, light below and right, grey inside. */
    static void recess(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        graphics.fill(x - 1, y - 1, x + width, y, SLOT_DARK);
        graphics.fill(x - 1, y, x, y + height, SLOT_DARK);
        graphics.fill(x, y + height, x + width + 1, y + height + 1, SLOT_LIGHT);
        graphics.fill(x + width, y, x + width + 1, y + height, SLOT_LIGHT);
        graphics.fill(x, y, x + width, y + height, SLOT);
    }

    /** The energy bar, in its recess, with how much of the buffer it holds written over it. */
    static void energyBar(GuiGraphicsExtractor graphics, Font font, int x, int y, int width, int energy, int capacity) {
        recess(graphics, x, y, width, ENERGY_HEIGHT);
        float charge = capacity <= 0 ? 0f : Math.min(1f, (float) energy / capacity);
        graphics.fill(x, y, x + Math.round(width * charge), y + ENERGY_HEIGHT, ENERGY);
        Component stored = Component.translatable("craftworks.machine.energy", energy, capacity);
        graphics.text(font, stored, x + (width - font.width(stored)) / 2, y + 2, 0xFFFFFFFF, true);
    }

    /**
     * A fluid gauge in its recess: the fluid's still texture, tinted as the fluid is in the world, tiled over the
     * part of the gauge the box fills.
     */
    static void fluidGauge(GuiGraphicsExtractor graphics, FluidStack fluid, int x, int y, int width, int capacity) {
        recess(graphics, x, y, width, ENERGY_HEIGHT);
        if (fluid.isEmpty()) {
            return;
        }
        FluidModel model = Minecraft.getInstance().getModelManager().getFluidStateModelSet().get(fluid.getFluid().defaultFluidState());
        int tint = model.fluidTintSource() == null ? 0xFFFFFFFF : 0xFF000000 | model.fluidTintSource().color(fluid.getFluid().defaultFluidState());
        int filled = Math.min(width, Math.round(width * (float) fluid.getAmount() / Math.max(1, capacity)));
        graphics.enableScissor(x, y, x + filled, y + ENERGY_HEIGHT);
        for (int tileX = x; tileX < x + filled; tileX += FLUID_TILE) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, model.stillMaterial().sprite(), tileX, y, FLUID_TILE, FLUID_TILE, tint);
        }
        graphics.disableScissor();
    }

    /** Whether ({@code mouseX}, {@code mouseY}) is over a gauge at ({@code x}, {@code y}) {@code width} wide. */
    static boolean overGauge(int x, int y, int width, int mouseX, int mouseY) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + ENERGY_HEIGHT;
    }

    /** Over a gauge, the fluid's name and how much of the box it fills. */
    static void fluidTooltip(GuiGraphicsExtractor graphics, Font font, FluidStack fluid, int capacity, int mouseX, int mouseY) {
        NumberFormat number = NumberFormat.getIntegerInstance();
        graphics.setComponentTooltipForNextFrame(font, List.of(
                fluid.isEmpty() ? Component.translatable("craftworks.machine.fluid_empty").withStyle(ChatFormatting.GRAY) : fluid.getHoverName(),
                Component.translatable("craftworks.machine.fluid", number.format(fluid.getAmount()), number.format(capacity))
                        .withStyle(ChatFormatting.GRAY)), mouseX, mouseY);
    }

    /** A slot's ghost: the item it would take, veiled, with the count a craft needs when it is more than one. */
    static void ghost(GuiGraphicsExtractor graphics, Font font, ItemStack ghost, int x, int y) {
        graphics.item(ghost, x, y);
        graphics.fill(x, y, x + 16, y + 16, GHOST_VEIL);
        if (ghost.getCount() > 1) {
            String count = Integer.toString(ghost.getCount());
            graphics.text(font, count, x + 17 - font.width(count), y + 9, 0xFFFFFFFF, true);
        }
    }
}
