// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.assembler.client;

import java.util.List;

import io.github._5thlayer.craftworks.assembler.PlanQueueView;
import io.github._5thlayer.craftworks.assembler.CancelClick;
import io.github._5thlayer.craftworks.network.PlanCancelPacket;
import io.github._5thlayer.craftworks.network.QueueSyncPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.neoforged.neoforge.client.event.ContainerScreenEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * The Plan queue on the inventory screen, in the area the 2x2 grid left blank (ADR-0005).
 *
 * <p>The inventory screen is the Assembler: Fill Recipe queues from it, and this is where the queue is
 * watched and cancelled. A click on either icon of a row cancels crafts of that row's final recipe the
 * way Factorio's hand-craft queue does -- see {@link CancelClick}. What does not fit shows as a
 * "+N more" line, as Factorio does; a side panel is the follow-up if two rows prove too few.
 *
 * <p>An empty queue shows a hint in its place instead, so a new player who finds no 2x2 grid learns where
 * a craft starts (ADR-0007). It names no viewer's buttons beyond "+", and is cut to the blanked area with
 * a trailing "…" by {@link Ellipsis}, its full text in a tooltip; the hotbar overlay has none.
 *
 * <p>Drawn on the container's foreground, in the menu's coordinates, at a lower priority than
 * {@link InventoryGridBlank}'s grey so it lands over it. Not on {@code Render.Post}: that runs after the
 * screen has spent its tooltip in a stratum of its own, so anything drawn there covers the tooltip.
 */
final class InventoryQueue {

    /** The blanked area's left edge and the first row's top, in the screen's own coordinates. */
    private static final int LEFT = 98;
    private static final int TOP = 7;
    private static final int ROW_HEIGHT = 18;
    private static final int ROWS = 2;
    private static final int TEXT = 0xFF404040;
    /** From {@link #LEFT} to the blanked area's right edge, less a pixel of margin ({@link GridCover}: 171). */
    private static final int HINT_WIDTH = 72;
    /** The hint sits where a row's text would. */
    private static final int HINT_DOWN = 4;
    private static final Component HINT = Component.translatable("craftworks.queue.empty_hint");
    /** The tooltip wraps the full hint at this width. */
    private static final int TOOLTIP_WIDTH = 170;

    private InventoryQueue() {
    }

    static void onForeground(ContainerScreenEvent.Render.Foreground event) {
        if (!(event.getContainerScreen() instanceof InventoryScreen)) return;
        List<QueueSyncPacket.Entry> entries = PlanQueueView.entries();
        GuiGraphicsExtractor graphics = event.getGuiGraphics();
        Font font = Minecraft.getInstance().font;
        if (entries.isEmpty()) {
            graphics.text(font, Ellipsis.fit(HINT.getString(), HINT_WIDTH, font::width), LEFT, TOP + HINT_DOWN, TEXT, false);
            return;
        }
        int shown = Math.min(ROWS, entries.size());
        for (int index = 0; index < shown; index++) {
            QueueSyncPacket.Entry entry = entries.get(index);
            QueueRow.draw(graphics, font, entry, index == 0, LEFT, TOP + index * ROW_HEIGHT, TEXT, false);
        }
        if (entries.size() > shown) {
            graphics.text(font, Component.translatable("craftworks.queue.and_more", entries.size() - shown),
                    LEFT, TOP + shown * ROW_HEIGHT + 1, TEXT, false);
        }
    }

    /**
     * The hovered icon's tooltip. Set before the screen draws, because the screen spends its deferred
     * tooltip at the end of its own render, and one set on {@code Render.Post} is never shown.
     */
    static void onTooltip(ScreenEvent.Render.Pre event) {
        if (!(event.getScreen() instanceof InventoryScreen screen)) return;
        List<QueueSyncPacket.Entry> entries = PlanQueueView.entries();
        Font font = Minecraft.getInstance().font;
        if (entries.isEmpty()) {
            hintTooltip(event, screen, font);
            return;
        }
        for (int index = 0; index < Math.min(ROWS, entries.size()); index++) {
            QueueSyncPacket.Entry entry = entries.get(index);
            if (!QueueRow.layout(font, entry, screen.getGuiLeft() + LEFT)
                    .hit(event.getMouseX(), event.getMouseY(), rowTop(screen, index))) continue;
            event.getGuiGraphics().setComponentTooltipForNextFrame(font, List.of(
                    PlanItems.name(entry.rootItem()),
                    Component.translatable("craftworks.queue.cancel.left"),
                    Component.translatable("craftworks.queue.cancel.right"),
                    Component.translatable("craftworks.queue.cancel.shift")),
                    event.getMouseX(), event.getMouseY());
            return;
        }
    }

    static void onClick(ScreenEvent.MouseButtonPressed.Pre event) {
        if (!(event.getScreen() instanceof InventoryScreen screen)) return;
        List<QueueSyncPacket.Entry> entries = PlanQueueView.entries();
        Font font = Minecraft.getInstance().font;
        for (int index = 0; index < Math.min(ROWS, entries.size()); index++) {
            QueueSyncPacket.Entry entry = entries.get(index);
            if (!QueueRow.layout(font, entry, screen.getGuiLeft() + LEFT)
                    .hit(event.getMouseX(), event.getMouseY(), rowTop(screen, index))) continue;
            int crafts = CancelClick.crafts(event.getButton(), event.getMouseButtonEvent().hasShiftDown());
            if (crafts == 0) return;
            ClientPacketDistributor.sendToServer(new PlanCancelPacket(entry.planId(), crafts));
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
            event.setCanceled(true);
            return;
        }
    }

    /** The full hint, wrapped, while the cut one is hovered. A hint that fits whole has no tooltip. */
    private static void hintTooltip(ScreenEvent.Render.Pre event, InventoryScreen screen, Font font) {
        String full = HINT.getString();
        String shown = Ellipsis.fit(full, HINT_WIDTH, font::width);
        if (shown.equals(full)) return;
        int left = screen.getGuiLeft() + LEFT;
        int top = hintTop(screen);
        int mouseX = event.getMouseX();
        int mouseY = event.getMouseY();
        if (mouseX < left || mouseX >= left + font.width(shown) || mouseY < top || mouseY >= top + font.lineHeight) return;
        event.getGuiGraphics().setTooltipForNextFrame(font.split(HINT, TOOLTIP_WIDTH), mouseX, mouseY);
    }

    private static int hintTop(InventoryScreen screen) {
        return screen.getGuiTop() + TOP + HINT_DOWN;
    }

    private static int rowTop(InventoryScreen screen, int index) {
        return screen.getGuiTop() + TOP + index * ROW_HEIGHT;
    }
}
