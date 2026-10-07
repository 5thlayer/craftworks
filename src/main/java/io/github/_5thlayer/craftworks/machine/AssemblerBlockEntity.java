// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.machine;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import io.github._5thlayer.craftworks.machine.FluidLayout.Connection;
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
import net.neoforged.neoforge.fluids.FluidStackTemplate;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
import net.neoforged.neoforge.transfer.RangedResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.Nullable;

/**
 * An Assembler's block entity, one type for all three tiers: a {@link HeldMachineBlockEntity} with seven item
 * slots (five inputs, the product and the remainders) and, on tiers 2 and 3, the input and output fluid boxes
 * ({@link MachineFluids}) of {@link FluidLayout#ASSEMBLER}.
 *
 * <p>The Fluid Connections ({@link FluidLayout#connections}) exist only while the Held recipe runs here and
 * names a fluid, in or out. Each tick every connection pulls the Held recipe's fluid ingredients from the
 * block it faces, each into the box its order names, and after the craft pushes the fluid results out of their
 * boxes into it. The craft waits while an output box cannot hold what it makes. The boxes are kept over a reload
 * and a Fast Replace between tiers 2 and 3, and voided by a change of the Held recipe and by tier 1, which has none.
 */
public final class AssemblerBlockEntity extends HeldMachineBlockEntity {

    private static final String FLUID_KEY = "fluids";
    /** What saves before the boxes were many kept: one box, holding the Held recipe's one fluid ingredient. */
    private static final String OLD_FLUID_KEY = "fluid";

    private static final FluidLayout LAYOUT = FluidLayout.ASSEMBLER;

    private final MachineFluids fluids;
    private final MachineFluidFace fluidFace;
    /** The output boxes alone, which a connection pushes from. */
    private final ResourceHandler<FluidResource> outputs;

    public AssemblerBlockEntity(BlockPos pos, BlockState state) {
        super(Assemblers.BLOCK_ENTITY.get(), pos, state, AssemblerSlots.LAYOUT, AssemblerBlockEntity::tierOf);
        fluids = new MachineFluids(LAYOUT, new MachineFluids.Owner() {
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

            /** 4 crafts' worth of the ingredient bound to input box {@code n}, or a full box. */
            @Override
            public int inputCapacity(int n) {
                return inputBoxCapacity(n);
            }

            /** What the Held recipe sizes output box {@code n} at, or the box's own 100 mB. */
            @Override
            public int outputCapacity(int n) {
                if (!(level instanceof ServerLevel server)) {
                    return FluidBoxes.OUTPUT_BOX;
                }
                return runnable(server).filter(recipe -> n < recipe.fluidResults().size())
                        .map(recipe -> FluidBoxes.outputVolumes(LAYOUT.fluidOutputs(),
                                recipe.fluidResults().stream().map(FluidStackTemplate::amount).toList(), recipe.pinnedFluidResults()).get(n))
                        .orElse(FluidBoxes.OUTPUT_BOX);
            }

            @Override
            public void changed() {
                setChanged();
            }
        });
        fluidFace = new MachineFluidFace(LAYOUT, fluids);
        outputs = RangedResourceHandler.of(fluids, LAYOUT.fluidInputs(), LAYOUT.boxes());
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
     * and a tier with no fluid boxes voids what they held.
     */
    @Override
    public void setBlockState(BlockState state) {
        super.setBlockState(state);
        resizeBuffer();
        if (!tierOf(state).hasFluidBoxes()) {
            fluids.emptyAll();
        }
    }

    /** The fluid boxes, for the menu and the game tests. Nothing is in them on tier 1. */
    public MachineFluids fluids() {
        return fluids;
    }

    private Direction facing() {
        return getBlockState().getValue(AssemblerBlock.FACING);
    }

    private List<Connection> connections() {
        return LAYOUT.connections(worldPosition, facing());
    }

    /**
     * The boxes worth showing, in order: those the Held recipe binds a fluid to, and any that holds some. Server
     * only, which alone resolves the Held recipe. For Jade.
     */
    public List<Integer> boxesInUse() {
        Optional<AssemblingRecipe> recipe = level instanceof ServerLevel server ? runnable(server) : Optional.empty();
        List<Integer> shown = new ArrayList<>();
        for (int box = 0; box < LAYOUT.boxes(); box++) {
            int bound = LAYOUT.isInput(box)
                    ? recipe.map(found -> found.fluidIngredients().size()).orElse(0)
                    : recipe.map(found -> found.fluidResults().size()).orElse(0);
            if (LAYOUT.binding(box) < bound || fluids.getAmountAsInt(box) > 0) {
                shown.add(box);
            }
        }
        return shown;
    }

    // -- the Held recipe ------------------------------------------------------------------------

    /**
     * The Held recipe if this Assembler can run it. Whether it can at all is worked out once per recipe
     * instance; the category and the fluids are asked every time, since a Fast Replace or a config edit moves them.
     */
    @Override
    protected Optional<AssemblingRecipe> runnable(ServerLevel server) {
        return held.runnable(server, HeldRecipes::canRun,
                found -> HeldRecipes.takesCategory(tier(), found) && HeldRecipes.takesFluids(tier(), found));
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
        return hasFluidConnections() && LAYOUT.isConnection(worldPosition, facing(), at, side) ? fluidFace : null;
    }

    /**
     * Makes the connections what the Held recipe says: the origin's block state, which the model draws the
     * rings from, and the capability of the connection blocks, which every pipe that asked is told
     * changed. A box holding what the recipe no longer binds to it is voided. Asked when the recipe is set and
     * every tick, which also catches a Fast Replace, a load and a reload of the recipes.
     */
    @Override
    protected void syncConnections(ServerLevel server) {
        boolean connected = hasFluidConnections();
        for (int box = 0; box < LAYOUT.boxes(); box++) {
            if (!connected || !fluids.isValid(box, fluids.getResource(box))) {
                fluids.empty(box);
            }
        }
        BlockState state = getBlockState();
        if (state.getBlock() instanceof AssemblerBlock && state.getValue(AssemblerBlock.FLUID_CONNECTIONS) != connected) {
            server.setBlock(worldPosition, state.setValue(AssemblerBlock.FLUID_CONNECTIONS, connected), Block.UPDATE_CLIENTS);
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
                int room = fluids.capacity(box) - fluids.getAmountAsInt(box);
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
        for (int box = LAYOUT.fluidInputs(); box < LAYOUT.boxes(); box++) {
            if (fluids.getAmountAsInt(box) > 0) {
                return true;
            }
        }
        return false;
    }

    // -- the craft ------------------------------------------------------------------------------

    /**
     * Takes one craft's inputs, the {@code n}th fluid ingredient from input box {@code n} and the {@code n}th
     * item ingredient from the {@code n}th slot, and places its first item result in the product slot and, in the
     * remainder slot, the ingredients' remainders and its further item results, and each fluid result in the
     * output box its order names.
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
        for (int n = 0; n < recipe.fluidResults().size(); n++) {
            var made = recipe.fluidResults().get(n);
            if (fluids.insert(LAYOUT.outputBox(n), FluidResource.of(made), made.amount(), tx) != made.amount()) {
                return MachineState.OUTPUT_FULL;
            }
        }
        return null;
    }

    /** An ingredient's remainder goes in the remainder slot: cake's buckets, honey block's bottles. */
    @Override
    protected @Nullable MachineState placeRemainder(ItemResource resource, int count, TransactionContext tx) {
        ItemStackTemplate remainder = resource.getItem().getCraftingRemainder(resource.toStack(1));
        if (remainder == null) {
            return null;
        }
        int owed = remainder.count() * count;
        return inventory.insert(AssemblerSlots.REMAINDERS, ItemResource.of(remainder), owed, tx) == owed ? null : MachineState.OUTPUT_FULL;
    }

    // -- persistence ----------------------------------------------------------------------------

    /** Only {@code fluids} is ever written, the boxes in order. */
    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        fluids.serialize(output.child(FLUID_KEY));
    }

    /**
     * Reads {@code fluids} if the save has it. A save from before the boxes were many has {@code fluid}, one box
     * that took the Held recipe's one fluid ingredient, and what it held goes in input box 0, which takes that
     * ingredient now. A box above its new capacity is left as it is: {@link MachineFluids#displayCapacity} covers it.
     */
    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        Optional<ValueInput> current = input.child(FLUID_KEY);
        if (current.isPresent()) {
            fluids.deserialize(current.get());
            return;
        }
        fluids.emptyAll();
        input.child(OLD_FLUID_KEY).ifPresent(old -> {
            FluidStacksResourceHandler single = new FluidStacksResourceHandler(1, FluidBoxes.INPUT_VOLUME);
            single.deserialize(old);
            fluids.set(0, single.getResource(0).toStack(single.getAmountAsInt(0)));
        });
    }

    // -- the screen -----------------------------------------------------------------------------

    @Override
    public @Nullable AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return AssemblerMenu.open(containerId, playerInventory, this);
    }
}
