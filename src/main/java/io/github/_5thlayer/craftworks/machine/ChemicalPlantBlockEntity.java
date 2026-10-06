// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.machine;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import io.github._5thlayer.craftworks.machine.ChemicalPlantConnections.Connection;
import io.github._5thlayer.craftworks.recipe.AssemblingRecipe;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
import net.neoforged.neoforge.transfer.RangedResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.Nullable;

/**
 * A Chemical Plant's block entity: a {@link HeldMachineBlockEntity} with two input slots and a product slot,
 * and two input fluid boxes and two output fluid boxes ({@link ChemicalPlantFluids}).
 *
 * <p>The Fluid Connections ({@link ChemicalPlantConnections}) exist only while the Held recipe runs here and
 * names a fluid, in or out. Each tick every connection pulls the Held recipe's fluid ingredients from the
 * block it faces, each into the box its order names, and after the craft pushes the fluid results out of their
 * boxes into it: the plant makes fluid, which an Assembler never does. The craft waits while an output box
 * cannot hold what it makes. Boxes are voided by a change of the Held recipe, as an Assembler's is.
 */
public final class ChemicalPlantBlockEntity extends HeldMachineBlockEntity {

    private static final String FLUID_KEY = "fluids";

    private final ChemicalPlantFluids fluids = new ChemicalPlantFluids(new ChemicalPlantFluids.Owner() {
        @Override
        public boolean takesInput(int n, FluidResource resource) {
            return !resource.isEmpty() && level instanceof ServerLevel server
                    && runnable(server).filter(recipe -> n < recipe.fluidIngredients().size()
                            && recipe.fluidIngredients().get(n).ingredient().test(resource.toStack(1))).isPresent();
        }

        @Override
        public boolean makesOutput(int n, FluidResource resource) {
            return !resource.isEmpty() && level instanceof ServerLevel server
                    && runnable(server).filter(recipe -> n < recipe.fluidResults().size()
                            && resource.matches(recipe.fluidResults().get(n))).isPresent();
        }

        /** The Overload Limit's crafts of the result bound to output box {@code n}, or at least a bucket. */
        @Override
        public int outputCapacity(int n) {
            if (!(level instanceof ServerLevel server)) {
                return ChemicalPlantFluids.INPUT_CAPACITY;
            }
            return runnable(server).filter(recipe -> n < recipe.fluidResults().size())
                    .map(recipe -> OverloadLimit.outputBox(ChemicalPlantFluids.INPUT_CAPACITY, overloadCrafts(recipe),
                            recipe.fluidResults().get(n).amount()))
                    .orElse(ChemicalPlantFluids.INPUT_CAPACITY);
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
        super(ChemicalPlants.BLOCK_ENTITY.get(), pos, state, ChemicalPlantSlots.LAYOUT);
    }

    @Override
    protected MachineDefaults defaults() {
        return ChemicalPlantDefaults.INSTANCE;
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
            if (ChemicalPlantFluids.binding(box) < bound || fluids.getAmountAsInt(box) > 0) {
                shown.add(box);
            }
        }
        return shown;
    }

    // -- the Held recipe ------------------------------------------------------------------------

    /**
     * The Held recipe if this plant can run it: what its boxes and slots take is worked out once per recipe
     * instance, and its category, which the config moves, every time.
     */
    @Override
    protected Optional<AssemblingRecipe> runnable(ServerLevel server) {
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

    /**
     * Makes the connections what the Held recipe says: the origin's block state, which the model draws the
     * rings from, and the capability of the four connection blocks, which every pipe that asked is told
     * changed. A box holding what the recipe no longer binds to it is voided. Asked when the recipe is set and
     * every tick, which also catches a load and a reload of the recipes.
     */
    @Override
    protected void syncConnections(ServerLevel server) {
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

    @Override
    protected void emptyFluids() {
        fluids.emptyAll();
    }

    /**
     * Each connection pulls what the Held recipe consumes from the block it faces, each fluid into the box its
     * order names, up to the box's room. One transaction a connection and a box, simulated within and
     * committed: a neighbour holding another fluid gives none.
     */
    @Override
    protected void beforeCraft(ServerLevel server, AssemblingRecipe recipe) {
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
                        FluidMoves.consumedBy(recipe.fluidIngredients().get(box)), room);
            }
        }
    }

    /** Each connection pushes what the output boxes hold into the block it faces, as much as it takes. */
    @Override
    protected void afterCraft(ServerLevel server) {
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

    // -- the craft ------------------------------------------------------------------------------

    /**
     * Takes one craft's inputs, the {@code n}th fluid ingredient from input box {@code n} and the {@code n}th
     * item ingredient from the {@code n}th slot, and places its item result in the product slot and each
     * fluid result in the output box its order names.
     */
    @Override
    protected @Nullable MachineState finish(AssemblingRecipe recipe, TransactionContext tx) {
        List<SizedFluidIngredient> wanted = recipe.fluidIngredients();
        for (int box = 0; box < wanted.size(); box++) {
            FluidResource resource = fluids.getResource(box);
            if (resource.isEmpty() || !wanted.get(box).test(resource.toStack(fluids.getAmountAsInt(box)))
                    || fluids.extract(box, resource, wanted.get(box).amount(), tx) != wanted.get(box).amount()) {
                return MachineState.MISSING_INGREDIENTS;
            }
        }
        MachineState stopped = takeItems(recipe, tx);
        if (stopped != null) {
            return stopped;
        }
        ItemStackTemplate product = recipe.productTemplate().orElse(null);
        if (product != null
                && inventory.insert(ChemicalPlantSlots.PRODUCT, ItemResource.of(product), product.count(), tx) != product.count()) {
            return MachineState.OUTPUT_FULL;
        }
        for (int n = 0; n < recipe.fluidResults().size(); n++) {
            var made = recipe.fluidResults().get(n);
            if (fluids.insert(ChemicalPlantFluids.outputBox(n), FluidResource.of(made), made.amount(), tx) != made.amount()) {
                return MachineState.OUTPUT_FULL;
            }
        }
        return null;
    }

    // -- persistence ----------------------------------------------------------------------------

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        fluids.serialize(output.child(FLUID_KEY));
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        fluids.deserialize(input.childOrEmpty(FLUID_KEY));
    }

    // -- the screen -----------------------------------------------------------------------------

    @Override
    public @Nullable AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return ChemicalPlantMenu.open(containerId, playerInventory, this);
    }
}
