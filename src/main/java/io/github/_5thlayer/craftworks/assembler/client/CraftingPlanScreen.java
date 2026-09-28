// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.assembler.client;

import java.util.List;
import java.util.Map;

import io.github._5thlayer.craftworks.assembler.AssemblerQueueView;
import io.github._5thlayer.craftworks.assembler.CraftButtons;
import io.github._5thlayer.craftworks.assembler.CraftingPlanMenu;
import io.github._5thlayer.craftworks.assembler.PlanDisplay;
import io.github._5thlayer.craftworks.network.PlanCraftPacket;
import io.github._5thlayer.craftworks.network.QueueSyncPacket;
import io.github._5thlayer.craftworks.planner.ItemAmount;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * The Crafting Plan (#7): what one craft spends, makes on the way and cannot get, three buttons that
 * queue, and the queue running underneath.
 *
 * <p>There is no Start and no Cancel. {@code +1}, {@code +5} and {@code all} each queue at once, the
 * plan re-resolves in place as the inventory is spent, and cancelling stays on the inventory screen,
 * which already lists the queue. Close returns there with the queue still running.
 *
 * <p>Locked is its own column beside Missing because the two ask different things of the player:
 * unlock one, gather the other. A Locked entry the Lock source gave a reason for says it under its count,
 * cut to the column and whole on hover (#18).
 *
 * <p>Drawn from fills rather than a texture, as a flat panel.
 */
final class CraftingPlanScreen extends AbstractContainerScreen<CraftingPlanMenu> {

    /**
     * Consume, To craft, Missing, Locked. Consume first because it is what a press spends: queueing
     * pays the whole raw cost at once, so the price reads before the purchase.
     */
    private static final int COLUMNS = 4;

    /**
     * How many lines a column shows before it says how many it did not. A column that silently stopped
     * would tell a player they have everything.
     */
    private static final int MAX_LINES = 6;

    private static final int ROW_HEIGHT = 18;

    /** Queue rows drawn under the plan; the inventory screen shows the rest. */
    private static final int QUEUE_ROWS = 3;

    private static final int QUEUE_TOP = 156;

    private static final int PANEL = 0xFF2B2B2B;

    /**
     * The item under the cursor, or empty. Collected while the panel draws and spent after the rest of
     * the screen has, because a tooltip painted from inside the background would be drawn over.
     */
    private ItemStack hovered = ItemStack.EMPTY;

    /** A Locked entry's reason under the cursor, or null; spent after the screen draws, as {@link #hovered} is. */
    private Component hoveredReason;

    private Button one;
    private Button five;
    private Button all;

    CraftingPlanScreen(CraftingPlanMenu menu, Inventory inventory, Component title) {
        // Wide: four columns do not fit in an inventory's width.
        super(menu, inventory, title, 340, 270);
    }

    @Override
    protected void init() {
        super.init();
        int y = topPos + imageHeight - 26;
        one = addRenderableWidget(Button.builder(Component.literal("+1"), b -> craft(1))
                .bounds(leftPos + 8, y, 36, 20).build());
        five = addRenderableWidget(Button.builder(Component.literal("+5"), b -> craft(5))
                .bounds(leftPos + 48, y, 36, 20).build());
        all = addRenderableWidget(Button.builder(Component.translatable("craftworks.plan.all"),
                        b -> craft(menu.buttons().allCount()))
                .bounds(leftPos + 88, y, 40, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("craftworks.plan.close"), b -> onClose())
                .bounds(leftPos + imageWidth - 76, y, 70, 20).build());
        refreshButtons();
    }

    /** Back to the inventory screen, where the queue and Fill Recipe are, not to the world. */
    @Override
    public void onClose() {
        super.onClose();
        if (minecraft != null && minecraft.player != null) {
            minecraft.setScreen(new InventoryScreen(minecraft.player));
        }
    }

    private void craft(int crafts) {
        ClientPacketDistributor.sendToServer(new PlanCraftPacket(menu.display().recipe(), crafts));
    }

    /** Read off the menu every frame, since a {@code PlanUpdatePacket} can change it at any time. */
    private void refreshButtons() {
        CraftButtons buttons = menu.buttons();
        one.active = buttons.one();
        five.active = buttons.five();
        all.active = buttons.all();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        hovered = ItemStack.EMPTY;
        hoveredReason = null;
        refreshButtons();
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        if (!hovered.isEmpty()) graphics.setTooltipForNextFrame(font, hovered, mouseX, mouseY);
        else if (hoveredReason != null) graphics.setTooltipForNextFrame(font, hoveredReason, mouseX, mouseY);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        // The world dims behind the panel as it does behind the inventory.
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, PANEL);
        PlanDisplay display = menu.display();
        int width = (imageWidth - 16) / COLUMNS;
        column(graphics, leftPos + 8, width, "consume", display.consume(), Map.of(), ChatFormatting.AQUA, mouseX, mouseY);
        column(graphics, leftPos + 8 + width, width, "to_craft", display.toCraft(), Map.of(), ChatFormatting.WHITE,
                mouseX, mouseY);
        column(graphics, leftPos + 8 + 2 * width, width, "missing", display.missing(), Map.of(), ChatFormatting.RED,
                mouseX, mouseY);
        column(graphics, leftPos + 8 + 3 * width, width, "locked", display.locked(), display.lockReasons(),
                ChatFormatting.GOLD, mouseX, mouseY);
        // Its own line above the buttons: a translated sentence of unknown width.
        Component reason = !display.complete() ? refusal(display)
                : menu.shortOf() > 0 ? Component.translatable("craftworks.plan.reason.not_enough_for", menu.shortOf())
                : null;
        if (reason != null) {
            graphics.text(font, reason.copy().withStyle(ChatFormatting.RED),
                    leftPos + 8, topPos + imageHeight - 38, 0xFFFF5555, false);
        }
        renderQueue(graphics);
    }

    /** The "Can't start" line, naming the first Locked entry's reason when the Lock source gave one. */
    private static Component refusal(PlanDisplay display) {
        String why = reason(display);
        if (why.equals("locked")) {
            for (ItemAmount amount : display.locked()) {
                Component lockReason = display.lockReasons().get(amount.item());
                if (lockReason != null) return Component.translatable("craftworks.plan.reason.locked_because", lockReason);
            }
        }
        return Component.translatable("craftworks.plan.reason." + why);
    }

    /**
     * Why an incomplete plan cannot be queued. Locked before Missing: unlocking is the fix there, and
     * "not enough" would send the player gathering for an item no amount of gathering lets them craft.
     * Four empty columns is the Resolver unable to read the recipe at all, not "you are short".
     */
    private static String reason(PlanDisplay display) {
        if (!display.locked().isEmpty()) return "locked";
        if (!display.missing().isEmpty()) return "missing";
        return display.consume().isEmpty() && display.toCraft().isEmpty() ? "unplannable" : "not_enough";
    }

    /**
     * The first few queued plans, so a press can be watched running without leaving the screen. No
     * cancel here: a cancel beside three buttons that queue would be one misclick from refunding what
     * was just paid for.
     */
    private void renderQueue(GuiGraphicsExtractor graphics) {
        int y = topPos + QUEUE_TOP;
        graphics.text(font, Component.translatable("craftworks.plan.queue").withStyle(ChatFormatting.GRAY),
                leftPos + 8, y, 0xFFAAAAAA, false);
        y += 12;
        List<QueueSyncPacket.Entry> entries = AssemblerQueueView.entries();
        if (entries.isEmpty()) {
            graphics.text(font, Component.translatable("craftworks.plan.queue_empty").withStyle(ChatFormatting.GRAY),
                    leftPos + 10, y + 4, 0xFFAAAAAA, false);
            return;
        }
        int shown = Math.min(QUEUE_ROWS, entries.size());
        for (int index = 0; index < shown; index++) {
            QueueRow.draw(graphics, font, entries.get(index), index == 0, leftPos + 8, y, 0xFFFFFFFF, false);
            y += ROW_HEIGHT;
        }
        if (entries.size() > shown) {
            graphics.text(font, Component.translatable("craftworks.queue.and_more", entries.size() - shown),
                    leftPos + 8, y, 0xFF888888, false);
        }
    }

    private void column(GuiGraphicsExtractor graphics, int x, int width, String key, List<ItemAmount> amounts,
            Map<String, Component> reasons, ChatFormatting colour, int mouseX, int mouseY) {
        graphics.text(font, Component.translatable("craftworks.plan." + key).withStyle(colour),
                x, topPos + 22, 0xFFFFFFFF, false);
        int y = topPos + 34;
        for (ItemAmount amount : amounts.subList(0, Math.min(MAX_LINES, amounts.size()))) {
            // The icon, not the name: the name is one hover away, from vanilla's own tooltip.
            ItemStack stack = PlanItems.stack(amount.item());
            graphics.item(stack, x, y);
            Component reason = reasons.get(amount.item());
            if (reason == null) {
                graphics.text(font, "x " + amount.count(), x + 20, y + 5, 0xFFCCCCCC, false);
            } else {
                graphics.text(font, "x " + amount.count(), x + 20, y, 0xFFCCCCCC, false);
                List<FormattedCharSequence> lines = font.split(reason, width - 22);
                if (!lines.isEmpty()) graphics.text(font, lines.get(0), x + 20, y + 9, 0xFFFFAA00, false);
                if (mouseX >= x + 20 && mouseX < x + width && mouseY >= y && mouseY < y + 18) hoveredReason = reason;
            }
            if (!stack.isEmpty() && mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16) {
                hovered = stack;
            }
            y += ROW_HEIGHT;
        }
        if (amounts.size() > MAX_LINES) {
            graphics.text(font, Component.translatable("craftworks.queue.and_more", amounts.size() - MAX_LINES),
                    x, y, 0xFF888888, false);
        }
    }

    /** The title only: the inherited second label names a player inventory this screen does not draw. */
    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.text(font, title, 8, 6, 0xFFFFFFFF, false);
    }
}
