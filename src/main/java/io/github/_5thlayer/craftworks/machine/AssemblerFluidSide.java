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
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.Nullable;

/**
 * The fluid half of an Assembler, which its block entity owns: the input and output boxes
 * ({@link MachineFluids}) of {@link FluidLayout#ASSEMBLER} and the Fluid Connections ({@link FluidLayout#connections})
 * that pull the Held recipe's fluid ingredients in and push its fluid results out.
 *
 * <p>The connections exist only while the Held recipe runs here and names a fluid, in or out. The fluid side knows
 * the Held recipe only through its {@link Host}, so a test can stand one up with a recipe of its own and reach
 * every part of it without the craft loop: {@link #pull} and {@link #push} are one move each, {@link #take} and
 * {@link #place} are the fluid part of one craft, and the boxes are saved and loaded as {@code fluids}. The boxes
 * are kept over a reload and a Fast Replace between any two tiers, and voided by a change of the Held recipe
 * ({@link #empty}).
 */
public final class AssemblerFluidSide {

    private static final String FLUID_KEY = "fluids";
    /** What saves before the boxes were many kept: one box, holding the Held recipe's one fluid ingredient. */
    private static final String OLD_FLUID_KEY = "fluid";

    private static final FluidLayout LAYOUT = FluidLayout.ASSEMBLER;

    /** What the fluid side asks of the Assembler that owns it. */
    public interface Host {

        /** The Held recipe if the Assembler can run it; empty with none, and off the server, which alone resolves it. */
        Optional<AssemblingRecipe> runnable();

        /** The origin's position, which the connections stand around. */
        BlockPos pos();

        /** The origin's block state: its facing turns the connections, and its connection flag is what the model draws. */
        BlockState blockState();

        /** The boxes changed, so the block entity needs saving. */
        void changed();
    }

    private final Host host;
    private final MachineFluids boxes;
    private final MachineFluidFace face;
    /** The output boxes alone, which a connection pushes from. */
    private final ResourceHandler<FluidResource> outputs;

    public AssemblerFluidSide(Host host) {
        this.host = host;
        boxes = new MachineFluids(LAYOUT, new MachineFluids.Owner() {
            @Override
            public boolean takesInput(int n, FluidResource resource) {
                return !resource.isEmpty() && host.runnable()
                        .filter(recipe -> n < recipe.fluidIngredients().size()
                                && recipe.fluidIngredients().get(n).ingredient().test(resource.toStack(1)))
                        .isPresent();
            }

            @Override
            public boolean makesOutput(int n, FluidResource resource) {
                return !resource.isEmpty() && host.runnable()
                        .filter(recipe -> n < recipe.fluidResults().size() && resource.matches(recipe.fluidResults().get(n)))
                        .isPresent();
            }

            /** 4 crafts' worth of the Held recipe's {@code n}th fluid ingredient, or a full box with none bound to it. */
            @Override
            public int inputCapacity(int n) {
                return host.runnable().filter(recipe -> n < recipe.fluidIngredients().size())
                        .map(recipe -> FluidBoxes.inputLimit(recipe.fluidIngredients().get(n).amount()))
                        .orElse(FluidBoxes.INPUT_VOLUME);
            }

            /** What the Held recipe sizes output box {@code n} at, or the box's own 100 mB. */
            @Override
            public int outputCapacity(int n) {
                return host.runnable().filter(recipe -> n < recipe.fluidResults().size())
                        .map(recipe -> FluidBoxes.outputVolumes(LAYOUT.fluidOutputs(),
                                recipe.fluidResults().stream().map(FluidStackTemplate::amount).toList(), recipe.pinnedFluidResults()).get(n))
                        .orElse(FluidBoxes.OUTPUT_BOX);
            }

            @Override
            public void changed() {
                host.changed();
            }
        });
        face = new MachineFluidFace(LAYOUT, boxes);
        outputs = RangedResourceHandler.of(boxes, LAYOUT.fluidInputs(), LAYOUT.boxes());
    }

    /** The boxes, inputs first, for the menu and the game tests. */
    public MachineFluids boxes() {
        return boxes;
    }

    private Direction facing() {
        return host.blockState().getValue(AssemblerBlock.FACING);
    }

    private List<Connection> connections() {
        return LAYOUT.connections(host.pos(), facing());
    }

    /** Whether the Fluid Connections exist: the Held recipe runs here and names a fluid, in or out. */
    public boolean hasConnections() {
        return host.runnable()
                .filter(recipe -> !recipe.fluidIngredients().isEmpty() || !recipe.fluidResults().isEmpty()).isPresent();
    }

    /**
     * The boxes worth showing, in order: those the Held recipe binds a fluid to, and any that holds some. For Jade.
     */
    public List<Integer> boxesInUse() {
        Optional<AssemblingRecipe> recipe = host.runnable();
        List<Integer> shown = new ArrayList<>();
        for (int box = 0; box < LAYOUT.boxes(); box++) {
            int bound = LAYOUT.isInput(box)
                    ? recipe.map(found -> found.fluidIngredients().size()).orElse(0)
                    : recipe.map(found -> found.fluidResults().size()).orElse(0);
            if (LAYOUT.binding(box) < bound || boxes.getAmountAsInt(box) > 0) {
                shown.add(box);
            }
        }
        return shown;
    }

    /**
     * The fluid capability of the footprint block at {@code at}, seen from {@code side}: the boxes where that
     * block is a Fluid Connection and the face is the one pointing away from the Assembler, while the
     * connections exist, and otherwise nothing. Asked by the part blocks' lookups, which see only a position.
     */
    public @Nullable ResourceHandler<FluidResource> connection(BlockPos at, Direction side) {
        return hasConnections() && LAYOUT.isConnection(host.pos(), facing(), at, side) ? face : null;
    }

    /**
     * Makes the connections what the Held recipe says: the origin's block state, which the model draws the
     * rings from, and the capability of the connection blocks, which every pipe that asked is told
     * changed. A box holding what the recipe no longer binds to it is voided. Asked when the recipe is set and
     * every tick, which also catches a Fast Replace, a load and a reload of the recipes.
     */
    public void syncConnections(ServerLevel server) {
        boolean connected = hasConnections();
        for (int box = 0; box < LAYOUT.boxes(); box++) {
            if (!connected || !boxes.isValid(box, boxes.getResource(box))) {
                boxes.empty(box);
            }
        }
        BlockState state = host.blockState();
        if (state.getBlock() instanceof AssemblerBlock && state.getValue(AssemblerBlock.FLUID_CONNECTIONS) != connected) {
            server.setBlock(host.pos(), state.setValue(AssemblerBlock.FLUID_CONNECTIONS, connected), Block.UPDATE_CLIENTS);
            for (Connection connection : connections()) {
                server.invalidateCapabilities(connection.block());
            }
        }
    }

    /** Empties every box: the Held recipe changed. */
    public void empty() {
        boxes.emptyAll();
    }

    /**
     * Each connection pulls what {@code recipe} consumes from the block it faces, each fluid into the box its
     * order names, up to the box's room. One transaction a connection and a box, simulated within and
     * committed: a neighbour holding another fluid gives none.
     */
    public void pull(ServerLevel server, AssemblingRecipe recipe) {
        for (Connection connection : connections()) {
            ResourceHandler<FluidResource> neighbour = null;
            for (int box = 0; box < recipe.fluidIngredients().size(); box++) {
                int room = boxes.capacity(box) - boxes.getAmountAsInt(box);
                if (room <= 0) {
                    continue;
                }
                if (neighbour == null) {
                    neighbour = FluidMoves.across(server, connection.beyond(), connection.side());
                    if (neighbour == null) {
                        break;
                    }
                }
                FluidMoves.move(neighbour, RangedResourceHandler.ofSingleIndex(boxes, box),
                        FluidMoves.consumedBy(recipe.fluidIngredients().get(box)), room);
            }
        }
    }

    /** Each connection pushes what the output boxes hold into the block it faces, as much as it takes. */
    public void push(ServerLevel server) {
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
            if (boxes.getAmountAsInt(box) > 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * Takes one craft's fluid ingredients out of the input boxes, the {@code n}th from input box {@code n}, in
     * {@code tx}. Returns null if they all went, and otherwise {@link MachineState#MISSING_INGREDIENTS} for the
     * first box that is empty, holds another fluid or holds less than the recipe needs. Some may already be
     * taken in {@code tx} on a stop, so the caller aborts it.
     */
    public @Nullable MachineState take(AssemblingRecipe recipe, TransactionContext tx) {
        List<SizedFluidIngredient> wanted = recipe.fluidIngredients();
        for (int box = 0; box < wanted.size(); box++) {
            FluidResource resource = boxes.getResource(box);
            if (resource.isEmpty() || !wanted.get(box).test(resource.toStack(boxes.getAmountAsInt(box)))
                    || boxes.extract(box, resource, wanted.get(box).amount(), tx) != wanted.get(box).amount()) {
                return MachineState.MISSING_INGREDIENTS;
            }
        }
        return null;
    }

    /**
     * Places one craft's fluid results, the {@code n}th in the output box its order names. Returns null if they
     * all went, and otherwise {@link MachineState#OUTPUT_FULL}.
     */
    public @Nullable MachineState place(AssemblingRecipe recipe, TransactionContext tx) {
        for (int n = 0; n < recipe.fluidResults().size(); n++) {
            var made = recipe.fluidResults().get(n);
            if (boxes.insert(LAYOUT.outputBox(n), FluidResource.of(made), made.amount(), tx) != made.amount()) {
                return MachineState.OUTPUT_FULL;
            }
        }
        return null;
    }

    /** Only {@code fluids} is ever written, the boxes in order. */
    public void save(ValueOutput output) {
        boxes.serialize(output.child(FLUID_KEY));
    }

    /**
     * Reads {@code fluids} if the save has it. A save from before the boxes were many has {@code fluid}, one box
     * that took the Held recipe's one fluid ingredient, and what it held goes in input box 0, which takes that
     * ingredient now. A box above its new capacity is left as it is: {@link MachineFluids#displayCapacity} covers it.
     */
    public void load(ValueInput input) {
        Optional<ValueInput> current = input.child(FLUID_KEY);
        if (current.isPresent()) {
            boxes.deserialize(current.get());
            return;
        }
        boxes.emptyAll();
        input.child(OLD_FLUID_KEY).ifPresent(old -> {
            FluidStacksResourceHandler single = new FluidStacksResourceHandler(1, FluidBoxes.INPUT_VOLUME);
            single.deserialize(old);
            boxes.set(0, single.getResource(0).toStack(single.getAmountAsInt(0)));
        });
    }
}
