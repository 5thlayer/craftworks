// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * Offers unlimited FE to the energy capability of each block touching it, every tick, and shows one of its
 * own that extracts without limit and accepts nothing. It speaks only NeoForge's energy capability.
 */
public final class CreativeEnergySourceBlockEntity extends BlockEntity {

    /** What the capability shows: full, and never short of what is asked of it. */
    private static final EnergyHandler SOURCE = new EnergyHandler() {
        @Override
        public long getAmountAsLong() {
            return Long.MAX_VALUE;
        }

        @Override
        public long getCapacityAsLong() {
            return Long.MAX_VALUE;
        }

        @Override
        public int insert(int amount, TransactionContext transaction) {
            return 0;
        }

        @Override
        public int extract(int amount, TransactionContext transaction) {
            return amount;
        }
    };

    public CreativeEnergySourceBlockEntity(BlockPos pos, BlockState state) {
        super(Assemblers.CREATIVE_ENERGY_SOURCE_ENTITY.get(), pos, state);
    }

    public EnergyHandler energyFace() {
        return SOURCE;
    }

    /** Fills each neighbour's handler on the face that touches this block, as much as it takes. */
    public void serverTick(ServerLevel server) {
        for (Direction side : Direction.values()) {
            EnergyHandler neighbour = server.getCapability(Capabilities.Energy.BLOCK, worldPosition.relative(side), side.getOpposite());
            if (neighbour == null) {
                continue;
            }
            try (Transaction tx = Transaction.openRoot()) {
                neighbour.insert(Integer.MAX_VALUE, tx);
                tx.commit();
            }
        }
    }
}
