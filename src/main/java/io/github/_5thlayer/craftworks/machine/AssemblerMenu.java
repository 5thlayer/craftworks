// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.machine;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import io.github._5thlayer.craftworks.network.AssemblerHeldPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
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
import net.minecraft.world.item.ItemStackTemplate;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.IndexModifier;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;
import net.neoforged.neoforge.transfer.item.ResourceHandlerSlot;
import org.jspecify.annotations.Nullable;

/**
 * The Assembler's menu: five inputs, the product and the remainders, the Held recipe and how far its
 * craft is. No recipe is picked here and none cleared: the recipe viewer's Fill Recipe lands on {@link
 * #request}, and an Assembler's recipe is replaced, never removed.
 *
 * <p>The Held recipe crosses to the client as {@link AssemblerHeldPacket} when it changes, since the
 * client has no recipe manager to read the ingredients each slot is ghosted with. Progress, duration and
 * energy ride in data slots.
 */
public final class AssemblerMenu extends AbstractContainerMenu {

    /** What the screen draws of the Held recipe: its id, what each input slot takes, and its product. */
    public record Held(Identifier id, List<SizedIngredient> ingredients, ItemStackTemplate result) {

        public static final StreamCodec<RegistryFriendlyByteBuf, Held> STREAM_CODEC = StreamCodec.composite(
                Identifier.STREAM_CODEC, Held::id,
                SizedIngredient.STREAM_CODEC.apply(ByteBufCodecs.list()), Held::ingredients,
                ItemStackTemplate.STREAM_CODEC, Held::result,
                Held::new);
    }

    private static final int DATA_PROGRESS = 0;
    private static final int DATA_DURATION = 1;
    private static final int DATA_ENERGY = 2;
    private static final int DATA_CAPACITY = 3;
    private static final int DATA_COUNT = 4;

    public static final int INPUT_X = 8;
    public static final int INPUT_Y = 36;
    public static final int PRODUCT_X = 134;
    public static final int REMAINDERS_X = 152;
    public static final int INVENTORY_Y = 84;

    private final @Nullable AssemblerBlockEntity machine;
    private final BlockPos pos;
    private final ContainerData data;
    private final Player player;

    /** What the screen shows of the Held recipe; the server sends it, and it is empty until then. */
    private Optional<Held> held = Optional.empty();

    /** The Held recipe's id as last sent to the client, or unset before the first send. */
    private @Nullable Optional<Identifier> sent;

    /** Client side: the slots stand over a stub the menu's own sync fills. */
    public AssemblerMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf buffer) {
        this(containerId, playerInventory, null, buffer.readBlockPos(), new ItemStacksResourceHandler(AssemblerSlots.SIZE),
                new SimpleContainerData(DATA_COUNT));
    }

    private AssemblerMenu(int containerId, Inventory playerInventory, @Nullable AssemblerBlockEntity machine, BlockPos pos,
            ItemStacksResourceHandler inventory, ContainerData data) {
        super(Assemblers.MENU.get(), containerId);
        this.machine = machine;
        this.pos = pos;
        this.data = data;
        this.player = playerInventory.player;
        IndexModifier<ItemResource> modifier = inventory::set;
        for (int slot = 0; slot < AssemblerSlots.INPUTS; slot++) {
            addSlot(new InputSlot(inventory, modifier, slot, INPUT_X + slot * 18, INPUT_Y));
        }
        addSlot(new OutputSlot(inventory, modifier, AssemblerSlots.PRODUCT, PRODUCT_X, INPUT_Y));
        addSlot(new OutputSlot(inventory, modifier, AssemblerSlots.REMAINDERS, REMAINDERS_X, INPUT_Y));
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
    static AssemblerMenu open(int containerId, Inventory playerInventory, AssemblerBlockEntity machine) {
        ContainerData data = new ContainerData() {
            @Override
            public int get(int index) {
                return switch (index) {
                    case DATA_PROGRESS -> machine.craftProgress();
                    case DATA_DURATION -> machine.craftDuration();
                    case DATA_ENERGY -> machine.energy();
                    case DATA_CAPACITY -> machine.energyCapacity();
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
        return new AssemblerMenu(containerId, playerInventory, machine, machine.getBlockPos(),
                machine.inventory(), data);
    }

    public BlockPos pos() {
        return pos;
    }

    /** The Held recipe as the client was last told it, or empty. */
    public Optional<Held> held() {
        return held;
    }

    /** The client taking the Held recipe the server sent. */
    public void show(Optional<Held> held) {
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

    public int capacity() {
        return data.get(DATA_CAPACITY);
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
                Optional<Held> view = now.flatMap(id -> HeldRecipes.find(level, id)
                        .map(holder -> new Held(id, holder.value().ingredients(), holder.value().result())));
                PacketDistributor.sendToPlayer(server, new AssemblerHeldPacket(containerId, view));
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
        HoldVerdict verdict = HeldRecipes.verdict(player, id);
        if (verdict.held()) {
            machine.setHeldRecipe(id, player);
        } else {
            player.sendSystemMessage(Component.translatable(verdict.messageKey(), HeldRecipes.name(player.level(), id)));
        }
        return verdict;
    }

    /** Fill Recipe on the Assembler this player has open, if any. */
    public static void fill(ServerPlayer player, Identifier id) {
        if (player.containerMenu instanceof AssemblerMenu menu) {
            menu.request(player, id);
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        int machineSlots = AssemblerSlots.SIZE;
        if (index < machineSlots) {
            if (!moveItemStackTo(stack, machineSlots, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, 0, AssemblerSlots.INPUTS, false)) {
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
