// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;
import org.jspecify.annotations.Nullable;

/**
 * The Assembler's menu: five inputs, the product and the remainders, as {@link HeldMachineMenu} lays them out.
 * Each fluid box (which fluid, by its registry id, and how much) rides in data slots after the shared ones, with the
 * volume of every box, which the Held recipe sizes; every tier draws them, and the inventory stands lower to give the
 * gauges a row.
 */
public final class AssemblerMenu extends HeldMachineMenu<AssemblerBlockEntity> {

    private static final FluidLayout LAYOUT = FluidLayout.ASSEMBLER;

    /** Each box takes two: the fluid's registry id, then its amount. */
    private static final int DATA_BOXES = DATA_SHARED;
    private static final int DATA_CAPACITIES = DATA_BOXES + 2 * LAYOUT.boxes();
    private static final int DATA_COUNT = DATA_CAPACITIES + LAYOUT.boxes();

    public static final int REMAINDERS_X = PRODUCT_X + 18;
    /** Where the player's inventory starts: the gauges and the energy bar take a row each between the slots and it. */
    public static final int INVENTORY_Y_WITH_FLUIDS = 96;

    /** Client side: the slots stand over a stub the menu's own sync fills. */
    public AssemblerMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf buffer) {
        this(containerId, playerInventory, null, buffer.readBlockPos(), new ItemStacksResourceHandler(AssemblerSlots.SIZE),
                new SimpleContainerData(DATA_COUNT));
    }

    private AssemblerMenu(int containerId, Inventory playerInventory, @Nullable AssemblerBlockEntity machine, BlockPos pos,
            ItemStacksResourceHandler inventory, ContainerData data) {
        super(Assemblers.MENU.get(), containerId, playerInventory, machine, pos, AssemblerSlots.LAYOUT, inventory, data,
                INVENTORY_Y_WITH_FLUIDS);
    }

    /** Where the player's inventory starts: lower for the gauges, and the screen is that much taller. */
    public int inventoryY() {
        return INVENTORY_Y_WITH_FLUIDS;
    }

    /** Server side, over the machine's own inventory. */
    static AssemblerMenu open(int containerId, Inventory playerInventory, AssemblerBlockEntity machine) {
        ContainerData data = data(machine, DATA_COUNT, index -> {
            if (index < DATA_CAPACITIES) {
                return fluidData(machine.fluids().contents((index - DATA_BOXES) / 2), (index - DATA_BOXES) % 2);
            }
            return machine.fluids().displayCapacity(index - DATA_CAPACITIES);
        });
        return new AssemblerMenu(containerId, playerInventory, machine, machine.getBlockPos(), machine.inventory(), data);
    }

    @Override
    protected MachineKind kind() {
        return MachineKind.ASSEMBLER;
    }

    @Override
    protected HoldVerdict verdict(ServerPlayer player, Identifier id) {
        return HeldRecipes.verdict(player, machine.tier(), id);
    }

    /** What is in box {@code box} (the inputs, then the outputs), or empty: the fluid and how much, as the server last told it. */
    public FluidStack fluid(int box) {
        return fluidAt(DATA_BOXES + 2 * box);
    }

    /** What box {@code box} holds at most, in mB, as the Held recipe sizes it and the server last told it. */
    public int fluidCapacity(int box) {
        return data.get(DATA_CAPACITIES + box);
    }
}
