// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.machine;

import java.util.Optional;
import java.util.function.Function;

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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
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
 * The block entity of a machine that makes its Held recipe over and over, an Assembler or a Chemical Plant:
 * the Held recipe, the item slots, an energy buffer and the craft under way. What a machine adds is its fluid
 * boxes, which recipes it can run, and what one craft takes and makes ({@link #finish}).
 *
 * <p>The Held recipe is an id, resolved when asked and never on load, when the recipes may not be there. It
 * is never matched from the items put in; Fill Recipe on the open screen sets it ({@link HeldMachineMenu#request}).
 * A machine with none, or whose own cannot run, idles and keeps it.
 *
 * <p>A craft is one transaction a tick: pay that tick's share of the craft's energy, count a tick of progress,
 * and on the last take the inputs and place the outputs, committing only if all of it went. Everything that
 * would stall it is asked first, in a probe that aborts, so a blocked machine draws nothing, starts nothing and
 * voids nothing. Progress is held across a stall: the inputs are only taken on the last tick, and the energy
 * already paid is the craft's. Changing the Held recipe resets it.
 */
public abstract class HeldMachineBlockEntity extends BlockEntity implements MenuProvider, MachineItemFace.Gate {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final String HELD_KEY = "held_recipe";
    private static final String PROGRESS_KEY = "progress";
    private static final String ITEMS_KEY = "items";
    private static final String ENERGY_KEY = "energy";

    protected final HeldRecipeRef held = new HeldRecipeRef();
    private int progress;

    protected final MachineInventory inventory;
    private final EnergyBuffer buffer = new EnergyBuffer();
    private final MachineItemFace items;

    private final Function<BlockState, MachineDefaults> defaultsOf;

    /** {@code defaultsOf} reads the machine's figures from its block state: an Assembler's follow its tier, the others' are fixed. */
    protected HeldMachineBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, MachineSlots slots,
            Function<BlockState, MachineDefaults> defaultsOf) {
        super(type, pos, state);
        this.defaultsOf = defaultsOf;
        inventory = new MachineInventory(slots, new MachineInventory.Owner() {
            @Override
            public Optional<SizedIngredient> ingredientAt(int slot) {
                return level instanceof ServerLevel server ? runnable(server).flatMap(recipe -> ingredientFor(slot, recipe)) : Optional.empty();
            }

            @Override
            public void changed() {
                setChanged();
            }
        });
        items = new MachineItemFace(this, inventory);
        buffer.resize(CraftworksConfig.buffer(defaults()));
    }

    /** The figures this machine starts from before the server config says otherwise. Read from the block state alone. */
    protected MachineDefaults defaults() {
        return defaultsOf.apply(getBlockState());
    }

    /**
     * The Held recipe if this machine can run it: what it asks once per recipe instance and what it asks every
     * time are the machine's, through {@link HeldRecipeRef#runnable}.
     */
    protected abstract Optional<AssemblingRecipe> runnable(ServerLevel server);

    /**
     * Takes one craft's inputs and places its outputs. Returns null if it all went, and otherwise what stopped it:
     * {@link MachineState#MISSING_INGREDIENTS} or {@link MachineState#OUTPUT_FULL}. Never part of a craft: the
     * caller aborts the transaction on a stop.
     */
    protected abstract @Nullable MachineState finish(AssemblingRecipe recipe, TransactionContext tx);

    /**
     * Makes the Fluid Connections what the Held recipe says, and voids a box holding what the recipe no longer
     * takes. Asked when the recipe is set and every tick.
     */
    protected abstract void syncConnections(ServerLevel server);

    /** Empties every fluid box: the Held recipe changed. */
    protected abstract void emptyFluids();

    /** Before the craft each tick, with a Held recipe that runs: the Fluid Connections pull its fluid in. */
    protected void beforeCraft(ServerLevel server, AssemblingRecipe recipe) {
    }

    /** After the craft each tick, with a Held recipe that runs: the Fluid Connections push its fluid out. */
    protected void afterCraft(ServerLevel server) {
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

    /** The resize a Fast Replace needs: the buffer follows the block. */
    protected void resizeBuffer() {
        buffer.resize(CraftworksConfig.buffer(defaults()));
    }

    // -- the Held recipe ------------------------------------------------------------------------

    /** The ingredient the Held recipe puts in {@code slot}: the {@code n}th in the {@code n}th input, and none elsewhere. */
    private Optional<SizedIngredient> ingredientFor(int slot, AssemblingRecipe recipe) {
        return inventory.slots().ingredientFor(slot, recipe.ingredients());
    }

    /**
     * Holds {@code next}. A change hands every ingredient already in the input slots back to {@code player},
     * what does not fit dropping at their feet, and starts the craft over; the outputs stay. Setting the
     * recipe already held moves nothing. The Lock is not asked here: {@link HeldMachineMenu#request} asked
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
        emptyFluids();
        if (level instanceof ServerLevel server) {
            syncConnections(server);
        }
        setChanged();
    }

    /** The item of this machine keeps the Held recipe (Groundworks hands the origin the item's data). */
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
        if (resource.isEmpty() || !(level instanceof ServerLevel server)) {
            return false;
        }
        return runnable(server)
                .flatMap(recipe -> ingredientFor(slot, recipe))
                .filter(ingredient -> ingredient.ingredient().test(resource.toStack(1)))
                .isPresent();
    }

    /**
     * How many more of its ingredient an insert through the item capability may put in {@code slot}: the
     * Overload Limit less what the slot holds. The menu's slots do not ask, so the hand is not held to it.
     */
    @Override
    public int overloadRoom(int slot) {
        if (!(level instanceof ServerLevel server)) {
            return 0;
        }
        return runnable(server)
                .flatMap(recipe -> ingredientFor(slot, recipe)
                        .map(ingredient -> OverloadLimit.room(ingredient.count(), overloadCrafts(recipe),
                                inventory.getAmountAsInt(slot))))
                .orElse(0);
    }

    /**
     * What input fluid box {@code n} holds at most, in mB: 4 crafts' worth of the Held recipe's {@code n}th fluid
     * ingredient, or a full box with none bound to it. A full box off the server, which alone resolves the Held recipe.
     */
    protected int inputBoxCapacity(int n) {
        if (!(level instanceof ServerLevel server)) {
            return FluidBoxes.INPUT_VOLUME;
        }
        return runnable(server).filter(recipe -> n < recipe.fluidIngredients().size())
                .map(recipe -> FluidBoxes.inputLimit(recipe.fluidIngredients().get(n).amount()))
                .orElse(FluidBoxes.INPUT_VOLUME);
    }

    /** How many crafts of {@code recipe} the Overload Limit lets this machine hold. */
    protected int overloadCrafts(AssemblingRecipe recipe) {
        return OverloadLimit.crafts(CraftworksConfig.speed(defaults()), recipe.time());
    }

    // -- the craft ------------------------------------------------------------------------------

    public void serverTick(ServerLevel server) {
        resizeBuffer();
        syncConnections(server);
        Optional<AssemblingRecipe> resolved = runnable(server);
        if (resolved.isEmpty()) {
            return;
        }
        AssemblingRecipe recipe = resolved.get();
        beforeCraft(server, recipe);
        craft(recipe);
        afterCraft(server);
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
        return CraftRates.durationTicks(CraftworksConfig.speed(defaults()), recipe.time());
    }

    /** The FE this tick of a craft of {@code duration} ticks costs: its share of the machine's price for the craft. */
    private int feThisTick(AssemblingRecipe recipe, int duration) {
        MachineDefaults machine = defaults();
        int price = CraftRates.fePerCraft(CraftworksConfig.power(machine), CraftworksConfig.speed(machine), recipe.time());
        return CraftRates.feForTick(Math.min(progress, duration - 1), duration, price);
    }

    /**
     * What this machine is doing, asked the way {@link #serverTick} asks and changing nothing: the first
     * check it would fail, or {@link MachineState#CRAFTING}. Server only, which alone resolves the Held
     * recipe; anywhere else it reads as {@link MachineState#NO_RECIPE}. For Jade (#28).
     */
    public MachineState state() {
        if (held.id().isEmpty() || !(level instanceof ServerLevel server)) {
            return MachineState.NO_RECIPE;
        }
        Optional<AssemblingRecipe> resolved = runnable(server);
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
     * Takes the {@code n}th item ingredient from the {@code n}th input slot, for {@link #finish}, placing each
     * one's remainder. Returns null if they all went, and otherwise what stopped it.
     */
    protected @Nullable MachineState takeItems(AssemblingRecipe recipe, TransactionContext tx) {
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

    /**
     * Places what {@code count} of {@code resource} leave behind when a craft takes them, or returns
     * {@link MachineState#OUTPUT_FULL}. Nothing by default: Fill Recipe refuses a Chemical Plant a recipe whose
     * ingredients leave any.
     */
    protected @Nullable MachineState placeRemainder(ItemResource resource, int count, TransactionContext tx) {
        return null;
    }

    /** Ticks into the craft under way, for the screen's progress bar. */
    public int craftProgress() {
        return progress;
    }

    /** The Held recipe's ticks at this machine's speed, or 0 with none that runs. Server only. */
    public int craftDuration() {
        if (!(level instanceof ServerLevel server)) {
            return 0;
        }
        return runnable(server).map(this::duration).orElse(0);
    }

    // -- persistence ----------------------------------------------------------------------------

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        held.id().ifPresent(id -> output.store(HELD_KEY, Identifier.CODEC, id));
        output.putInt(PROGRESS_KEY, progress);
        inventory.serialize(output.child(ITEMS_KEY));
        buffer.serialize(output.child(ENERGY_KEY));
    }

    /** The id only. It is resolved when asked, never here, where the recipes may not be loaded. */
    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        held.set(input.read(HELD_KEY, Identifier.CODEC).orElse(null));
        progress = input.getIntOr(PROGRESS_KEY, 0);
        inventory.deserialize(input.childOrEmpty(ITEMS_KEY));
        buffer.deserialize(input.childOrEmpty(ENERGY_KEY));
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
}
