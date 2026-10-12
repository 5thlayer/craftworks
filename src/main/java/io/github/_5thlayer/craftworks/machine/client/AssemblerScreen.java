// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.machine.client;

import java.util.Optional;

import io.github._5thlayer.craftworks.machine.AssemblerMenu;
import io.github._5thlayer.craftworks.machine.FluidGaugeLayout;
import io.github._5thlayer.craftworks.machine.FluidLayout;
import io.github._5thlayer.craftworks.machine.MachineGhosts;
import io.github._5thlayer.craftworks.machine.MachineState;
import io.github._5thlayer.libworks.ghost.Ghost;
import io.github._5thlayer.libworks.ghost.GhostScreen;
import io.github._5thlayer.libworks.ghost.Ghosts;
import io.github._5thlayer.libworks.ghost.client.GhostDraw;
import io.github._5thlayer.libworks.machine.client.MachineDraw;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/**
 * The Assembler's screen: the Held recipe at its head as its product's icon and name, its five inputs, its four
 * shared outputs and, on tiers 2 and 3, the drain and fill slots under the input gauges, then rows laid out as the suite's other machines lay theirs (#55): how far the craft
 * is, a gauge for each fluid box (the inputs down the left and the outputs down the right, as
 * {@link FluidGaugeLayout} lays them), the energy in the buffer and what the Assembler is doing. A stand-in drawn
 * from fills with libworks-runtime's {@link MachineDraw}.
 *
 * <p>No recipe is picked here and none cleared; the recipe viewer's Fill Recipe is the only picker. The Held
 * recipe is ghosted in the slots: each ingredient in the input slot that takes it, with the count one craft
 * needs, and the product in its slot. A tag ingredient cycles through its members. An input holding less than
 * one craft is red. What is ghosted, and where, is {@link MachineGhosts}'s; {@link #ghostAt} is how the recipe
 * viewers ask what is under the mouse: EMI through libworks-runtime, which this screen is a {@link GhostScreen} for.
 */
public final class AssemblerScreen extends AbstractContainerScreen<AssemblerMenu> implements GhostScreen {

    private static final int PROGRESS = 0xFF5DA05D;
    private static final int ENERGY = 0xFFB8392B;
    private static final int SHORT = 0xFFB84C4C;
    /** The room a bar keeps either side of its label, as {@link MachineDraw#bar} does. */
    private static final int LABEL_PADDING = 4;
    private static final int BAR_WIDTH = 160;
    /** Where the Held recipe's name starts, after its product's icon at the head. */
    private static final int HEAD_TEXT_X = 28;
    /** Nothing ghosts through {@link MachineDraw#panel}: an input's red goes between the recesses and the ghosts. */
    private static final Ghosts NO_GHOSTS = new Ghosts();

    private final FluidGaugeLayout gauges = FluidGaugeLayout.of(FluidLayout.ASSEMBLER);

    /** What the empty slots and the head show, and what EMI's and JEI's shortcuts ask of. */
    private final MachineGhosts.Screen ghosts;

    public AssemblerScreen(AssemblerMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, 176, AssemblerMenu.INVENTORY_Y + 82);
        inventoryLabelY = AssemblerMenu.INVENTORY_Y - 11;
        ghosts = MachineGhosts.forAssembler(menu, () -> leftPos, () -> topPos);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        // The world dims behind the panel as it does behind the inventory.
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        MachineDraw.panel(graphics, font, leftPos, topPos, imageWidth, imageHeight, menu.slots, NO_GHOSTS, 0);
        menu.held().filter(recipe -> !recipe.product().isEmpty()).ifPresent(recipe ->
                graphics.item(recipe.product(), leftPos + MachineGhosts.HEAD_X, topPos + MachineGhosts.HEAD_Y));
        for (int index = 0; index < menu.layout().size(); index++) {
            Slot slot = menu.slots.get(index);
            if (menu.layout().isInput(index) && menu.isShort(index)) {
                int x = leftPos + slot.x;
                int y = topPos + slot.y;
                graphics.fill(x, y, x + MachineDraw.SLOT_SIZE, y + MachineDraw.SLOT_SIZE, SHORT);
            }
        }
        // The slots' ghosts, over their recesses and an input's red; the head draws its own product above.
        for (Ghost ghost : ghosts.slots().shown(Util.getMillis())) {
            GhostDraw.draw(graphics, font, ghost);
        }
        bar(graphics, AssemblerMenu.PROGRESS_Y, menu.progress(), PROGRESS, progressLine());
        for (int box = 0; box < FluidLayout.ASSEMBLER.boxes(); box++) {
            MachineDraw.gauge(graphics, font, leftPos + gauges.x(box), topPos + gaugeY(box), gauges.width(), menu.fluid(box), menu.fluidCapacity(box));
        }
        bar(graphics, AssemblerMenu.ENERGY_Y, charge(), ENERGY, energyLine());
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractLabels(graphics, mouseX, mouseY);
        menu.held().filter(recipe -> !recipe.product().isEmpty()).ifPresent(recipe -> graphics.text(font,
                MachineDraw.fitted(font, recipe.product().getHoverName(), headWidth()), HEAD_TEXT_X, MachineGhosts.HEAD_Y + 4, MachineDraw.TEXT, false));
        graphics.text(font, MachineDraw.fitted(font, stateLine(), stateWidth()), MachineDraw.MARGIN, AssemblerMenu.STATUS_Y, MachineDraw.TEXT, false);
    }

    /** The slots' ghosts, the ones drawn veiled; the head's only answers {@link #ghostAt}. */
    @Override
    public Ghosts ghosts() {
        return ghosts.slots();
    }

    /**
     * The ghost under the mouse, or empty over a real stack, an empty slot with nothing to ghost, and the
     * rest of the panel: what the slots and the head show, and what EMI's and JEI's shortcuts ask of it. The head's
     * is beyond {@link #ghosts()}, so this finds it too.
     */
    @Override
    public Optional<Ghost> ghostAt(double mouseX, double mouseY) {
        return ghosts.at(mouseX, mouseY, Util.getMillis());
    }

    /**
     * Over a ghost, its item's tooltip, as over a real stack; over a gauge, the fluid's name and how much of the box it
     * fills; over a bar or a line cut short, the whole of it.
     */
    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractTooltip(graphics, mouseX, mouseY);
        Optional<Ghost> ghost = ghostAt(mouseX, mouseY);
        if (ghost.isPresent()) {
            GhostDraw.tooltip(graphics, font, ghost.get(), mouseX, mouseY);
            return;
        }
        int x = mouseX - leftPos;
        int y = mouseY - topPos;
        for (int box = 0; box < FluidLayout.ASSEMBLER.boxes(); box++) {
            if (MachineDraw.over(gauges.x(box), gaugeY(box), gauges.width(), MachineDraw.BAR_HEIGHT, x, y)) {
                MachineDraw.fluidTooltip(graphics, font, menu.fluid(box), menu.fluidCapacity(box), mouseX, mouseY);
                return;
            }
        }
        if (overBar(AssemblerMenu.ENERGY_Y, x, y)) {
            MachineDraw.wholeOnHover(graphics, font, energyLine(), BAR_WIDTH - LABEL_PADDING, mouseX, mouseY);
        } else if (MachineDraw.over(MachineDraw.MARGIN, AssemblerMenu.STATUS_Y, stateWidth(), font.lineHeight, x, y)) {
            MachineDraw.wholeOnHover(graphics, font, stateLine(), stateWidth(), mouseX, mouseY);
        } else if (MachineDraw.over(HEAD_TEXT_X, MachineGhosts.HEAD_Y, headWidth(), MachineDraw.SLOT_SIZE, x, y)) {
            menu.held().filter(recipe -> !recipe.product().isEmpty()).ifPresent(recipe ->
                    MachineDraw.wholeOnHover(graphics, font, recipe.product().getHoverName(), headWidth(), mouseX, mouseY));
        }
    }

    private int gaugeY(int box) {
        return AssemblerMenu.GAUGES_Y + gauges.row(box) * AssemblerMenu.ROW;
    }

    /** The name's room: up to the outputs. */
    private int headWidth() {
        return AssemblerMenu.OUTPUT_X - LABEL_PADDING - HEAD_TEXT_X;
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

    /** What the Assembler is doing, as its Jade tooltip words it. */
    private Component stateLine() {
        return menu.state() == MachineState.NO_RECIPE
                ? Component.translatable("craftworks.jade.no_held_recipe")
                : Component.translatable(menu.state().langKey());
    }

    /** A full-width bar at {@code y} from the panel's corner. */
    private void bar(GuiGraphicsExtractor graphics, int y, float fraction, int colour, Component label) {
        MachineDraw.bar(graphics, font, leftPos + MachineDraw.MARGIN, topPos + y, BAR_WIDTH, MachineDraw.BAR_HEIGHT, fraction, colour, label);
    }

    private static boolean overBar(int barY, int x, int y) {
        return MachineDraw.over(MachineDraw.MARGIN, barY, BAR_WIDTH, MachineDraw.BAR_HEIGHT, x, y);
    }
}
