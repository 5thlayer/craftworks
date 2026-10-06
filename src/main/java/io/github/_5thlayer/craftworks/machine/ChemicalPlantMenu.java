// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.machine;

import java.util.Objects;
import java.util.Optional;

import io.github._5thlayer.craftworks.network.AssemblerHeldPacket;
import io.github._5thlayer.craftworks.network.CraftworksNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.IndexModifier;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;
import net.neoforged.neoforge.transfer.item.ResourceHandlerSlot;
import org.jspecify.annotations.Nullable;

/**
 * The Chemical Plant's menu: two inputs and the product, the four fluid boxes, the Held recipe and how far its
 * craft is. As the Assembler's, no recipe is picked here and none cleared: the recipe viewer's Fill Recipe
 * lands on {@link #request}, and a plant's recipe is replaced, never removed.
 *
 * <p>The Held recipe crosses to the client as {@link AssemblerHeldPacket} when it changes, since the client has
 * no recipe manager to read the ingredients each slot is ghosted with. Progress, duration, energy and each
 * box (which fluid, by its registry id, and how much) ride in data slots, with the volume of the two output
 * boxes, which the Held recipe sizes.
 */
public final class ChemicalPlantMenu extends AbstractContainerMenu implements HeldRecipeMenu {

    private static final int DATA_PROGRESS = 0;
    private static final int DATA_DURATION = 1;
    private static final int DATA_ENERGY = 2;
    private static final int DATA_ENERGY_CAPACITY = 3;
    /** Each box takes two: the fluid's registry id, then its amount. */
    private static final int DATA_BOXES = 4;
    private static final int DATA_OUTPUT_CAPACITY = DATA_BOXES + 2 * ChemicalPlantFluids.SIZE;
    private static final int DATA_COUNT = DATA_OUTPUT_CAPACITY + ChemicalPlantFluids.OUTPUTS;

    public static final int INPUT_X = AssemblerMenu.INPUT_X;
    public static final int INPUT_Y = AssemblerMenu.INPUT_Y;
    public static final int PRODUCT_X = AssemblerMenu.PRODUCT_X;
    /** Lower than the Assembler's: the four gauges and the energy bar take a row each between the slots and the inventory. */
    public static final int INVENTORY_Y = 96;

    private final @Nullable ChemicalPlantBlockEntity machine;
    private final BlockPos pos;
    private final ContainerData data;
    private final Player player;

    /** What the screen shows of the Held recipe; the server sends it, and it is empty until then. */
    private Optional<AssemblerMenu.Held> held = Optional.empty();

    /** The Held recipe's id as last sent to the client, or unset before the first send. */
    private @Nullable Optional<Identifier> sent;

    /** Client side: the slots stand over a stub the menu's own sync fills. */
    public ChemicalPlantMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf buffer) {
        this(containerId, playerInventory, null, buffer.readBlockPos(), new ItemStacksResourceHandler(ChemicalPlantSlots.SIZE),
                new SimpleContainerData(DATA_COUNT));
    }

    private ChemicalPlantMenu(int containerId, Inventory playerInventory, @Nullable ChemicalPlantBlockEntity machine, BlockPos pos,
            ItemStacksResourceHandler inventory, ContainerData data) {
        super(ChemicalPlants.MENU.get(), containerId);
        this.machine = machine;
        this.pos = pos;
        this.data = data;
        this.player = playerInventory.player;
        IndexModifier<ItemResource> modifier = inventory::set;
        for (int slot = 0; slot < ChemicalPlantSlots.INPUTS; slot++) {
            addSlot(new InputSlot(inventory, modifier, slot, INPUT_X + slot * 18, INPUT_Y));
        }
        addSlot(new AssemblerMenu.OutputSlot(inventory, modifier, ChemicalPlantSlots.PRODUCT, PRODUCT_X, INPUT_Y));
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(playerInventory, column + row * 9 + 9, 8 + column * 18, INVENTORY_Y + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(playerInventory, column, 8 + column * 18, INVENTORY_Y + 58));
        }
        addDataSlots(data);
    }

    /** Server side, over the machine's own inventory. */
    static ChemicalPlantMenu open(int containerId, Inventory playerInventory, ChemicalPlantBlockEntity machine) {
        ContainerData data = new ContainerData() {
            @Override
            public int get(int index) {
                if (index >= DATA_BOXES && index < DATA_OUTPUT_CAPACITY) {
                    FluidStack contents = machine.fluids().contents((index - DATA_BOXES) / 2);
                    return (index - DATA_BOXES) % 2 == 0 ? BuiltInRegistries.FLUID.getId(contents.getFluid()) : contents.getAmount();
                }
                if (index >= DATA_OUTPUT_CAPACITY && index < DATA_COUNT) {
                    return machine.fluids().capacity(ChemicalPlantFluids.INPUTS + index - DATA_OUTPUT_CAPACITY);
                }
                return switch (index) {
                    case DATA_PROGRESS -> machine.craftProgress();
                    case DATA_DURATION -> machine.craftDuration();
                    case DATA_ENERGY -> machine.energy();
                    case DATA_ENERGY_CAPACITY -> machine.energyCapacity();
                    default -> 0;
                };
            }

            @Override
            public void set(int index, int value) {
            }

            @Override
            public int getCount() {
                return DATA_COUNT;
            }
        };
        return new ChemicalPlantMenu(containerId, playerInventory, machine, machine.getBlockPos(), machine.inventory(), data);
    }

    public BlockPos pos() {
        return pos;
    }

    /** The Held recipe as the client was last told it, or empty. */
    public Optional<AssemblerMenu.Held> held() {
        return held;
    }

    @Override
    public void show(Optional<AssemblerMenu.Held> held) {
        this.held = held;
    }

    /** How far the craft under way is, from 0 to 1; 0 with no recipe that runs. */
    public float progress() {
        int duration = data.get(DATA_DURATION);
        return duration <= 0 ? 0f : Math.min(1f, (float) data.get(DATA_PROGRESS) / duration);
    }

    public int energy() {
        return data.get(DATA_ENERGY);
    }

    public int energyCapacity() {
        return data.get(DATA_ENERGY_CAPACITY);
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
                : data.get(DATA_OUTPUT_CAPACITY + box - ChemicalPlantFluids.INPUTS);
    }

    /** Whether input {@code slot} holds less than one craft of the Held recipe needs, so the screen draws it red. */
    public boolean isShort(int slot) {
        return held.filter(recipe -> AssemblerSlots.isShort(slot, recipe.ingredients(),
                slots.get(slot).getItem().getCount(), SizedIngredient::count)).isPresent();
    }

    /** Sends the client the Held recipe whenever it is not the one it was last told. */
    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (machine != null && player instanceof ServerPlayer server && machine.getLevel() instanceof ServerLevel level) {
            Optional<Identifier> now = machine.heldRecipe();
            if (!Objects.equals(sent, now)) {
                sent = now;
                Optional<AssemblerMenu.Held> view = now.flatMap(id -> HeldRecipes.find(level, id)
                        .map(holder -> new AssemblerMenu.Held(id, holder.value().ingredients(), holder.value().results())));
                CraftworksNetwork.sendToPlayer(server, new AssemblerHeldPacket(containerId, view));
            }
        }
    }

    /**
     * Holds {@code id} if the player may set it, or tells them why not. The setter Fill Recipe lands on, so a
     * refusal is never a gesture that silently did nothing. The Lock source is asked here, of this player, and
     * never again (ADR-0013).
     */
    @Override
    public HoldVerdict request(ServerPlayer player, Identifier id) {
        if (machine == null) {
            return HoldVerdict.NOT_ASSEMBLING;
        }
        HoldVerdict verdict = ChemicalPlantRecipes.verdict(player, id);
        if (verdict.held()) {
            machine.setHeldRecipe(id, player);
        } else {
            player.sendSystemMessage(Component.translatable(verdict.messageKey("chemical_plant"), HeldRecipes.name(player.level(), id)));
        }
        return verdict;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index < ChemicalPlantSlots.SIZE) {
            if (!moveItemStackTo(stack, ChemicalPlantSlots.SIZE, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, 0, ChemicalPlantSlots.INPUTS, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return original;
    }

    @Override
    public boolean stillValid(Player player) {
        if (machine != null && machine.isRemoved()) {
            return false;
        }
        // Vanilla's buffer in Container.stillValidBlockEntity, so a screen opened at full reach stays open.
        return player.isWithinBlockInteractionRange(pos, 4.0);
    }

    /**
     * The server asks the machine; the client asks the Held recipe it was sent through the same rule, so a
     * wrong item is refused in the hand rather than placed and put back by the sync.
     */
    private final class InputSlot extends ResourceHandlerSlot {
        InputSlot(ResourceHandler<ItemResource> handler, IndexModifier<ItemResource> modifier, int index, int x, int y) {
            super(handler, modifier, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            if (stack.isEmpty()) {
                return false;
            }
            if (machine != null) {
                return machine.accepts(getSlotIndex(), ItemResource.of(stack));
            }
            return held.filter(recipe -> AssemblerSlots.accepts(getSlotIndex(), recipe.ingredients(), stack,
                    (SizedIngredient sized, ItemStack item) -> sized.ingredient().test(item))).isPresent();
        }
    }
}
