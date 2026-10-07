// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import io.github._5thlayer.craftworks.Craftworks;
import io.github._5thlayer.craftworks.gametest.AssemblerMachineTests.Placed;
import io.github._5thlayer.craftworks.machine.AssemblerBlock;
import io.github._5thlayer.craftworks.machine.AssemblerBlockEntity;
import io.github._5thlayer.craftworks.machine.AssemblerMenu;
import io.github._5thlayer.craftworks.machine.AssemblerSlots;
import io.github._5thlayer.craftworks.machine.AssemblerTier;
import io.github._5thlayer.craftworks.machine.Assemblers;
import io.github._5thlayer.craftworks.machine.FluidLayout;
import io.github._5thlayer.craftworks.machine.HoldVerdict;
import io.github._5thlayer.craftworks.machine.MachineFluids;
import io.github._5thlayer.craftworks.machine.MachineState;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.network.payload.AdvancedContainerSetDataPayload;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * Fluid through the Assembler's Fluid Connections (#25, #40): the two input and three output boxes that take the
 * Held recipe's fluids by order and the six connections that pull them from a neighbour and push the results out,
 * where the connections stand for each facing and that they exist only while the Held recipe names a fluid, in or
 * out, what Fill Recipe refuses, how the boxes are sized, what a Fast Replace and a save do to them, and what an
 * old save's one box becomes. The neighbour is {@link TestTank}, a plain fluid-handler block, a source that only
 * gives or a drain that only takes, and the recipes are the game tests' own.
 */
final class AssemblerFluidTests {

    /** Dirt and 250 mB of water make a clay ball; in {@code crafting-with-fluid}. */
    static final Identifier WATER_CRAFT = id("gametest/water_craft");
    private static final Identifier LAVA_CRAFT = id("gametest/lava_craft");
    /** The same, in the default category. */
    private static final Identifier CRAFTING_FLUID = id("gametest/crafting_fluid");
    private static final Identifier TWO_FLUIDS = id("gametest/two_fluids");
    private static final Identifier BIG_FLUID = id("gametest/big_fluid");
    private static final Identifier FULL_BOX = id("gametest/full_box");
    /** One dirt and 10 mB of water make a clay ball. */
    private static final Identifier WATER_SMALL = id("gametest/water_small");
    /** One fluid ingredient that water and lava both match. */
    private static final Identifier WATER_OR_LAVA = id("gametest/water_or_lava");
    /** A gold ingot and 250 mB of water make 9 gold nuggets and 50 mB of lava, in the default category. */
    private static final Identifier FLUID_RESULT = id("gametest/fluid_recipe");
    private static final Identifier SAPLING = AssemblerTests.OAK_SAPLING;

    /** Iron and 200 mB of water and 100 mB of lava make a gold ingot and 600 mB of water, in 20 ticks. */
    private static final Identifier FULL = id("gametest/assembler_full");
    /** 100 mB of water make 400 mB of water and 100 mB of lava: two output boxes, each bound to a result, and no item. */
    private static final Identifier TWO_OUTPUTS = id("gametest/assembler_two_outputs");
    /** 100 mB of water and 100 mB of lava make 30 mB of lava and, in two boxes, 45 and 55 mB of water, in 20 ticks. */
    private static final Identifier THREE_OUTPUTS = id("gametest/assembler_three_outputs");
    /** 10 mB of water make 40 mB of lava, in one result and one of three output boxes; the other is the same, Pinned. */
    private static final Identifier ONE_RESULT = id("gametest/assembler_one_result");
    private static final Identifier PINNED = id("gametest/assembler_pinned");
    private static final Identifier FULL_BOXES = id("gametest/assembler_full_boxes");
    private static final Identifier THREE_FLUIDS = id("gametest/assembler_three_fluids");
    private static final Identifier FOUR_RESULTS = id("gametest/assembler_four_results");

    private static final BlockPos ORIGIN = AssemblerMachineTests.ORIGIN;
    private static final List<Direction> FACINGS = List.of(Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST);
    /** What each output box holds when full under {@link #THREE_OUTPUTS}: the larger of 100 mB and 3 crafts' worth of its result. */
    private static final int[] OUTPUT_VOLUME = {100, 135, 165};
    private static final Fluid[] OUTPUT_FLUID = {Fluids.LAVA, Fluids.WATER, Fluids.WATER};

    private AssemblerFluidTests() {
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(Craftworks.MOD_ID, path);
    }

    static void register(CraftworksGameTests.Registrar tests) {
        tests.test("a_tier_2_assembler_beside_a_tank_pulls_water_and_crafts_a_recipe_that_needs_it", 20, AssemblerFluidTests::pullsAndCrafts);
        tests.test("it_pulls_only_the_held_recipes_fluid", 20, AssemblerFluidTests::onlyTheHeldFluid);
        tests.test("an_ingredient_that_several_fluids_match_takes_the_first_and_refuses_another_while_the_box_holds_one", 20,
                AssemblerFluidTests::multiFluidIngredient);
        tests.test("it_stops_at_1000_mb", 20, AssemblerFluidTests::stopsAtABoxFull);
        tests.test("an_assembler_box_holding_more_than_its_capacity_keeps_its_fluid_displays_at_least_that_and_takes_no_more", 20, AssemblerFluidTests::overfullBoxIsKept);
        tests.test("a_tier_2_or_3_assembler_stops_pulling_at_4_crafts_worth_and_a_pipe_that_pushes_is_held_to_it", 20, AssemblerFluidTests::stopsAtFourCrafts);
        tests.test("changing_the_held_recipe_voids_the_five_boxes", 20, AssemblerFluidTests::voidsOnChange);
        tests.test("the_five_fluid_boxes_survive_a_save_and_reload_and_only_fluids_is_written", 20, AssemblerFluidTests::survivesReload);
        tests.test("an_old_save_with_one_fluid_box_loads_into_input_box_1_and_fluids_wins_over_it", 20, AssemblerFluidTests::oldFormatMigrates);
        for (Direction facing : FACINGS) {
            tests.test("the_six_fluid_connections_of_an_assembler_facing_" + facing.getName() + "_stand_on_the_ends_of_its_front_and_back_edges_and_the_centres_of_its_sides", 20,
                    helper -> connectionsFollowFacing(helper, facing));
            tests.test("any_one_of_the_six_connections_alone_feeds_and_drains_an_assembler_facing_" + facing.getName(), 40,
                    helper -> anyConnectionDoes(helper, facing));
        }
        for (Direction facing : FACINGS) {
            tests.test("an_assembler_facing_" + facing.getName() + "_shows_no_fluid_capability_on_the_centres_of_its_front_and_back_edges", 20,
                    helper -> oldCentresAreNotConnections(helper, facing));
            tests.test("an_assembler_facing_" + facing.getName() + "_makes_a_five_fluid_recipe_through_five_separate_networks", 40,
                    helper -> fiveNetworks(helper, facing));
        }
        tests.test("a_neighbour_that_no_connection_of_an_assembler_faces_is_neither_pulled_from_nor_pushed_to", 20, AssemblerFluidTests::onlyConnectionsAreUsed);
        tests.test("tier_1_has_the_six_fluid_connections_while_it_holds_a_fluid_recipe", 20, AssemblerFluidTests::tier1HasTheConnections);
        tests.test("the_connections_come_and_go_with_a_fluid_in_or_out_of_the_held_recipe", 20, AssemblerFluidTests::connectionsComeAndGo);
        tests.test("a_pipe_that_asked_before_the_recipe_is_told_when_the_connection_appears", 20, AssemblerFluidTests::cachesAreTold);
        tests.test("an_assembler_crafts_two_fluids_and_an_item_into_an_item_and_a_fluid_fed_and_drained_through_its_fluid_connections", 40,
                AssemblerFluidTests::craftsThroughItsConnections);
        tests.test("an_assembler_crafts_two_fluids_into_three_fed_and_drained_through_its_fluid_connections", 40,
                AssemblerFluidTests::craftsTwoIntoThree);
        tests.test("an_assembler_stalls_with_an_output_box_full_and_goes_on_once_it_is_drained", 200, AssemblerFluidTests::stallsOnAFullBox);
        tests.test("an_assembler_stalls_on_each_full_output_box_and_goes_on_once_it_is_drained", 100, AssemblerFluidTests::stallsOnEachFullBox);
        tests.test("an_assembler_waits_while_the_product_slot_is_full_and_makes_no_fluid", 20, AssemblerFluidTests::waitsOnAFullProduct);
        tests.test("a_recipe_with_no_item_at_all_crafts_fluid_into_fluid_and_pushes_both_results_out", 40, AssemblerFluidTests::fluidOnly);
        tests.test("a_fluid_result_comes_with_the_items_a_recipe_makes", 40, AssemblerFluidTests::fluidBesideItems);
        tests.test("an_assembler_pulls_each_fluid_into_the_input_box_its_order_names_and_nothing_else", 20, AssemblerFluidTests::pullsByOrder);
        tests.test("an_assemblers_connection_fills_an_input_box_and_drains_an_output_box_and_does_neither_the_other_way", 20, AssemblerFluidTests::connectionFaces);
        tests.test("an_assemblers_input_box_holds_4_crafts_of_its_ingredient_and_an_output_box_the_larger_of_100_mb_and_3_crafts_of_its_result", 20, AssemblerFluidTests::boxSizes);
        tests.test("a_recipe_with_one_fluid_result_on_a_three_output_assembler_gets_the_merged_boxes_and_pinned_it_does_not", 20, AssemblerFluidTests::mergedAndPinnedBoxes);
        tests.test("an_output_box_holds_3_crafts_worth_pinned_and_the_craft_stalls_past_it", 20, AssemblerFluidTests::outputBoxHoldsCraftsAndStalls);
        tests.test("fill_recipe_on_every_tier_refuses_more_than_two_fluid_ingredients_three_fluid_results_and_more_than_1000_mb", 20, AssemblerFluidTests::refusals);
        tests.test("a_recipe_with_a_fluid_result_is_held_by_every_tier", 20, AssemblerFluidTests::fluidResultsAreHeld);
        tests.test("a_tier_1_assembler_pulls_a_recipes_fluid_crafts_it_and_pushes_its_fluid_results", 40, AssemblerFluidTests::tier1Crafts);
        tests.test("a_fast_replace_between_any_two_tiers_keeps_the_boxes_the_held_recipe_and_the_connections", 20, AssemblerFluidTests::fastReplace);
        tests.test("breaking_an_assembler_voids_its_boxes", 20, AssemblerFluidTests::breaking);
        tests.test("the_screen_of_every_tier_has_a_gauge_over_each_synced_box_and_stands_lower", 20, AssemblerFluidTests::gauges);
        tests.test("the_open_assemblers_boxes_cross_to_the_client", 20, AssemblerFluidTests::crossesToTheClient);
    }

    // -- fixtures -------------------------------------------------------------------------------

    /** A Fluid Connection: the footprint block it is, and the way its face points. */
    record Connection(BlockPos block, Direction side) {

        /** The block the face touches, where a neighbour stands. */
        BlockPos beyond() {
            return block.relative(side);
        }
    }

    /**
     * The six connections of an Assembler facing {@code facing}, as the issue gives them and not as the code does,
     * spaced so no two of their pipes touch: the two ends of the bottom-layer edge it faces, left to right, the two
     * ends of the opposite edge, then the centre of its left side and the centre of its right. The first two are on
     * the facing side.
     */
    static List<Connection> connections(Direction facing) {
        Direction right = facing.getClockWise();
        return List.of(
                new Connection(ORIGIN.relative(facing).relative(right, -1), facing),
                new Connection(ORIGIN.relative(facing).relative(right, 1), facing),
                new Connection(ORIGIN.relative(facing.getOpposite()).relative(right, -1), facing.getOpposite()),
                new Connection(ORIGIN.relative(facing.getOpposite()).relative(right, 1), facing.getOpposite()),
                new Connection(ORIGIN.relative(right, -1), right.getOpposite()),
                new Connection(ORIGIN.relative(right, 1), right));
    }

    /** The block the first connection faces: the left end of the edge the Assembler faces, where a lone tank or pipe goes. */
    static BlockPos beyond(Direction facing) {
        return connections(facing).get(0).beyond();
    }

    private static TestTank.Entity tank(GameTestHelper helper, BlockPos at, Fluid fluid, int amount, TestTank.Mode mode) {
        helper.setBlock(at, TestTank.BLOCK.get().defaultBlockState());
        TestTank.Entity tank = helper.getBlockEntity(at, TestTank.Entity.class);
        tank.fill(fluid, amount);
        tank.mode = mode;
        return tank;
    }

    private static TestTank.Entity tank(GameTestHelper helper, BlockPos at, Fluid fluid, int amount) {
        return tank(helper, at, fluid, amount, TestTank.Mode.BOTH);
    }

    private static TestTank.Entity source(GameTestHelper helper, Connection at, Fluid fluid, int amount) {
        return tank(helper, at.beyond(), fluid, amount, TestTank.Mode.SOURCE);
    }

    private static TestTank.Entity drain(GameTestHelper helper, Connection at) {
        return tank(helper, at.beyond(), Fluids.EMPTY, 0, TestTank.Mode.SINK);
    }

    private static int inTank(TestTank.Entity tank) {
        return tank.tank.getAmountAsInt(0);
    }

    /** What a drain holds of {@code fluid}: all of it, or none if it holds another. */
    private static int drained(TestTank.Entity sink, Fluid fluid) {
        return sink.tank.getResource(0).getFluid() == fluid ? inTank(sink) : 0;
    }

    private static FluidStack box(Placed assembler, int box) {
        return assembler.machine().fluids().contents(box);
    }

    private static int amount(Placed assembler, int box) {
        return box(assembler, box).getAmount();
    }

    private static ResourceHandler<FluidResource> fluid(Placed assembler, Connection at) {
        return assembler.helper().getLevel().getCapability(Capabilities.Fluid.BLOCK, assembler.helper().absolutePos(at.block()), at.side());
    }

    static void tick(GameTestHelper helper, Placed assembler, int ticks) {
        SimpleEnergyHandler supply = AssemblerMachineTests.supply();
        for (int tick = 0; tick < ticks; tick++) {
            AssemblerMachineTests.feed(assembler, supply, 1000);
            assembler.machine().serverTick(helper.getLevel());
        }
    }

    // -- pulling --------------------------------------------------------------------------------

    private static void pullsAndCrafts(GameTestHelper helper) {
        Placed assembler = AssemblerMachineTests.place(helper, AssemblerTier.TWO);
        TestTank.Entity water = tank(helper, beyond(assembler.facing()), Fluids.WATER, 5000);
        AssemblerMachineTests.hold(assembler, WATER_CRAFT);
        AssemblerMachineTests.insert(assembler, 0, Items.DIRT, 2);
        helper.assertTrue(assembler.machine().state() == MachineState.MISSING_INGREDIENTS,
                "an Assembler with the dirt and no water reported " + assembler.machine().state());

        // Powered every tick, it crafts on the 14th: the first tick filled the box, and a craft took 250 of it.
        int ran = 0;
        SimpleEnergyHandler supply = AssemblerMachineTests.supply();
        while (AssemblerMachineTests.count(assembler, AssemblerSlots.PRODUCT) == 0 && ran < 100) {
            AssemblerMachineTests.feed(assembler, supply, 1000);
            assembler.machine().serverTick(helper.getLevel());
            ran++;
        }
        helper.assertTrue(ran == 14, "it finished its craft after " + ran + " ticks, not 14");
        helper.assertTrue(assembler.machine().inventory().getResource(AssemblerSlots.PRODUCT).getItem() == Items.CLAY_BALL,
                "the product was not a clay ball");
        helper.assertTrue(amount(assembler, 0) == 750, "the box holds " + amount(assembler, 0) + " mB, not the 1000 it filled to less the 250 spent");
        helper.assertTrue(inTank(water) == 4000, "the tank holds " + inTank(water) + " mB, not the 1000 less");
        helper.succeed();
    }

    private static void onlyTheHeldFluid(GameTestHelper helper) {
        Placed assembler = AssemblerMachineTests.place(helper, AssemblerTier.TWO);
        TestTank.Entity lava = tank(helper, beyond(assembler.facing()), Fluids.LAVA, 5000);
        AssemblerMachineTests.hold(assembler, WATER_CRAFT);
        tick(helper, assembler, 5);
        helper.assertTrue(amount(assembler, 0) == 0, "a neighbour holding lava filled the box with " + amount(assembler, 0) + " mB");
        helper.assertTrue(inTank(lava) == 5000, "the lava tank lost " + (5000 - inTank(lava)) + " mB");

        // And what the box does take it takes through the capability too: only the Held recipe's fluid.
        ResourceHandler<FluidResource> face = fluid(assembler, connections(assembler.facing()).get(1));
        helper.assertTrue(face != null, "no fluid capability at the connection");
        helper.assertTrue(insert(face, Fluids.LAVA, 100) == 0, "the box took lava, which the Held recipe doesn't use");
        helper.assertTrue(insert(face, Fluids.WATER, 100) == 100, "a mod that pushes could not fill the box with the Held recipe's fluid");
        helper.assertTrue(amount(assembler, 0) == 100, "the pushed water is not in the box");
        helper.assertTrue(extract(face, Fluids.WATER, 100) == 0, "the box gave fluid out through its connection");
        helper.succeed();
    }

    /** Nothing mixes: the first fluid in fills the box, and the other member waits until the box empties. */
    private static void multiFluidIngredient(GameTestHelper helper) {
        Placed assembler = AssemblerMachineTests.place(helper, AssemblerTier.TWO);
        Direction facing = assembler.facing();
        // The connections on the facing side are asked first.
        TestTank.Entity water = tank(helper, beyond(facing), Fluids.WATER, 400);
        TestTank.Entity lava = tank(helper, connections(facing).get(2).beyond(), Fluids.LAVA, 5000);
        AssemblerMachineTests.hold(assembler, WATER_OR_LAVA);
        AssemblerMachineTests.insert(assembler, 0, Items.DIRT, 1);
        tick(helper, assembler, 1);
        helper.assertTrue(box(assembler, 0).getFluid() == Fluids.WATER && amount(assembler, 0) == 400,
                "the box holds " + box(assembler, 0) + ", not the 400 mB of water first in");
        helper.assertTrue(inTank(lava) == 5000, "lava went into a box holding water: the tank lost " + (5000 - inTank(lava)));
        helper.assertTrue(inTank(water) == 0, "the water tank still holds " + inTank(water));

        // The recipe takes the water it holds, and the craft runs.
        SimpleEnergyHandler supply = AssemblerMachineTests.supply();
        for (int ran = 0; ran < 20 && AssemblerMachineTests.count(assembler, AssemblerSlots.PRODUCT) == 0; ran++) {
            AssemblerMachineTests.feed(assembler, supply, 1000);
            assembler.machine().serverTick(helper.getLevel());
        }
        helper.assertTrue(AssemblerMachineTests.count(assembler, AssemblerSlots.PRODUCT) == 1, "the craft did not run");
        helper.assertTrue(amount(assembler, 0) == 150, "the box holds " + amount(assembler, 0) + " mB, not the 400 less the 250 spent");
        helper.assertTrue(inTank(lava) == 5000, "lava was pulled while the box held water");

        // Emptied, the box takes whichever member comes next.
        assembler.machine().fluids().set(0, FluidStack.EMPTY);
        tick(helper, assembler, 1);
        helper.assertTrue(box(assembler, 0).getFluid() == Fluids.LAVA && amount(assembler, 0) == 1000,
                "the emptied box holds " + box(assembler, 0) + ", not a box of lava");
        helper.succeed();
    }

    private static void stopsAtABoxFull(GameTestHelper helper) {
        Placed assembler = AssemblerMachineTests.place(helper, AssemblerTier.TWO);
        TestTank.Entity water = tank(helper, beyond(assembler.facing()), Fluids.WATER, 5000);
        AssemblerMachineTests.hold(assembler, WATER_CRAFT);
        // Unpowered: nothing spends the box, so it only fills.
        for (int tick = 0; tick < 5; tick++) {
            assembler.machine().serverTick(helper.getLevel());
        }
        helper.assertTrue(amount(assembler, 0) == 1000, "the box holds " + amount(assembler, 0) + " mB, not its 1000");
        helper.assertTrue(inTank(water) == 4000, "the tank gave " + (5000 - inTank(water)) + " mB, not 1000");
        helper.succeed();
    }

    /** 10 mB a craft: the box holds 40 mB, whatever the tier's speed, and a mod that pushes is held to it too. */
    private static void stopsAtFourCrafts(GameTestHelper helper) {
        for (AssemblerTier tier : List.of(AssemblerTier.TWO, AssemblerTier.THREE)) {
            Placed assembler = AssemblerMachineTests.place(helper, tier);
            TestTank.Entity water = tank(helper, beyond(assembler.facing()), Fluids.WATER, 5000);
            AssemblerMachineTests.hold(assembler, WATER_SMALL);
            helper.assertTrue(assembler.machine().fluids().capacity(0) == 40,
                    "a tier " + tier + " box holds " + assembler.machine().fluids().capacity(0) + " mB, not 40");
            for (int tick = 0; tick < 5; tick++) {
                assembler.machine().serverTick(helper.getLevel());
            }
            helper.assertTrue(amount(assembler, 0) == 40, "the box holds " + amount(assembler, 0) + " mB, not the 40 of 4 crafts of 10");
            helper.assertTrue(inTank(water) == 4960, "the tank gave " + (5000 - inTank(water)) + " mB, not 40");
            ResourceHandler<FluidResource> face = fluid(assembler, connections(assembler.facing()).get(1));
            helper.assertTrue(face != null && insert(face, Fluids.WATER, 500) == 0, "a mod that pushes filled a box already holding 4 crafts");
            assembler.machine().fluids().set(0, new FluidStack(Fluids.WATER, 15));
            helper.assertTrue(insert(face, Fluids.WATER, 500) == 25 && amount(assembler, 0) == 40,
                    "a mod that pushes took the box past 40 mB: " + amount(assembler, 0));
            helper.setBlock(beyond(assembler.facing()), Blocks.AIR);
            helper.destroyBlock(ORIGIN);
        }
        helper.succeed();
    }

    /** A box saved under 0.4.2's 1,000 mB rule keeps its fluid under a 40 mB one, displays at least that, and takes no more. */
    private static void overfullBoxIsKept(GameTestHelper helper) {
        Placed assembler = AssemblerMachineTests.place(helper, AssemblerTier.TWO);
        AssemblerMachineTests.hold(assembler, WATER_SMALL);
        MachineFluids boxes = assembler.machine().fluids();
        boxes.set(0, new FluidStack(Fluids.WATER, 1000));
        helper.assertTrue(boxes.capacity(0) == 40, "the box holds " + boxes.capacity(0));
        helper.assertTrue(boxes.displayCapacity(0) >= 1000, "the box displays " + boxes.displayCapacity(0) + " for 1000 mB");
        helper.assertTrue(amount(assembler, 0) == 1000, "an overfull box lost fluid");
        ResourceHandler<FluidResource> face = fluid(assembler, connections(assembler.facing()).get(1));
        helper.assertTrue(face != null && insert(face, Fluids.WATER, 100) == 0, "an overfull box took more");
        helper.succeed();
    }

    private static void voidsOnChange(GameTestHelper helper) {
        Placed assembler = AssemblerMachineTests.place(helper, AssemblerTier.TWO);
        AssemblerMachineTests.hold(assembler, FULL);
        MachineFluids boxes = assembler.machine().fluids();
        boxes.set(0, new FluidStack(Fluids.WATER, 600));
        boxes.set(1, new FluidStack(Fluids.LAVA, 300));
        boxes.set(2, new FluidStack(Fluids.WATER, 700));

        // The recipe it already holds moves nothing.
        AssemblerMachineTests.hold(assembler, FULL);
        helper.assertTrue(amount(assembler, 0) == 600 && amount(assembler, 1) == 300 && amount(assembler, 2) == 700,
                "Fill Recipe on the recipe already held changed the boxes");
        // Another with fluids, and one with none: every box goes either way.
        AssemblerMachineTests.hold(assembler, LAVA_CRAFT);
        for (int index = 0; index < FluidLayout.ASSEMBLER.boxes(); index++) {
            helper.assertTrue(amount(assembler, index) == 0, "box " + index + " held " + amount(assembler, index) + " mB after the recipe changed to lava");
        }
        boxes.set(0, new FluidStack(Fluids.LAVA, 600));
        boxes.set(4, new FluidStack(Fluids.LAVA, 90));
        AssemblerMachineTests.hold(assembler, SAPLING);
        helper.assertTrue(amount(assembler, 0) == 0 && amount(assembler, 4) == 0,
                "the boxes held " + amount(assembler, 0) + " and " + amount(assembler, 4) + " mB after the recipe changed to one with no fluid");
        helper.succeed();
    }

    // -- saving ---------------------------------------------------------------------------------

    private static TagValueOutput output(GameTestHelper helper) {
        return TagValueOutput.createWithContext(ProblemReporter.DISCARDING, helper.getLevel().registryAccess());
    }

    private static AssemblerBlockEntity load(GameTestHelper helper, TagValueOutput saved) {
        AssemblerBlockEntity loaded = new AssemblerBlockEntity(helper.absolutePos(ORIGIN), helper.getBlockState(ORIGIN));
        loaded.setLevel(helper.getLevel());
        loaded.loadCustomOnly(TagValueInput.create(ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), saved.buildResult()));
        return loaded;
    }

    private static void survivesReload(GameTestHelper helper) {
        Placed assembler = AssemblerMachineTests.place(helper, AssemblerTier.THREE);
        AssemblerMachineTests.hold(assembler, FULL);
        MachineFluids boxes = assembler.machine().fluids();
        boxes.set(0, new FluidStack(Fluids.WATER, 750));
        boxes.set(1, new FluidStack(Fluids.LAVA, 300));
        boxes.set(2, new FluidStack(Fluids.WATER, 1500));
        boxes.set(3, new FluidStack(Fluids.LAVA, 90));
        TagValueOutput saved = output(helper);
        assembler.machine().saveCustomOnly(saved);

        // Only `fluids` is written: the old key is not.
        CompoundTag tag = saved.buildResult();
        helper.assertTrue(tag.contains("fluids") && !tag.contains("fluid"), "the save has the keys " + tag.keySet());

        AssemblerBlockEntity loaded = load(helper, saved);
        for (int box = 0; box < FluidLayout.ASSEMBLER.boxes(); box++) {
            helper.assertTrue(FluidStack.matches(loaded.fluids().contents(box), boxes.contents(box)),
                    "box " + box + " read back as " + loaded.fluids().contents(box) + ", not " + box(assembler, box));
        }
        helper.assertTrue(loaded.fluids().contents(2).getAmount() == 1500, "an output box over 1,000 mB read back as " + loaded.fluids().contents(2));
        helper.succeed();
    }

    /**
     * A save from before the boxes were many holds the one box under {@code fluid}: it goes in input box 1, a box above
     * its new capacity as it is, and the next save writes {@code fluids} alone. A save with both keys is read as
     * {@code fluids}.
     */
    private static void oldFormatMigrates(GameTestHelper helper) {
        Placed assembler = AssemblerMachineTests.place(helper, AssemblerTier.TWO);
        TagValueOutput old = output(helper);
        old.store("held_recipe", Identifier.CODEC, WATER_CRAFT);
        FluidStacksResourceHandler single = new FluidStacksResourceHandler(1, 1000);
        single.set(0, FluidResource.of(Fluids.WATER), 750);
        single.serialize(old.child("fluid"));

        AssemblerBlockEntity loaded = load(helper, old);
        helper.assertTrue(loaded.heldRecipe().equals(Optional.of(WATER_CRAFT)), "the Held recipe read back as " + loaded.heldRecipe());
        helper.assertTrue(loaded.fluids().contents(0).getFluid() == Fluids.WATER && loaded.fluids().contents(0).getAmount() == 750,
                "the old box read back in input box 1 as " + loaded.fluids().contents(0));
        for (int box = 1; box < FluidLayout.ASSEMBLER.boxes(); box++) {
            helper.assertTrue(loaded.fluids().contents(box).isEmpty(), "box " + box + " read back as " + loaded.fluids().contents(box));
        }

        // Saved again, it is written in the new format only, and reads back the same.
        TagValueOutput resaved = output(helper);
        loaded.saveCustomOnly(resaved);
        CompoundTag tag = resaved.buildResult();
        helper.assertTrue(tag.contains("fluids") && !tag.contains("fluid"), "the new save has the keys " + tag.keySet());
        helper.assertTrue(load(helper, resaved).fluids().contents(0).getAmount() == 750, "the resaved box did not read back");

        // A box above its new capacity is left as it is and displayed whole.
        TagValueOutput overfull = output(helper);
        overfull.store("held_recipe", Identifier.CODEC, WATER_SMALL);
        single.set(0, FluidResource.of(Fluids.WATER), 1000);
        single.serialize(overfull.child("fluid"));
        AssemblerBlockEntity big = load(helper, overfull);
        helper.assertTrue(big.fluids().capacity(0) == 40 && big.fluids().contents(0).getAmount() == 1000 && big.fluids().displayCapacity(0) >= 1000,
                "an old box of 1000 mB under a 40 mB rule read back as " + big.fluids().contents(0) + " of " + big.fluids().capacity(0));

        // An old save of an empty box, and one with no box at all, read back empty.
        TagValueOutput empty = output(helper);
        new FluidStacksResourceHandler(1, 1000).serialize(empty.child("fluid"));
        helper.assertTrue(load(helper, empty).fluids().contents(0).isEmpty(), "an old empty box read back holding fluid");
        helper.assertTrue(load(helper, output(helper)).fluids().contents(0).isEmpty(), "a save with no box read back holding fluid");

        // Both keys: the new one wins.
        AssemblerMachineTests.hold(assembler, WATER_CRAFT);
        assembler.machine().fluids().set(0, new FluidStack(Fluids.WATER, 100));
        TagValueOutput both = output(helper);
        assembler.machine().saveCustomOnly(both);
        single.set(0, FluidResource.of(Fluids.LAVA), 5);
        single.serialize(both.child("fluid"));
        AssemblerBlockEntity either = load(helper, both);
        helper.assertTrue(either.fluids().contents(0).getFluid() == Fluids.WATER && either.fluids().contents(0).getAmount() == 100,
                "a save with both keys read back as " + either.fluids().contents(0));
        helper.succeed();
    }

    // -- where the connections are --------------------------------------------------------------

    /**
     * Every block of the footprint on every side, as "offset from the origin, side", where a fluid capability
     * answers.
     */
    private static Set<String> exposed(GameTestHelper helper, Placed assembler) {
        Set<String> found = new TreeSet<>();
        BlockPos origin = helper.absolutePos(ORIGIN);
        List<BlockPos> blocks = Assemblers.footprint(assembler.tier()).positions(origin, assembler.facing());
        for (BlockPos pos : blocks) {
            for (Direction side : Direction.values()) {
                if (helper.getLevel().getCapability(Capabilities.Fluid.BLOCK, pos, side) != null) {
                    found.add(pos.subtract(origin).toShortString() + " " + side);
                }
            }
        }
        return found;
    }

    /** The faces that are connections for a facing, as {@link #exposed} spells them: the six sites of the bottom layer. */
    private static Set<String> connectionFaces(Direction facing) {
        Set<String> faces = new TreeSet<>();
        for (Connection connection : connections(facing)) {
            faces.add(connection.block().subtract(ORIGIN).toShortString() + " " + connection.side());
        }
        return faces;
    }

    private static void connectionsFollowFacing(GameTestHelper helper, Direction facing) {
        Placed assembler = AssemblerMachineTests.place(helper, AssemblerTier.TWO, facing);
        helper.assertTrue(exposed(helper, assembler).isEmpty(), "an Assembler with no recipe answered a fluid lookup at " + exposed(helper, assembler));
        AssemblerMachineTests.hold(assembler, WATER_CRAFT);
        Set<String> expected = connectionFaces(facing);
        helper.assertTrue(expected.size() == 6, "an Assembler has six connections, not " + expected);
        helper.assertTrue(exposed(helper, assembler).equals(expected),
                "facing " + facing + " the fluid capability answered at " + exposed(helper, assembler) + ", not at " + expected);
        // On the bottom layer, and no two of their pipes touch, so each carries a network of its own.
        for (Connection connection : connections(facing)) {
            helper.assertTrue(connection.block().getY() == ORIGIN.getY(), "a connection is above the bottom layer: " + connection);
        }
        helper.assertTrue(connections(facing).subList(0, 2).stream().allMatch(connection -> connection.side() == facing),
                "the first two connections are not on the side it faces");
        List<Connection> all = connections(facing);
        for (int one = 0; one < all.size(); one++) {
            for (int other = one + 1; other < all.size(); other++) {
                BlockPos gap = all.get(one).beyond().subtract(all.get(other).beyond());
                helper.assertTrue(Math.abs(gap.getX()) + Math.abs(gap.getY()) + Math.abs(gap.getZ()) > 1,
                        "the pipes at " + all.get(one) + " and " + all.get(other) + " touch");
            }
        }
        helper.assertTrue(helper.getBlockState(ORIGIN).getValue(AssemblerBlock.FLUID_CONNECTIONS), "the origin does not show its connections");
        helper.succeed();
    }

    /** The centres of the front and back edges were connections once, with the edges' ends: now they answer nothing. */
    private static void oldCentresAreNotConnections(GameTestHelper helper, Direction facing) {
        Placed assembler = AssemblerMachineTests.place(helper, AssemblerTier.TWO, facing);
        AssemblerMachineTests.hold(assembler, WATER_CRAFT);
        for (Direction side : List.of(facing, facing.getOpposite())) {
            BlockPos centre = helper.absolutePos(ORIGIN.relative(side));
            helper.assertTrue(helper.getLevel().getCapability(Capabilities.Fluid.BLOCK, centre, side) == null,
                    "the centre of the edge on the " + side + " side answered a fluid lookup, facing " + facing);
        }
        helper.succeed();
    }

    /**
     * Five of the six connections, each with a tank of its own and nothing closing a side between them: a recipe of two
     * fluids in and three out, the five boxes, crafts with every fluid in its own network.
     */
    private static void fiveNetworks(GameTestHelper helper, Direction facing) {
        Placed assembler = AssemblerMachineTests.place(helper, AssemblerTier.THREE, facing);
        List<Connection> at = connections(facing);
        TestTank.Entity water = source(helper, at.get(0), Fluids.WATER, 5000);
        TestTank.Entity lava = source(helper, at.get(1), Fluids.LAVA, 5000);
        List<TestTank.Entity> sinks = List.of(drain(helper, at.get(2)), drain(helper, at.get(4)), drain(helper, at.get(5)));
        AssemblerMachineTests.hold(assembler, THREE_OUTPUTS);

        SimpleEnergyHandler supply = AssemblerMachineTests.supply();
        for (int ran = 0; ran < 40 && inTank(sinks.get(0)) == 0; ran++) {
            AssemblerMachineTests.feed(assembler, supply, 1000);
            assembler.machine().serverTick(helper.getLevel());
        }
        tick(helper, assembler, 2);
        helper.assertTrue(inTank(water) < 5000 && inTank(lava) < 5000, "an input was not pulled: " + inTank(water) + " and " + inTank(lava) + " mB left");
        helper.assertTrue(drained(sinks.get(0), Fluids.LAVA) == 30, "the drain beside the back holds " + sinks.get(0).tank.getResource(0) + " x " + inTank(sinks.get(0)));
        // Any connection serves any box, so the 100 mB of water made in two boxes lands in the two water drains between them.
        int waterOut = drained(sinks.get(1), Fluids.WATER) + drained(sinks.get(2), Fluids.WATER);
        helper.assertTrue(waterOut == 100, "the two water drains hold " + waterOut + " mB, not the 100 made");
        helper.succeed();
    }

    /** Each of the six alone fills an input box from a source and drains an output box into a drain, then goes. */
    private static void anyConnectionDoes(GameTestHelper helper, Direction facing) {
        for (int index = 0; index < 6; index++) {
            Placed assembler = AssemblerMachineTests.place(helper, AssemblerTier.TWO, facing);
            Connection connection = connections(facing).get(index);
            AssemblerMachineTests.hold(assembler, FULL);
            assembler.machine().fluids().set(2, new FluidStack(Fluids.WATER, 300));
            TestTank.Entity water = source(helper, connection, Fluids.WATER, 5000);
            tick(helper, assembler, 1);
            helper.assertTrue(amount(assembler, 0) == 800 && inTank(water) == 4200,
                    "the connection at " + connection + " alone left " + amount(assembler, 0) + " mB in the box and " + inTank(water) + " in the tank");
            // The same face, with a drain in the source's place, takes the output.
            TestTank.Entity sink = drain(helper, connection);
            tick(helper, assembler, 1);
            helper.assertTrue(inTank(sink) == 300 && amount(assembler, 2) == 0,
                    "the connection at " + connection + " alone drained " + inTank(sink) + " mB, leaving " + amount(assembler, 2));
            helper.setBlock(connection.beyond(), Blocks.AIR);
            helper.destroyBlock(ORIGIN);
        }
        helper.succeed();
    }

    /** A connection is one face of one block: its other faces, its block's neighbours and the upper layer's are not. */
    private static void onlyConnectionsAreUsed(GameTestHelper helper) {
        Placed assembler = AssemblerMachineTests.place(helper, AssemblerTier.TWO);
        AssemblerMachineTests.hold(assembler, FULL);
        assembler.machine().fluids().set(2, new FluidStack(Fluids.WATER, 300));
        Direction facing = assembler.facing();
        Direction across = facing.getClockWise();
        // The centres of the front and back edges, which were connections before the six were spaced apart, beside a
        // corner's side face, and above a connection block's neighbour in the upper layer, and above the footprint.
        for (BlockPos at : List.of(ORIGIN.relative(facing, 2),
                ORIGIN.relative(facing.getOpposite(), 2),
                ORIGIN.relative(facing).relative(across, 2),
                ORIGIN.above().relative(facing, 2),
                ORIGIN.relative(facing).above(2))) {
            TestTank.Entity water = tank(helper, at, Fluids.WATER, 5000);
            tick(helper, assembler, 2);
            helper.assertTrue(amount(assembler, 0) == 0 && inTank(water) == 5000 && amount(assembler, 2) == 300,
                    "a tank at " + at + " was pulled from, or pushed to: " + inTank(water));
            helper.setBlock(at, Blocks.AIR);
        }
        helper.succeed();
    }

    /** Tier 1's connections are the same six as any tier's, and come and go with a fluid in the Held recipe. */
    private static void tier1HasTheConnections(GameTestHelper helper) {
        Placed assembler = AssemblerMachineTests.place(helper, AssemblerTier.ONE);
        helper.assertTrue(exposed(helper, assembler).isEmpty(), "tier 1 with no recipe answered a fluid lookup at " + exposed(helper, assembler));
        AssemblerMachineTests.hold(assembler, SAPLING);
        helper.assertTrue(exposed(helper, assembler).isEmpty(), "tier 1 holding a recipe with no fluid answered a fluid lookup at " + exposed(helper, assembler));
        helper.assertTrue(!connectionsShown(helper), "tier 1 shows connections with a recipe with no fluid");
        Set<String> expected = connectionFaces(assembler.facing());
        for (Identifier recipe : List.of(WATER_CRAFT, FLUID_RESULT, TWO_OUTPUTS)) {
            AssemblerMachineTests.hold(assembler, recipe);
            helper.assertTrue(exposed(helper, assembler).equals(expected),
                    "tier 1 holding " + recipe + " answered a fluid lookup at " + exposed(helper, assembler) + ", not at " + expected);
            helper.assertTrue(connectionsShown(helper), "tier 1 holding " + recipe + " shows no connections");
        }
        AssemblerMachineTests.hold(assembler, SAPLING);
        helper.assertTrue(exposed(helper, assembler).isEmpty() && !connectionsShown(helper), "tier 1 kept its connections after the recipe lost its fluid");
        helper.succeed();
    }

    // -- appearing and going --------------------------------------------------------------------

    private static void connectionsComeAndGo(GameTestHelper helper) {
        Placed assembler = AssemblerMachineTests.place(helper, AssemblerTier.TWO);
        helper.assertTrue(exposed(helper, assembler).isEmpty(), "with no Held recipe there were connections at " + exposed(helper, assembler));
        AssemblerMachineTests.hold(assembler, SAPLING);
        helper.assertTrue(exposed(helper, assembler).isEmpty(), "with a recipe with no fluid there were connections at " + exposed(helper, assembler));
        helper.assertTrue(!connectionsShown(helper), "the origin shows connections with a recipe with no fluid");

        AssemblerMachineTests.hold(assembler, WATER_CRAFT);
        helper.assertTrue(exposed(helper, assembler).size() == 6, "a fluid ingredient gave connections at " + exposed(helper, assembler));
        helper.assertTrue(connectionsShown(helper), "the origin does not show connections with a fluid recipe");

        // One with a fluid only in its results still has them, to push through, and so has one with no item at all.
        AssemblerMachineTests.hold(assembler, FLUID_RESULT);
        helper.assertTrue(exposed(helper, assembler).size() == 6, "a recipe with a fluid result gave connections at " + exposed(helper, assembler));
        AssemblerMachineTests.hold(assembler, TWO_OUTPUTS);
        helper.assertTrue(exposed(helper, assembler).size() == 6, "a recipe with fluids and no item gave connections at " + exposed(helper, assembler));

        AssemblerMachineTests.hold(assembler, SAPLING);
        helper.assertTrue(exposed(helper, assembler).isEmpty(), "after the recipe changed to one with no fluid there were connections at " + exposed(helper, assembler));
        helper.assertTrue(!connectionsShown(helper), "the origin still shows connections after the recipe lost its fluid");
        helper.succeed();
    }

    private static boolean connectionsShown(GameTestHelper helper) {
        return helper.getBlockState(ORIGIN).getValue(AssemblerBlock.FLUID_CONNECTIONS);
    }

    /** A pipe keeps what it was told for a face until the level says it changed (NeoForge's capability caches). */
    private static void cachesAreTold(GameTestHelper helper) {
        Placed assembler = AssemblerMachineTests.place(helper, AssemblerTier.TWO);
        Direction facing = assembler.facing();
        // The corner connection, which the Assembler had none at before the six.
        Connection connection = connections(facing).get(0);
        BlockCapabilityCache<ResourceHandler<FluidResource>, Direction> pipe =
                BlockCapabilityCache.create(Capabilities.Fluid.BLOCK, helper.getLevel(), helper.absolutePos(connection.block()), connection.side());
        helper.assertTrue(pipe.getCapability() == null, "the cache saw a capability before any recipe");
        AssemblerMachineTests.hold(assembler, WATER_CRAFT);
        helper.assertTrue(pipe.getCapability() != null, "the cache did not see the connection appear");
        AssemblerMachineTests.hold(assembler, SAPLING);
        helper.assertTrue(pipe.getCapability() == null, "the cache did not see the connection go");
        helper.succeed();
    }

    // -- the craft ------------------------------------------------------------------------------

    /**
     * Water at one connection, lava at another and a drain at a third: the Assembler fills its two input boxes by
     * the recipe's order, crafts in the recipe's time at its speed, and pushes the 600 mB of water it made out through
     * a connection of its own.
     */
    private static void craftsThroughItsConnections(GameTestHelper helper) {
        // Tier 3, whose Overload Limit lets 3 of the iron in.
        Placed assembler = AssemblerMachineTests.place(helper, AssemblerTier.THREE);
        List<Connection> at = connections(assembler.facing());
        TestTank.Entity water = source(helper, at.get(0), Fluids.WATER, 5000);
        TestTank.Entity lava = source(helper, at.get(5), Fluids.LAVA, 5000);
        TestTank.Entity sink = drain(helper, at.get(1));
        AssemblerMachineTests.hold(assembler, FULL);
        helper.assertTrue(AssemblerMachineTests.insert(assembler, 0, Items.IRON_INGOT, 3) == 3, "the iron did not go in");

        SimpleEnergyHandler supply = AssemblerMachineTests.supply();
        int ran = 0;
        while (AssemblerMachineTests.count(assembler, AssemblerSlots.PRODUCT) == 0 && ran < 100) {
            AssemblerMachineTests.feed(assembler, supply, 1000);
            assembler.machine().serverTick(helper.getLevel());
            ran++;
        }
        helper.assertTrue(ran == assembler.machine().craftDuration(), "it finished its craft after " + ran + " ticks, not " + assembler.machine().craftDuration());
        helper.assertTrue(assembler.machine().inventory().getResource(AssemblerSlots.PRODUCT).getItem() == Items.GOLD_INGOT,
                "the product was not a gold ingot");
        helper.assertTrue(AssemblerMachineTests.count(assembler, 0) == 2, "the craft left " + AssemblerMachineTests.count(assembler, 0) + " iron, not 2");
        // The first tick filled each input box to 4 crafts' worth, 800 and 400 mB, and the craft took 200 and 100.
        helper.assertTrue(box(assembler, 0).getFluid() == Fluids.WATER && amount(assembler, 0) == 600, "input box 1 holds " + box(assembler, 0));
        helper.assertTrue(box(assembler, 1).getFluid() == Fluids.LAVA && amount(assembler, 1) == 300, "input box 2 holds " + box(assembler, 1));
        helper.assertTrue(inTank(water) == 4200 && inTank(lava) == 4600,
                "the tanks hold " + inTank(water) + " and " + inTank(lava) + " mB, not 4200 and 4600");
        // The result went out through the connections, as it was made.
        helper.assertTrue(drained(sink, Fluids.WATER) == 600, "the drain holds " + sink.tank.getResource(0) + " x " + inTank(sink));
        for (int out = 2; out < 5; out++) {
            helper.assertTrue(amount(assembler, out) == 0, "output box " + out + " still holds " + box(assembler, out));
        }
        helper.succeed();
    }

    /**
     * Water at one connection, lava at another and a drain at each of three more: two fluids in and three out, as
     * Factorio's basic oil processing is.
     */
    private static void craftsTwoIntoThree(GameTestHelper helper) {
        Placed assembler = AssemblerMachineTests.place(helper, AssemblerTier.THREE);
        List<Connection> at = connections(assembler.facing());
        TestTank.Entity water = source(helper, at.get(0), Fluids.WATER, 5000);
        TestTank.Entity lava = source(helper, at.get(1), Fluids.LAVA, 5000);
        List<TestTank.Entity> sinks = List.of(drain(helper, at.get(2)), drain(helper, at.get(3)), drain(helper, at.get(4)));
        AssemblerMachineTests.hold(assembler, THREE_OUTPUTS);

        SimpleEnergyHandler supply = AssemblerMachineTests.supply();
        int ran = 0;
        while (sinks.stream().mapToInt(AssemblerFluidTests::inTank).sum() == 0 && ran < 100) {
            AssemblerMachineTests.feed(assembler, supply, 1000);
            assembler.machine().serverTick(helper.getLevel());
            ran++;
        }
        helper.assertTrue(ran == assembler.machine().craftDuration(), "it finished its craft after " + ran + " ticks, not " + assembler.machine().craftDuration());
        // The first tick filled each input box to 4 crafts' worth, 400 mB each, and the craft took 100.
        helper.assertTrue(box(assembler, 0).getFluid() == Fluids.WATER && amount(assembler, 0) == 300, "input box 1 holds " + box(assembler, 0));
        helper.assertTrue(box(assembler, 1).getFluid() == Fluids.LAVA && amount(assembler, 1) == 300, "input box 2 holds " + box(assembler, 1));
        helper.assertTrue(inTank(water) == 4600 && inTank(lava) == 4600,
                "the tanks hold " + inTank(water) + " and " + inTank(lava) + " mB, not 4600 and 4600");
        // The results went out as they were made: 30 mB of lava and 100 of water, whichever drain took each.
        int lavaOut = sinks.stream().mapToInt(sink -> drained(sink, Fluids.LAVA)).sum();
        int waterOut = sinks.stream().mapToInt(sink -> drained(sink, Fluids.WATER)).sum();
        helper.assertTrue(lavaOut == 30 && waterOut == 100, "the drains took " + lavaOut + " mB of lava and " + waterOut + " of water, not 30 and 100");
        for (int out = 2; out < 5; out++) {
            helper.assertTrue(amount(assembler, out) == 0, "output box " + out + " still holds " + box(assembler, out));
        }
        helper.succeed();
    }

    /**
     * With no way out, the output box fills at 1,800 mB (three crafts of 600), and the fourth craft waits: no
     * energy drawn, no progress made, no input taken. Given a drain, the box empties and the craft goes on.
     */
    private static void stallsOnAFullBox(GameTestHelper helper) {
        Placed assembler = AssemblerMachineTests.place(helper, AssemblerTier.TWO);
        List<Connection> at = connections(assembler.facing());
        TestTank.Entity water = source(helper, at.get(0), Fluids.WATER, 8000);
        source(helper, at.get(5), Fluids.LAVA, 8000);
        AssemblerMachineTests.hold(assembler, FULL);
        SimpleEnergyHandler supply = AssemblerMachineTests.supply();
        for (int ran = 0; ran < 150; ran++) {
            if (AssemblerMachineTests.count(assembler, 0) == 0) {
                AssemblerMachineTests.insert(assembler, 0, Items.IRON_INGOT, 1);
            }
            AssemblerMachineTests.feed(assembler, supply, 1000);
            assembler.machine().serverTick(helper.getLevel());
        }
        int product = AssemblerMachineTests.count(assembler, AssemblerSlots.PRODUCT);
        helper.assertTrue(product == 3, "it made " + product + " crafts, not the 3 the box holds");
        helper.assertTrue(amount(assembler, 2) == 1800, "the output box holds " + amount(assembler, 2) + " mB, not its 1800");
        helper.assertTrue(assembler.machine().state() == MachineState.OUTPUT_FULL, "an Assembler with a full output box reported " + assembler.machine().state());

        // Stalled, it draws nothing, makes no progress and keeps its inputs.
        int energy = assembler.machine().energy();
        int progress = assembler.machine().craftProgress();
        int iron = AssemblerMachineTests.count(assembler, 0);
        int inWater = amount(assembler, 0);
        for (int tick = 0; tick < 10; tick++) {
            assembler.machine().serverTick(helper.getLevel());
        }
        helper.assertTrue(assembler.machine().energy() == energy, "a stalled Assembler drew " + (energy - assembler.machine().energy()) + " FE");
        helper.assertTrue(assembler.machine().craftProgress() == progress && AssemblerMachineTests.count(assembler, 0) == iron && amount(assembler, 0) == inWater,
                "a stalled Assembler moved: progress " + assembler.machine().craftProgress() + ", iron " + AssemblerMachineTests.count(assembler, 0)
                        + ", water " + amount(assembler, 0));

        // A drain at a connection: the box empties through it, and the craft goes on.
        TestTank.Entity sink = drain(helper, at.get(2));
        for (int ran = 0; ran < 80 && AssemblerMachineTests.count(assembler, AssemblerSlots.PRODUCT) < 4; ran++) {
            if (AssemblerMachineTests.count(assembler, 0) == 0) {
                AssemblerMachineTests.insert(assembler, 0, Items.IRON_INGOT, 1);
            }
            AssemblerMachineTests.feed(assembler, supply, 1000);
            assembler.machine().serverTick(helper.getLevel());
        }
        helper.assertTrue(AssemblerMachineTests.count(assembler, AssemblerSlots.PRODUCT) == 4,
                "it made " + AssemblerMachineTests.count(assembler, AssemblerSlots.PRODUCT) + " crafts, not 4, once drained");
        helper.assertTrue(inTank(sink) >= 1800, "the drain took " + inTank(sink) + " mB");
        helper.assertTrue(inTank(water) < 8000, "no water was pulled");
        helper.succeed();
    }

    /**
     * With no way out, each output box in turn at its volume stalls the craft: no energy drawn, no progress made,
     * no input taken, the state Output full. Given a drain, the box empties and the craft goes on.
     */
    private static void stallsOnEachFullBox(GameTestHelper helper) {
        for (int out = 0; out < 3; out++) {
            Placed assembler = AssemblerMachineTests.place(helper, AssemblerTier.TWO);
            List<Connection> at = connections(assembler.facing());
            source(helper, at.get(0), Fluids.WATER, 8000);
            source(helper, at.get(1), Fluids.LAVA, 8000);
            AssemblerMachineTests.hold(assembler, THREE_OUTPUTS);
            int box = FluidLayout.ASSEMBLER.outputBox(out);
            helper.assertTrue(assembler.machine().fluids().capacity(box) == OUTPUT_VOLUME[out],
                    "output box " + (out + 1) + " holds " + assembler.machine().fluids().capacity(box) + " mB, not " + OUTPUT_VOLUME[out]);
            assembler.machine().fluids().set(box, new FluidStack(OUTPUT_FLUID[out], OUTPUT_VOLUME[out]));

            SimpleEnergyHandler supply = AssemblerMachineTests.supply();
            AssemblerMachineTests.feed(assembler, supply, 1000);
            assembler.machine().serverTick(helper.getLevel());
            int energy = assembler.machine().energy();
            int water = amount(assembler, 0);
            int lava = amount(assembler, 1);
            for (int tick = 0; tick < 30; tick++) {
                assembler.machine().serverTick(helper.getLevel());
            }
            helper.assertTrue(assembler.machine().state() == MachineState.OUTPUT_FULL,
                    "an Assembler with output box " + (out + 1) + " full reported " + assembler.machine().state());
            helper.assertTrue(assembler.machine().energy() == energy && assembler.machine().craftProgress() == 0
                            && amount(assembler, 0) == water && amount(assembler, 1) == lava && amount(assembler, box) == OUTPUT_VOLUME[out],
                    "an Assembler with output box " + (out + 1) + " full went on: progress " + assembler.machine().craftProgress());

            // A drain at a connection: the box empties through it, and the craft goes on.
            drain(helper, at.get(3));
            for (int ran = 0; ran < 60 && assembler.machine().craftProgress() == 0; ran++) {
                AssemblerMachineTests.feed(assembler, supply, 1000);
                assembler.machine().serverTick(helper.getLevel());
            }
            helper.assertTrue(assembler.machine().craftProgress() > 0, "output box " + (out + 1) + " drained and the craft still did not go on");
            helper.destroyBlock(ORIGIN);
            helper.setBlock(at.get(0).beyond(), Blocks.AIR);
            helper.setBlock(at.get(1).beyond(), Blocks.AIR);
            helper.setBlock(at.get(3).beyond(), Blocks.AIR);
        }
        helper.succeed();
    }

    private static void waitsOnAFullProduct(GameTestHelper helper) {
        Placed assembler = AssemblerMachineTests.place(helper, AssemblerTier.TWO);
        List<Connection> at = connections(assembler.facing());
        source(helper, at.get(0), Fluids.WATER, 5000);
        source(helper, at.get(5), Fluids.LAVA, 5000);
        AssemblerMachineTests.hold(assembler, FULL);
        AssemblerMachineTests.insert(assembler, 0, Items.IRON_INGOT, 1);
        assembler.machine().inventory().set(AssemblerSlots.PRODUCT, ItemResource.of(Items.GOLD_INGOT), 64);
        tick(helper, assembler, 40);
        helper.assertTrue(AssemblerMachineTests.count(assembler, AssemblerSlots.PRODUCT) == 64 && AssemblerMachineTests.count(assembler, 0) == 1,
                "a craft went on with a full product slot");
        helper.assertTrue(assembler.machine().state() == MachineState.OUTPUT_FULL, "an Assembler with a full product slot reported " + assembler.machine().state());
        helper.assertTrue(amount(assembler, 2) == 0, "output went into a box for a craft that never was");
        helper.succeed();
    }

    /**
     * A recipe that names no item: water in, and 400 mB of water and 100 mB of lava out, each into its own box and
     * out through the drains, with every item slot untouched.
     */
    private static void fluidOnly(GameTestHelper helper) {
        Placed assembler = AssemblerMachineTests.place(helper, AssemblerTier.TWO);
        List<Connection> at = connections(assembler.facing());
        TestTank.Entity water = source(helper, at.get(0), Fluids.WATER, 5000);
        List<TestTank.Entity> sinks = List.of(drain(helper, at.get(1)), drain(helper, at.get(2)));
        AssemblerMachineTests.hold(assembler, TWO_OUTPUTS);
        helper.assertTrue(assembler.machine().state() != MachineState.CANT_RUN, "a recipe with no item could not run");

        tick(helper, assembler, assembler.machine().craftDuration() + 3);
        helper.assertTrue(inTank(water) < 5000, "no water was pulled");
        int waterOut = sinks.stream().mapToInt(sink -> drained(sink, Fluids.WATER)).sum();
        int lavaOut = sinks.stream().mapToInt(sink -> drained(sink, Fluids.LAVA)).sum();
        helper.assertTrue(waterOut == 400 && lavaOut == 100, "the drains took " + waterOut + " mB of water and " + lavaOut + " of lava, not 400 and 100");
        helper.assertTrue(amount(assembler, 2) == 0 && amount(assembler, 3) == 0, "the output boxes still hold " + amount(assembler, 2) + " and " + amount(assembler, 3));
        for (int slot = 0; slot < AssemblerSlots.SIZE; slot++) {
            helper.assertTrue(AssemblerMachineTests.count(assembler, slot) == 0, "item slot " + slot + " holds something");
        }
        helper.succeed();
    }

    /** A recipe with items and a fluid result: the items go where they always went, the fluid in its output box. */
    private static void fluidBesideItems(GameTestHelper helper) {
        Placed assembler = AssemblerMachineTests.place(helper, AssemblerTier.TWO);
        List<Connection> at = connections(assembler.facing());
        source(helper, at.get(0), Fluids.WATER, 5000);
        AssemblerMachineTests.hold(assembler, FLUID_RESULT);
        helper.assertTrue(AssemblerMachineTests.insert(assembler, 0, Items.GOLD_INGOT, 1) == 1, "the gold did not go in");
        SimpleEnergyHandler supply = AssemblerMachineTests.supply();
        for (int ran = 0; ran < 100 && AssemblerMachineTests.count(assembler, AssemblerSlots.PRODUCT) == 0; ran++) {
            AssemblerMachineTests.feed(assembler, supply, 1000);
            assembler.machine().serverTick(helper.getLevel());
        }
        helper.assertTrue(assembler.machine().inventory().getResource(AssemblerSlots.PRODUCT).getItem() == Items.GOLD_NUGGET
                && AssemblerMachineTests.count(assembler, AssemblerSlots.PRODUCT) == 9,
                "the product slot holds " + assembler.machine().inventory().getResource(AssemblerSlots.PRODUCT) + " x " + AssemblerMachineTests.count(assembler, AssemblerSlots.PRODUCT));
        helper.assertTrue(box(assembler, 2).getFluid() == Fluids.LAVA && amount(assembler, 2) == 50, "output box 1 holds " + box(assembler, 2));
        helper.assertTrue(amount(assembler, 0) == 750, "input box 1 holds " + amount(assembler, 0) + " mB, not the 1000 it filled to less the 250 spent");
        helper.succeed();
    }

    private static void pullsByOrder(GameTestHelper helper) {
        Placed assembler = AssemblerMachineTests.place(helper, AssemblerTier.TWO);
        List<Connection> at = connections(assembler.facing());
        AssemblerMachineTests.hold(assembler, FULL);
        // Lava first and alone: it goes to the box the recipe's second ingredient names, and water has none yet.
        TestTank.Entity lava = source(helper, at.get(3), Fluids.LAVA, 5000);
        tick(helper, assembler, 1);
        helper.assertTrue(amount(assembler, 0) == 0 && box(assembler, 1).getFluid() == Fluids.LAVA && amount(assembler, 1) == 400,
                "lava went to " + box(assembler, 0) + " and " + box(assembler, 1));
        helper.assertTrue(inTank(lava) == 4600, "the lava tank holds " + inTank(lava));
        // A fluid the recipe doesn't use is left where it is.
        helper.setBlock(at.get(3).beyond(), Blocks.AIR);
        TestTank.Entity other = source(helper, at.get(0), Fluids.FLOWING_LAVA, 5000);
        tick(helper, assembler, 1);
        helper.assertTrue(amount(assembler, 0) == 0 && inTank(other) == 5000, "a fluid the recipe doesn't use was pulled: " + box(assembler, 0));
        helper.setBlock(at.get(0).beyond(), Blocks.AIR);
        TestTank.Entity water = source(helper, at.get(0), Fluids.WATER, 5000);
        tick(helper, assembler, 1);
        helper.assertTrue(box(assembler, 0).getFluid() == Fluids.WATER && amount(assembler, 0) == 800, "water went to " + box(assembler, 0));
        helper.assertTrue(inTank(water) == 4200, "the water tank holds " + inTank(water));
        helper.succeed();
    }

    private static void connectionFaces(GameTestHelper helper) {
        Placed assembler = AssemblerMachineTests.place(helper, AssemblerTier.TWO);
        AssemblerMachineTests.hold(assembler, FULL);
        ResourceHandler<FluidResource> face = fluid(assembler, connections(assembler.facing()).get(1));
        helper.assertTrue(face != null && face.size() == 5, "the connection shows " + (face == null ? "nothing" : face.size() + " boxes"));
        // A mod that pushes fills the input box the recipe's order names, and only with the fluid it takes there.
        helper.assertTrue(insert(face, Fluids.WATER, 150) == 150 && box(assembler, 0).getFluid() == Fluids.WATER, "water did not reach input box 1");
        helper.assertTrue(insert(face, Fluids.LAVA, 50) == 50 && box(assembler, 1).getFluid() == Fluids.LAVA, "lava did not reach input box 2");
        helper.assertTrue(insert(face, Fluids.WATER, 5000) == 650, "input box 1 took more than its 4 crafts' 800 mB");
        helper.assertTrue(insert(face, Fluids.FLOWING_LAVA, 100) == 0, "a fluid the recipe doesn't use went in");
        // Nothing comes out of an input box.
        helper.assertTrue(extract(face, Fluids.WATER, 100) == 0 && extract(face, Fluids.LAVA, 10) == 0, "an input box gave fluid out");
        // An output box is drained and never filled from outside.
        assembler.machine().fluids().set(2, new FluidStack(Fluids.WATER, 400));
        helper.assertTrue(extract(face, Fluids.WATER, 150) == 150 && amount(assembler, 2) == 250, "an output box gave " + (400 - amount(assembler, 2)));
        assembler.machine().fluids().set(0, FluidStack.EMPTY);
        helper.assertTrue(insert(face, Fluids.WATER, 100) == 100 && amount(assembler, 2) == 250, "water went into an output box, or did not go into the input");
        helper.succeed();
    }

    // -- the boxes' sizes -----------------------------------------------------------------------

    private static void boxSizes(GameTestHelper helper) {
        Placed assembler = AssemblerMachineTests.place(helper, AssemblerTier.TWO);
        MachineFluids boxes = assembler.machine().fluids();
        for (int box = 0; box < 5; box++) {
            int expected = box < 2 ? 1000 : 100;
            helper.assertTrue(boxes.capacity(box) == expected, "box " + box + " holds " + boxes.capacity(box) + " mB with no Held recipe, not " + expected);
        }
        // An input box holds 4 crafts of its ingredient; an output box the larger of 100 mB and 3 crafts of its result.
        AssemblerMachineTests.hold(assembler, FULL);
        helper.assertTrue(boxes.capacity(0) == 800 && boxes.capacity(1) == 400, "the input boxes hold " + boxes.capacity(0) + " and " + boxes.capacity(1));
        helper.assertTrue(boxes.capacity(2) == 1800, "output box 1 holds " + boxes.capacity(2) + " mB, not 3 crafts of 600");
        helper.assertTrue(boxes.capacity(3) == 100 && boxes.capacity(4) == 100,
                "output boxes 2 and 3 have no result bound to them and hold " + boxes.capacity(3) + " and " + boxes.capacity(4) + " mB, not 100");
        AssemblerMachineTests.hold(assembler, TWO_OUTPUTS);
        helper.assertTrue(boxes.capacity(0) == 400, "the input box holds " + boxes.capacity(0) + " mB, not 4 crafts of 100");
        helper.assertTrue(boxes.capacity(2) == 1200, "output box 1 holds " + boxes.capacity(2) + " mB, not 3 crafts of 400");
        helper.assertTrue(boxes.capacity(3) == 300, "output box 2 holds " + boxes.capacity(3) + " mB, not 3 crafts of 100");
        AssemblerMachineTests.hold(assembler, FULL_BOXES);
        helper.assertTrue(boxes.capacity(0) == 1000 && boxes.capacity(2) == 3000 && boxes.capacity(3) == 3000,
                "1000 mB a craft gave boxes of " + boxes.capacity(0) + ", " + boxes.capacity(2) + " and " + boxes.capacity(3));
        AssemblerMachineTests.hold(assembler, THREE_OUTPUTS);
        helper.assertTrue(boxes.capacity(2) == 100 && boxes.capacity(3) == 135 && boxes.capacity(4) == 165,
                "three results sized the boxes at " + boxes.capacity(2) + ", " + boxes.capacity(3) + " and " + boxes.capacity(4));
        // The results bind by order, so each box takes only its own result.
        AssemblerMachineTests.hold(assembler, TWO_OUTPUTS);
        ResourceHandler<FluidResource> face = fluid(assembler, connections(assembler.facing()).get(0));
        helper.assertTrue(extract(face, Fluids.WATER, 1) == 0, "an empty box gave water");
        boxes.set(2, new FluidStack(Fluids.WATER, 1200));
        boxes.set(3, new FluidStack(Fluids.LAVA, 300));
        helper.assertTrue(extract(face, Fluids.WATER, 5000) == 1200 && extract(face, Fluids.LAVA, 5000) == 300, "the outputs were not drained whole");
        helper.succeed();
    }

    /**
     * A recipe with one fluid result on an Assembler's three output boxes gets the two unused boxes' volume too, 300 mB
     * where two boxes would give 200; Pinned, it doesn't.
     */
    private static void mergedAndPinnedBoxes(GameTestHelper helper) {
        Placed assembler = AssemblerMachineTests.place(helper, AssemblerTier.TWO);
        MachineFluids boxes = assembler.machine().fluids();
        AssemblerMachineTests.hold(assembler, ONE_RESULT);
        helper.assertTrue(boxes.capacity(2) == 300 && boxes.capacity(3) == 100 && boxes.capacity(4) == 100,
                "an unpinned recipe sizes its boxes at " + boxes.capacity(2) + ", " + boxes.capacity(3) + " and " + boxes.capacity(4)
                        + ", not 300 (its own 100 and the two unused boxes') and 100 and 100");
        AssemblerMachineTests.hold(assembler, PINNED);
        helper.assertTrue(boxes.capacity(2) == 120 && boxes.capacity(3) == 100 && boxes.capacity(4) == 100,
                "a pinned recipe sizes its boxes at " + boxes.capacity(2) + ", " + boxes.capacity(3) + " and " + boxes.capacity(4)
                        + ", not 120 (3 crafts of 40) and 100 and 100");
        // A recipe with a result in two boxes has one box to merge.
        AssemblerMachineTests.hold(assembler, TWO_OUTPUTS);
        helper.assertTrue(boxes.capacity(2) == 1200 && boxes.capacity(3) == 300 && boxes.capacity(4) == 100,
                "two results sized the boxes at " + boxes.capacity(2) + ", " + boxes.capacity(3) + " and " + boxes.capacity(4));
        helper.succeed();
    }

    /**
     * With no way out the Assembler makes crafts until its output box can't hold another's result, and waits there. A
     * recipe of 40 mB a craft that is Pinned keeps to its own box, 3 crafts' worth, 120 mB; unpinned, the first result
     * also takes the two unused boxes' 200 mB and the volume is 300, 7 crafts and 20 mB to spare.
     */
    private static void outputBoxHoldsCraftsAndStalls(GameTestHelper helper) {
        for (Identifier recipe : List.of(PINNED, ONE_RESULT)) {
            int volume = recipe.equals(PINNED) ? 120 : 300;
            int made = volume / 40 * 40;
            Placed assembler = AssemblerMachineTests.place(helper, AssemblerTier.TWO);
            source(helper, connections(assembler.facing()).get(0), Fluids.WATER, 5000);
            AssemblerMachineTests.hold(assembler, recipe);
            helper.assertTrue(assembler.machine().fluids().capacity(2) == volume,
                    recipe + " sizes output box 1 at " + assembler.machine().fluids().capacity(2) + " mB, not " + volume);
            tick(helper, assembler, 300);
            helper.assertTrue(amount(assembler, 2) == made && box(assembler, 2).getFluid() == Fluids.LAVA,
                    recipe + " made " + box(assembler, 2) + ", not " + made + " mB of lava, " + made / 40 + " crafts");
            helper.assertTrue(assembler.machine().state() == MachineState.OUTPUT_FULL, "an Assembler with a full output box reported " + assembler.machine().state());
            // Stalled: it draws nothing, makes no progress and takes no input.
            int energy = assembler.machine().energy();
            int inBox = amount(assembler, 0);
            for (int tick = 0; tick < 10; tick++) {
                assembler.machine().serverTick(helper.getLevel());
            }
            helper.assertTrue(assembler.machine().energy() == energy && assembler.machine().craftProgress() == 0
                            && amount(assembler, 0) == inBox && amount(assembler, 2) == made,
                    recipe + " went on past a full box");
            helper.setBlock(connections(assembler.facing()).get(0).beyond(), Blocks.AIR);
            helper.destroyBlock(ORIGIN);
        }
        helper.succeed();
    }

    // -- Fill Recipe ----------------------------------------------------------------------------

    /** Under the default categories, all three on every tier: what a tier refuses is what no tier has the boxes for. */
    private static void refusals(GameTestHelper helper) {
        for (AssemblerTier tier : AssemblerTier.values()) {
            Placed assembler = AssemblerMachineTests.place(helper, tier);
            HoldVerdict three = AssemblerMachineTests.request(assembler, THREE_FLUIDS);
            HoldVerdict four = AssemblerMachineTests.request(assembler, FOUR_RESULTS);
            HoldVerdict big = AssemblerMachineTests.request(assembler, BIG_FLUID);
            helper.assertTrue(three == HoldVerdict.TOO_MANY_FLUIDS, tier + ": three fluid ingredients was " + three);
            helper.assertTrue(four == HoldVerdict.TOO_MANY_FLUIDS, tier + ": four fluid results was " + four);
            helper.assertTrue(big == HoldVerdict.FLUID_TOO_LARGE, tier + ": 1,001 mB a craft was " + big);
            helper.assertTrue(assembler.machine().heldRecipe().isEmpty(), tier + ": a refused recipe was held");
            helper.assertTrue(assembler.player().heard.containsAll(List.of("craftworks.assembler.refused.too_many_fluids",
                    "craftworks.assembler.refused.fluid_too_large")), tier + ": the player was told " + assembler.player().heard);
            // Two fluid ingredients, three fluid results and exactly the box's size are taken, whatever the category.
            for (Identifier recipe : List.of(TWO_FLUIDS, THREE_OUTPUTS, FULL_BOXES, FULL, WATER_CRAFT, FULL_BOX, CRAFTING_FLUID)) {
                HoldVerdict verdict = AssemblerMachineTests.request(assembler, recipe);
                helper.assertTrue(verdict == HoldVerdict.HELD, tier + " answered " + recipe + " with " + verdict);
            }
            helper.destroyBlock(ORIGIN);
        }
        helper.succeed();
    }

    private static void fluidResultsAreHeld(GameTestHelper helper) {
        for (AssemblerTier tier : AssemblerTier.values()) {
            Placed assembler = AssemblerMachineTests.place(helper, tier);
            for (Identifier recipe : List.of(FLUID_RESULT, TWO_OUTPUTS)) {
                HoldVerdict verdict = AssemblerMachineTests.request(assembler, recipe);
                helper.assertTrue(verdict == HoldVerdict.HELD, tier + " answered " + recipe + ", which makes fluid, with " + verdict);
                helper.assertTrue(assembler.machine().heldRecipe().equals(Optional.of(recipe)), tier + " did not hold " + recipe);
            }
            helper.destroyBlock(ORIGIN);
        }
        helper.succeed();
    }

    /** Tier 1 at its own speed: water in through one connection, the gold ingot, and the nuggets and lava out. */
    private static void tier1Crafts(GameTestHelper helper) {
        Placed assembler = AssemblerMachineTests.place(helper, AssemblerTier.ONE);
        List<Connection> at = connections(assembler.facing());
        TestTank.Entity water = source(helper, at.get(0), Fluids.WATER, 5000);
        TestTank.Entity sink = drain(helper, at.get(1));
        helper.assertTrue(AssemblerMachineTests.request(assembler, FLUID_RESULT) == HoldVerdict.HELD, "tier 1 did not hold a recipe with a fluid in and out");
        helper.assertTrue(AssemblerMachineTests.insert(assembler, 0, Items.GOLD_INGOT, 1) == 1, "the gold did not go in");
        SimpleEnergyHandler supply = AssemblerMachineTests.supply();
        int ran = 0;
        while (AssemblerMachineTests.count(assembler, AssemblerSlots.PRODUCT) == 0 && ran < 200) {
            AssemblerMachineTests.feed(assembler, supply, 1000);
            assembler.machine().serverTick(helper.getLevel());
            ran++;
        }
        helper.assertTrue(ran == assembler.machine().craftDuration(), "it finished its craft after " + ran + " ticks, not " + assembler.machine().craftDuration());
        helper.assertTrue(assembler.machine().inventory().getResource(AssemblerSlots.PRODUCT).getItem() == Items.GOLD_NUGGET
                && AssemblerMachineTests.count(assembler, AssemblerSlots.PRODUCT) == 9, "the product slot did not hold 9 gold nuggets");
        helper.assertTrue(amount(assembler, 0) == 750 && inTank(water) == 4000,
                "the box holds " + amount(assembler, 0) + " mB and the tank " + inTank(water) + ", not 750 and 4000");
        helper.assertTrue(drained(sink, Fluids.LAVA) == 50 && amount(assembler, 2) == 0,
                "the drain took " + drained(sink, Fluids.LAVA) + " mB of lava, not 50, leaving " + amount(assembler, 2));
        helper.succeed();
    }

    // -- Fast Replace and breaking --------------------------------------------------------------

    private static void fastReplace(GameTestHelper helper) {
        Placed assembler = AssemblerMachineTests.place(helper, AssemblerTier.TWO);
        AssemblerBlockEntity machine = assembler.machine();
        AssemblerMachineTests.hold(assembler, FULL);
        machine.fluids().set(0, new FluidStack(Fluids.WATER, 400));
        machine.fluids().set(1, new FluidStack(Fluids.LAVA, 100));
        machine.fluids().set(2, new FluidStack(Fluids.WATER, 300));

        AssemblerMachineTests.swap(helper, assembler, AssemblerTier.THREE, ORIGIN.above().north().east());
        helper.assertTrue(helper.getBlockEntity(ORIGIN, AssemblerBlockEntity.class) == machine, "the swap replaced the block entity");
        helper.assertTrue(machine.tier() == AssemblerTier.THREE, "the block entity did not follow to tier 3");
        helper.assertTrue(amount(assembler, 0) == 400 && box(assembler, 0).getFluid() == Fluids.WATER && amount(assembler, 1) == 100 && amount(assembler, 2) == 300,
                "a swap from tier 2 to 3 left " + amount(assembler, 0) + ", " + amount(assembler, 1) + " and " + amount(assembler, 2) + " mB in the boxes");
        // The ring and the capability change together: no tick between the swap and the ring.
        helper.assertTrue(connectionsShown(helper), "tier 3 did not show its connections the moment of the swap");
        helper.assertTrue(exposed(helper, new Placed(helper, assembler.player(), AssemblerTier.THREE, assembler.facing())).size() == 6,
                "tier 3 did not keep its connections after the swap");

        // Tier 1 has the same boxes: a swap to it keeps what they hold, the Held recipe and the connections.
        AssemblerMachineTests.swap(helper, assembler, AssemblerTier.ONE, ORIGIN);
        helper.assertTrue(machine.tier() == AssemblerTier.ONE, "the block entity did not follow to tier 1");
        helper.assertTrue(amount(assembler, 0) == 400 && box(assembler, 0).getFluid() == Fluids.WATER && amount(assembler, 1) == 100 && amount(assembler, 2) == 300,
                "a swap from tier 3 to 1 left " + amount(assembler, 0) + ", " + amount(assembler, 1) + " and " + amount(assembler, 2) + " mB in the boxes");
        helper.assertTrue(machine.heldRecipe().equals(Optional.of(FULL)), "the swap to tier 1 lost the Held recipe");
        helper.assertTrue(connectionsShown(helper), "tier 1 did not show its connections the moment of the swap");
        helper.assertTrue(exposed(helper, new Placed(helper, assembler.player(), AssemblerTier.ONE, assembler.facing())).size() == 6,
                "tier 1 did not keep its connections after the swap");

        // And from tier 1 to 2, which boxes follow just the same.
        AssemblerMachineTests.swap(helper, assembler, AssemblerTier.TWO, ORIGIN);
        helper.assertTrue(amount(assembler, 0) == 400 && amount(assembler, 2) == 300, "the boxes changed in a round trip through tier 1");
        helper.assertTrue(machine.heldRecipe().equals(Optional.of(FULL)), "the swap back to tier 2 lost the Held recipe");
        helper.succeed();
    }

    private static void breaking(GameTestHelper helper) {
        Placed assembler = AssemblerMachineTests.place(helper, AssemblerTier.TWO);
        AssemblerMachineTests.hold(assembler, FULL);
        assembler.machine().fluids().set(0, new FluidStack(Fluids.WATER, 400));
        assembler.machine().fluids().set(2, new FluidStack(Fluids.WATER, 700));
        assembler.player().gameMode.destroyBlock(helper.absolutePos(ORIGIN.above().north().east()));
        List<ItemStack> drops = helper.getEntities(EntityType.ITEM).stream().map(ItemEntity::getItem).toList();
        ItemStack item = drops.stream().filter(stack -> stack.is(Assemblers.item(AssemblerTier.TWO).get())).findFirst().orElse(ItemStack.EMPTY);
        helper.assertTrue(!item.isEmpty() && FULL.equals(item.get(Assemblers.HELD_RECIPE.get())), "the dropped item does not hold the Held recipe");
        Placed again = AssemblerMachineTests.placeItem(helper, AssemblerTier.TWO, item, Direction.WEST);
        helper.assertTrue(again.machine().heldRecipe().equals(Optional.of(FULL)), "the item placed again lost the Held recipe");
        helper.assertTrue(amount(again, 0) == 0 && amount(again, 2) == 0, "the boxes were not voided by the break: " + amount(again, 0) + " and " + amount(again, 2) + " mB");
        helper.succeed();
    }

    // -- the screen -----------------------------------------------------------------------------

    private static void gauges(GameTestHelper helper) {
        for (AssemblerTier tier : AssemblerTier.values()) {
            gauges(helper, tier);
            helper.destroyBlock(ORIGIN);
        }
        helper.succeed();
    }

    private static void gauges(GameTestHelper helper, AssemblerTier tier) {
        Placed assembler = AssemblerMachineTests.place(helper, tier);
        AssemblerMachineTests.hold(assembler, FULL);
        MachineFluids boxes = assembler.machine().fluids();
        boxes.set(0, new FluidStack(Fluids.WATER, 400));
        boxes.set(1, new FluidStack(Fluids.LAVA, 25));
        boxes.set(2, new FluidStack(Fluids.WATER, 1700));
        AssemblerMenu menu = menuOf(assembler);
        helper.assertTrue(menu.fluid(0).getFluid() == Fluids.WATER && menu.fluid(0).getAmount() == 400, "the menu shows " + menu.fluid(0) + " in box 1");
        helper.assertTrue(menu.fluid(1).getFluid() == Fluids.LAVA && menu.fluid(1).getAmount() == 25, "the menu shows " + menu.fluid(1) + " in box 2");
        helper.assertTrue(menu.fluid(2).getAmount() == 1700 && menu.fluid(3).isEmpty() && menu.fluid(4).isEmpty(),
                "the menu shows " + menu.fluid(2) + ", " + menu.fluid(3) + " and " + menu.fluid(4));
        helper.assertTrue(menu.fluidCapacity(0) == 800 && menu.fluidCapacity(1) == 400 && menu.fluidCapacity(2) == 1800
                        && menu.fluidCapacity(3) == 100 && menu.fluidCapacity(4) == 100,
                "the menu shows capacities " + menu.fluidCapacity(0) + ", " + menu.fluidCapacity(1) + ", " + menu.fluidCapacity(2) + ", "
                        + menu.fluidCapacity(3) + " and " + menu.fluidCapacity(4));
        // The screen is a row taller: the player's inventory stands 12 lower.
        helper.assertTrue(menu.inventoryY() == AssemblerMenu.INVENTORY_Y && menu.slots.get(AssemblerSlots.SIZE).y == 96,
                tier + "'s inventory stands at " + menu.slots.get(AssemblerSlots.SIZE).y + ", not 96");

        // The client's menu, opened from the buffer the block writes, is the same on every tier.
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        buffer.writeBlockPos(helper.absolutePos(ORIGIN));
        AssemblerMenu client = new AssemblerMenu(2, assembler.player().getInventory(), buffer);
        helper.assertTrue(client.inventoryY() == AssemblerMenu.INVENTORY_Y && client.slots.get(AssemblerSlots.SIZE).y == 96,
                "the client's menu of a " + tier + " Assembler stands its inventory at " + client.slots.get(AssemblerSlots.SIZE).y);
    }

    private static void crossesToTheClient(GameTestHelper helper) {
        for (AssemblerTier tier : AssemblerTier.values()) {
            crossesToTheClient(helper, tier);
            helper.destroyBlock(ORIGIN);
        }
        helper.succeed();
    }

    private static void crossesToTheClient(GameTestHelper helper, AssemblerTier tier) {
        Placed assembler = AssemblerMachineTests.place(helper, tier);
        AssemblerMachineTests.hold(assembler, FULL);
        MachineFluids boxes = assembler.machine().fluids();
        boxes.set(0, new FluidStack(Fluids.WATER, 400));
        boxes.set(1, new FluidStack(Fluids.LAVA, 25));
        boxes.set(2, new FluidStack(Fluids.WATER, 1700));
        boxes.set(4, new FluidStack(Fluids.LAVA, 60));

        AssemblerMenu client = AssemblerMachineTests.openOnTheClient(helper, Assemblers.MENU.get(), AdvancedContainerSetDataPayload.TYPE);
        helper.assertTrue(client.fluid(0).getFluid() == Fluids.WATER && client.fluid(0).getAmount() == 400, tier + "'s screen shows " + client.fluid(0) + " in box 1");
        helper.assertTrue(client.fluid(1).getFluid() == Fluids.LAVA && client.fluid(1).getAmount() == 25, "the screen shows " + client.fluid(1) + " in box 2");
        helper.assertTrue(client.fluid(2).getAmount() == 1700 && client.fluid(3).isEmpty() && client.fluid(4).getAmount() == 60,
                "the screen shows " + client.fluid(2) + ", " + client.fluid(3) + " and " + client.fluid(4));
        helper.assertTrue(client.fluidCapacity(0) == 800 && client.fluidCapacity(1) == 400 && client.fluidCapacity(2) == 1800
                        && client.fluidCapacity(3) == 100 && client.fluidCapacity(4) == 100,
                "the screen shows capacities " + client.fluidCapacity(0) + ", " + client.fluidCapacity(1) + ", " + client.fluidCapacity(2) + ", "
                        + client.fluidCapacity(3) + " and " + client.fluidCapacity(4));
    }

    private static AssemblerMenu menuOf(Placed assembler) {
        AssemblerMenu menu = (AssemblerMenu) assembler.machine().createMenu(1, assembler.player().getInventory(), assembler.player());
        assembler.player().containerMenu = menu;
        return menu;
    }

    // -- fixtures -------------------------------------------------------------------------------

    private static int insert(ResourceHandler<FluidResource> handler, Fluid fluid, int amount) {
        try (Transaction tx = Transaction.openRoot()) {
            int inserted = handler.insert(FluidResource.of(fluid), amount, tx);
            tx.commit();
            return inserted;
        }
    }

    private static int extract(ResourceHandler<FluidResource> handler, Fluid fluid, int amount) {
        try (Transaction tx = Transaction.openRoot()) {
            int extracted = handler.extract(FluidResource.of(fluid), amount, tx);
            tx.commit();
            return extracted;
        }
    }
}
