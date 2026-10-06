// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.machine;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * A machine that holds fluid, described: which machine it is, its item input slots, whether it has a product
 * slot, its input and output fluid boxes and where its Fluid Connections stand. The block entity, the menu, the
 * boxes, the connections and the recipes it may hold all read this and nothing machine-specific, so a machine
 * is one more instance: the Chemical Plant is {@link #CHEMICAL_PLANT}.
 *
 * <p>The boxes are numbered inputs first, then outputs; the Held recipe's {@code n}th fluid ingredient goes in
 * input box {@code n} and its {@code n}th fluid result in output box {@code n}. The item slots are the inputs,
 * then the product's if the machine has one.
 *
 * @param kind        which machine this is, for its lang keys and its Recipe viewer tab
 * @param defaults    the figures it starts from before the server config says otherwise
 * @param itemInputs  its item input slots, which take the Held recipe's ingredients by order
 * @param product     whether it has a product slot, after the inputs
 * @param fluidInputs its input fluid boxes
 * @param fluidOutputs its output fluid boxes
 * @param connections where its Fluid Connections stand
 */
public record FluidMachine(MachineKind kind, MachineDefaults defaults, int itemInputs,
        boolean product, int fluidInputs, int fluidOutputs, List<Site> connections) {

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

    public FluidMachine {
        connections = List.copyOf(connections);
    }

    /**
     * Factorio's chemical plant: two item inputs and a product, two input and two output boxes, and four
     * connections, at either end of the two opposite bottom-layer edges of its 3x3, two on the side it faces and
     * two on the other.
     */
    public static final FluidMachine CHEMICAL_PLANT = new FluidMachine(MachineKind.CHEMICAL_PLANT, ChemicalPlantDefaults.INSTANCE,
            2, true, 2, 2, List.of(
                    new Site(1, 1, Face.AHEAD), new Site(1, -1, Face.AHEAD),
                    new Site(-1, 1, Face.BEHIND), new Site(-1, -1, Face.BEHIND)));

    // -- the item slots -------------------------------------------------------------------------

    /** The slot of the product, after the inputs, or {@link MachineSlots#NONE} if the machine has no product slot. */
    public int productSlot() {
        return product ? itemInputs : MachineSlots.NONE;
    }

    /** Whether the machine has any item slot, an input or a product: a recipe with an item can be held only if it does. */
    public boolean hasItemSlots() {
        return itemInputs > 0 || product;
    }

    public MachineSlots slots() {
        return new MachineSlots(itemInputs + (product ? 1 : 0), itemInputs, productSlot());
    }

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
