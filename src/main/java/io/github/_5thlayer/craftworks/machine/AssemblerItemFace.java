// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.machine;

import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * The Assembler's item capability, on every face of every block of its footprint: inputs are filtered
 * by the Held recipe and held to the Overload Limit, and only the two outputs extract.
 */
final class AssemblerItemFace implements ResourceHandler<ItemResource> {

    private final AssemblerBlockEntity machine;
    private final ResourceHandler<ItemResource> inventory;

    AssemblerItemFace(AssemblerBlockEntity machine, ResourceHandler<ItemResource> inventory) {
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
        return inventory.insert(index, resource, Math.min(amount, machine.overloadRoom(index)), transaction);
    }

    @Override
    public int extract(int index, ItemResource resource, int amount, TransactionContext transaction) {
        if (AssemblerSlots.isInput(index)) {
            return 0;
        }
        return inventory.extract(index, resource, amount, transaction);
    }
}
