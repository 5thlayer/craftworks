// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.machine;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

import com.mojang.logging.LogUtils;
import io.github._5thlayer.craftworks.CraftworksConfig;
import io.github._5thlayer.craftworks.machine.ChemicalPlantConnections.Connection;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
import net.neoforged.neoforge.transfer.RangedResourceHandler;
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
 * A Chemical Plant's block entity: the Held recipe, two input slots and a product slot, an energy buffer, two
 * input fluid boxes and two output fluid boxes, and the craft under way. It is built on the Assembler's
 * machinery and crafts the same way: the Held recipe is an id resolved when asked, and a craft is one
 * transaction a tick that pays that tick's share of its energy, counts a tick of progress and, on the last,
 * takes the inputs and places the outputs, committing only if all of it went. A craft that would stall is
 * asked first, in a probe that aborts, so a blocked plant draws nothing, starts nothing and voids nothing.
 *
 * <p>The Fluid Connections ({@link ChemicalPlantConnections}) exist only while the Held recipe runs here and
 * names a fluid, in or out. Each tick every connection pulls the Held recipe's fluid ingredients from the
 * block it faces, each into the box its order names, and after the craft pushes the fluid results out of their
 * boxes into it: the plant makes fluid, which an Assembler never does. The craft waits while an output box
 * cannot hold what it makes. Boxes are voided by a change of the Held recipe, as an Assembler's is.
 */
public final class ChemicalPlantBlockEntity extends BlockEntity implements MenuProvider, HeldMachine, MachineItemFace.Gate {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final String HELD_KEY = "held_recipe";
    private static final String PROGRESS_KEY = "progress";
    private static final String ITEMS_KEY = "items";
    private static final String ENERGY_KEY = "energy";
    private static final String FLUID_KEY = "fluids";

    private final HeldRecipeSlot held = new HeldRecipeSlot();
    private int progress;

    private final MachineInventory inventory = new MachineInventory(ChemicalPlantSlots.SIZE, ChemicalPlantSlots.INPUTS,
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
    private final MachineItemFace items = new MachineItemFace(this, inventory);
    private final ChemicalPlantFluids fluids = new ChemicalPlantFluids(new ChemicalPlantFluids.Owner() {
        @Override
        public boolean takesInput(int box, FluidResource resource) {
            return takesFluid(box, resource);
        }

        @Override
        public boolean makesOutput(int box, FluidResource resource) {
            return makesFluid(box, resource);
        }

        @Override
        public int outputCapacity(int box) {
            return outputVolume(box);
        }

        @Override
        public void changed() {
            setChanged();
        }
    });
    private final ChemicalPlantFluidFace fluidFace = new ChemicalPlantFluidFace(fluids);
    /** The two output boxes alone, which a connection pushes from. */
    private final ResourceHandler<FluidResource> outputs = RangedResourceHandler.of(fluids,
            ChemicalPlantFluids.INPUTS, ChemicalPlantFluids.SIZE);

    public ChemicalPlantBlockEntity(BlockPos pos, BlockState state) {
        super(ChemicalPlants.BLOCK_ENTITY.get(), pos, state);
        buffer.resize(CraftworksConfig.buffer(ChemicalPlantRates.INSTANCE));
    }

    /** The two input slots then the product, for the menu and the game tests. */
    public ItemStacksResourceHandler inventory() {
        return inventory;
    }

    ResourceHandler<ItemResource> itemFace() {
        return items;
    }

    EnergyHandler energyFace() {
        return buffer.face();
    }

    /** The four fluid boxes, for the menu and the game tests. */
    public ChemicalPlantFluids fluids() {
        return fluids;
    }

    private Direction facing() {
        return getBlockState().getValue(ChemicalPlantBlock.FACING);
    }

    private List<Connection> connections() {
        return ChemicalPlantConnections.of(worldPosition, facing());
    }

    /**
     * The boxes worth showing, in order: those the Held recipe binds a fluid to, and any that holds some. Server
     * only, which alone resolves the Held recipe. For Jade.
     */
    public List<Integer> boxesInUse() {
        Optional<AssemblingRecipe> recipe = level instanceof ServerLevel server ? runnable(server) : Optional.empty();
        List<Integer> shown = new ArrayList<>();
        for (int box = 0; box < ChemicalPlantFluids.SIZE; box++) {
            int bound = ChemicalPlantFluids.isInput(box)
                    ? recipe.map(found -> found.fluidIngredients().size()).orElse(0)
                    : recipe.map(found -> found.fluidResults().size()).orElse(0);
            int index = ChemicalPlantFluids.isInput(box) ? box : box - ChemicalPlantFluids.INPUTS;
            if (index < bound || fluids.getAmountAsInt(box) > 0) {
                shown.add(box);
            }
        }
        return shown;
    }

    /** The energy in the buffer, in FE. */
    public int energy() {
        return buffer.getAmountAsInt();
    }

    public int energyCapacity() {
        return buffer.getCapacityAsInt();
    }

    @Override
    public Optional<Identifier> heldRecipe() {
        return held.id();
    }

    // -- the Held recipe ------------------------------------------------------------------------

    /**
     * The Held recipe if this plant can run it: what its boxes and slots take is worked out once per recipe
     * instance, and its category, which the config moves, every time.
     */
    private Optional<AssemblingRecipe> runnable(ServerLevel server) {
        return held.runnable(server, ChemicalPlantRecipes::canRun, ChemicalPlantRecipes::takesCategory);
    }

    /** Whether the Fluid Connections exist: the Held recipe runs here and names a fluid, in or out. Server only. */
    public boolean hasFluidConnections() {
        return level instanceof ServerLevel server
                && runnable(server).filter(recipe -> !recipe.fluidIngredients().isEmpty() || !recipe.fluidResults().isEmpty()).isPresent();
    }

    /**
     * The fluid capability of the footprint block at {@code at}, seen from {@code side}: the boxes where that
     * block is a Fluid Connection and the face is the one pointing away from the machine, while the
     * connections exist, and otherwise nothing. Asked by the part blocks' lookups, which see only a position.
     */
    @Nullable ResourceHandler<FluidResource> fluidConnection(BlockPos at, Direction side) {
        return hasFluidConnections() && ChemicalPlantConnections.isFace(worldPosition, facing(), at, side) ? fluidFace : null;
    }

    private boolean takesFluid(int box, FluidResource resource) {
        return !resource.isEmpty() && level instanceof ServerLevel server
                && runnable(server).filter(recipe -> box < recipe.fluidIngredients().size()
                        && recipe.fluidIngredients().get(box).ingredient().test(resource.toStack(1))).isPresent();
    }

    private boolean makesFluid(int box, FluidResource resource) {
        return !resource.isEmpty() && level instanceof ServerLevel server
                && runnable(server).filter(recipe -> box < recipe.fluidResults().size()
                        && resource.matches(recipe.fluidResults().get(box))).isPresent();
    }

    /** What output box {@code box} holds under the Held recipe: the Overload Limit's crafts of its result, or at least a bucket. */
    private int outputVolume(int box) {
        if (!(level instanceof ServerLevel server)) {
            return ChemicalPlantFluids.INPUT_CAPACITY;
        }
        return runnable(server).filter(recipe -> box < recipe.fluidResults().size())
                .map(recipe -> OverloadLimit.outputBox(ChemicalPlantFluids.INPUT_CAPACITY,
                        OverloadLimit.crafts(CraftworksConfig.speed(ChemicalPlantRates.INSTANCE), recipe.time()),
                        recipe.fluidResults().get(box).amount()))
                .orElse(ChemicalPlantFluids.INPUT_CAPACITY);
    }

    private static Predicate<FluidResource> consumedBy(SizedFluidIngredient wanted) {
        return fluid -> wanted.ingredient().test(fluid.toStack(1));
    }

    /**
     * Makes the connections what the Held recipe says: the origin's block state, which the model draws the
     * rings from, and the capability of the four connection blocks, which every pipe that asked is told
     * changed. A box holding what the recipe no longer binds to it is voided. Asked when the recipe is set and
     * every tick, which also catches a load and a reload of the recipes.
     */
    private void syncConnections(ServerLevel server) {
        boolean connected = hasFluidConnections();
        for (int box = 0; box < ChemicalPlantFluids.SIZE; box++) {
            if (!connected || !fluids.isValid(box, fluids.getResource(box))) {
                fluids.empty(box);
            }
        }
        BlockState state = getBlockState();
        if (state.getBlock() instanceof ChemicalPlantBlock && state.getValue(ChemicalPlantBlock.FLUID_CONNECTIONS) != connected) {
            server.setBlock(worldPosition, state.setValue(ChemicalPlantBlock.FLUID_CONNECTIONS, connected), Block.UPDATE_CLIENTS);
            for (Connection connection : connections()) {
                server.invalidateCapabilities(connection.block());
            }
        }
    }

    /**
     * Each connection pulls what the Held recipe consumes from the block it faces, each fluid into the box its
     * order names, up to the box's room. One transaction a connection and a box, simulated within and
     * committed: a neighbour holding another fluid gives none.
     */
    private void pull(ServerLevel server, AssemblingRecipe recipe) {
        for (Connection connection : connections()) {
            ResourceHandler<FluidResource> neighbour = null;
            for (int box = 0; box < recipe.fluidIngredients().size(); box++) {
                int room = ChemicalPlantFluids.INPUT_CAPACITY - fluids.getAmountAsInt(box);
                if (room <= 0) {
                    continue;
                }
                if (neighbour == null) {
                    neighbour = FluidMoves.across(server, connection.beyond(), connection.side());
                    if (neighbour == null) {
                        break;
                    }
                }
                FluidMoves.move(neighbour, RangedResourceHandler.ofSingleIndex(fluids, box),
                        consumedBy(recipe.fluidIngredients().get(box)), room);
            }
        }
    }

    /** Each connection pushes what the output boxes hold into the block it faces, as much as it takes. */
    private void push(ServerLevel server) {
        for (Connection connection : connections()) {
            if (!anyOutput()) {
                return;
            }
            ResourceHandler<FluidResource> neighbour = FluidMoves.across(server, connection.beyond(), connection.side());
            if (neighbour != null) {
                FluidMoves.move(outputs, neighbour, fluid -> true, Integer.MAX_VALUE);
            }
        }
    }

    private boolean anyOutput() {
        for (int box = ChemicalPlantFluids.INPUTS; box < ChemicalPlantFluids.SIZE; box++) {
            if (fluids.getAmountAsInt(box) > 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * Holds {@code next}. A change hands every ingredient already in the input slots back to {@code player},
     * what does not fit dropping at their feet, and starts the craft over; the product stays. Setting the
     * recipe already held moves nothing. The Lock is not asked here: {@link ChemicalPlantMenu#request} asked
     * it once, of the player who pressed (ADR-0013). The four boxes are voided, and the Fluid Connections are
     * made what the new recipe says.
     */
    public void setHeldRecipe(Identifier next, Player player) {
        if (next.equals(held.idOrNull())) {
            return;
        }
        for (int slot = 0; slot < ChemicalPlantSlots.INPUTS; slot++) {
            ItemStack stack = inventory.getResource(slot).toStack(inventory.getAmountAsInt(slot));
            if (stack.isEmpty()) {
                continue;
            }
            inventory.set(slot, ItemResource.EMPTY, 0);
            player.getInventory().placeItemBackInInventory(stack);
        }
        held.set(next);
        progress = 0;
        fluids.emptyAll();
        if (level instanceof ServerLevel server) {
            syncConnections(server);
        }
        setChanged();
    }

    /** The item of this plant keeps the Held recipe (Groundworks hands the origin the item's data). */
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
                .flatMap(recipe -> AssemblerSlots.ingredientFor(slot, recipe.ingredients()))
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
                .flatMap(recipe -> AssemblerSlots.ingredientFor(slot, recipe.ingredients())
                        .map(ingredient -> OverloadLimit.room(ingredient.count(),
                                OverloadLimit.crafts(CraftworksConfig.speed(ChemicalPlantRates.INSTANCE), recipe.time()),
                                inventory.getAmountAsInt(slot))))
                .orElse(0);
    }

    // -- the craft ------------------------------------------------------------------------------

    public void serverTick(ServerLevel server) {
        buffer.resize(CraftworksConfig.buffer(ChemicalPlantRates.INSTANCE));
        syncConnections(server);
        Optional<AssemblingRecipe> resolved = runnable(server);
        if (resolved.isEmpty()) {
            return;
        }
        AssemblingRecipe recipe = resolved.get();
        pull(server, recipe);
        craft(recipe);
        push(server);
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
                    LOGGER.warn("Chemical Plant at {} passed its checks and could not finish {}", worldPosition.toShortString(), held.idOrNull());
                    return;
                }
                next = 0;
            }
            tx.commit();
            progress = next;
        }
        setChanged();
    }

    private static int duration(AssemblingRecipe recipe) {
        return AssemblerRates.durationTicks(CraftworksConfig.speed(ChemicalPlantRates.INSTANCE), recipe.time());
    }

    /** The FE this tick of a craft of {@code duration} ticks costs: its share of the price for the craft. */
    private int feThisTick(AssemblingRecipe recipe, int duration) {
        double speed = CraftworksConfig.speed(ChemicalPlantRates.INSTANCE);
        int price = AssemblerRates.fePerCraft(CraftworksConfig.power(ChemicalPlantRates.INSTANCE), speed, recipe.time());
        return AssemblerRates.feForTick(Math.min(progress, duration - 1), duration, price);
    }

    /**
     * What this plant is doing, asked the way {@link #serverTick} asks and changing nothing: the first check it
     * would fail, or {@link AssemblerState#CRAFTING}. Server only, which alone resolves the Held recipe;
     * anywhere else it reads as {@link AssemblerState#NO_RECIPE}. For Jade.
     */
    @Override
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
        int duration = duration(recipe);
        int fe = feThisTick(recipe, duration);
        try (Transaction probe = Transaction.openRoot()) {
            return buffer.extract(fe, probe) == fe ? AssemblerState.CRAFTING : AssemblerState.NEEDS_POWER;
        }
    }

    /**
     * Takes one craft's inputs, the {@code n}th fluid ingredient from input box {@code n} and the {@code n}th
     * item ingredient from the {@code n}th slot, and places its item result in the product slot and each
     * fluid result in the output box its order names. Returns null if it all went, and otherwise what stopped
     * it: {@link AssemblerState#MISSING_INGREDIENTS} or {@link AssemblerState#OUTPUT_FULL}. Never part of a
     * craft: the caller aborts the transaction on a stop.
     */
    private @Nullable AssemblerState finish(AssemblingRecipe recipe, TransactionContext tx) {
        List<SizedFluidIngredient> wanted = recipe.fluidIngredients();
        for (int box = 0; box < wanted.size(); box++) {
            FluidResource resource = fluids.getResource(box);
            if (resource.isEmpty() || !wanted.get(box).test(resource.toStack(fluids.getAmountAsInt(box)))
                    || fluids.extract(box, resource, wanted.get(box).amount(), tx) != wanted.get(box).amount()) {
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
        }
        ItemStackTemplate product = recipe.productTemplate().orElse(null);
        if (product != null
                && inventory.insert(ChemicalPlantSlots.PRODUCT, ItemResource.of(product), product.count(), tx) != product.count()) {
            return AssemblerState.OUTPUT_FULL;
        }
        for (int result = 0; result < recipe.fluidResults().size(); result++) {
            var made = recipe.fluidResults().get(result);
            if (fluids.insert(ChemicalPlantFluids.INPUTS + result, FluidResource.of(made), made.amount(), tx) != made.amount()) {
                return AssemblerState.OUTPUT_FULL;
            }
        }
        return null;
    }

    /** Ticks into the craft under way, for the screen's progress bar. */
    @Override
    public int craftProgress() {
        return progress;
    }

    /** The Held recipe's ticks at this plant's speed, or 0 with none that runs. Server only. */
    @Override
    public int craftDuration() {
        if (!(level instanceof ServerLevel server)) {
            return 0;
        }
        return runnable(server).map(ChemicalPlantBlockEntity::duration).orElse(0);
    }

    // -- persistence ----------------------------------------------------------------------------

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        held.id().ifPresent(id -> output.store(HELD_KEY, Identifier.CODEC, id));
        output.putInt(PROGRESS_KEY, progress);
        inventory.serialize(output.child(ITEMS_KEY));
        buffer.serialize(output.child(ENERGY_KEY));
        fluids.serialize(output.child(FLUID_KEY));
    }

    /** The id only. It is resolved when asked, never here, where the recipes may not be loaded. */
    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        held.set(input.read(HELD_KEY, Identifier.CODEC).orElse(null));
        progress = input.getIntOr(PROGRESS_KEY, 0);
        inventory.deserialize(input.childOrEmpty(ITEMS_KEY));
        buffer.deserialize(input.childOrEmpty(ENERGY_KEY));
        fluids.deserialize(input.childOrEmpty(FLUID_KEY));
    }

    /** Going, by a break or a command, drops the items; the energy and the fluid are lost. */
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
        return ChemicalPlantMenu.open(containerId, playerInventory, this);
    }
}
