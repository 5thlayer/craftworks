// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.machine;

import java.util.List;
import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * Where an Assembler's two Fluid Connections are: the centres of two opposite edges of the bottom layer of
 * its 3x3, one on the side it faces and one on the other, as Factorio's assembling machine 2 has them. A
 * connection is the footprint block there and the face of it that points away from the machine; the block
 * that face touches is what it pulls from. They turn with the facing and have no direction of their own.
 */
final class FluidConnections {

    private FluidConnections() {
    }

    /** The way each connection points, outward: the facing and its opposite. */
    static List<Direction> sides(Direction facing) {
        return List.of(facing, facing.getOpposite());
    }

    /** The footprint block of the connection pointing {@code side}, the origin's neighbour on that side. */
    static BlockPos at(BlockPos origin, Direction side) {
        return origin.relative(side);
    }

    /** The block the connection pointing {@code side} pulls from. */
    static BlockPos neighbour(BlockPos origin, Direction side) {
        return origin.relative(side, 2);
    }

    /** The way the connection at {@code pos} points, if the footprint block there is a connection. */
    static Optional<Direction> sideAt(BlockPos origin, Direction facing, BlockPos pos) {
        return sides(facing).stream().filter(side -> at(origin, side).equals(pos)).findFirst();
    }
}
