// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.machine.client;

import io.github._5thlayer.craftworks.machine.MachineState;
import io.github._5thlayer.craftworks.machine.RefinerMenu;
import io.github._5thlayer.libworks.ghost.Ghosts;
import io.github._5thlayer.libworks.machine.client.MachineDraw;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * The Refiner's screen, laid out as the suite's other machines lay theirs (#55): its input and output, then rows for
 * the smelt's progress, the energy in the buffer and what it is doing. A stand-in drawn from fills with
 * libworks-runtime's {@link MachineDraw}.
 */
public final class RefinerScreen extends AbstractContainerScreen<RefinerMenu> {

    private static final int PROGRESS = 0xFF5DA05D;
    private static final int ENERGY = 0xFFB8392B;
    /** The room a bar keeps either side of its label, as {@link MachineDraw#bar} does. */
    private static final int LABEL_PADDING = 4;
    private static final int BAR_WIDTH = 160;
    /** The Refiner ghosts nothing: any smeltable item goes in its input. */
    private static final Ghosts NO_GHOSTS = new Ghosts();

    public RefinerScreen(RefinerMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, 176, RefinerMenu.INVENTORY_Y + 82);
        inventoryLabelY = RefinerMenu.INVENTORY_Y - 11;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        MachineDraw.panel(graphics, font, leftPos, topPos, imageWidth, imageHeight, menu.slots, NO_GHOSTS, 0);
        bar(graphics, RefinerMenu.PROGRESS_Y, menu.progress(), PROGRESS, progressLine());
        bar(graphics, RefinerMenu.ENERGY_Y, charge(), ENERGY, energyLine());
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractLabels(graphics, mouseX, mouseY);
        graphics.text(font, MachineDraw.fitted(font, stateLine(), stateWidth()), MachineDraw.MARGIN, RefinerMenu.STATUS_Y, MachineDraw.TEXT, false);
    }

    /** Over the energy bar or the status line cut short, the whole of it. */
    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractTooltip(graphics, mouseX, mouseY);
        int x = mouseX - leftPos;
        int y = mouseY - topPos;
        if (MachineDraw.over(MachineDraw.MARGIN, RefinerMenu.ENERGY_Y, BAR_WIDTH, MachineDraw.BAR_HEIGHT, x, y)) {
            MachineDraw.wholeOnHover(graphics, font, energyLine(), BAR_WIDTH - LABEL_PADDING, mouseX, mouseY);
        } else if (MachineDraw.over(MachineDraw.MARGIN, RefinerMenu.STATUS_Y, stateWidth(), font.lineHeight, x, y)) {
            MachineDraw.wholeOnHover(graphics, font, stateLine(), stateWidth(), mouseX, mouseY);
        }
    }

    private int stateWidth() {
        return imageWidth - 2 * MachineDraw.MARGIN;
    }

    private float charge() {
        return menu.energyCapacity() <= 0 ? 0f : (float) menu.energy() / menu.energyCapacity();
    }

    private Component progressLine() {
        return Component.translatable("craftworks.machine.progress", Math.round(menu.progress() * 100));
    }

    private Component energyLine() {
        return Component.translatable("craftworks.machine.energy", menu.energy(), menu.energyCapacity());
    }

    /** What the Refiner is doing, as its Jade tooltip words it. */
    private Component stateLine() {
        return menu.state() == MachineState.NO_RECIPE
                ? Component.translatable("craftworks.machine.nothing_to_smelt")
                : Component.translatable(menu.state().langKey());
    }

    /** A full-width bar at {@code y} from the panel's corner. */
    private void bar(GuiGraphicsExtractor graphics, int y, float fraction, int colour, Component label) {
        MachineDraw.bar(graphics, font, leftPos + MachineDraw.MARGIN, topPos + y, BAR_WIDTH, MachineDraw.BAR_HEIGHT, fraction, colour, label);
    }
}
