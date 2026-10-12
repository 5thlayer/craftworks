// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.machine;

import java.util.Objects;
import java.util.Optional;

import io.github._5thlayer.craftworks.network.CraftworksNetwork;
import io.github._5thlayer.craftworks.network.HeldRecipeSyncPacket;
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
 * The Assembler's menu: its five inputs in a row, its four shared outputs in a block of two by two with the drain
 * and fill slots of tiers 2 and 3 in a column beside them, then the player's inventory; the Held recipe and how far its craft is. No recipe is picked here and none cleared: the recipe
 * viewer's Fill Recipe lands on {@link #request}, and the Held recipe is replaced, never removed.
 *
 * <p>The Held recipe crosses to the client as {@link HeldRecipeSyncPacket} when it changes, since the client
 * has no recipe manager to read the ingredients each slot is ghosted with. Progress, duration and energy ride
 * in the first data slots ({@link #DATA_SHARED}). Each fluid box (which fluid, by its registry id, and how much)
 * rides in data slots after them, with the volume of every box; the inventory stands lower to give the gauges a
 * row. Whether the drain and fill slots are in use, which the tier decides, rides last: a tier-1 menu has the slots
 * but they are inactive, drawn and clicked by nobody.
 */
public final class AssemblerMenu extends AbstractContainerMenu {

    private static final FluidLayout LAYOUT = FluidLayout.ASSEMBLER;
    private static final MachineSlots SLOTS = AssemblerSlots.LAYOUT;

    private static final int DATA_PROGRESS = 0;
    private static final int DATA_DURATION = 1;
    private static final int DATA_ENERGY = 2;
    private static final int DATA_ENERGY_CAPACITY = 3;
    /** The data slots before the fluid boxes': progress, duration, energy and its capacity. */
    private static final int DATA_SHARED = 4;
    /** Each box takes two: the fluid's registry id, then its amount. */
    private static final int DATA_CAPACITIES = DATA_SHARED + 2 * LAYOUT.boxes();
    /** What the Assembler is doing, a {@link MachineState} ordinal, for the screen's status line. */
    private static final int DATA_STATE = DATA_CAPACITIES + LAYOUT.boxes();
    /** 1 when the tier has a drain slot and a fill slot, else 0. */
    private static final int DATA_CONTAINERS = DATA_STATE + 1;
    private static final int DATA_COUNT = DATA_CONTAINERS + 1;

    public static final int INPUT_X = 8;
    public static final int INPUT_Y = 36;
    /** The drain slot above the fill slot, in the column left of the outputs. */
    public static final int CONTAINER_X = 116;
    public static final int DRAIN_Y = 16;
    public static final int FILL_Y = INPUT_Y;
    /** The shared outputs, two by two: the product lands in the first, and the rest in any with room. */
    public static final int OUTPUT_X = 134;
    public static final int OUTPUT_Y = 16;
    /** Between the outputs' rows, which stand on the head's row and the inputs'. */
    public static final int OUTPUT_ROW = INPUT_Y - OUTPUT_Y;
    public static final int OUTPUT_COLUMNS = 2;
    /**
     * One row of the panel: a bar or a gauge and the gap below it. Two less than the suite's other machines space
     * theirs, so the Assembler's three rows of gauges keep the panel 240 tall, which fits a 720-line window at GUI
     * scale 3.
     */
    public static final int ROW = 16;
    public static final int PROGRESS_Y = 56;
    /** The gauges' top row; they run down in two columns ({@link FluidGaugeLayout}). */
    public static final int GAUGES_Y = PROGRESS_Y + ROW;
    public static final int ENERGY_Y = GAUGES_Y + FluidGaugeLayout.of(LAYOUT).rows() * ROW;
    public static final int STATUS_Y = ENERGY_Y + ROW;
    /** Where the player's inventory starts, its label clear of the status line above it, as the Harvester's is. */
    public static final int INVENTORY_Y = STATUS_Y + 22;

    private final @Nullable AssemblerBlockEntity assembler;
    private final BlockPos pos;
    private final ContainerData data;
    private final Player player;

    /** What the screen shows of the Held recipe; the server sends it, and it is empty until then. */
    private Optional<HeldRecipeView> held = Optional.empty();

    /** The Held recipe's id as last sent to the client, or unset before the first send. */
    private @Nullable Optional<Identifier> sent;

    /** Client side: the slots stand over a stub the menu's own sync fills. */
    public AssemblerMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf buffer) {
        this(containerId, playerInventory, null, buffer.readBlockPos(), new ItemStacksResourceHandler(AssemblerSlots.SIZE),
                new SimpleContainerData(DATA_COUNT));
    }

    /**
     * The inputs from {@link #INPUT_X}, the outputs from {@link #OUTPUT_X}, the drain and fill slots at
     * {@link #CONTAINER_X}, and the player's inventory from {@link #INVENTORY_Y} down. The client's {@code assembler} is null, and its slots stand
     * over a stub the menu's own sync fills.
     */
    private AssemblerMenu(int containerId, Inventory playerInventory, @Nullable AssemblerBlockEntity assembler, BlockPos pos,
            ItemStacksResourceHandler inventory, ContainerData data) {
        super(Assemblers.MENU.get(), containerId);
        this.assembler = assembler;
        this.pos = pos;
        this.data = data;
        this.player = playerInventory.player;
        IndexModifier<ItemResource> modifier = inventory::set;
        for (int slot = 0; slot < SLOTS.inputs(); slot++) {
            addSlot(new InputSlot(inventory, modifier, slot, INPUT_X + slot * 18, INPUT_Y));
        }
        for (int slot = SLOTS.product(); slot < SLOTS.product() + SLOTS.outputs(); slot++) {
            int output = slot - SLOTS.product();
            addSlot(new OutputSlot(inventory, modifier, slot,
                    OUTPUT_X + output % OUTPUT_COLUMNS * 18, OUTPUT_Y + output / OUTPUT_COLUMNS * OUTPUT_ROW));
        }
        addSlot(new ContainerSlot(inventory, modifier, AssemblerSlots.DRAIN, CONTAINER_X, DRAIN_Y));
        addSlot(new ContainerSlot(inventory, modifier, AssemblerSlots.FILL, CONTAINER_X, FILL_Y));
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

    /** Server side, over the Assembler's own inventory. */
    static AssemblerMenu open(int containerId, Inventory playerInventory, AssemblerBlockEntity assembler) {
        ContainerData data = new ContainerData() {
            @Override
            public int get(int index) {
                if (index < DATA_SHARED) {
                    return switch (index) {
                        case DATA_PROGRESS -> assembler.craftProgress();
                        case DATA_DURATION -> assembler.craftDuration();
                        case DATA_ENERGY -> assembler.energy();
                        case DATA_ENERGY_CAPACITY -> assembler.energyCapacity();
                        default -> 0;
                    };
                }
                if (index < DATA_CAPACITIES) {
                    return fluidData(assembler.fluids().contents((index - DATA_SHARED) / 2), (index - DATA_SHARED) % 2);
                }
                if (index < DATA_STATE) {
                    return assembler.fluids().displayCapacity(index - DATA_CAPACITIES);
                }
                if (index == DATA_STATE) {
                    return assembler.state().ordinal();
                }
                return index == DATA_CONTAINERS && assembler.tier().hasContainerSlots() ? 1 : 0;
            }

            @Override
            public void set(int index, int value) {
            }

            @Override
            public int getCount() {
                return DATA_COUNT;
            }
        };
        return new AssemblerMenu(containerId, playerInventory, assembler, assembler.getBlockPos(), assembler.inventory(), data);
    }

    /** The two data slots of a fluid box holding {@code contents}: {@code half} 0 is the fluid's registry id, 1 its amount. */
    private static int fluidData(FluidStack contents, int half) {
        return half == 0 ? BuiltInRegistries.FLUID.getId(contents.getFluid()) : contents.getAmount();
    }

    /** The fluid box whose data slots start at {@code index}, as the server last told it, or empty. */
    private FluidStack fluidAt(int index) {
        int amount = data.get(index + 1);
        Fluid fluid = BuiltInRegistries.FLUID.byId(data.get(index));
        return amount <= 0 || fluid == Fluids.EMPTY ? FluidStack.EMPTY : new FluidStack(fluid, amount);
    }

    /** Whether this tier has a drain slot and a fill slot: tiers 2 and 3. */
    public boolean hasContainerSlots() {
        return assembler != null ? assembler.tier().hasContainerSlots() : data.get(DATA_CONTAINERS) > 0;
    }

    /** Where the player's inventory starts, {@link #INVENTORY_Y}: below the gauges, the energy bar and the status line. */
    public int inventoryY() {
        return INVENTORY_Y;
    }

    public BlockPos pos() {
        return pos;
    }

    /** Where the Assembler's slots are among the menu's, ahead of the player's. */
    public MachineSlots layout() {
        return SLOTS;
    }

    /** The Held recipe as the client was last told it, or empty. */
    public Optional<HeldRecipeView> held() {
        return held;
    }

    /** The client taking the Held recipe the server sent. */
    public void show(Optional<HeldRecipeView> held) {
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

    /** What the Assembler is doing, as the server last sent it. */
    public MachineState state() {
        return MachineState.of(data.get(DATA_STATE));
    }

    public int energyCapacity() {
        return data.get(DATA_ENERGY_CAPACITY);
    }

    /** What is in box {@code box} (the inputs, then the outputs), or empty: the fluid and how much, as the server last told it. */
    public FluidStack fluid(int box) {
        return fluidAt(DATA_SHARED + 2 * box);
    }

    /** What box {@code box} holds at most, in mB, as the Held recipe sizes it and the server last told it. */
    public int fluidCapacity(int box) {
        return data.get(DATA_CAPACITIES + box);
    }

    /** Whether input {@code slot} holds less than one craft of the Held recipe needs, so the screen draws it red. */
    public boolean isShort(int slot) {
        return held.filter(recipe -> SLOTS.isShort(slot, recipe.ingredients(),
                slots.get(slot).getItem().getCount(), SizedIngredient::count)).isPresent();
    }

    /** Sends the client the Held recipe whenever it is not the one it was last told. */
    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (assembler != null && player instanceof ServerPlayer server && assembler.getLevel() instanceof ServerLevel level) {
            Optional<Identifier> now = assembler.heldRecipe();
            if (!Objects.equals(sent, now)) {
                sent = now;
                Optional<HeldRecipeView> view = now.flatMap(id -> HeldRecipes.find(level, id)
                        .map(holder -> new HeldRecipeView(id, holder.value().ingredients(), holder.value().results())));
                CraftworksNetwork.sendToPlayer(server, new HeldRecipeSyncPacket(containerId, view));
            }
        }
    }

    /**
     * Holds {@code id} if the player may set it, or tells them why not. The setter Fill Recipe lands on,
     * so a refusal is never a gesture that silently did nothing. The Lock source is asked here, of this
     * player, and never again (ADR-0013).
     */
    public HoldVerdict request(ServerPlayer player, Identifier id) {
        if (assembler == null) {
            return HoldVerdict.NOT_ASSEMBLING;
        }
        HoldVerdict verdict = HeldRecipes.verdict(player, assembler.tier(), id);
        if (verdict.held()) {
            assembler.setHeldRecipe(id, player);
        } else {
            player.sendSystemMessage(Component.translatable(verdict.messageKey(), HeldRecipes.name(player.level(), id)));
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
        if (index < SLOTS.size()) {
            if (!moveItemStackTo(stack, SLOTS.size(), slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else {
            // As the item face routes: an ingredient to its input first, then a filled container to the drain
            // slot and an empty one to the fill slot, which each take only what they are for.
            boolean moved = moveItemStackTo(stack, 0, SLOTS.inputs(), false);
            if (!stack.isEmpty()) {
                moved |= moveItemStackTo(stack, AssemblerSlots.DRAIN, SLOTS.size(), false);
            }
            if (!moved) {
                return ItemStack.EMPTY;
            }
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
        if (assembler != null && assembler.isRemoved()) {
            return false;
        }
        // Vanilla's buffer in Container.stillValidBlockEntity, so a screen opened at full reach stays open.
        return player.isWithinBlockInteractionRange(pos, 4.0);
    }

    /**
     * The server asks the Assembler; the client asks the Held recipe it was sent through the same rule, so a
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
            if (assembler != null) {
                return assembler.accepts(getSlotIndex(), ItemResource.of(stack));
            }
            return held.filter(recipe -> SLOTS.accepts(getSlotIndex(), recipe.ingredients(), stack,
                    (SizedIngredient sized, ItemStack item) -> sized.ingredient().test(item))).isPresent();
        }
    }

    /**
     * The drain slot takes a filled fluid container and the fill slot an empty one, in a tier that has them, and
     * is not there in one that has not. The client and the server ask the same thing of the item itself: the
     * Held recipe is not asked, so a container waits where the player put it until it can move.
     */
    private final class ContainerSlot extends ResourceHandlerSlot {
        ContainerSlot(ResourceHandler<ItemResource> handler, IndexModifier<ItemResource> modifier, int index, int x, int y) {
            super(handler, modifier, index, x, y);
        }

        @Override
        public boolean isActive() {
            return hasContainerSlots();
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return hasContainerSlots() && AssemblerContainers.placeable(getSlotIndex(), stack);
        }
    }

    /** An output takes nothing from the player. */
    private static final class OutputSlot extends ResourceHandlerSlot {
        OutputSlot(ResourceHandler<ItemResource> handler, IndexModifier<ItemResource> modifier, int index, int x, int y) {
            super(handler, modifier, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }
    }
}
