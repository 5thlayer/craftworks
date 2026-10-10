// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.IndexModifier;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;
import net.neoforged.neoforge.transfer.item.ResourceHandlerSlot;
import org.jspecify.annotations.Nullable;

/**
 * The Refiner's menu: its input and output with the smelt's progress between them, the energy bar below, then the
 * player's inventory. Progress, duration and energy ride in data slots.
 */
public final class RefinerMenu extends AbstractContainerMenu {

    private static final int DATA_PROGRESS = 0;
    private static final int DATA_DURATION = 1;
    private static final int DATA_ENERGY = 2;
    private static final int DATA_ENERGY_CAPACITY = 3;
    private static final int DATA_COUNT = 4;

    public static final int INPUT_X = 56;
    public static final int OUTPUT_X = 116;
    public static final int SLOT_Y = 34;
    public static final int ENERGY_Y = 58;
    public static final int INVENTORY_Y = 84;

    private final @Nullable RefinerBlockEntity refiner;
    private final BlockPos pos;
    private final ContainerData data;
    private final Player player;

    /** Client side: the slots stand over a stub the menu's own sync fills. */
    public RefinerMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf buffer) {
        this(containerId, playerInventory, null, buffer.readBlockPos(), new ItemStacksResourceHandler(RefinerSlots.SIZE),
                new SimpleContainerData(DATA_COUNT));
    }

    private RefinerMenu(int containerId, Inventory playerInventory, @Nullable RefinerBlockEntity refiner, BlockPos pos,
            ItemStacksResourceHandler inventory, ContainerData data) {
        super(Refiners.MENU.get(), containerId);
        this.refiner = refiner;
        this.pos = pos;
        this.data = data;
        this.player = playerInventory.player;
        IndexModifier<ItemResource> modifier = inventory::set;
        addSlot(new InputSlot(inventory, modifier, RefinerSlots.INPUT, INPUT_X, SLOT_Y));
        addSlot(new OutputSlot(inventory, modifier, RefinerSlots.OUTPUT, OUTPUT_X, SLOT_Y));
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

    /** Server side, over the Refiner's own inventory. */
    static RefinerMenu open(int containerId, Inventory playerInventory, RefinerBlockEntity refiner) {
        ContainerData data = new ContainerData() {
            @Override
            public int get(int index) {
                return switch (index) {
                    case DATA_PROGRESS -> refiner.smeltProgress();
                    case DATA_DURATION -> refiner.smeltDuration();
                    case DATA_ENERGY -> refiner.energy();
                    case DATA_ENERGY_CAPACITY -> refiner.energyCapacity();
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
        return new RefinerMenu(containerId, playerInventory, refiner, refiner.getBlockPos(), refiner.inventory(), data);
    }

    public BlockPos pos() {
        return pos;
    }

    /** How far the smelt under way is, from 0 to 1; 0 with nothing smelting. */
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

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index < RefinerSlots.SIZE) {
            if (!moveItemStackTo(stack, RefinerSlots.SIZE, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, RefinerSlots.INPUT, RefinerSlots.INPUT + 1, false)) {
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
        if (refiner != null && refiner.isRemoved()) {
            return false;
        }
        // Vanilla's buffer in Container.stillValidBlockEntity, so a screen opened at full reach stays open.
        return player.isWithinBlockInteractionRange(pos, 4.0);
    }

    /** Takes what a smelting or blasting recipe takes, on both sides, from the recipes the server syncs. */
    private final class InputSlot extends ResourceHandlerSlot {
        InputSlot(ResourceHandler<ItemResource> handler, IndexModifier<ItemResource> modifier, int index, int x, int y) {
            super(handler, modifier, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return RefinerRecipes.isIngredient(player.level(), stack);
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
