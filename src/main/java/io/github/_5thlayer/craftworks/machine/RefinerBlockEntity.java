// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.machine;

import java.util.Optional;

import io.github._5thlayer.craftworks.CraftworksConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.Nullable;

/**
 * The Refiner's block entity: an input, an output, an energy buffer and the smelt under way.
 *
 * <p>A smelt is one transaction a tick, as an Assembler's craft is: pay that tick's share of the smelt's FE, count
 * a tick of progress, and on the last take one input and place the result, committing only if all of it went. A
 * result the output cannot take is asked first, in a probe that aborts, so a backed-up Refiner draws nothing and
 * voids nothing. Progress is held while the output is full or the buffer short, and thrown away when the input
 * leaves or changes to an item another recipe smelts, so a swap is no head start.
 */
public final class RefinerBlockEntity extends BlockEntity implements MenuProvider, MachineItemFace.Gate {

    private static final String PROGRESS_KEY = "progress";
    private static final String ITEMS_KEY = "items";
    private static final String ENERGY_KEY = "energy";

    private final MachineInventory inventory;
    private final EnergyBuffer buffer = new EnergyBuffer();
    private final MachineItemFace items;

    private int progress;
    private int duration;
    /** The recipe the progress belongs to; unset after a load, which adopts the first recipe it finds. */
    private @Nullable Identifier smelting;

    public RefinerBlockEntity(BlockPos pos, BlockState state) {
        super(Refiners.BLOCK_ENTITY.get(), pos, state);
        inventory = new MachineInventory(RefinerSlots.LAYOUT, new MachineInventory.Owner() {
            @Override
            public Optional<SizedIngredient> ingredientAt(int slot) {
                return Optional.empty();
            }

            @Override
            public void changed() {
                setChanged();
            }
        });
        items = new MachineItemFace(this, inventory);
        resizeBuffer();
    }

    /** The item slots, the input then the output, for the menu and the game tests. */
    public ItemStacksResourceHandler inventory() {
        return inventory;
    }

    /** The item capability, the same on every side: the input takes what a recipe smelts, only the output gives. */
    ResourceHandler<ItemResource> itemFace() {
        return items;
    }

    EnergyHandler energyFace() {
        return buffer.face();
    }

    public int energy() {
        return buffer.getAmountAsInt();
    }

    public int energyCapacity() {
        return buffer.getCapacityAsInt();
    }

    private void resizeBuffer() {
        buffer.resize(CraftworksConfig.refinerBuffer());
    }

    // -- input ----------------------------------------------------------------------------------

    @Override
    public boolean accepts(int slot, ItemResource resource) {
        return slot == RefinerSlots.INPUT && level != null && RefinerRecipes.isIngredient(level, resource.toStack(1));
    }

    /**
     * How many more of {@code resource} an insert through the item capability may put in the input: the Overload
     * Limit of the smelt that takes it, less what the slot holds. The menu's slots do not ask, so the hand is not
     * held to it. Server only, which alone has the recipes.
     */
    @Override
    public int overloadRoom(int slot, ItemResource resource) {
        if (!(level instanceof ServerLevel server) || slot != RefinerSlots.INPUT) {
            return 0;
        }
        return RefinerRecipes.find(server, resource.toStack(1))
                .map(found -> OverloadLimit.room(1,
                        OverloadLimit.crafts(CraftworksConfig.refinerSpeed(), found.value().cookingTime()),
                        inventory.getAmountAsInt(RefinerSlots.INPUT)))
                .orElse(0);
    }

    // -- the smelt ------------------------------------------------------------------------------

    public void serverTick(ServerLevel server) {
        resizeBuffer();
        Optional<RecipeHolder<? extends AbstractCookingRecipe>> found = recipe(server);
        if (found.isEmpty()) {
            if (progress != 0 || duration != 0) {
                progress = 0;
                duration = 0;
                setChanged();
            }
            setLit(false);
            return;
        }
        Identifier id = found.get().id().identifier();
        if (smelting != null && !smelting.equals(id)) {
            progress = 0;
        }
        smelting = id;
        AbstractCookingRecipe recipe = found.get().value();
        duration = CraftRates.durationTicks(CraftworksConfig.refinerSpeed(), recipe.cookingTime());
        ItemStack result = result(recipe);
        try (Transaction probe = Transaction.openRoot()) {
            if (finish(result, probe) != null) {
                setLit(false);
                return;
            }
        }
        int fe = feThisTick(recipe, duration);
        try (Transaction tx = Transaction.openRoot()) {
            if (buffer.extract(fe, tx) != fe) {
                setLit(false);
                return;
            }
            int next = progress + 1;
            if (next >= duration) {
                finish(result, tx);
                next = 0;
            }
            tx.commit();
            progress = next;
        }
        setLit(true);
        setChanged();
    }

    private Optional<RecipeHolder<? extends AbstractCookingRecipe>> recipe(ServerLevel server) {
        ItemResource input = inventory.getResource(RefinerSlots.INPUT);
        return input.isEmpty() ? Optional.empty() : RefinerRecipes.find(server, input.toStack(1));
    }

    private ItemStack result(AbstractCookingRecipe recipe) {
        return recipe.assemble(new SingleRecipeInput(inventory.getResource(RefinerSlots.INPUT).toStack(1)));
    }

    private int feThisTick(AbstractCookingRecipe recipe, int duration) {
        int price = CraftRates.fePerCraft(CraftworksConfig.refinerPower(), CraftworksConfig.refinerSpeed(), recipe.cookingTime());
        return CraftRates.feForTick(Math.min(progress, duration - 1), duration, price);
    }

    /** Takes one input and places {@code result}. Null if it went, otherwise what stopped it; the caller aborts on a stop. */
    private @Nullable MachineState finish(ItemStack result, TransactionContext tx) {
        ItemResource input = inventory.getResource(RefinerSlots.INPUT);
        if (inventory.extract(RefinerSlots.INPUT, input, 1, tx) != 1) {
            return MachineState.MISSING_INGREDIENTS;
        }
        if (result.isEmpty()) {
            return null;
        }
        int count = result.getCount();
        return inventory.insert(RefinerSlots.OUTPUT, ItemResource.of(result), count, tx) == count ? null : MachineState.OUTPUT_FULL;
    }

    private void setLit(boolean lit) {
        BlockState state = getBlockState();
        if (level != null && state.getValue(RefinerBlock.LIT) != lit) {
            level.setBlock(worldPosition, state.setValue(RefinerBlock.LIT, lit), Block.UPDATE_ALL);
        }
    }

    /**
     * What this Refiner is doing, asked the way {@link #serverTick} asks and changing nothing: the first check it
     * would fail, or {@link MachineState#CRAFTING}. {@link MachineState#NO_RECIPE} with nothing to smelt. For Jade.
     */
    public MachineState state() {
        if (!(level instanceof ServerLevel server)) {
            return MachineState.NO_RECIPE;
        }
        Optional<RecipeHolder<? extends AbstractCookingRecipe>> found = recipe(server);
        if (found.isEmpty()) {
            return inventory.getResource(RefinerSlots.INPUT).isEmpty() ? MachineState.NO_RECIPE : MachineState.CANT_RUN;
        }
        AbstractCookingRecipe recipe = found.get().value();
        try (Transaction probe = Transaction.openRoot()) {
            MachineState stalled = finish(result(recipe), probe);
            if (stalled != null) {
                return stalled;
            }
        }
        int fe = feThisTick(recipe, CraftRates.durationTicks(CraftworksConfig.refinerSpeed(), recipe.cookingTime()));
        try (Transaction probe = Transaction.openRoot()) {
            return buffer.extract(fe, probe) == fe ? MachineState.CRAFTING : MachineState.NEEDS_POWER;
        }
    }

    /** Ticks into the smelt under way, for the screen's progress bar. */
    public int smeltProgress() {
        return progress;
    }

    /** The smelt's ticks at this speed, or 0 with nothing to smelt. */
    public int smeltDuration() {
        return duration;
    }

    // -- persistence ----------------------------------------------------------------------------

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt(PROGRESS_KEY, progress);
        inventory.serialize(output.child(ITEMS_KEY));
        buffer.serialize(output.child(ENERGY_KEY));
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        progress = input.getIntOr(PROGRESS_KEY, 0);
        inventory.deserialize(input.childOrEmpty(ITEMS_KEY));
        buffer.deserialize(input.childOrEmpty(ENERGY_KEY));
    }

    /** Going, by a break or a command, drops the items; the energy is lost. */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (level == null) {
            return;
        }
        for (int slot = 0; slot < inventory.size(); slot++) {
            ItemStack stack = inventory.getResource(slot).toStack(inventory.getAmountAsInt(slot));
            if (!stack.isEmpty()) {
                Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack);
            }
        }
    }

    // -- the screen -----------------------------------------------------------------------------

    @Override
    public Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return RefinerMenu.open(containerId, playerInventory, this);
    }
}
