// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.machine;

import java.util.function.Predicate;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandlerUtil;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jspecify.annotations.Nullable;

/**
 * What a machine's Fluid Connection does with the block it faces: look up its fluid handler, and move fluid
 * between it and a box in one transaction, simulated within and committed only if some moved. An Assembler
 * only pulls; a Chemical Plant pulls its ingredients and pushes its results.
 */
final class FluidMoves {

    private FluidMoves() {
    }

    /** The fluid handler of the block at {@code neighbour}, on its face towards the machine, if it has one. */
    static @Nullable ResourceHandler<FluidResource> across(ServerLevel server, BlockPos neighbour, Direction outward) {
        return server.getCapability(Capabilities.Fluid.BLOCK, neighbour, outward.getOpposite());
    }

    /** Moves up to {@code max} mB of what {@code filter} allows from one handler to the other; how much moved. */
    static int move(ResourceHandler<FluidResource> from, ResourceHandler<FluidResource> to,
            Predicate<FluidResource> filter, int max) {
        try (Transaction tx = Transaction.openRoot()) {
            int moved = ResourceHandlerUtil.move(from, to, filter, max, tx);
            if (moved > 0) {
                tx.commit();
            }
            return moved;
        }
    }
}
