// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.machine;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * Where a Chemical Plant's four Fluid Connections are: at either end of the two opposite bottom-layer edges of
 * its 3x3, the one that faces the way it does and the one that faces away, where Factorio's chemical plant has
 * its pipe connections. A connection is the footprint block there and the face of it that points away from the
 * machine; the block that face touches is what it pulls from and pushes to. They turn with the facing, and
 * have no direction of their own: any of them fills an input box and drains an output box.
 */
final class ChemicalPlantConnections {

    /** A connection: the footprint block it is, and the way its face points. */
    record Connection(BlockPos block, Direction side) {

        /** The block the face touches. */
        BlockPos beyond() {
            return block.relative(side);
        }
    }

    private ChemicalPlantConnections() {
    }

    /** The four, the two on the side the plant faces first. */
    static List<Connection> of(BlockPos origin, Direction facing) {
        Direction cw = facing.getClockWise();
        Direction ccw = facing.getCounterClockWise();
        Direction away = facing.getOpposite();
        return List.of(
                new Connection(origin.relative(facing).relative(cw), facing),
                new Connection(origin.relative(facing).relative(ccw), facing),
                new Connection(origin.relative(away).relative(cw), away),
                new Connection(origin.relative(away).relative(ccw), away));
    }

    /** Whether the face of the footprint block at {@code at} pointing {@code side} is one of the four. */
    static boolean isFace(BlockPos origin, Direction facing, BlockPos at, Direction side) {
        return of(origin, facing).stream().anyMatch(connection -> connection.block().equals(at) && connection.side() == side);
    }
}
