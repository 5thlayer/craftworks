// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;
import org.jspecify.annotations.Nullable;

/**
 * The Chemical Plant's menu: two inputs and the product, as {@link HeldMachineMenu} lays them out, and the four
 * fluid boxes. Each box (which fluid, by its registry id, and how much) rides in data slots after the shared
 * ones, with the volume of the two output boxes, which the Held recipe sizes.
 */
public final class ChemicalPlantMenu extends HeldMachineMenu<ChemicalPlantBlockEntity> {

    /** Each box takes two: the fluid's registry id, then its amount. */
    private static final int DATA_BOXES = DATA_SHARED;
    private static final int DATA_OUTPUT_CAPACITY = DATA_BOXES + 2 * ChemicalPlantFluids.SIZE;
    private static final int DATA_COUNT = DATA_OUTPUT_CAPACITY + ChemicalPlantFluids.OUTPUTS;

    /** Lower than the Assembler's: the four gauges and the energy bar take a row each between the slots and the inventory. */
    public static final int INVENTORY_Y = 96;

    /** Client side: the slots stand over a stub the menu's own sync fills. */
    public ChemicalPlantMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf buffer) {
        this(containerId, playerInventory, null, buffer.readBlockPos(), new ItemStacksResourceHandler(ChemicalPlantSlots.SIZE),
                new SimpleContainerData(DATA_COUNT));
    }

    private ChemicalPlantMenu(int containerId, Inventory playerInventory, @Nullable ChemicalPlantBlockEntity machine, BlockPos pos,
            ItemStacksResourceHandler inventory, ContainerData data) {
        super(ChemicalPlants.MENU.get(), containerId, playerInventory, machine, pos, ChemicalPlantSlots.LAYOUT, inventory, data,
                INVENTORY_Y);
    }

    /** Server side, over the machine's own inventory. */
    static ChemicalPlantMenu open(int containerId, Inventory playerInventory, ChemicalPlantBlockEntity machine) {
        ContainerData data = data(machine, DATA_COUNT, index -> {
            if (index < DATA_OUTPUT_CAPACITY) {
                FluidStack contents = machine.fluids().contents((index - DATA_BOXES) / 2);
                return (index - DATA_BOXES) % 2 == 0 ? BuiltInRegistries.FLUID.getId(contents.getFluid()) : contents.getAmount();
            }
            return machine.fluids().capacity(ChemicalPlantFluids.outputBox(index - DATA_OUTPUT_CAPACITY));
        });
        return new ChemicalPlantMenu(containerId, playerInventory, machine, machine.getBlockPos(), machine.inventory(), data);
    }

    @Override
    protected MachineKind kind() {
        return MachineKind.CHEMICAL_PLANT;
    }

    @Override
    protected HoldVerdict verdict(ServerPlayer player, Identifier id) {
        return ChemicalPlantRecipes.verdict(player, id);
    }

    /** What is in box {@code box} (the two inputs, then the two outputs), or empty: the fluid and how much, as the server last told it. */
    public FluidStack fluid(int box) {
        int amount = data.get(DATA_BOXES + 2 * box + 1);
        Fluid fluid = BuiltInRegistries.FLUID.byId(data.get(DATA_BOXES + 2 * box));
        return amount <= 0 || fluid == Fluids.EMPTY ? FluidStack.EMPTY : new FluidStack(fluid, amount);
    }

    /** What box {@code box} holds at most, in mB: a bucket for an input, and what the Held recipe sizes an output at. */
    public int fluidCapacity(int box) {
        return ChemicalPlantFluids.isInput(box) ? ChemicalPlantFluids.INPUT_CAPACITY
                : data.get(DATA_OUTPUT_CAPACITY + ChemicalPlantFluids.binding(box));
    }
}
