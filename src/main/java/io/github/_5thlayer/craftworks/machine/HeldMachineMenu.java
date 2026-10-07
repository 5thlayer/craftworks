// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.machine;

import java.util.Objects;
import java.util.Optional;
import java.util.function.IntUnaryOperator;

import io.github._5thlayer.craftworks.network.CraftworksNetwork;
import io.github._5thlayer.craftworks.network.HeldRecipeSyncPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.MenuType;
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
 * The menu of a machine that makes its Held recipe, an Assembler's: its inputs in a row,
 * then its outputs, then the player's inventory; the Held recipe and how far its craft is. No recipe is picked
 * here and none cleared: the recipe viewer's Fill Recipe lands on {@link #request}, and the Held recipe is
 * replaced, never removed.
 *
 * <p>The Held recipe crosses to the client as {@link HeldRecipeSyncPacket} when it changes, since the client
 * has no recipe manager to read the ingredients each slot is ghosted with. Progress, duration and energy ride
 * in the first data slots, and a machine's fluid boxes in those after ({@link #DATA_SHARED}).
 */
public abstract class HeldMachineMenu<M extends AssemblerBlockEntity> extends AbstractContainerMenu {

    private static final int DATA_PROGRESS = 0;
    private static final int DATA_DURATION = 1;
    private static final int DATA_ENERGY = 2;
    private static final int DATA_ENERGY_CAPACITY = 3;
    /** The data slots every machine's menu has; a machine's own come after. */
    protected static final int DATA_SHARED = 4;

    public static final int INPUT_X = 8;
    public static final int INPUT_Y = 36;
    public static final int PRODUCT_X = 134;

    protected final @Nullable M machine;
    private final BlockPos pos;
    protected final ContainerData data;
    private final Player player;
    private final MachineSlots layout;

    /** What the screen shows of the Held recipe; the server sends it, and it is empty until then. */
    private Optional<HeldRecipeView> held = Optional.empty();

    /** The Held recipe's id as last sent to the client, or unset before the first send. */
    private @Nullable Optional<Identifier> sent;

    /**
     * The inputs from {@link #INPUT_X}, the product at {@link #PRODUCT_X} and any further output beside it, and
     * the player's inventory from {@code inventoryY} down. The client's {@code machine} is null, and its slots
     * stand over a stub the menu's own sync fills.
     */
    protected HeldMachineMenu(MenuType<?> type, int containerId, Inventory playerInventory, @Nullable M machine, BlockPos pos,
            MachineSlots layout, ItemStacksResourceHandler inventory, ContainerData data, int inventoryY) {
        super(type, containerId);
        this.machine = machine;
        this.pos = pos;
        this.data = data;
        this.player = playerInventory.player;
        this.layout = layout;
        IndexModifier<ItemResource> modifier = inventory::set;
        for (int slot = 0; slot < layout.inputs(); slot++) {
            addSlot(new InputSlot(inventory, modifier, slot, INPUT_X + slot * 18, INPUT_Y));
        }
        for (int slot = layout.product(); slot < layout.size(); slot++) {
            addSlot(new OutputSlot(inventory, modifier, slot, PRODUCT_X + (slot - layout.product()) * 18, INPUT_Y));
        }
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(playerInventory, column + row * 9 + 9, 8 + column * 18, inventoryY + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(playerInventory, column, 8 + column * 18, inventoryY + 58));
        }
        addDataSlots(data);
    }

    /**
     * The server's data slots over {@code machine}: the shared ones, then {@code own} for each index from
     * {@link #DATA_SHARED} up to {@code count}.
     */
    protected static ContainerData data(AssemblerBlockEntity machine, int count, IntUnaryOperator own) {
        return new ContainerData() {
            @Override
            public int get(int index) {
                return switch (index) {
                    case DATA_PROGRESS -> machine.craftProgress();
                    case DATA_DURATION -> machine.craftDuration();
                    case DATA_ENERGY -> machine.energy();
                    case DATA_ENERGY_CAPACITY -> machine.energyCapacity();
                    default -> index < count ? own.applyAsInt(index) : 0;
                };
            }

            @Override
            public void set(int index, int value) {
            }

            @Override
            public int getCount() {
                return count;
            }
        };
    }

    /** The two data slots of a fluid box holding {@code contents}: {@code half} 0 is the fluid's registry id, 1 its amount. */
    protected static int fluidData(FluidStack contents, int half) {
        return half == 0 ? BuiltInRegistries.FLUID.getId(contents.getFluid()) : contents.getAmount();
    }

    /** The fluid box whose data slots start at {@code index}, as the server last told it, or empty. */
    protected FluidStack fluidAt(int index) {
        int amount = data.get(index + 1);
        Fluid fluid = BuiltInRegistries.FLUID.byId(data.get(index));
        return amount <= 0 || fluid == Fluids.EMPTY ? FluidStack.EMPTY : new FluidStack(fluid, amount);
    }

    /** Whether this machine takes {@code id} for {@code player}, asked once at Fill Recipe. */
    protected abstract HoldVerdict verdict(ServerPlayer player, Identifier id);

    public BlockPos pos() {
        return pos;
    }

    /** Where the machine's slots are among the menu's, ahead of the player's. */
    public MachineSlots layout() {
        return layout;
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

    public int energyCapacity() {
        return data.get(DATA_ENERGY_CAPACITY);
    }

    /** Whether input {@code slot} holds less than one craft of the Held recipe needs, so the screen draws it red. */
    public boolean isShort(int slot) {
        return held.filter(recipe -> layout.isShort(slot, recipe.ingredients(),
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
        if (machine == null) {
            return HoldVerdict.NOT_ASSEMBLING;
        }
        HoldVerdict verdict = verdict(player, id);
        if (verdict.held()) {
            machine.setHeldRecipe(id, player);
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
        if (index < layout.size()) {
            if (!moveItemStackTo(stack, layout.size(), slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, 0, layout.inputs(), false)) {
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
            return held.filter(recipe -> layout.accepts(getSlotIndex(), recipe.ingredients(), stack,
                    (SizedIngredient sized, ItemStack item) -> sized.ingredient().test(item))).isPresent();
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
