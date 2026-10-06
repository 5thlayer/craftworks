// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.SimpleContainerData;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;
import org.jspecify.annotations.Nullable;

/**
 * A fluid machine's menu: its item slots, as {@link HeldMachineMenu} lays them out, and its fluid boxes. Each
 * box (which fluid, by its registry id, and how much) rides in data slots after the shared ones, with the
 * volume of every box, which the Held recipe sizes.
 */
public final class FluidMachineMenu extends HeldMachineMenu<FluidMachineBlockEntity> {

    /** Each box takes two: the fluid's registry id, then its amount. */
    private static final int DATA_BOXES = DATA_SHARED;

    /** Lower than the Assembler's: the gauges and the energy bar take a row each between the slots and the inventory. */
    public static final int INVENTORY_Y = 96;

    private final FluidMachine description;

    /** Client side: the slots stand over a stub the menu's own sync fills. */
    public FluidMachineMenu(MenuType<?> type, int containerId, Inventory playerInventory, RegistryFriendlyByteBuf buffer,
            FluidMachine description) {
        this(type, containerId, playerInventory, null, buffer.readBlockPos(), description,
                new ItemStacksResourceHandler(description.slots().size()), new SimpleContainerData(dataCount(description)));
    }

    private FluidMachineMenu(MenuType<?> type, int containerId, Inventory playerInventory, @Nullable FluidMachineBlockEntity machine,
            BlockPos pos, FluidMachine description, ItemStacksResourceHandler inventory, ContainerData data) {
        super(type, containerId, playerInventory, machine, pos, description.slots(), inventory, data, INVENTORY_Y);
        this.description = description;
    }

    private static int dataCapacities(FluidMachine description) {
        return DATA_BOXES + 2 * description.boxes();
    }

    private static int dataCount(FluidMachine description) {
        return dataCapacities(description) + description.boxes();
    }

    /** Server side, over the machine's own inventory. */
    static FluidMachineMenu open(MenuType<?> menuType, int containerId, Inventory playerInventory, FluidMachineBlockEntity machine) {
        FluidMachine description = machine.machine();
        int capacities = dataCapacities(description);
        ContainerData data = data(machine, dataCount(description), index -> {
            if (index < capacities) {
                return fluidData(machine.fluids().contents((index - DATA_BOXES) / 2), (index - DATA_BOXES) % 2);
            }
            return machine.fluids().capacity(index - capacities);
        });
        return new FluidMachineMenu(menuType, containerId, playerInventory, machine, machine.getBlockPos(), description,
                machine.inventory(), data);
    }

    @Override
    protected MachineKind kind() {
        return description.kind();
    }

    @Override
    protected HoldVerdict verdict(ServerPlayer player, Identifier id) {
        return FluidMachineRecipes.verdict(description, player, id);
    }

    /** The machine this is the menu of, which the screen reads its box counts from. */
    public FluidMachine description() {
        return description;
    }

    /** What is in box {@code box} (the inputs, then the outputs), or empty: the fluid and how much, as the server last told it. */
    public FluidStack fluid(int box) {
        return fluidAt(DATA_BOXES + 2 * box);
    }

    /** What box {@code box} holds at most, in mB, as the Held recipe sizes it and the server last told it. */
    public int fluidCapacity(int box) {
        return data.get(dataCapacities(description) + box);
    }
}
