// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.machine;

import java.util.function.BiFunction;

import io.github._5thlayer.libworks.machine.FluidContainers;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.RangedResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandlerUtil;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.jspecify.annotations.Nullable;

/**
 * The work of an Assembler's drain and fill slots of tiers 2 and 3 (ADR-0021), in its tick: a filled container
 * in the drain slot is poured into the input box bound to its fluid, an empty one in the fill slot is filled from
 * the first output box holding a whole container's worth, and what the container becomes goes to the shared
 * outputs. The move is libworks-runtime's {@link FluidContainers}, whole or not at all, a container that cannot
 * move waiting in its slot; this picks the output slot it goes to. Each call moves at most one container.
 */
final class AssemblerContainers {

    private AssemblerContainers() {
    }

    /** Whether {@code stack} is a fluid container holding some fluid: what the drain slot takes by hand. */
    static boolean isFilled(ItemStack stack) {
        return FluidContainers.holds(stack, fluid -> true);
    }

    /** Whether {@code stack} is a fluid container holding none: what the fill slot takes by hand. */
    static boolean isEmpty(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        var fluids = ItemAccess.forStack(stack.copyWithCount(1)).getCapability(Capabilities.Fluid.ITEM);
        return fluids != null && ResourceHandlerUtil.isEmpty(fluids);
    }

    /** Whether a hand may put {@code stack} in {@code slot}: a filled container in the drain slot, an empty one in the fill slot. */
    static boolean placeable(int slot, ItemStack stack) {
        return switch (slot) {
            case AssemblerSlots.DRAIN -> isFilled(stack);
            case AssemblerSlots.FILL -> isEmpty(stack);
            default -> false;
        };
    }

    /** Pours the container in the drain slot into the input box bound to its fluid; whether one went. */
    static boolean drain(MachineInventory inventory, AssemblerFluidSide fluids) {
        return move(inventory, AssemblerSlots.DRAIN,
                (container, output) -> FluidContainers.drain(container, output, fluids.inputs()));
    }

    /** Fills the container in the fill slot from the first output box that holds a whole container's worth; whether one went. */
    static boolean fill(MachineInventory inventory, AssemblerFluidSide fluids) {
        MachineFluids boxes = fluids.boxes();
        for (int box = FluidLayout.ASSEMBLER.fluidInputs(); box < FluidLayout.ASSEMBLER.boxes(); box++) {
            FluidResource held = boxes.getResource(box);
            if (held.isEmpty()) {
                continue;
            }
            RangedResourceHandler<FluidResource> tank = RangedResourceHandler.ofSingleIndex(boxes, box);
            if (move(inventory, AssemblerSlots.FILL, (container, output) -> FluidContainers.fill(container, output, tank, held))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Offers {@code move} the container in {@code from} and each output slot in turn, those holding something
     * first, and lands the first that goes: one less in {@code from}, and the output slot as the move left it.
     */
    private static boolean move(MachineInventory inventory, int from,
            BiFunction<ItemStack, ItemStack, @Nullable ItemStack> move) {
        ItemStack container = stack(inventory, from);
        if (container.isEmpty()) {
            return false;
        }
        for (int slot : inventory.slots().outputOrder(output -> inventory.getAmountAsInt(output) > 0)) {
            ItemStack after = move.apply(container, stack(inventory, slot));
            if (after != null) {
                inventory.set(slot, ItemResource.of(after), after.getCount());
                container.shrink(1);
                inventory.set(from, container.isEmpty() ? ItemResource.EMPTY : ItemResource.of(container), container.getCount());
                return true;
            }
        }
        return false;
    }

    private static ItemStack stack(MachineInventory inventory, int slot) {
        return inventory.getResource(slot).toStack(inventory.getAmountAsInt(slot));
    }
}
