// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.machine;

import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * The energy buffer of a machine that crafts, an Assembler: sized by the machine's
 * config, drawn from by its craft, and filled from outside through {@link #face}, which never gives any back.
 */
final class EnergyBuffer extends SimpleEnergyHandler {

    private final EnergyHandler face = new EnergyHandler() {
        @Override
        public long getAmountAsLong() {
            return EnergyBuffer.this.getAmountAsLong();
        }

        @Override
        public long getCapacityAsLong() {
            return EnergyBuffer.this.getCapacityAsLong();
        }

        @Override
        public int insert(int amount, TransactionContext transaction) {
            return EnergyBuffer.this.insert(amount, transaction);
        }

        @Override
        public int extract(int amount, TransactionContext transaction) {
            return 0;
        }
    };

    EnergyBuffer() {
        super(0);
    }

    void resize(int capacity) {
        this.capacity = capacity;
        this.maxInsert = capacity;
        this.maxExtract = capacity;
        this.energy = Math.min(energy, capacity);
    }

    /** What the capability shows: any source fills the buffer, and nothing drains it from outside. */
    EnergyHandler face() {
        return face;
    }
}
