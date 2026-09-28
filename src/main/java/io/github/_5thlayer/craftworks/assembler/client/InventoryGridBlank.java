// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.assembler.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.inventory.RecipeBookType;
import net.neoforged.neoforge.client.event.ContainerScreenEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

/**
 * The client half of removing the 2x2 crafting grid (ADR-0007): the part of the grid that is a picture.
 *
 * <p>{@code InventoryMenuMixin} takes the slots and the recipe, which is what makes the grid unable
 * to craft. Two things it cannot reach survive it, and both are here:
 *
 * <ul>
 *   <li><b>The grid is painted on {@code inventory.png}</b> -- four slot wells, an arrow and a result
 *       well -- so with the slots inactive the player is left looking at an empty crafting grid
 *       rather than at no crafting grid. It is covered with the panel's own grey, together with the
 *       {@code container.crafting} title above it, which names a surface that no longer exists. The
 *       queue is drawn over the grey ({@link InventoryQueue}).
 *   <li><b>The recipe book button</b> would still open a book of grid recipes. Clicking one now
 *       crafts nothing, but it moves items into invisible slots that hand them back on close, which
 *       is a worse answer than the button not being there.
 * </ul>
 *
 * <p><b>Creative's own inventory tab is left alone, deliberately.</b> Its grid is as dead as anywhere,
 * since it re-adds the player menu's real slots; what survives is the picture, on a different texture
 * at coordinates this class would have to guess.
 *
 * <p><b>The button is identified by its rectangle</b>, because nothing else about it is reachable:
 * {@code ImageButton} keeps its sprite set private, the screen holds it in no field, and it carries
 * no id or message. Removing the first {@code ImageButton} would take another mod's button the day one
 * is added before it. The cost is that this fails silently in both directions: a vanilla layout change
 * makes the removal a no-op, and a foreign button placed at exactly that rectangle is removed instead.
 * It is a cosmetic guard on a screen whose grid already crafts nothing.
 *
 * <p>Drawn on {@code Render.Foreground} because that is the pass that runs inside the screen's own
 * translation -- the coordinates below are the vanilla menu's -- and after the slots. The carried stack
 * and every tooltip are drawn later still, so neither can be painted over.
 */
final class InventoryGridBlank {

    /** The result slot's own well, the arrow and the 2x2, plus the title line above them. RIGHT is exclusive: the
     * well's white highlight is column 170. */
    private static final int LEFT = 97;
    private static final int TOP = 5;
    private static final int RIGHT = 171;
    private static final int BOTTOM = 54;

    /** Vanilla's GUI panel grey, which is what the covered region would have been. */
    private static final int PANEL_GREY = 0xFFC6C6C6;

    /** {@code InventoryScreen.getRecipeBookButtonPosition}: {@code leftPos + 104, height / 2 - 22}, 20 by 18. */
    private static final int RECIPE_BUTTON_X = 104;
    private static final int RECIPE_BUTTON_Y_FROM_MIDDLE = -22;
    private static final int RECIPE_BUTTON_WIDTH = 20;
    private static final int RECIPE_BUTTON_HEIGHT = 18;

    private InventoryGridBlank() {
    }

    /**
     * Closes the crafting recipe book before the screen lays itself out.
     *
     * <p>{@code Init.Pre}, not {@code Post}, because {@code RecipeBookComponent.init} latches
     * visibility from the book's settings and the screen then positions itself around it. Closing it
     * here means the component is never visible, so nothing renders it, nothing routes clicks to it,
     * and {@code leftPos} is the un-shifted one every other hook on this screen assumes.
     *
     * <p>This writes the client's own copy of the setting on every open rather than telling the
     * server; the server's copy has no other reader.
     */
    static void onScreenOpening(ScreenEvent.Init.Pre event) {
        if (!(event.getScreen() instanceof InventoryScreen)) return;
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        player.getRecipeBook().setOpen(RecipeBookType.CRAFTING, false);
    }

    static void onScreenInit(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof InventoryScreen screen)) return;
        for (GuiEventListener listener : event.getListenersList()) {
            if (listener instanceof ImageButton button
                    && button.getX() == screen.getGuiLeft() + RECIPE_BUTTON_X
                    && button.getY() == screen.height / 2 + RECIPE_BUTTON_Y_FROM_MIDDLE
                    && button.getWidth() == RECIPE_BUTTON_WIDTH
                    && button.getHeight() == RECIPE_BUTTON_HEIGHT) {
                event.removeListener(button);
                return;
            }
        }
    }

    static void onScreenForeground(ContainerScreenEvent.Render.Foreground event) {
        if (!(event.getContainerScreen() instanceof InventoryScreen)) return;
        event.getGuiGraphics().fill(LEFT, TOP, RIGHT, BOTTOM, PANEL_GREY);
    }
}
