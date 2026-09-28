// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.assembler;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

/**
 * The Crafting Plan (#7): what a middle click on Fill Recipe opens, and what a request that cannot be
 * queued opens instead.
 *
 * <p>A menu with no slots, and a menu rather than a client screen because a plan is server truth: the
 * server resolves it and opens this. The plan itself never comes here. What the client gets is a
 * {@link PlanDisplay} for one craft and the Resolver's {@code largestAffordable}, which decides which
 * of {@code +1}, {@code +5} and {@code all} are lit ({@link CraftButtons}). Both are replaced in place
 * by {@code PlanUpdatePacket} while the screen is up, because each press spends the inventory the next
 * one is resolved against.
 */
public final class CraftingPlanMenu extends AbstractContainerMenu {

    private PlanDisplay display;
    private int largestAffordable;

    /**
     * How many crafts the request that opened the plan asked for and could not queue, or 0 when nothing
     * was refused (a middle click). A plan is shown for one craft, so without this a +5 that one craft's
     * items cover would open a complete plan with no word on why nothing was queued.
     */
    private final int refused;

    public CraftingPlanMenu(int containerId, PlanDisplay display, int largestAffordable, int refused) {
        super(PersonalAssembler.CRAFTING_PLAN.get(), containerId);
        this.display = display;
        this.largestAffordable = largestAffordable;
        this.refused = refused;
    }

    /** The client's side, from the opening data {@link PersonalAssembler#openPlan} writes. */
    public CraftingPlanMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        this(containerId, PlanDisplay.STREAM_CODEC.decode(buffer), buffer.readVarInt(), buffer.readVarInt());
    }

    public PlanDisplay display() {
        return display;
    }

    /** The refused count while the inventory still does not cover it, else 0: what "not enough" names. */
    public int shortOf() {
        return refused > Math.max(0, largestAffordable) ? refused : 0;
    }

    public CraftButtons buttons() {
        return CraftButtons.of(largestAffordable);
    }

    /** The client taking a re-resolved plan. */
    public void update(PlanDisplay display, int largestAffordable) {
        this.display = display;
        this.largestAffordable = largestAffordable;
    }

    /** Always: the plan belongs to the player, with no block to walk away from. */
    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    /** Nothing to shift-click into: there are no slots. */
    @Override
    public ItemStack quickMoveStack(Player player, int slot) {
        return ItemStack.EMPTY;
    }
}
