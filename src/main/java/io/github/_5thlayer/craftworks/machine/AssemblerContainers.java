// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.machine;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Predicate;

import io.github._5thlayer.libworks.machine.FluidContainers;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.RangedResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandlerUtil;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.jspecify.annotations.Nullable;

/**
 * The work of an Assembler's drain and fill slots (ADR-0021), in its tick: a filled container
 * in the drain slot is poured into the input box bound to its fluid, an empty one in the fill slot is filled from
 * the first output box holding a whole container's worth, and what the container becomes goes to the shared
 * outputs. The move is libworks-runtime's {@link FluidContainers}, whole or not at all, a container that cannot
 * move waiting in its slot; this picks the output slot it goes to. Each call moves at most one container.
 */
public final class AssemblerContainers {

    private AssemblerContainers() {
    }

    /** Whether {@code stack} is a fluid container holding some fluid: what the drain slot takes by hand. */
    static boolean isFilled(ItemStack stack) {
        return FluidContainers.holds(stack, fluid -> true);
    }

    /** Whether {@code stack} is a fluid container holding none: what the fill slot takes by hand. */
    static boolean isEmptyContainer(ItemStack stack) {
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
            case AssemblerSlots.FILL -> isEmptyContainer(stack);
            default -> false;
        };
    }

    /**
     * Whether the item face puts {@code container} in container slot {@code slot}, which takes only a container that
     * can move: in the drain slot a filled one whose fluid {@code inputTakes}, in the fill slot an empty one that
     * can be filled with one of the fluids the Held recipe {@code makes}.
     */
    static boolean routable(int slot, ItemStack container, Predicate<FluidResource> inputTakes, List<FluidResource> makes) {
        return switch (slot) {
            case AssemblerSlots.DRAIN -> FluidContainers.holds(container, inputTakes);
            case AssemblerSlots.FILL -> makes.stream().anyMatch(fluid -> FluidContainers.takes(container, fluid));
            default -> false;
        };
    }

    // -- what the slots show --------------------------------------------------------------------

    /** More than any container holds, for a tank to fill one from. */
    private static final int PLENTY = Integer.MAX_VALUE / 2;

    /** Scanned on first use, once for the game: the registries are final by the time a screen opens. */
    private static final class EmptyContainers {
        static final List<ItemStack> ALL = scan();

        private static List<ItemStack> scan() {
            List<FluidResource> sources = BuiltInRegistries.FLUID.stream()
                    .filter(fluid -> fluid != Fluids.EMPTY && fluid.isSource(fluid.defaultFluidState())).map(FluidResource::of).toList();
            List<ItemStack> found = new ArrayList<>();
            for (var item : BuiltInRegistries.ITEM) {
                ItemStack stack = new ItemStack(item);
                if (!stack.isEmpty() && sources.stream().anyMatch(fluid -> FluidContainers.takes(stack, fluid))) {
                    found.add(stack);
                }
            }
            return List.copyOf(found);
        }
    }

    /**
     * Every empty fluid container in the loaded mods: each item whose fluid item handler, on a fresh stack, is empty
     * and takes some fluid. The fill slot's ghosts. Scanned once, on first use.
     */
    public static List<ItemStack> emptyContainers() {
        return EmptyContainers.ALL;
    }

    /** What {@code fluid} fills each of {@code empties} into, one stack each, skipping a container that cannot hold it. */
    public static List<ItemStack> filledForms(List<ItemStack> empties, List<Fluid> fluids) {
        List<ItemStack> forms = new ArrayList<>();
        for (Fluid fluid : fluids) {
            for (ItemStack empty : empties) {
                ItemStack form = filled(empty, fluid);
                if (form != null && forms.stream().noneMatch(known -> ItemStack.isSameItemSameComponents(known, form))) {
                    forms.add(form);
                }
            }
        }
        return forms;
    }

    private static @Nullable ItemStack filled(ItemStack empty, Fluid fluid) {
        if (fluid.isSame(Fluids.EMPTY) || !fluid.isSource(fluid.defaultFluidState())) {
            return null;
        }
        FluidStacksResourceHandler tank = new FluidStacksResourceHandler(1, PLENTY);
        tank.set(0, FluidResource.of(fluid), PLENTY);
        ItemStack form = FluidContainers.fill(empty, ItemStack.EMPTY, tank, FluidResource.of(fluid));
        return form == null || form.isEmpty() ? null : form;
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
        for (int slot : inventory.outputOrder()) {
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
