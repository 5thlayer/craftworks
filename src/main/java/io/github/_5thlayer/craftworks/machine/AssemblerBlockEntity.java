// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.machine;

import java.util.Optional;

import com.mojang.logging.LogUtils;
import io.github._5thlayer.craftworks.CraftworksConfig;
import io.github._5thlayer.craftworks.recipe.AssemblingRecipe;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * An Assembler's block entity, one type for all three tiers: the Held recipe, seven item slots, an energy
 * buffer and the craft under way.
 *
 * <p>The Held recipe is an id, resolved when asked and never on load, when the recipes may not be there.
 * It is never matched from the items put in; Fill Recipe on the open screen sets it ({@link
 * AssemblerMenu#request}). An Assembler with none, or whose own cannot run, idles and keeps it.
 *
 * <p>A craft is one transaction a tick: pay that tick's share of the craft's energy, count a tick of
 * progress, and on the last take the inputs and place the outputs, committing only if all of it went.
 * Everything that would stall it is asked first, in a probe that aborts, so a blocked Assembler draws
 * nothing, starts nothing and voids nothing. Progress is held across a stall: the inputs are only taken
 * on the last tick, and the energy already paid is the craft's. Changing the Held recipe resets it.
 */
public final class AssemblerBlockEntity extends BlockEntity implements MenuProvider {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final String HELD_KEY = "held_recipe";
    private static final String PROGRESS_KEY = "progress";
    private static final String ITEMS_KEY = "items";
    private static final String ENERGY_KEY = "energy";

    private @Nullable Identifier held;
    /** The recipe instance {@link #runnable} last checked, and whether an Assembler can run it. Never saved. */
    private @Nullable AssemblingRecipe checked;
    private boolean checkedRuns;
    private int progress;

    private final ItemStacksResourceHandler inventory = new ItemStacksResourceHandler(AssemblerSlots.SIZE) {
        /**
         * An input holds at least two crafts of its ingredient however little the item stacks to: cake's
         * three milk buckets must fit in one slot, and a bucket stacks to one.
         */
        @Override
        protected int getCapacity(int index, ItemResource resource) {
            int stack = super.getCapacity(index, resource);
            if (!AssemblerSlots.isInput(index) || resource.isEmpty() || !(level instanceof ServerLevel server)) {
                return stack;
            }
            return runnable(server)
                    .flatMap(recipe -> AssemblerSlots.ingredientFor(index, recipe.ingredients()))
                    .map(ingredient -> Math.min(Item.ABSOLUTE_MAX_STACK_SIZE,
                            Math.max(stack, ingredient.count() * OverloadLimit.MINIMUM)))
                    .orElse(stack);
        }

        @Override
        protected void onContentsChanged(int index, ItemStack previousContents) {
            setChanged();
        }
    };

    private final Buffer buffer = new Buffer();
    private final AssemblerItemFace items = new AssemblerItemFace(this, inventory);

    /** What the capability shows: any source fills the buffer, and nothing drains it from outside. */
    private final EnergyHandler energyFace = new EnergyHandler() {
        @Override
        public long getAmountAsLong() {
            return buffer.getAmountAsLong();
        }

        @Override
        public long getCapacityAsLong() {
            return buffer.getCapacityAsLong();
        }

        @Override
        public int insert(int amount, TransactionContext transaction) {
            return buffer.insert(amount, transaction);
        }

        @Override
        public int extract(int amount, TransactionContext transaction) {
            return 0;
        }
    };

    public AssemblerBlockEntity(BlockPos pos, BlockState state) {
        super(Assemblers.BLOCK_ENTITY.get(), pos, state);
        buffer.resize(CraftworksConfig.assemblerBuffer(tierOf(state)));
    }

    /** The energy buffer, sized by the tier's config. Craft draws extract from it; the capability only fills it. */
    private static final class Buffer extends SimpleEnergyHandler {

        Buffer() {
            super(0);
        }

        void resize(int capacity) {
            this.capacity = capacity;
            this.maxInsert = capacity;
            this.maxExtract = capacity;
            this.energy = Math.min(energy, capacity);
        }
    }

    private static AssemblerTier tierOf(BlockState state) {
        return state.getBlock() instanceof AssemblerBlock block ? block.tier() : AssemblerTier.ONE;
    }

    /** The tier is the block's: one block entity type serves all three. */
    public AssemblerTier tier() {
        return tierOf(getBlockState());
    }

    /** Swapped for another tier by Fast Replace, which keeps this block entity: the buffer follows the new tier. */
    @Override
    public void setBlockState(BlockState state) {
        super.setBlockState(state);
        buffer.resize(CraftworksConfig.assemblerBuffer(tierOf(state)));
    }

    /** The seven slots, inputs then the product then the remainders, for the menu and the game tests. */
    public ItemStacksResourceHandler inventory() {
        return inventory;
    }

    ResourceHandler<ItemResource> itemFace() {
        return items;
    }

    EnergyHandler energyFace() {
        return energyFace;
    }

    /** The energy in the buffer, in FE. */
    public int energy() {
        return buffer.getAmountAsInt();
    }

    public int energyCapacity() {
        return buffer.getCapacityAsInt();
    }

    public Optional<Identifier> heldRecipe() {
        return Optional.ofNullable(held);
    }

    // -- the Held recipe ------------------------------------------------------------------------

    /**
     * The Held recipe if this Assembler can run it. Asked every tick and by every pipe, so whether it can run
     * is worked out once per recipe instance: a reload hands back new ones.
     */
    private Optional<AssemblingRecipe> runnable(ServerLevel server) {
        if (held == null) {
            return Optional.empty();
        }
        Optional<AssemblingRecipe> recipe = HeldRecipes.find(server, held).map(RecipeHolder::value);
        recipe.filter(found -> found != checked).ifPresent(found -> {
            checked = found;
            checkedRuns = HeldRecipes.canRun(found);
        });
        return recipe.filter(found -> checkedRuns);
    }

    /**
     * Holds {@code next}. A change hands every ingredient already in the input slots back to {@code player},
     * what does not fit dropping at their feet, and starts the craft over; the outputs stay. Setting the
     * recipe already held moves nothing. The Lock is not asked here: {@link AssemblerMenu#request} asked
     * it once, of the player who pressed (ADR-0013).
     */
    public void setHeldRecipe(Identifier next, Player player) {
        if (next.equals(held)) {
            return;
        }
        for (int slot = 0; slot < AssemblerSlots.INPUTS; slot++) {
            ItemStack stack = inventory.getResource(slot).toStack(inventory.getAmountAsInt(slot));
            if (stack.isEmpty()) {
                continue;
            }
            inventory.set(slot, ItemResource.EMPTY, 0);
            player.getInventory().placeItemBackInInventory(stack);
        }
        held = next;
        progress = 0;
        setChanged();
    }

    /** The item of this Assembler keeps the Held recipe (Groundworks hands the origin the item's data). */
    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        if (held != null) {
            components.set(Assemblers.HELD_RECIPE.get(), held);
        }
    }

    @Override
    protected void applyImplicitComponents(DataComponentGetter components) {
        super.applyImplicitComponents(components);
        held = components.get(Assemblers.HELD_RECIPE.get());
    }

    // -- input ----------------------------------------------------------------------------------

    /** Whether input {@code slot} takes {@code resource}: false off the server, which alone resolves the Held recipe. */
    public boolean accepts(int slot, ItemResource resource) {
        if (resource.isEmpty() || !(level instanceof ServerLevel server)) {
            return false;
        }
        return runnable(server)
                .flatMap(recipe -> AssemblerSlots.ingredientFor(slot, recipe.ingredients()))
                .filter(ingredient -> ingredient.ingredient().test(resource.toStack(1)))
                .isPresent();
    }

    /**
     * How many more of its ingredient an insert through the item capability may put in {@code slot}: the
     * Overload Limit less what the slot holds. The menu's slots do not ask, so the hand is not held to it.
     */
    public int overloadRoom(int slot) {
        if (!(level instanceof ServerLevel server)) {
            return 0;
        }
        return runnable(server)
                .flatMap(recipe -> AssemblerSlots.ingredientFor(slot, recipe.ingredients())
                        .map(ingredient -> OverloadLimit.room(ingredient.count(),
                                OverloadLimit.crafts(CraftworksConfig.assemblerSpeed(tier()), recipe.time()),
                                inventory.getAmountAsInt(slot))))
                .orElse(0);
    }

    // -- the craft ------------------------------------------------------------------------------

    public void serverTick(ServerLevel server) {
        buffer.resize(CraftworksConfig.assemblerBuffer(tier()));
        Optional<AssemblingRecipe> resolved = runnable(server);
        if (resolved.isEmpty()) {
            return;
        }
        AssemblingRecipe recipe = resolved.get();
        try (Transaction probe = Transaction.openRoot()) {
            if (!finish(recipe, probe)) {
                return;
            }
        }
        AssemblerTier tier = tier();
        double speed = CraftworksConfig.assemblerSpeed(tier);
        int duration = AssemblerRates.durationTicks(speed, recipe.time());
        int price = AssemblerRates.fePerCraft(CraftworksConfig.assemblerPower(tier), speed, recipe.time());
        int fe = AssemblerRates.feForTick(Math.min(progress, duration - 1), duration, price);
        try (Transaction tx = Transaction.openRoot()) {
            if (buffer.extract(fe, tx) != fe) {
                return;
            }
            int next = progress + 1;
            if (next >= duration) {
                if (!finish(recipe, tx)) {
                    LOGGER.warn("Assembler at {} passed its checks and could not finish {}", worldPosition.toShortString(), held);
                    return;
                }
                next = 0;
            }
            tx.commit();
            progress = next;
        }
        setChanged();
    }

    /**
     * Takes one craft's inputs, the {@code n}th ingredient from the {@code n}th slot, and places its
     * product and the ingredients' remainders, or reports that it cannot. Never part of a craft: the
     * caller aborts the transaction on false.
     */
    private boolean finish(AssemblingRecipe recipe, TransactionContext tx) {
        for (int slot = 0; slot < recipe.ingredients().size(); slot++) {
            SizedIngredient sized = recipe.ingredients().get(slot);
            ItemResource resource = inventory.getResource(slot);
            if (resource.isEmpty() || !sized.ingredient().test(resource.toStack(1))
                    || inventory.extract(slot, resource, sized.count(), tx) != sized.count()) {
                return false;
            }
            ItemStackTemplate remainder = resource.getItem().getCraftingRemainder(resource.toStack(1));
            if (remainder != null) {
                int owed = remainder.count() * sized.count();
                if (inventory.insert(AssemblerSlots.REMAINDERS, ItemResource.of(remainder), owed, tx) != owed) {
                    return false;
                }
            }
        }
        ItemStackTemplate result = recipe.result();
        return inventory.insert(AssemblerSlots.PRODUCT, ItemResource.of(result), result.count(), tx) == result.count();
    }

    /** Ticks into the craft under way, for the screen's progress bar. */
    public int craftProgress() {
        return progress;
    }

    /** The Held recipe's ticks at this tier's speed, or 0 with none that runs. Server only. */
    public int craftDuration() {
        if (!(level instanceof ServerLevel server)) {
            return 0;
        }
        return runnable(server)
                .map(recipe -> AssemblerRates.durationTicks(CraftworksConfig.assemblerSpeed(tier()), recipe.time()))
                .orElse(0);
    }

    // -- persistence ----------------------------------------------------------------------------

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (held != null) {
            output.store(HELD_KEY, Identifier.CODEC, held);
        }
        output.putInt(PROGRESS_KEY, progress);
        inventory.serialize(output.child(ITEMS_KEY));
        buffer.serialize(output.child(ENERGY_KEY));
    }

    /** The id only. It is resolved when asked, never here, where the recipes may not be loaded. */
    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        held = input.read(HELD_KEY, Identifier.CODEC).orElse(null);
        progress = input.getIntOr(PROGRESS_KEY, 0);
        inventory.deserialize(input.childOrEmpty(ITEMS_KEY));
        buffer.deserialize(input.childOrEmpty(ENERGY_KEY));
    }

    /**
     * Going, by a break or a command, drops the contents; the energy is lost. Not asked when Fast Replace
     * swaps the tier, which keeps this block entity and everything in it.
     */
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
        return AssemblerMenu.open(containerId, playerInventory, this);
    }
}
