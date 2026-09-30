// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.compat.emi;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import dev.emi.emi.EmiRenderHelper;
import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.Bounds;
import dev.emi.emi.config.SidebarType;
import dev.emi.emi.runtime.EmiDrawContext;
import dev.emi.emi.screen.EmiScreenBase;
import dev.emi.emi.screen.EmiScreenManager;
import dev.emi.emi.screen.EmiScreenManager.SidebarPanel;
import io.github._5thlayer.craftworks.api.IndexTab;
import io.github._5thlayer.craftworks.tabs.SearchTerms;
import io.github._5thlayer.craftworks.tabs.TabFiles;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * The index tabs: a row of saved searches directly above EMI's item index, and above nothing else (#15).
 *
 * <p>Clicking a tab writes its query into EMI's search bar, replacing what was typed. A tab is lit while
 * every term of its query is in the search ({@link SearchTerms}), so the row keeps no state of its own:
 * typing a tab's query lights it just as a click does, and nothing is saved across restarts.
 *
 * <p>EMI lays its panels out only when the screen changes, so the room above the index is taken while it
 * lays out: the panel showing the index is given bounds a row shorter at the top. Which panel shows the
 * index can change without a layout (its cycle button), and so can whether there are any tabs, so each
 * frame checks the last layout still fits and asks EMI for a new one when it doesn't.
 */
public final class IndexTabRow {

    private static final int TAB_SIZE = 20;
    private static final int GAP = 1;
    private static final int ROW_HEIGHT = TAB_SIZE + 2;

    private static final int FACE = 0xFF2B2B2B;
    private static final int FACE_LIT = 0xFF6B6B6B;
    private static final int EDGE = 0xFF555555;
    private static final int EDGE_LIT = 0xFFFFFFFF;
    private static final int HOVER = 0x40FFFFFF;

    /** Whether each panel was given room for the row at EMI's last layout. */
    private static final Map<SidebarPanel, Boolean> ROOM = new IdentityHashMap<>();

    private IndexTabRow() {
    }

    /** Whether EMI should leave room for the row at the top of this panel as it lays it out. */
    public static boolean wantsRoom(SidebarPanel panel) {
        return panel.getType() == SidebarType.INDEX && !TabFiles.row().isEmpty();
    }

    /** Records the room a panel was laid out with. */
    public static void laidOut(SidebarPanel panel, boolean room) {
        ROOM.put(panel, room);
    }

    /** The bounds EMI would lay a panel out in, less a row at the top. */
    public static Bounds belowRow(Bounds bounds) {
        return new Bounds(bounds.x(), bounds.y() + ROW_HEIGHT, bounds.width(), Math.max(0, bounds.height() - ROW_HEIGHT));
    }

    /** Asks EMI to lay out again when the index moved panel, or the row appeared or went, since the last layout. */
    public static void keepRoom() {
        if (EmiScreenBase.getCurrent().isEmpty()) return;
        for (Map.Entry<SidebarPanel, Boolean> panel : ROOM.entrySet()) {
            if (panel.getValue() != wantsRoom(panel.getKey())) {
                EmiScreenManager.forceRecalculate();
                return;
            }
        }
    }

    public static void render(EmiDrawContext context, int mouseX, int mouseY) {
        Layout layout = layout();
        if (layout == null) return;
        String search = EmiApi.getSearchText();
        for (int index = 0; index < layout.tabs().size(); index++) {
            IndexTab tab = layout.tabs().get(index);
            int x = layout.x(index);
            int y = layout.y();
            boolean lit = SearchTerms.lit(tab.query(), search);
            context.fill(x, y, TAB_SIZE, TAB_SIZE, lit ? EDGE_LIT : EDGE);
            context.fill(x + 1, y + 1, TAB_SIZE - 2, TAB_SIZE - 2, lit ? FACE_LIT : FACE);
            context.drawStack(EmiStack.of(icon(tab)), x + 2, y + 2);
            if (layout.hit(mouseX, mouseY) == index) context.fill(x + 1, y + 1, TAB_SIZE - 2, TAB_SIZE - 2, HOVER);
        }
    }

    /** The hovered tab's name, drawn with EMI's foreground so it lands over the index. */
    public static void renderTooltip(EmiDrawContext context, int mouseX, int mouseY) {
        Layout layout = layout();
        if (layout == null) return;
        int index = layout.hit(mouseX, mouseY);
        if (index < 0) return;
        Component name = Component.translatable(layout.tabs().get(index).name());
        EmiRenderHelper.drawTooltip(EmiScreenBase.getCurrent().screen(), context,
                List.of(ClientTooltipComponent.create(name.getVisualOrderText())), mouseX, mouseY);
    }

    /** A left click on a tab writes its query into the search bar; true when a tab took the click. */
    public static boolean click(MouseButtonEvent event) {
        if (event.button() != 0) return false;
        Layout layout = layout();
        if (layout == null) return false;
        int index = layout.hit((int) event.x(), (int) event.y());
        if (index < 0) return false;
        EmiApi.setSearchText(layout.tabs().get(index).query());
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
        return true;
    }

    /** Where the row sits now, or null when there is none to draw: no index shown, or no room left for it. */
    private static Layout layout() {
        if (EmiScreenManager.isDisabled() || EmiScreenBase.getCurrent().isEmpty()) return null;
        SidebarPanel panel = EmiScreenManager.getPanelFor(SidebarType.INDEX);
        if (panel == null || !panel.isVisible() || !ROOM.getOrDefault(panel, false)) return null;
        List<IndexTab> tabs = TabFiles.row();
        Bounds bounds = panel.getBounds();
        int fits = (bounds.width() + GAP) / (TAB_SIZE + GAP);
        if (tabs.isEmpty() || fits <= 0) return null;
        return new Layout(tabs.subList(0, Math.min(fits, tabs.size())), bounds.left(), bounds.top() - ROW_HEIGHT + 1);
    }

    private static Item icon(IndexTab tab) {
        Identifier id = Identifier.tryParse(tab.icon());
        return id == null ? Items.BARRIER : BuiltInRegistries.ITEM.getOptional(id).orElse(Items.BARRIER);
    }

    private record Layout(List<IndexTab> tabs, int left, int y) {

        int x(int index) {
            return left + index * (TAB_SIZE + GAP);
        }

        /** The tab under the mouse, or -1. */
        int hit(int mouseX, int mouseY) {
            if (mouseY < y || mouseY >= y + TAB_SIZE || mouseX < left) return -1;
            int index = (mouseX - left) / (TAB_SIZE + GAP);
            boolean onTab = (mouseX - left) % (TAB_SIZE + GAP) < TAB_SIZE;
            return index < tabs.size() && onTab ? index : -1;
        }
    }
}
