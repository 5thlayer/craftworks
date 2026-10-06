// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.machine;

import java.util.Optional;
import java.util.function.Predicate;

import com.mojang.logging.LogUtils;
import io.github._5thlayer.craftworks.CraftworksConfig;
import io.github._5thlayer.craftworks.recipe.AssemblingRecipe;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * An Assembler's block entity, one type for all three tiers: the Held recipe, seven item slots, an energy
 * buffer, a fluid box on tiers 2 and 3, and the craft under way.
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
 *
 * <p>Tiers 2 and 3 take a fluid ingredient through the Fluid Connections ({@link FluidConnections}), which
 * exist only while the Held recipe has one: each tick every connection pulls the Held recipe's fluid from
 * the block it faces into the one fluid box, up to the box's room, and the craft takes its amount from the
 * box with the items. The box is kept over a reload and a Fast Replace between the two tiers, and voided by
 * a change of the Held recipe and by a tier with no box. An Assembler never pushes fluid.
 */
public final class AssemblerBlockEntity extends BlockEntity implements MenuProvider {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final String HELD_KEY = "held_recipe";
    private static final String PROGRESS_KEY = "progress";
    private static final String ITEMS_KEY = "items";
    private static final String ENERGY_KEY = "energy";
    private static final String FLUID_KEY = "fluid";

    private final HeldRecipeSlot held = new HeldRecipeSlot();
    private int progress;

    private final MachineInventory inventory = new MachineInventory(AssemblerSlots.SIZE, AssemblerSlots.INPUTS,
            new MachineInventory.Owner() {
                @Override
                public Optional<SizedIngredient> ingredientAt(int slot) {
                    return level instanceof ServerLevel server
                            ? runnable(server).flatMap(recipe -> AssemblerSlots.ingredientFor(slot, recipe.ingredients()))
                            : Optional.empty();
                }

                @Override
                public void changed() {
                    setChanged();
                }
            });

    private final EnergyBuffer buffer = new EnergyBuffer();
    private final MachineItemFace items = new MachineItemFace(new MachineItemFace.Gate() {
        @Override
        public boolean accepts(int slot, ItemResource resource) {
            return AssemblerBlockEntity.this.accepts(slot, resource);
        }

        @Override
        public int overloadRoom(int slot) {
            return AssemblerBlockEntity.this.overloadRoom(slot);
        }
    }, inventory);
    private final AssemblerFluidBox fluidBox = new AssemblerFluidBox(this);
    private final AssemblerFluidConnection fluidConnection = new AssemblerFluidConnection(fluidBox);

    public AssemblerBlockEntity(BlockPos pos, BlockState state) {
        super(Assemblers.BLOCK_ENTITY.get(), pos, state);
        buffer.resize(CraftworksConfig.assemblerBuffer(tierOf(state)));
    }

    private static AssemblerTier tierOf(BlockState state) {
        return state.getBlock() instanceof AssemblerBlock block ? block.tier() : AssemblerTier.ONE;
    }

    /** The tier is the block's: one block entity type serves all three. */
    public AssemblerTier tier() {
        return tierOf(getBlockState());
    }

    /**
     * Swapped for another tier by Fast Replace, which keeps this block entity: the buffer follows the new tier,
     * and a tier with no fluid box voids what the box held.
     */
    @Override
    public void setBlockState(BlockState state) {
        super.setBlockState(state);
        buffer.resize(CraftworksConfig.assemblerBuffer(tierOf(state)));
        if (!tierOf(state).hasFluidBox()) {
            fluidBox.empty();
        }
    }

    /** The seven slots, inputs then the product then the remainders, for the menu and the game tests. */
    public ItemStacksResourceHandler inventory() {
        return inventory;
    }

    ResourceHandler<ItemResource> itemFace() {
        return items;
    }

    EnergyHandler energyFace() {
        return buffer.face();
    }

    /** The fluid box, for the menu and the game tests. Nothing is in it on tier 1. */
    public AssemblerFluidBox fluidBox() {
        return fluidBox;
    }

    /**
     * The fluid capability of the footprint block at {@code at}, seen from {@code side}: the box where that
     * block is a Fluid Connection and the face is the one pointing away from the machine, while the
     * connections exist, and otherwise nothing. Asked by the part blocks' lookups, which see only a position.
     */
    @Nullable ResourceHandler<FluidResource> fluidConnection(BlockPos at, Direction side) {
        if (!hasFluidConnections()) {
            return null;
        }
        return FluidConnections.sideAt(worldPosition, facing(), at).filter(side::equals).isPresent() ? fluidConnection : null;
    }

    private Direction facing() {
        return getBlockState().getValue(AssemblerBlock.FACING);
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

    // -- the Held recipe ------------------------------------------------------------------------

    /**
     * The Held recipe if this Assembler can run it. Asked every tick and by every pipe, so whether it can run
     * is worked out once per recipe instance: a reload hands back new ones.
     */
    private Optional<AssemblingRecipe> runnable(ServerLevel server) {
        // The category and the fluids are asked every time, not cached with the rest: a Fast Replace or a config
        // edit moves them.
        return held.runnable(server, HeldRecipes::canRun,
                found -> HeldRecipes.takesCategory(tier(), found) && HeldRecipes.takesFluids(tier(), found));
    }

    /** The fluid the Held recipe consumes, if it can run here and has one: tier 1 never has. */
    private Optional<SizedFluidIngredient> fluidIngredient(ServerLevel server) {
        return runnable(server).flatMap(AssemblerBlockEntity::fluidOf);
    }

    /** The one fluid ingredient a recipe an Assembler can run has, if any. */
    private static Optional<SizedFluidIngredient> fluidOf(AssemblingRecipe recipe) {
        return recipe.fluidIngredients().stream().findFirst();
    }

    /** What the box takes in for this recipe fluid: any fluid the ingredient matches. */
    private static Predicate<FluidResource> consumedBy(SizedFluidIngredient wanted) {
        return fluid -> wanted.ingredient().test(fluid.toStack(1));
    }

    /** Whether the Fluid Connections exist: the Held recipe runs here and has a fluid ingredient. Server only. */
    public boolean hasFluidConnections() {
        return level instanceof ServerLevel server && fluidIngredient(server).isPresent();
    }

    /** Whether the box takes {@code resource}: it is what the Held recipe consumes. False off the server. */
    boolean takesFluid(FluidResource resource) {
        return !resource.isEmpty() && level instanceof ServerLevel server
                && fluidIngredient(server).filter(wanted -> consumedBy(wanted).test(resource)).isPresent();
    }

    /**
     * Makes the connections what the Held recipe says: the origin's block state, which the model draws the
     * rings from, and the capability of the two connection blocks, which every pipe that asked is told changed.
     * A box holding what the recipe no longer takes is voided. Asked when the recipe is set and every tick,
     * which also catches a Fast Replace, a load and a reload of the recipes.
     */
    private void syncConnections(ServerLevel server) {
        boolean connected = fluidIngredient(server).isPresent();
        if (!connected || !takesFluid(fluidBox.getResource(0))) {
            fluidBox.empty();
        }
        BlockState state = getBlockState();
        if (state.getBlock() instanceof AssemblerBlock && state.getValue(AssemblerBlock.FLUID_CONNECTIONS) != connected) {
            server.setBlock(worldPosition, state.setValue(AssemblerBlock.FLUID_CONNECTIONS, connected), Block.UPDATE_CLIENTS);
            for (Direction side : FluidConnections.sides(facing())) {
                server.invalidateCapabilities(FluidConnections.at(worldPosition, side));
            }
        }
    }

    /**
     * Each connection pulls what the Held recipe consumes from the block it faces, up to the box's room. One
     * transaction a connection, simulated within and committed: a neighbour holding another fluid gives none.
     */
    private void pull(ServerLevel server, SizedFluidIngredient wanted) {
        for (Direction side : FluidConnections.sides(facing())) {
            int room = AssemblerFluidBox.CAPACITY - fluidBox.getAmountAsInt(0);
            if (room <= 0) {
                return;
            }
            ResourceHandler<FluidResource> neighbour = FluidMoves.across(server, FluidConnections.neighbour(worldPosition, side), side);
            if (neighbour != null) {
                FluidMoves.move(neighbour, fluidBox, consumedBy(wanted), room);
            }
        }
    }

    /**
     * Holds {@code next}. A change hands every ingredient already in the input slots back to {@code player},
     * what does not fit dropping at their feet, and starts the craft over; the outputs stay. Setting the
     * recipe already held moves nothing. The Lock is not asked here: {@link AssemblerMenu#request} asked
     * it once, of the player who pressed (ADR-0013). The fluid box is voided, and the Fluid Connections are
     * made what the new recipe says.
     */
    public void setHeldRecipe(Identifier next, Player player) {
        if (next.equals(held.idOrNull())) {
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
        held.set(next);
        progress = 0;
        fluidBox.empty();
        if (level instanceof ServerLevel server) {
            syncConnections(server);
        }
        setChanged();
    }

    /** The item of this Assembler keeps the Held recipe (Groundworks hands the origin the item's data). */
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
        syncConnections(server);
        Optional<AssemblingRecipe> resolved = runnable(server);
        if (resolved.isEmpty()) {
            return;
        }
        AssemblingRecipe recipe = resolved.get();
        fluidOf(recipe).ifPresent(wanted -> pull(server, wanted));
        try (Transaction probe = Transaction.openRoot()) {
            if (finish(recipe, probe) != null) {
                return;
            }
        }
        int duration = AssemblerRates.durationTicks(CraftworksConfig.assemblerSpeed(tier()), recipe.time());
        int fe = feThisTick(recipe, duration);
        try (Transaction tx = Transaction.openRoot()) {
            if (buffer.extract(fe, tx) != fe) {
                return;
            }
            int next = progress + 1;
            if (next >= duration) {
                if (finish(recipe, tx) != null) {
                    LOGGER.warn("Assembler at {} passed its checks and could not finish {}", worldPosition.toShortString(), held.idOrNull());
                    return;
                }
                next = 0;
            }
            tx.commit();
            progress = next;
        }
        setChanged();
    }

    /** The FE this tick of a craft of {@code duration} ticks costs: its share of the tier's price for the craft. */
    private int feThisTick(AssemblingRecipe recipe, int duration) {
        AssemblerTier tier = tier();
        double speed = CraftworksConfig.assemblerSpeed(tier);
        int price = AssemblerRates.fePerCraft(CraftworksConfig.assemblerPower(tier), speed, recipe.time());
        return AssemblerRates.feForTick(Math.min(progress, duration - 1), duration, price);
    }

    /**
     * What this Assembler is doing, asked the way {@link #serverTick} asks and changing nothing: the first
     * check it would fail, or {@link AssemblerState#CRAFTING}. Server only, which alone resolves the Held
     * recipe; anywhere else it reads as {@link AssemblerState#NO_RECIPE}. For Jade (#28).
     */
    public AssemblerState state() {
        if (held.id().isEmpty() || !(level instanceof ServerLevel server)) {
            return AssemblerState.NO_RECIPE;
        }
        Optional<AssemblingRecipe> resolved = runnable(server);
        if (resolved.isEmpty()) {
            return AssemblerState.CANT_RUN;
        }
        AssemblingRecipe recipe = resolved.get();
        try (Transaction probe = Transaction.openRoot()) {
            AssemblerState stalled = finish(recipe, probe);
            if (stalled != null) {
                return stalled;
            }
        }
        int duration = AssemblerRates.durationTicks(CraftworksConfig.assemblerSpeed(tier()), recipe.time());
        int fe = feThisTick(recipe, duration);
        try (Transaction probe = Transaction.openRoot()) {
            return buffer.extract(fe, probe) == fe ? AssemblerState.CRAFTING : AssemblerState.NEEDS_POWER;
        }
    }

    /**
     * Takes one craft's inputs, the {@code n}th ingredient from the {@code n}th slot and its fluid from the
     * box, and places its
     * first result in the product slot and, in the remainder slot, the ingredients' remainders and its
     * further results. Returns null if it all went, and otherwise what stopped it:
     * {@link AssemblerState#MISSING_INGREDIENTS} or {@link AssemblerState#OUTPUT_FULL}. Never part of a
     * craft: the caller aborts the transaction on a stop.
     */
    private @Nullable AssemblerState finish(AssemblingRecipe recipe, TransactionContext tx) {
        for (SizedFluidIngredient fluid : recipe.fluidIngredients()) {
            FluidResource resource = fluidBox.getResource(0);
            if (resource.isEmpty() || !fluid.test(resource.toStack(fluidBox.getAmountAsInt(0)))
                    || fluidBox.extract(0, resource, fluid.amount(), tx) != fluid.amount()) {
                return AssemblerState.MISSING_INGREDIENTS;
            }
        }
        for (int slot = 0; slot < recipe.ingredients().size(); slot++) {
            SizedIngredient sized = recipe.ingredients().get(slot);
            ItemResource resource = inventory.getResource(slot);
            if (resource.isEmpty() || !sized.ingredient().test(resource.toStack(1))
                    || inventory.extract(slot, resource, sized.count(), tx) != sized.count()) {
                return AssemblerState.MISSING_INGREDIENTS;
            }
            ItemStackTemplate remainder = resource.getItem().getCraftingRemainder(resource.toStack(1));
            if (remainder != null) {
                int owed = remainder.count() * sized.count();
                if (inventory.insert(AssemblerSlots.REMAINDERS, ItemResource.of(remainder), owed, tx) != owed) {
                    return AssemblerState.OUTPUT_FULL;
                }
            }
        }
        for (ItemStackTemplate extra : recipe.extraResults()) {
            if (inventory.insert(AssemblerSlots.REMAINDERS, ItemResource.of(extra), extra.count(), tx) != extra.count()) {
                return AssemblerState.OUTPUT_FULL;
            }
        }
        ItemStackTemplate product = recipe.productTemplate().orElse(null);
        if (product != null
                && inventory.insert(AssemblerSlots.PRODUCT, ItemResource.of(product), product.count(), tx) != product.count()) {
            return AssemblerState.OUTPUT_FULL;
        }
        return null;
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
        held.id().ifPresent(id -> output.store(HELD_KEY, Identifier.CODEC, id));
        output.putInt(PROGRESS_KEY, progress);
        inventory.serialize(output.child(ITEMS_KEY));
        buffer.serialize(output.child(ENERGY_KEY));
        fluidBox.serialize(output.child(FLUID_KEY));
    }

    /** The id only. It is resolved when asked, never here, where the recipes may not be loaded. */
    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        held.set(input.read(HELD_KEY, Identifier.CODEC).orElse(null));
        progress = input.getIntOr(PROGRESS_KEY, 0);
        inventory.deserialize(input.childOrEmpty(ITEMS_KEY));
        buffer.deserialize(input.childOrEmpty(ENERGY_KEY));
        fluidBox.deserialize(input.childOrEmpty(FLUID_KEY));
    }

    /**
     * Going, by a break or a command, drops the items; the energy and the fluid are lost. Not asked when Fast
     * Replace swaps the tier, which keeps this block entity and everything in it.
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
