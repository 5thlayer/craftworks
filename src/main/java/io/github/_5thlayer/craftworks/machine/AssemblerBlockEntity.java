// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.machine;

import java.util.Optional;

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
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.Nullable;

/**
 * An Assembler's block entity, one type for all three tiers: a {@link HeldMachineBlockEntity} with seven item
 * slots (five inputs, the product and the remainders) and a fluid box on tiers 2 and 3.
 *
 * <p>Tiers 2 and 3 take a fluid ingredient through the Fluid Connections ({@link FluidConnections}), which
 * exist only while the Held recipe has one: each tick every connection pulls the Held recipe's fluid from
 * the block it faces into the one fluid box, up to the box's room, and the craft takes its amount from the
 * box with the items. The box is kept over a reload and a Fast Replace between the two tiers, and voided by
 * a change of the Held recipe and by a tier with no box. An Assembler never pushes fluid.
 */
public final class AssemblerBlockEntity extends HeldMachineBlockEntity {

    private static final String FLUID_KEY = "fluid";

    private final AssemblerFluidBox fluidBox = new AssemblerFluidBox(this);
    private final AssemblerFluidConnection fluidConnection = new AssemblerFluidConnection(fluidBox);

    public AssemblerBlockEntity(BlockPos pos, BlockState state) {
        super(Assemblers.BLOCK_ENTITY.get(), pos, state, AssemblerSlots.LAYOUT, null);
    }

    private static AssemblerTier tierOf(BlockState state) {
        return state.getBlock() instanceof AssemblerBlock block ? block.tier() : AssemblerTier.ONE;
    }

    /** The tier is the block's: one block entity type serves all three. */
    public AssemblerTier tier() {
        return tierOf(getBlockState());
    }

    @Override
    protected MachineDefaults defaults() {
        return tier();
    }

    /**
     * Swapped for another tier by Fast Replace, which keeps this block entity: the buffer follows the new tier,
     * and a tier with no fluid box voids what the box held.
     */
    @Override
    public void setBlockState(BlockState state) {
        super.setBlockState(state);
        resizeBuffer();
        if (!tierOf(state).hasFluidBox()) {
            fluidBox.empty();
        }
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

    /** The fluid the Held recipe consumes, if it can run here and has one: tier 1 never has. */
    private Optional<SizedFluidIngredient> fluidIngredient(ServerLevel server) {
        return runnable(server).flatMap(AssemblerBlockEntity::fluidOf);
    }

    /** The one fluid ingredient a recipe an Assembler can run has, if any. */
    private static Optional<SizedFluidIngredient> fluidOf(AssemblingRecipe recipe) {
        return recipe.fluidIngredients().stream().findFirst();
    }

    /** Whether the Fluid Connections exist: the Held recipe runs here and has a fluid ingredient. Server only. */
    public boolean hasFluidConnections() {
        return level instanceof ServerLevel server && fluidIngredient(server).isPresent();
    }

    /** Whether the box takes {@code resource}: it is what the Held recipe consumes. False off the server. */
    boolean takesFluid(FluidResource resource) {
        return !resource.isEmpty() && level instanceof ServerLevel server
                && fluidIngredient(server).filter(wanted -> FluidMoves.consumedBy(wanted).test(resource)).isPresent();
    }

    /**
     * Makes the connections what the Held recipe says: the origin's block state, which the model draws the
     * rings from, and the capability of the two connection blocks, which every pipe that asked is told changed.
     * A box holding what the recipe no longer takes is voided. Asked when the recipe is set and every tick,
     * which also catches a Fast Replace, a load and a reload of the recipes.
     */
    @Override
    protected void syncConnections(ServerLevel server) {
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

    @Override
    protected void emptyFluids() {
        fluidBox.empty();
    }

    /**
     * Each connection pulls what the Held recipe consumes from the block it faces, up to the box's room. One
     * transaction a connection, simulated within and committed: a neighbour holding another fluid gives none.
     */
    @Override
    protected void beforeCraft(ServerLevel server, AssemblingRecipe recipe) {
        Optional<SizedFluidIngredient> wanted = fluidOf(recipe);
        if (wanted.isEmpty()) {
            return;
        }
        for (Direction side : FluidConnections.sides(facing())) {
            int room = AssemblerFluidBox.CAPACITY - fluidBox.getAmountAsInt(0);
            if (room <= 0) {
                return;
            }
            ResourceHandler<FluidResource> neighbour = FluidMoves.across(server, FluidConnections.neighbour(worldPosition, side), side);
            if (neighbour != null) {
                FluidMoves.move(neighbour, fluidBox, FluidMoves.consumedBy(wanted.get()), room);
            }
        }
    }

    // -- the craft ------------------------------------------------------------------------------

    /**
     * Takes one craft's inputs, the {@code n}th ingredient from the {@code n}th slot and its fluid from the
     * box, and places its first result in the product slot and, in the remainder slot, the ingredients'
     * remainders and its further results.
     */
    @Override
    protected @Nullable MachineState finish(AssemblingRecipe recipe, TransactionContext tx) {
        for (SizedFluidIngredient fluid : recipe.fluidIngredients()) {
            FluidResource resource = fluidBox.getResource(0);
            if (resource.isEmpty() || !fluid.test(resource.toStack(fluidBox.getAmountAsInt(0)))
                    || fluidBox.extract(0, resource, fluid.amount(), tx) != fluid.amount()) {
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

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        fluidBox.serialize(output.child(FLUID_KEY));
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        fluidBox.deserialize(input.childOrEmpty(FLUID_KEY));
    }

    // -- the screen -----------------------------------------------------------------------------

    @Override
    public @Nullable AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return AssemblerMenu.open(containerId, playerInventory, this);
    }
}
