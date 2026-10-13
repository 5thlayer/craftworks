// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.assembler.client;

import io.github._5thlayer.craftworks.assembler.InventoryGrid;
import io.github._5thlayer.craftworks.assembler.client.GridCover.SlotAt;
import io.github._5thlayer.craftworks.mixin.minecraft.AbstractRecipeBookScreenAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.navigation.ScreenPosition;
import net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.inventory.AbstractCraftingMenu;
import net.minecraft.world.inventory.RecipeBookType;
import net.minecraft.world.inventory.Slot;
import net.neoforged.neoforge.client.event.ContainerScreenEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

/**
 * The client half of removing the 2x2 crafting grid (ADR-0007): the part of the grid that is a picture.
 *
 * <p>It acts on the inventory screen and on any other recipe book screen whose menu copies the
 * inventory's grid ({@link InventoryGrid#is2x2}: a crafting menu with a 2x2 grid), such as the
 * screen of a mod that adds slots beside the player's. Nothing here names such a mod; the crafting table,
 * a 3x3, is left alone.
 *
 * <p>{@code AbstractCraftingMenuMixin} takes the slots of every 2x2 grid, the inventory's and a copy's, and
 * {@code InventoryMenuMixin} the inventory's recipe, which is what makes the grid unable to craft. Two things
 * they cannot reach survive them, and both are here:
 *
 * <ul>
 *   <li><b>The grid is painted on the screen's texture</b> -- four slot wells, an arrow and a result
 *       well -- so with the slots inactive the player is left looking at an empty crafting grid
 *       rather than at no crafting grid. It is covered with the panel's own grey, together with the
 *       {@code container.crafting} title above it, which names a surface that no longer exists. The
 *       cover is {@link GridCover}'s, worked out from where the menu's own grid and result slots stand,
 *       so a screen that moves the grid is covered where it moved to. The queue is drawn over the grey
 *       ({@link InventoryQueue}).
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
 * no id or message. Its position is the screen's own answer, {@code getRecipeBookButtonPosition}, which
 * a copy of the inventory overrides to put it where it likes; the size is vanilla's, 20 by 18. Removing
 * the first {@code ImageButton} would take another mod's button the day one is added before it. The
 * cost is that this fails silently in both directions: a layout change makes the removal a no-op, and a
 * foreign button placed at exactly that rectangle is removed instead. It is a cosmetic guard on a screen
 * whose grid already crafts nothing.
 *
 * <p>Drawn on {@code Render.Foreground} because that is the pass that runs inside the screen's own
 * translation -- the coordinates are the menu's slot coordinates -- and after the slots. The carried stack
 * and every tooltip are drawn later still, so neither can be painted over.
 */
final class InventoryGridBlank {

    /** Vanilla's GUI panel grey, which is what the covered region would have been. */
    private static final int PANEL_GREY = 0xFFC6C6C6;

    /** The recipe book button's size, vanilla's. */
    private static final int RECIPE_BUTTON_WIDTH = 20;
    private static final int RECIPE_BUTTON_HEIGHT = 18;

    private InventoryGridBlank() {
    }

    /** The menu of {@code screen} when it copies the inventory's grid, else null. */
    private static AbstractCraftingMenu gridMenu(Object screen) {
        if (screen instanceof AbstractRecipeBookScreen<?> book
                && InventoryGrid.is2x2(book.getMenu())) {
            return (AbstractCraftingMenu) book.getMenu();
        }
        return null;
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
        AbstractCraftingMenu menu = gridMenu(event.getScreen());
        if (menu == null) return;
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        player.getRecipeBook().setOpen(RecipeBookType.CRAFTING, false);
    }

    static void onScreenInit(ScreenEvent.Init.Post event) {
        if (gridMenu(event.getScreen()) == null) return;
        ScreenPosition at = ((AbstractRecipeBookScreenAccessor) event.getScreen()).craftworks$recipeBookButtonPosition();
        for (GuiEventListener listener : event.getListenersList()) {
            if (listener instanceof ImageButton button
                    && button.getX() == at.x()
                    && button.getY() == at.y()
                    && button.getWidth() == RECIPE_BUTTON_WIDTH
                    && button.getHeight() == RECIPE_BUTTON_HEIGHT) {
                event.removeListener(button);
                return;
            }
        }
    }

    static void onScreenForeground(ContainerScreenEvent.Render.Foreground event) {
        AbstractCraftingMenu menu = gridMenu(event.getContainerScreen());
        if (menu == null) return;
        GridCover cover = GridCover.around(InventoryGrid.slotsOf(menu).stream().map(InventoryGridBlank::at).toList());
        event.getGuiGraphics().fill(cover.left(), cover.top(), cover.right(), cover.bottom(), PANEL_GREY);
    }

    private static SlotAt at(Slot slot) {
        return new SlotAt(slot.x, slot.y);
    }
}
