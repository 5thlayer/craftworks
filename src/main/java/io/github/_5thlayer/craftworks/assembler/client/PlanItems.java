// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.assembler.client;

import io.github._5thlayer.craftworks.assembler.ItemKeys;
import net.minecraft.client.Minecraft;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * An item key as the client draws it.
 *
 * <p>The plan and the queue travel as strings, because the resolver and the queue count keys and
 * never learn what an {@code Item} is -- which is what keeps both of them unit-testable with no
 * Minecraft at all. Turning one back into a sprite or a name is a client-side job, and this is the
 * one place it happens: the three screens and the HUD would otherwise each have their own answer for
 * a key nothing is registered under.
 *
 * <p>The whole key, patch and all (ADR-0002). Drawing the bare item would put identical icons in a
 * plan's list for items that differ only by a component -- the same fold the queue no
 * longer makes, moved onto the screen, where it looks like a duplicate row.
 */
final class PlanItems {

    private PlanItems() {}

    /** The item's display name, or the raw key when nothing is registered under it. */
    static Component name(String key) {
        ItemStack stack = stack(key);
        return stack.isEmpty() ? Component.literal(key) : stack.getHoverName();
    }

    /**
     * One stack of the item, for drawing and for the vanilla tooltip.
     *
     * <p>Count one, not the plan's: the number beside the icon is the plan's, and a stack count
     * painted into the corner of the sprite would say it twice, differently, the moment a plan asks
     * for more than a stack.
     */
    static ItemStack stack(String key) {
        HolderLookup.Provider registries = registries();
        return registries == null ? ItemStack.EMPTY : ItemKeys.toStack(key, 1, registries);
    }

    private static HolderLookup.Provider registries() {
        return Minecraft.getInstance().level == null ? null : Minecraft.getInstance().level.registryAccess();
    }
}
