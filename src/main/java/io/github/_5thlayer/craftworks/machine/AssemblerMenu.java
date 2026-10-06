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
 * The fluid box (which fluid, by its registry id, and how much) rides in data slots after the shared ones; it
 * is drawn only by tiers 2 and 3, which {@link #hasFluidBox} tells the screen.
 */
public final class AssemblerMenu extends HeldMachineMenu<AssemblerBlockEntity> {

    /** The fluid box takes two: the fluid's registry id, then its amount. */
    private static final int DATA_FLUID = DATA_SHARED;
    private static final int DATA_COUNT = DATA_SHARED + 2;

    public static final int REMAINDERS_X = PRODUCT_X + 18;
    public static final int INVENTORY_Y = 84;

    private final AssemblerTier tier;

    /** Client side: the slots stand over a stub the menu's own sync fills. */
    public AssemblerMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf buffer) {
        this(containerId, playerInventory, null, buffer.readBlockPos(), new ItemStacksResourceHandler(AssemblerSlots.SIZE),
                new SimpleContainerData(DATA_COUNT));
    }

    private AssemblerMenu(int containerId, Inventory playerInventory, @Nullable AssemblerBlockEntity machine, BlockPos pos,
            ItemStacksResourceHandler inventory, ContainerData data) {
        super(Assemblers.MENU.get(), containerId, playerInventory, machine, pos, AssemblerSlots.LAYOUT, inventory, data, INVENTORY_Y);
        // The client has no machine to ask, but the block it opened is there.
        this.tier = machine != null ? machine.tier()
                : playerInventory.player.level().getBlockState(pos).getBlock() instanceof AssemblerBlock block ? block.tier() : AssemblerTier.ONE;
    }

    /** Server side, over the machine's own inventory. */
    static AssemblerMenu open(int containerId, Inventory playerInventory, AssemblerBlockEntity machine) {
        ContainerData data = data(machine, DATA_COUNT, index -> fluidData(machine.fluidBox().contents(), index - DATA_FLUID));
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

    /** Whether this Assembler's tier has a fluid box, and so the screen a gauge for it. */
    public boolean hasFluidBox() {
        return tier.hasFluidBox();
    }

    /** What is in the fluid box, or empty: the fluid and how much, as the server last told it. */
    public FluidStack fluid() {
        return fluidAt(DATA_FLUID);
    }
}
