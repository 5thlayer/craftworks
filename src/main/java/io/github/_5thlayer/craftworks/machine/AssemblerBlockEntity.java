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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
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
import org.slf4j.Logger;

/**
 * An Assembler's block entity, one type for all three tiers: the Held recipe, seven item slots (five inputs, the
 * product and the remainders), an energy buffer and the craft under way. Its fluid boxes and Fluid Connections
 * are its {@link AssemblerFluidSide}, which it owns.
 *
 * <p>The Held recipe is an id, resolved when asked and never on load, when the recipes may not be there. It
 * is never matched from the items put in; Fill Recipe on the open screen sets it ({@link AssemblerMenu#request}).
 * An Assembler with none, or whose tier cannot run its own, idles and keeps it.
 *
 * <p>A craft is one transaction a tick: pay that tick's share of the craft's energy, count a tick of progress,
 * and on the last take the inputs and place the outputs, committing only if all of it went. Everything that
 * would stall it is asked first, in a probe that aborts, so a blocked Assembler draws nothing, starts nothing and
 * voids nothing. Progress is held across a stall: the inputs are only taken on the last tick, and the energy
 * already paid is the craft's. Changing the Held recipe resets it. The craft waits while an output fluid box
 * cannot hold what it makes.
 */
public final class AssemblerBlockEntity extends BlockEntity implements MenuProvider, MachineItemFace.Gate {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final String HELD_KEY = "held_recipe";
    private static final String PROGRESS_KEY = "progress";
    private static final String ITEMS_KEY = "items";
    private static final String ENERGY_KEY = "energy";

    private final HeldRecipeRef held = new HeldRecipeRef();
    private int progress;

    private final MachineInventory inventory;
    private final EnergyBuffer buffer = new EnergyBuffer();
    private final MachineItemFace items;
    private final AssemblerFluidSide fluidSide;

    public AssemblerBlockEntity(BlockPos pos, BlockState state) {
        super(Assemblers.BLOCK_ENTITY.get(), pos, state);
        inventory = new MachineInventory(AssemblerSlots.LAYOUT, new MachineInventory.Owner() {
            @Override
            public Optional<SizedIngredient> ingredientAt(int slot) {
                return runnable().flatMap(recipe -> ingredientFor(slot, recipe));
            }

            @Override
            public void changed() {
                setChanged();
            }
        });
        items = new MachineItemFace(this, inventory);
        fluidSide = new AssemblerFluidSide(new AssemblerFluidSide.Host() {
            @Override
            public Optional<AssemblingRecipe> runnable() {
                return AssemblerBlockEntity.this.runnable();
            }

            @Override
            public BlockPos pos() {
                return worldPosition;
            }

            @Override
            public BlockState blockState() {
                return getBlockState();
            }

            @Override
            public void changed() {
                setChanged();
            }
        });
        resizeBuffer();
    }

    private static AssemblerTier tierOf(BlockState state) {
        return state.getBlock() instanceof AssemblerBlock block ? block.tier() : AssemblerTier.ONE;
    }

    /** The tier is the block's: one block entity type serves all three. Its figures follow it. */
    public AssemblerTier tier() {
        return tierOf(getBlockState());
    }

    /**
     * Swapped for another tier by Fast Replace, which keeps this block entity: the buffer follows the new tier,
     * and every tier has the same fluid boxes, so they keep what they held.
     */
    @Override
    public void setBlockState(BlockState state) {
        super.setBlockState(state);
        resizeBuffer();
    }

    /** The item slots, inputs first, for the menu and the game tests. */
    public ItemStacksResourceHandler inventory() {
        return inventory;
    }

    ResourceHandler<ItemResource> itemFace() {
        return items;
    }

    EnergyHandler energyFace() {
        return buffer.face();
    }

    /** The fluid boxes and Fluid Connections. */
    public AssemblerFluidSide fluidSide() {
        return fluidSide;
    }

    /** The fluid boxes, which the menu, Jade and the game tests read through this rather than through the fluid side. */
    public MachineFluids fluids() {
        return fluidSide.boxes();
    }

    /** The energy in the buffer, in FE. */
    public int energy() {
        return buffer.getAmountAsInt();
    }

    public int energyCapacity() {
        return buffer.getCapacityAsInt();
    }

    public Optional<Identifier> heldRecipe() {
        return held.id();
    }

    private void resizeBuffer() {
        buffer.resize(CraftworksConfig.buffer(tier()));
    }

    // -- the Held recipe ------------------------------------------------------------------------

    /**
     * The Held recipe if this Assembler can run it; empty with none, and off the server, which alone resolves
     * it. Whether it can run it at all is worked out once per recipe instance; the category is asked every
     * time, since a Fast Replace or a config edit moves it.
     */
    private Optional<AssemblingRecipe> runnable() {
        if (!(level instanceof ServerLevel server)) {
            return Optional.empty();
        }
        return held.runnable(server, HeldRecipes::canRun, found -> HeldRecipes.takesCategory(tier(), found));
    }

    /** The ingredient the Held recipe puts in {@code slot}: the {@code n}th in the {@code n}th input, and none elsewhere. */
    private Optional<SizedIngredient> ingredientFor(int slot, AssemblingRecipe recipe) {
        return inventory.slots().ingredientFor(slot, recipe.ingredients());
    }

    /**
     * Holds {@code next}. A change hands every ingredient already in the input slots back to {@code player},
     * what does not fit dropping at their feet, and starts the craft over; the outputs stay. Setting the
     * recipe already held moves nothing. The Lock is not asked here: {@link AssemblerMenu#request} asked
     * it once, of the player who pressed (ADR-0013). The fluid boxes are voided, and the Fluid Connections are
     * made what the new recipe says.
     */
    public void setHeldRecipe(Identifier next, Player player) {
        if (next.equals(held.idOrNull())) {
            return;
        }
        for (int slot = 0; slot < inventory.slots().inputs(); slot++) {
            ItemStack stack = inventory.getResource(slot).toStack(inventory.getAmountAsInt(slot));
            if (stack.isEmpty()) {
                continue;
            }
            inventory.set(slot, ItemResource.EMPTY, 0);
            player.getInventory().placeItemBackInInventory(stack);
        }
        held.set(next);
        progress = 0;
        fluidSide.empty();
        if (level instanceof ServerLevel server) {
            fluidSide.syncConnections(server);
        }
        setChanged();
    }

    /** The Assembler's item keeps the Held recipe (Groundworks hands the origin the item's data). */
    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        held.id().ifPresent(id -> components.set(Assemblers.HELD_RECIPE.get(), id));
    }

    @Override
    protected void applyImplicitComponents(DataComponentGetter components) {
        super.applyImplicitComponents(components);
        held.set(components.get(Assemblers.HELD_RECIPE.get()));
    }

    // -- input ----------------------------------------------------------------------------------

    /** Whether input {@code slot} takes {@code resource}: false off the server, which alone resolves the Held recipe. */
    @Override
    public boolean accepts(int slot, ItemResource resource) {
        if (resource.isEmpty()) {
            return false;
        }
        return runnable()
                .flatMap(recipe -> ingredientFor(slot, recipe))
                .filter(ingredient -> ingredient.ingredient().test(resource.toStack(1)))
                .isPresent();
    }

    /**
     * How many more of its ingredient an insert through the item capability may put in {@code slot}: the
     * Overload Limit less what the slot holds. The menu's slots do not ask, so the hand is not held to it.
     */
    @Override
    public int overloadRoom(int slot, ItemResource resource) {
        return runnable()
                .flatMap(recipe -> ingredientFor(slot, recipe)
                        .map(ingredient -> OverloadLimit.room(ingredient.count(), overloadCrafts(recipe),
                                inventory.getAmountAsInt(slot))))
                .orElse(0);
    }

    /** How many crafts of {@code recipe} the Overload Limit lets this Assembler hold. */
    private int overloadCrafts(AssemblingRecipe recipe) {
        return OverloadLimit.crafts(CraftworksConfig.speed(tier()), recipe.time());
    }

    // -- the craft ------------------------------------------------------------------------------

    public void serverTick(ServerLevel server) {
        resizeBuffer();
        fluidSide.syncConnections(server);
        Optional<AssemblingRecipe> resolved = runnable();
        if (resolved.isEmpty()) {
            return;
        }
        AssemblingRecipe recipe = resolved.get();
        fluidSide.pull(server, recipe);
        craft(recipe);
        fluidSide.push(server);
    }

    private void craft(AssemblingRecipe recipe) {
        try (Transaction probe = Transaction.openRoot()) {
            if (finish(recipe, probe) != null) {
                return;
            }
        }
        int duration = duration(recipe);
        int fe = feThisTick(recipe, duration);
        try (Transaction tx = Transaction.openRoot()) {
            if (buffer.extract(fe, tx) != fe) {
                return;
            }
            int next = progress + 1;
            if (next >= duration) {
                if (finish(recipe, tx) != null) {
                    LOGGER.warn("{} at {} passed its checks and could not finish {}", getBlockState().getBlock().getName().getString(),
                            worldPosition.toShortString(), held.idOrNull());
                    return;
                }
                next = 0;
            }
            tx.commit();
            progress = next;
        }
        setChanged();
    }

    private int duration(AssemblingRecipe recipe) {
        return CraftRates.durationTicks(CraftworksConfig.speed(tier()), recipe.time());
    }

    /** The FE this tick of a craft of {@code duration} ticks costs: its share of the tier's price for the craft. */
    private int feThisTick(AssemblingRecipe recipe, int duration) {
        AssemblerTier tier = tier();
        int price = CraftRates.fePerCraft(CraftworksConfig.power(tier), CraftworksConfig.speed(tier), recipe.time());
        return CraftRates.feForTick(Math.min(progress, duration - 1), duration, price);
    }

    /**
     * What this Assembler is doing, asked the way {@link #serverTick} asks and changing nothing: the first
     * check it would fail, or {@link MachineState#CRAFTING}. Server only, which alone resolves the Held
     * recipe; anywhere else it reads as {@link MachineState#NO_RECIPE}. For Jade (#28).
     */
    public MachineState state() {
        if (held.id().isEmpty() || !(level instanceof ServerLevel)) {
            return MachineState.NO_RECIPE;
        }
        Optional<AssemblingRecipe> resolved = runnable();
        if (resolved.isEmpty()) {
            return MachineState.CANT_RUN;
        }
        AssemblingRecipe recipe = resolved.get();
        try (Transaction probe = Transaction.openRoot()) {
            MachineState stalled = finish(recipe, probe);
            if (stalled != null) {
                return stalled;
            }
        }
        int duration = duration(recipe);
        int fe = feThisTick(recipe, duration);
        try (Transaction probe = Transaction.openRoot()) {
            return buffer.extract(fe, probe) == fe ? MachineState.CRAFTING : MachineState.NEEDS_POWER;
        }
    }

    /**
     * Takes one craft's inputs, the {@code n}th fluid ingredient from input box {@code n} and the {@code n}th
     * item ingredient from the {@code n}th slot, and places its first item result in the product slot and, in the
     * remainder slot, the ingredients' remainders and its further item results, and each fluid result in the
     * output box its order names. Returns null if it all went, and otherwise what stopped it:
     * {@link MachineState#MISSING_INGREDIENTS} or {@link MachineState#OUTPUT_FULL}. Never part of a craft: the
     * caller aborts the transaction on a stop.
     */
    private @Nullable MachineState finish(AssemblingRecipe recipe, TransactionContext tx) {
        MachineState stopped = fluidSide.take(recipe, tx);
        if (stopped != null) {
            return stopped;
        }
        stopped = takeItems(recipe, tx);
        if (stopped != null) {
            return stopped;
        }
        for (ItemStackTemplate extra : recipe.extraResults()) {
            if (inventory.insert(AssemblerSlots.REMAINDERS, ItemResource.of(extra), extra.count(), tx) != extra.count()) {
                return MachineState.OUTPUT_FULL;
            }
        }
        ItemStackTemplate product = recipe.productTemplate().orElse(null);
        if (product != null
                && inventory.insert(AssemblerSlots.PRODUCT, ItemResource.of(product), product.count(), tx) != product.count()) {
            return MachineState.OUTPUT_FULL;
        }
        return fluidSide.place(recipe, tx);
    }

    /**
     * Takes the {@code n}th item ingredient from the {@code n}th input slot, placing each one's remainder.
     * Returns null if they all went, and otherwise what stopped it.
     */
    private @Nullable MachineState takeItems(AssemblingRecipe recipe, TransactionContext tx) {
        for (int slot = 0; slot < recipe.ingredients().size(); slot++) {
            SizedIngredient sized = recipe.ingredients().get(slot);
            ItemResource resource = inventory.getResource(slot);
            if (resource.isEmpty() || !sized.ingredient().test(resource.toStack(1))
                    || inventory.extract(slot, resource, sized.count(), tx) != sized.count()) {
                return MachineState.MISSING_INGREDIENTS;
            }
            MachineState stopped = placeRemainder(resource, sized.count(), tx);
            if (stopped != null) {
                return stopped;
            }
        }
        return null;
    }

    /** An ingredient's remainder goes in the remainder slot: cake's buckets, honey block's bottles. */
    private @Nullable MachineState placeRemainder(ItemResource resource, int count, TransactionContext tx) {
        ItemStackTemplate remainder = resource.getItem().getCraftingRemainder(resource.toStack(1));
        if (remainder == null) {
            return null;
        }
        int owed = remainder.count() * count;
        return inventory.insert(AssemblerSlots.REMAINDERS, ItemResource.of(remainder), owed, tx) == owed ? null : MachineState.OUTPUT_FULL;
    }

    /** Ticks into the craft under way, for the screen's progress bar. */
    public int craftProgress() {
        return progress;
    }

    /** The Held recipe's ticks at this tier's speed, or 0 with none that runs. Server only. */
    public int craftDuration() {
        return runnable().map(this::duration).orElse(0);
    }

    // -- persistence ----------------------------------------------------------------------------

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        held.id().ifPresent(id -> output.store(HELD_KEY, Identifier.CODEC, id));
        output.putInt(PROGRESS_KEY, progress);
        inventory.serialize(output.child(ITEMS_KEY));
        buffer.serialize(output.child(ENERGY_KEY));
        fluidSide.save(output);
    }

    /** The id only. It is resolved when asked, never here, where the recipes may not be loaded. */
    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        held.set(input.read(HELD_KEY, Identifier.CODEC).orElse(null));
        progress = input.getIntOr(PROGRESS_KEY, 0);
        inventory.deserialize(input.childOrEmpty(ITEMS_KEY));
        buffer.deserialize(input.childOrEmpty(ENERGY_KEY));
        fluidSide.load(input);
    }

    /**
     * Going, by a break or a command, drops the items; the energy and the fluid are lost. Not asked when Fast
     * Replace swaps an Assembler's tier, which keeps this block entity and everything in it.
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
