// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.machine;

import java.util.List;

import io.github._5thlayer.craftworks.machine.FluidLayout.Face;
import io.github._5thlayer.craftworks.machine.FluidLayout.Site;

/**
 * A machine that holds fluid, described: which machine it is, its item input slots, whether it has a product
 * slot and its {@link FluidLayout}, the fluid boxes and Fluid Connections it shares with every machine that holds
 * fluid. The block entity, the menu, the recipes it may hold all read this and nothing machine-specific, so a machine
 * is one more instance: the Chemical Plant is {@link #CHEMICAL_PLANT} and the Oil Refinery {@link #OIL_REFINERY}.
 *
 * <p>The item slots are the inputs, then the product's if the machine has one.
 *
 * @param kind       which machine this is, for its lang keys and its Recipe viewer tab
 * @param defaults   the figures it starts from before the server config says otherwise
 * @param itemInputs its item input slots, which take the Held recipe's ingredients by order
 * @param product    whether it has a product slot, after the inputs
 * @param layout     its fluid boxes and where its Fluid Connections stand
 */
public record FluidMachine(MachineKind kind, MachineDefaults defaults, int itemInputs, boolean product, FluidLayout layout) {

    /**
     * Factorio's chemical plant: two item inputs and a product, two input and two output boxes, and four
     * connections, at either end of the two opposite bottom-layer edges of its 3x3, two on the side it faces and
     * two on the other.
     */
    public static final FluidMachine CHEMICAL_PLANT = new FluidMachine(MachineKind.CHEMICAL_PLANT, ChemicalPlantDefaults.INSTANCE,
            2, true, new FluidLayout(2, 2, List.of(
                    new Site(1, 1, Face.AHEAD), new Site(1, -1, Face.AHEAD),
                    new Site(-1, 1, Face.BEHIND), new Site(-1, -1, Face.BEHIND))));

    /**
     * Factorio's oil refinery: no item slots, two input and three output boxes, and five connections on the
     * bottom layer of its 5x5, where its pipe connections are: two at -1 and +1 along the edge it faces, three at
     * -2, 0 and +2 along the opposite edge.
     */
    public static final FluidMachine OIL_REFINERY = new FluidMachine(MachineKind.OIL_REFINERY, OilRefineryDefaults.INSTANCE,
            0, false, new FluidLayout(2, 3, List.of(
                    new Site(2, -1, Face.AHEAD), new Site(2, 1, Face.AHEAD),
                    new Site(-2, -2, Face.BEHIND), new Site(-2, 0, Face.BEHIND), new Site(-2, 2, Face.BEHIND))));

    /** Every fluid machine, which the config, the tabs and the registrations walk. */
    public static final List<FluidMachine> ALL = List.of(CHEMICAL_PLANT, OIL_REFINERY);

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
}
