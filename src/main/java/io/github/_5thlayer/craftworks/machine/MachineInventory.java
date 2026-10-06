// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.machine;

import java.util.Optional;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;

/**
 * The item slots of a machine that crafts: the input slots first, then its outputs. An input holds at least
 * two crafts of its ingredient however little the item stacks to: cake's three milk buckets must fit in one
 * slot, and a bucket stacks to one.
 */
final class MachineInventory extends ItemStacksResourceHandler {

    /** What the machine says about its slots: the Held recipe's ingredient in one, and that something changed. */
    interface Owner {

        /** The ingredient the Held recipe puts in input {@code slot}, if the machine can run it. Empty off the server. */
        Optional<SizedIngredient> ingredientAt(int slot);

        void changed();
    }

    private final MachineSlots slots;
    private final Owner owner;

    MachineInventory(MachineSlots slots, Owner owner) {
        super(slots.size());
        this.slots = slots;
        this.owner = owner;
    }

    MachineSlots slots() {
        return slots;
    }

    boolean isInput(int slot) {
        return slots.isInput(slot);
    }

    @Override
    protected int getCapacity(int index, ItemResource resource) {
        int stack = super.getCapacity(index, resource);
        if (!isInput(index) || resource.isEmpty()) {
            return stack;
        }
        return owner.ingredientAt(index)
                .map(ingredient -> Math.min(Item.ABSOLUTE_MAX_STACK_SIZE,
                        Math.max(stack, ingredient.count() * OverloadLimit.MINIMUM)))
                .orElse(stack);
    }

    @Override
    protected void onContentsChanged(int index, ItemStack previousContents) {
        owner.changed();
    }
}
