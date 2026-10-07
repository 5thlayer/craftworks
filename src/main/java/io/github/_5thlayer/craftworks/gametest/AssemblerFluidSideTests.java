// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.gametest;

import java.util.List;
import java.util.Optional;

import io.github._5thlayer.craftworks.Craftworks;
import io.github._5thlayer.craftworks.gametest.AssemblerMachineTests.Placed;
import io.github._5thlayer.craftworks.machine.AssemblerBlock;
import io.github._5thlayer.craftworks.machine.AssemblerFluidSide;
import io.github._5thlayer.craftworks.machine.AssemblerTier;
import io.github._5thlayer.craftworks.machine.Assemblers;
import io.github._5thlayer.craftworks.machine.FluidBoxes;
import io.github._5thlayer.craftworks.machine.FluidLayout;
import io.github._5thlayer.craftworks.machine.HeldRecipes;
import io.github._5thlayer.craftworks.machine.MachineFluids;
import io.github._5thlayer.craftworks.machine.MachineState;
import io.github._5thlayer.craftworks.recipe.AssemblingRecipe;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * The Assembler's fluid half on its own ({@link AssemblerFluidSide}, #44), reached without the craft loop: a fluid side
 * stood up over a host that holds a recipe of the game tests' own, for what it does with its boxes (what each takes
 * and holds, what a craft's fluid takes and places, what is saved), and one a placed Assembler owns, for what it
 * does with a neighbour's tank and which block answers a capability lookup. {@link AssemblerFluidTests} covers
 * the same through a whole craft.
 */
final class AssemblerFluidSideTests {

    private static final Identifier WATER_CRAFT = AssemblerFluidTests.WATER_CRAFT;
    /** Iron and 200 mB of water and 100 mB of lava make a gold ingot and 600 mB of water. */
    private static final Identifier FULL = id("gametest/assembler_full");
    /** One dirt and 10 mB of water make a clay ball. */
    private static final Identifier WATER_SMALL = id("gametest/water_small");
    /** A gold ingot and 250 mB of water make 9 gold nuggets and 50 mB of lava. */
    private static final Identifier FLUID_RESULT = id("gametest/fluid_recipe");

    private static final FluidLayout LAYOUT = FluidLayout.ASSEMBLER;
    private static final BlockPos ORIGIN = AssemblerMachineTests.ORIGIN;

    private AssemblerFluidSideTests() {
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(Craftworks.MOD_ID, path);
    }

    static void register(CraftworksGameTests.Registrar tests) {
        tests.test("a_fluid_side_takes_into_each_box_only_the_fluid_its_order_names", 20, AssemblerFluidSideTests::takesByOrder);
        tests.test("a_fluid_side_sizes_a_box_by_its_recipe_and_gives_a_full_box_with_none", 20, AssemblerFluidSideTests::sizesBoxes);
        tests.test("a_fluid_side_shows_the_boxes_its_recipe_binds_and_any_that_holds_fluid", 20, AssemblerFluidSideTests::boxesInUse);
        tests.test("a_fluid_side_takes_a_crafts_fluid_and_places_its_results_or_says_what_stopped_it", 20, AssemblerFluidSideTests::takesAndPlaces);
        tests.test("a_fluid_side_saves_its_boxes_as_fluids_and_reads_an_old_fluid", 20, AssemblerFluidSideTests::saves);
        tests.test("a_fluid_side_pulls_from_the_tank_a_connection_faces_up_to_the_box", 20, AssemblerFluidSideTests::pulls);
        tests.test("a_fluid_side_pushes_what_its_output_boxes_hold_into_the_drain_a_connection_faces", 20, AssemblerFluidSideTests::pushes);
        tests.test("a_fluid_side_makes_the_connection_flag_follow_the_held_recipe_and_voids_a_box_the_recipe_does_not_bind", 20, AssemblerFluidSideTests::syncs);
        tests.test("a_fluid_side_empties_every_box", 20, AssemblerFluidSideTests::empties);
        tests.test("a_fluid_side_answers_a_lookup_only_on_a_connection_while_its_recipe_names_a_fluid", 20, AssemblerFluidSideTests::answersLookups);
    }

    // -- fixtures -------------------------------------------------------------------------------

    private static Optional<AssemblingRecipe> recipe(GameTestHelper helper, Identifier id) {
        return HeldRecipes.find(helper.getLevel(), id).map(holder -> holder.value());
    }

    /** A fluid side over a host holding {@code held}, or none, and standing at no place in particular. */
    private static AssemblerFluidSide fluidSide(GameTestHelper helper, Identifier held) {
        Optional<AssemblingRecipe> recipe = held == null ? Optional.empty() : recipe(helper, held);
        BlockState state = Assemblers.block(AssemblerTier.TWO).get().defaultBlockState();
        return new AssemblerFluidSide(new AssemblerFluidSide.Host() {
            @Override
            public Optional<AssemblingRecipe> runnable() {
                return recipe;
            }

            @Override
            public BlockPos pos() {
                return BlockPos.ZERO;
            }

            @Override
            public BlockState blockState() {
                return state;
            }

            @Override
            public void changed() {
            }
        });
    }

    private static boolean takes(MachineFluids boxes, int box, Fluid fluid) {
        return boxes.isValid(box, FluidResource.of(fluid));
    }

    private static TestTank.Entity tank(GameTestHelper helper, BlockPos at, Fluid fluid, int amount, TestTank.Mode mode) {
        helper.setBlock(at, TestTank.BLOCK.get().defaultBlockState());
        TestTank.Entity tank = helper.getBlockEntity(at, TestTank.Entity.class);
        tank.fill(fluid, amount);
        tank.mode = mode;
        return tank;
    }

    private static TagValueOutput output(GameTestHelper helper) {
        return TagValueOutput.createWithContext(ProblemReporter.DISCARDING, helper.getLevel().registryAccess());
    }

    // -- the boxes ------------------------------------------------------------------------------

    private static void takesByOrder(GameTestHelper helper) {
        MachineFluids boxes = fluidSide(helper, FULL).boxes();
        helper.assertTrue(takes(boxes, 0, Fluids.WATER) && !takes(boxes, 0, Fluids.LAVA),
                "input box 1 should take the recipe's first fluid ingredient, water, and not lava");
        helper.assertTrue(takes(boxes, 1, Fluids.LAVA) && !takes(boxes, 1, Fluids.WATER),
                "input box 2 should take the second, lava, and not water");
        helper.assertTrue(takes(boxes, 2, Fluids.WATER) && !takes(boxes, 2, Fluids.LAVA),
                "output box 1 should take the recipe's one fluid result, water, and not lava");
        helper.assertTrue(!takes(boxes, 3, Fluids.WATER) && !takes(boxes, 4, Fluids.WATER),
                "an output box with no result bound to it should take nothing");

        MachineFluids none = fluidSide(helper, null).boxes();
        for (int box = 0; box < LAYOUT.boxes(); box++) {
            helper.assertTrue(!takes(none, box, Fluids.WATER), "box " + box + " took water with no Held recipe");
        }
        helper.succeed();
    }

    private static void sizesBoxes(GameTestHelper helper) {
        MachineFluids small = fluidSide(helper, WATER_SMALL).boxes();
        helper.assertTrue(small.capacity(0) == FluidBoxes.inputLimit(10) && small.capacity(0) == 40,
                "an input box under a recipe of 10 mB holds " + small.capacity(0) + " mB, not 4 crafts' worth, 40");
        helper.assertTrue(small.capacity(1) == FluidBoxes.INPUT_VOLUME,
                "an input box with no ingredient bound to it holds " + small.capacity(1) + " mB, not a full box");

        MachineFluids full = fluidSide(helper, FULL).boxes();
        helper.assertTrue(full.capacity(0) == FluidBoxes.inputLimit(200), "input box 1 holds " + full.capacity(0) + " mB under 200 a craft");
        int merged = FluidBoxes.outputVolumes(LAYOUT.fluidOutputs(), List.of(600), false).get(0);
        helper.assertTrue(full.capacity(2) == merged, "output box 1 holds " + full.capacity(2) + " mB, not the " + merged + " the recipe sizes it at");

        MachineFluids none = fluidSide(helper, null).boxes();
        helper.assertTrue(none.capacity(0) == FluidBoxes.INPUT_VOLUME && none.capacity(2) == FluidBoxes.OUTPUT_BOX,
                "with no Held recipe the boxes hold " + none.capacity(0) + " and " + none.capacity(2) + " mB, not their own volumes");
        helper.succeed();
    }

    private static void boxesInUse(GameTestHelper helper) {
        AssemblerFluidSide fluidSide = fluidSide(helper, WATER_CRAFT);
        helper.assertTrue(fluidSide.boxesInUse().equals(List.of(0)), "a recipe with one fluid in shows " + fluidSide.boxesInUse() + ", not [0]");
        fluidSide.boxes().set(3, new FluidStack(Fluids.LAVA, 20));
        helper.assertTrue(fluidSide.boxesInUse().equals(List.of(0, 3)), "a box holding fluid shows too, not " + fluidSide.boxesInUse());
        AssemblerFluidSide idle = fluidSide(helper, null);
        helper.assertTrue(idle.boxesInUse().isEmpty(), "an empty fluid side with no recipe shows " + idle.boxesInUse());
        helper.succeed();
    }

    // -- one craft's fluid ----------------------------------------------------------------------

    private static void takesAndPlaces(GameTestHelper helper) {
        AssemblerFluidSide fluidSide = fluidSide(helper, FULL);
        AssemblingRecipe recipe = recipe(helper, FULL).orElseThrow();
        MachineFluids boxes = fluidSide.boxes();

        try (Transaction tx = Transaction.openRoot()) {
            helper.assertTrue(fluidSide.take(recipe, tx) == MachineState.MISSING_INGREDIENTS, "empty boxes did not stop a take");
        }
        boxes.set(0, new FluidStack(Fluids.WATER, 300));
        boxes.set(1, new FluidStack(Fluids.LAVA, 50));
        try (Transaction tx = Transaction.openRoot()) {
            helper.assertTrue(fluidSide.take(recipe, tx) == MachineState.MISSING_INGREDIENTS, "50 mB of lava, short of 100, did not stop a take");
        }
        helper.assertTrue(boxes.contents(0).getAmount() == 300, "a take that stopped left " + boxes.contents(0).getAmount() + " mB in box 1, not 300");

        boxes.set(1, new FluidStack(Fluids.LAVA, 100));
        try (Transaction tx = Transaction.openRoot()) {
            helper.assertTrue(fluidSide.take(recipe, tx) == null, "a take with enough of both was stopped");
        }
        helper.assertTrue(boxes.contents(0).getAmount() == 300 && boxes.contents(1).getAmount() == 100, "a take that was aborted kept what it took");
        try (Transaction tx = Transaction.openRoot()) {
            fluidSide.take(recipe, tx);
            tx.commit();
        }
        helper.assertTrue(boxes.contents(0).getAmount() == 100 && boxes.contents(1).isEmpty(),
                "a committed take left " + boxes.contents(0) + " and " + boxes.contents(1) + ", not 100 mB of water and no lava");

        try (Transaction tx = Transaction.openRoot()) {
            helper.assertTrue(fluidSide.place(recipe, tx) == null, "a place into an empty output box was stopped");
            tx.commit();
        }
        helper.assertTrue(boxes.contents(2).getFluid() == Fluids.WATER && boxes.contents(2).getAmount() == 600,
                "the result is " + boxes.contents(2) + " in output box 1, not 600 mB of water");
        boxes.set(2, new FluidStack(Fluids.WATER, boxes.capacity(2) - 10));
        try (Transaction tx = Transaction.openRoot()) {
            helper.assertTrue(fluidSide.place(recipe, tx) == MachineState.OUTPUT_FULL, "a place into a box with room for 10 mB of 600 was not stopped");
        }
        helper.succeed();
    }

    // -- saving ---------------------------------------------------------------------------------

    private static void saves(GameTestHelper helper) {
        AssemblerFluidSide fluidSide = fluidSide(helper, FULL);
        fluidSide.boxes().set(0, new FluidStack(Fluids.WATER, 750));
        fluidSide.boxes().set(3, new FluidStack(Fluids.LAVA, 90));
        TagValueOutput saved = output(helper);
        fluidSide.save(saved);
        CompoundTag tag = saved.buildResult();
        helper.assertTrue(tag.keySet().equals(java.util.Set.of("fluids")), "the fluid side wrote the keys " + tag.keySet() + ", not only fluids");

        AssemblerFluidSide loaded = fluidSide(helper, FULL);
        loaded.load(TagValueInput.create(ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), tag));
        for (int box = 0; box < LAYOUT.boxes(); box++) {
            helper.assertTrue(FluidStack.matches(loaded.boxes().contents(box), fluidSide.boxes().contents(box)),
                    "box " + box + " read back as " + loaded.boxes().contents(box));
        }

        // A save from before the boxes were many: one box, which goes in input box 1.
        TagValueOutput old = output(helper);
        FluidStacksResourceHandler single = new FluidStacksResourceHandler(1, 1000);
        single.set(0, FluidResource.of(Fluids.WATER), 400);
        single.serialize(old.child("fluid"));
        AssemblerFluidSide migrated = fluidSide(helper, WATER_CRAFT);
        migrated.boxes().set(2, new FluidStack(Fluids.WATER, 5));
        migrated.load(TagValueInput.create(ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), old.buildResult()));
        helper.assertTrue(migrated.boxes().contents(0).getFluid() == Fluids.WATER && migrated.boxes().contents(0).getAmount() == 400,
                "the old box read back as " + migrated.boxes().contents(0));
        helper.assertTrue(migrated.boxes().contents(2).isEmpty(), "the boxes the old save lacks kept " + migrated.boxes().contents(2));

        // Neither key: every box is empty.
        migrated.load(TagValueInput.create(ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), new CompoundTag()));
        helper.assertTrue(migrated.boxes().contents(0).isEmpty(), "a save with no fluid left " + migrated.boxes().contents(0));
        helper.succeed();
    }

    // -- a neighbour ----------------------------------------------------------------------------

    private static void pulls(GameTestHelper helper) {
        Placed assembler = AssemblerMachineTests.place(helper, AssemblerTier.TWO);
        TestTank.Entity water = tank(helper, AssemblerFluidTests.beyondFirstConnection(assembler.facing()), Fluids.WATER, 5000, TestTank.Mode.BOTH);
        AssemblerMachineTests.hold(assembler, WATER_CRAFT);
        AssemblerFluidSide fluidSide = assembler.machine().fluidSide();

        fluidSide.pull(helper.getLevel(), recipe(helper, WATER_CRAFT).orElseThrow());
        helper.assertTrue(fluidSide.boxes().contents(0).getFluid() == Fluids.WATER && fluidSide.boxes().contents(0).getAmount() == 1000,
                "one pull put " + fluidSide.boxes().contents(0) + " in the box, not its 1000 mB of room");
        helper.assertTrue(water.tank.getAmountAsInt(0) == 4000, "the tank holds " + water.tank.getAmountAsInt(0) + " mB, not 4000");
        fluidSide.pull(helper.getLevel(), recipe(helper, WATER_CRAFT).orElseThrow());
        helper.assertTrue(water.tank.getAmountAsInt(0) == 4000, "a pull into a full box took more from the tank");
        helper.succeed();
    }

    private static void pushes(GameTestHelper helper) {
        Placed assembler = AssemblerMachineTests.place(helper, AssemblerTier.TWO);
        TestTank.Entity drain = tank(helper, AssemblerFluidTests.beyondFirstConnection(assembler.facing()), Fluids.EMPTY, 0, TestTank.Mode.SINK);
        AssemblerMachineTests.hold(assembler, FLUID_RESULT);
        AssemblerFluidSide fluidSide = assembler.machine().fluidSide();
        AssemblingRecipe recipe = recipe(helper, FLUID_RESULT).orElseThrow();

        try (Transaction tx = Transaction.openRoot()) {
            helper.assertTrue(fluidSide.place(recipe, tx) == null, "the result did not fit its box");
            tx.commit();
        }
        helper.assertTrue(fluidSide.boxes().contents(LAYOUT.outputBox(0)).getAmount() == 50, "the box does not hold the result");
        fluidSide.push(helper.getLevel());
        helper.assertTrue(drain.tank.getResource(0).getFluid() == Fluids.LAVA && drain.tank.getAmountAsInt(0) == 50,
                "the drain holds " + drain.tank.getAmountAsInt(0) + " mB of " + drain.tank.getResource(0).getFluid() + ", not 50 of lava");
        helper.assertTrue(fluidSide.boxes().contents(LAYOUT.outputBox(0)).isEmpty(), "the output box still holds its fluid");
        helper.succeed();
    }

    private static void answersLookups(GameTestHelper helper) {
        Placed assembler = AssemblerMachineTests.place(helper, AssemblerTier.ONE);
        Direction facing = assembler.facing();
        AssemblerFluidSide fluidSide = assembler.machine().fluidSide();
        BlockPos origin = helper.absolutePos(ORIGIN);

        AssemblerFluidTests.Connections connections = AssemblerFluidTests.connections(facing);
        AssemblerFluidTests.Connection first = connections.frontLeft();
        helper.assertTrue(fluidSide.connection(helper.absolutePos(first.block()), first.side()) == null,
                "a connection answered while the Held recipe names no fluid");
        AssemblerMachineTests.hold(assembler, WATER_CRAFT);
        helper.assertTrue(fluidSide.hasConnections(), "a recipe that names a fluid left the fluid side with no connections");
        for (AssemblerFluidTests.Connection connection : connections.all()) {
            BlockPos at = helper.absolutePos(connection.block());
            helper.assertTrue(fluidSide.connection(at, connection.side()) != null,
                    "no answer at the connection " + connection);
            helper.assertTrue(fluidSide.connection(at, connection.side().getOpposite()) == null,
                    "the face of a connection pointing at the Assembler answered");
        }
        helper.assertTrue(fluidSide.connection(origin, facing) == null, "the origin answered, which is no connection");
        AssemblerMachineTests.hold(assembler, FLUID_RESULT);
        helper.assertTrue(fluidSide.hasConnections(), "a recipe with a fluid result left the fluid side with no connections");
        helper.succeed();
    }

    // -- syncing --------------------------------------------------------------------------------

    private static boolean flag(GameTestHelper helper) {
        return helper.getBlockState(ORIGIN).getValue(AssemblerBlock.FLUID_CONNECTIONS);
    }

    private static void setFlag(GameTestHelper helper, boolean on) {
        helper.setBlock(ORIGIN, helper.getBlockState(ORIGIN).setValue(AssemblerBlock.FLUID_CONNECTIONS, on));
    }

    private static void syncs(GameTestHelper helper) {
        Placed assembler = AssemblerMachineTests.place(helper, AssemblerTier.TWO);
        AssemblerFluidSide fluidSide = assembler.machine().fluidSide();
        AssemblerMachineTests.hold(assembler, WATER_CRAFT);
        helper.assertTrue(flag(helper), "the connections are not shown under a recipe that names a fluid");

        // Knocked out of step, a sync puts the flag back, and voids what the recipe binds to no box.
        setFlag(helper, false);
        fluidSide.boxes().set(0, new FluidStack(Fluids.WATER, 100));
        fluidSide.boxes().set(1, new FluidStack(Fluids.LAVA, 100));
        fluidSide.syncConnections(helper.getLevel());
        helper.assertTrue(flag(helper), "a sync left the connections hidden under a recipe that names a fluid");
        helper.assertTrue(fluidSide.boxes().contents(0).getAmount() == 100, "a sync voided the water the recipe takes");
        helper.assertTrue(fluidSide.boxes().contents(1).isEmpty(), "a sync kept the lava in a box the recipe binds no fluid to");

        // With no fluid in the recipe, they go, and every box with them.
        AssemblerMachineTests.hold(assembler, AssemblerTests.OAK_SAPLING);
        setFlag(helper, true);
        fluidSide.boxes().set(0, new FluidStack(Fluids.WATER, 100));
        fluidSide.syncConnections(helper.getLevel());
        helper.assertTrue(!flag(helper), "a sync left the connections shown under a recipe with no fluid");
        helper.assertTrue(fluidSide.boxes().contents(0).isEmpty(), "a sync kept water with no connections");
        helper.succeed();
    }

    private static void empties(GameTestHelper helper) {
        AssemblerFluidSide fluidSide = fluidSide(helper, FULL);
        for (int box = 0; box < LAYOUT.boxes(); box++) {
            fluidSide.boxes().set(box, new FluidStack(Fluids.WATER, 10));
        }
        fluidSide.empty();
        for (int box = 0; box < LAYOUT.boxes(); box++) {
            helper.assertTrue(fluidSide.boxes().contents(box).isEmpty(), "box " + box + " still holds " + fluidSide.boxes().contents(box));
        }
        helper.succeed();
    }
}
