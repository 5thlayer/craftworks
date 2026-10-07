// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.machine;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * Where a machine holds fluid, described: how many input and output fluid boxes it has and where its Fluid
 * Connections stand. The boxes ({@link MachineFluids}), the connections ({@link MachineFluidFace}), the gauges
 * ({@link FluidGaugeLayout}) and the recipes it may hold all read this and nothing machine-specific, so a layout
 * is one more instance: an Assembler of tier 2 or 3's is {@link #ASSEMBLER}.
 *
 * <p>The boxes are numbered inputs first, then outputs; the Held recipe's {@code n}th fluid ingredient goes in
 * input box {@code n} and its {@code n}th fluid result in output box {@code n}.
 *
 * @param fluidInputs  its input fluid boxes
 * @param fluidOutputs its output fluid boxes
 * @param connections  where its Fluid Connections stand
 */
public record FluidLayout(int fluidInputs, int fluidOutputs, List<Site> connections) {

    /** The way a face points, from the machine's own facing: the way it faces, away from it, to its right or to its left. */
    public enum Face {
        AHEAD,
        BEHIND,
        RIGHT,
        LEFT;

        Direction resolve(Direction facing) {
            return switch (this) {
                case AHEAD -> facing;
                case BEHIND -> facing.getOpposite();
                case RIGHT -> facing.getClockWise();
                case LEFT -> facing.getCounterClockWise();
            };
        }
    }

    /**
     * Where a Fluid Connection is, relative to the origin and the facing: the footprint block {@code ahead} blocks
     * the way the machine faces (negative: behind) and {@code right} blocks to its right (negative: left), and the
     * face of it that points {@code face}, away from the machine.
     */
    public record Site(int ahead, int right, Face face) {

        Connection at(BlockPos origin, Direction facing) {
            BlockPos block = origin.relative(facing, ahead).relative(facing.getClockWise(), right);
            return new Connection(block, face.resolve(facing));
        }
    }

    /** A Fluid Connection as it stands: the footprint block it is, and the way its face points. */
    public record Connection(BlockPos block, Direction side) {

        /** The block the face touches, where the fluid goes to and comes from. */
        BlockPos beyond() {
            return block.relative(side);
        }
    }

    public FluidLayout {
        connections = List.copyOf(connections);
    }

    /**
     * An Assembler of tier 2 or 3's: two input and three output boxes, and six connections, all three blocks of
     * the bottom layer along each of the two opposite edges of its 3x3, three on the side it faces and three on the
     * other. With six, a recipe of up to five fluids has a connection for
     * each, since a connection has no direction and each pipe network carries one fluid.
     */
    public static final FluidLayout ASSEMBLER = new FluidLayout(2, 3, List.of(
            new Site(1, -1, Face.AHEAD), new Site(1, 0, Face.AHEAD), new Site(1, 1, Face.AHEAD),
            new Site(-1, -1, Face.BEHIND), new Site(-1, 0, Face.BEHIND), new Site(-1, 1, Face.BEHIND)));

    // -- the fluid boxes ------------------------------------------------------------------------

    /** Every box, the inputs then the outputs. */
    public int boxes() {
        return fluidInputs + fluidOutputs;
    }

    public boolean isInput(int box) {
        return box >= 0 && box < fluidInputs;
    }

    /** Which fluid ingredient or result {@code box} is bound to: {@code n} for input box {@code n} and for output box {@code n}. */
    public int binding(int box) {
        return isInput(box) ? box : box - fluidInputs;
    }

    /** Output box {@code n}, the box the Held recipe's {@code n}th fluid result goes in. */
    public int outputBox(int n) {
        return fluidInputs + n;
    }

    // -- the Fluid Connections ------------------------------------------------------------------

    /** The connections of a machine at {@code origin} facing {@code facing}, in the order they are described. */
    List<Connection> connections(BlockPos origin, Direction facing) {
        return connections.stream().map(site -> site.at(origin, facing)).toList();
    }

    /** Whether the face of the footprint block at {@code at} pointing {@code side} is one of the connections. */
    boolean isConnection(BlockPos origin, Direction facing, BlockPos at, Direction side) {
        return connections(origin, facing).stream().anyMatch(connection -> connection.block().equals(at) && connection.side() == side);
    }
}
