// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.machine.client;

import io.github._5thlayer.libworks.machine.client.MachineDraw;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * What the screens of the machines that craft draw beyond libworks-runtime's shared look ({@link MachineDraw}):
 * their progress bar, the energy bar and an input's red when the Held recipe is short of it. A stand-in drawn
 * from fills (real art is 5thlayer/craftworks#22).
 */
final class MachineScreens {

    static final int SHORT = 0xFFB84C4C;

    private static final int PROGRESS = 0xFF5DA05D;
    private static final int ENERGY = 0xFFB8392B;
    /** The progress bar stands in the slots' row, as tall as a slot. */
    private static final int PROGRESS_HEIGHT = 16;

    private MachineScreens() {
    }

    /** How far the craft or smelt under way is, with no label. */
    static void progressBar(GuiGraphicsExtractor graphics, Font font, int x, int y, int width, float progress) {
        MachineDraw.bar(graphics, font, x, y, width, PROGRESS_HEIGHT, progress, PROGRESS, Component.empty());
    }

    /** The energy bar, with how much of the buffer it holds written over it. */
    static void energyBar(GuiGraphicsExtractor graphics, Font font, int x, int y, int width, int energy, int capacity) {
        float charge = capacity <= 0 ? 0f : (float) energy / capacity;
        MachineDraw.bar(graphics, font, x, y, width, MachineDraw.BAR_HEIGHT, charge, ENERGY,
                Component.translatable("craftworks.machine.energy", energy, capacity));
    }
}
