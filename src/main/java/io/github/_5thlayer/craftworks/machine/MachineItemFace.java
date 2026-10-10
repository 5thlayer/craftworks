// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.machine;

import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * A machine's item capability, on every face of every block of its footprint: inputs are filtered
 * by the Held recipe and held to the Overload Limit, and only the outputs extract.
 */
final class MachineItemFace implements ResourceHandler<ItemResource> {

    /** What the machine decides about an input slot. */
    interface Gate {

        /** Whether input {@code slot} takes {@code resource} under the Held recipe. */
        boolean accepts(int slot, ItemResource resource);

        /** How many more of {@code resource} an insert may put in {@code slot}: the Overload Limit less what it holds. */
        int overloadRoom(int slot, ItemResource resource);
    }

    private final Gate machine;
    private final MachineInventory inventory;

    MachineItemFace(Gate machine, MachineInventory inventory) {
        this.machine = machine;
        this.inventory = inventory;
    }

    @Override
    public int size() {
        return inventory.size();
    }

    @Override
    public ItemResource getResource(int index) {
        return inventory.getResource(index);
    }

    @Override
    public long getAmountAsLong(int index) {
        return inventory.getAmountAsLong(index);
    }

    @Override
    public long getCapacityAsLong(int index, ItemResource resource) {
        return resource.isEmpty() || isValid(index, resource) ? inventory.getCapacityAsLong(index, resource) : 0;
    }

    @Override
    public boolean isValid(int index, ItemResource resource) {
        return machine.accepts(index, resource);
    }

    @Override
    public int insert(int index, ItemResource resource, int amount, TransactionContext transaction) {
        if (!machine.accepts(index, resource)) {
            return 0;
        }
        return inventory.insert(index, resource, Math.min(amount, machine.overloadRoom(index, resource)), transaction);
    }

    @Override
    public int extract(int index, ItemResource resource, int amount, TransactionContext transaction) {
        if (inventory.isInput(index)) {
            return 0;
        }
        return inventory.extract(index, resource, amount, transaction);
    }
}
