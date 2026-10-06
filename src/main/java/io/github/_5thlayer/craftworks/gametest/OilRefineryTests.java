// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import io.github._5thlayer.craftworks.Craftworks;
import io.github._5thlayer.craftworks.CraftworksConfig;
import io.github._5thlayer.craftworks.gametest.AssemblerMachineTests.FakeBuilder;
import io.github._5thlayer.craftworks.machine.FluidMachineBlockEntity;
import io.github._5thlayer.craftworks.machine.FluidMachineMenu;
import io.github._5thlayer.craftworks.machine.HoldVerdict;
import io.github._5thlayer.craftworks.machine.MachineState;
import io.github._5thlayer.craftworks.machine.OilRefineries;
import io.github._5thlayer.craftworks.machine.OilRefineryBlock;
import io.github._5thlayer.craftworks.machine.OilRefineryDefaults;
import io.github._5thlayer.craftworks.recipe.AssemblingCategory;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandlerUtil;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;

/**
 * The Oil Refinery (#27) on a real player and a real server: placed whole from its item on a 5x5x3 footprint,
 * set with Fill Recipe, and crafting an {@code oil-processing} recipe of two fluids in and three out through its
 * five Fluid Connections. The neighbours are {@link TestTank}s and the recipes are the game tests' own.
 */
final class OilRefineryTests {

    /** 100 mB of water and 100 mB of lava make 30 mB of lava and, in two boxes, 45 and 55 mB of water, in 20 ticks. */
    private static final Identifier FULL = id("gametest/oil_full");
    private static final Identifier FULL_BOXES = id("gametest/oil_full_boxes");
    private static final Identifier WITH_ITEM = id("gametest/oil_with_item");
    private static final Identifier ITEM_RESULT = id("gametest/oil_item_result");
    private static final Identifier THREE_FLUIDS = id("gametest/oil_three_fluids");
    private static final Identifier FOUR_RESULTS = id("gametest/oil_four_results");
    private static final Identifier BIG_FLUID = id("gametest/oil_big_fluid");
    private static final Identifier CHEMISTRY = id("gametest/plant_full");
    private static final Identifier STICK = Identifier.parse("minecraft:stick");
    private static final Identifier ITS_OWN_RECIPE = id("oil_refinery");

    private static final BlockPos ORIGIN = AssemblerMachineTests.ORIGIN;
    private static final List<Direction> FACINGS = List.of(Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST);
    /** What each output box holds when full under {@link #FULL}: the larger of 100 mB and 3 crafts' worth of its result. */
    private static final int[] OUTPUT_VOLUME = {100, 135, 165};
    private static final Fluid[] OUTPUT_FLUID = {Fluids.LAVA, Fluids.WATER, Fluids.WATER};

    private OilRefineryTests() {
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(Craftworks.MOD_ID, path);
    }

    static void register(CraftworksGameTests.Registrar tests) {
        tests.test("an_oil_refinery_crafts_two_fluids_into_three_fed_and_drained_through_its_fluid_connections", 40, OilRefineryTests::craftsThroughItsConnections);
        tests.test("an_oil_refinery_stalls_on_each_full_output_box_and_goes_on_once_it_is_drained", 100, OilRefineryTests::stallsOnEachFullBox);
        tests.test("fill_recipe_on_an_oil_refinery_refuses_a_recipe_outside_oil_processing_and_one_with_an_item", 20, OilRefineryTests::refusesWhatItCannotHold);
        tests.test("fill_recipe_refuses_what_the_boxes_of_an_oil_refinery_cannot_hold", 20, OilRefineryTests::refusesWhatTheBoxesCannotHold);
        for (Direction facing : FACINGS) {
            tests.test("an_oil_refinery_facing_" + facing.getName() + "_stands_whole_on_a_5x5x3_footprint_and_breaks_as_one", 20,
                    helper -> standsWholeAndBreaksAsOne(helper, facing));
            tests.test("the_five_fluid_connections_of_an_oil_refinery_facing_" + facing.getName() + "_stand_two_on_the_edge_it_faces_and_three_on_the_other", 20,
                    helper -> connectionsFollowFacing(helper, facing));
        }
        tests.test("an_oil_refinerys_item_keeps_the_held_recipe_and_places_it_back", 20, OilRefineryTests::itemKeepsTheHeldRecipe);
        tests.test("each_of_the_five_connections_alone_feeds_and_drains_an_oil_refinery", 40, OilRefineryTests::anyConnectionDoes);
        tests.test("an_oil_refinery_has_no_item_capability", 20, OilRefineryTests::hasNoItemFace);
        tests.test("the_oil_refinery_starts_from_speed_1_210_fe_a_tick_a_50000_fe_buffer_and_the_oil_processing_category", 20, OilRefineryTests::configDefaults);
    }

    // -- fixtures -------------------------------------------------------------------------------

    /** A Fluid Connection: the footprint block it is, and the way its face points. */
    private record Connection(BlockPos block, Direction side) {

        /** The block the face touches, where a neighbour stands. */
        BlockPos beyond() {
            return block.relative(side);
        }
    }

    /**
     * The five connections of a refinery facing {@code facing}, as the issue gives them and not as the code does:
     * on the bottom layer, two at -1 and +1 along the edge it faces, then three at -2, 0 and +2 along the opposite
     * edge.
     */
    private static List<Connection> connections(Direction facing) {
        Direction back = facing.getOpposite();
        Direction right = facing.getClockWise();
        List<Connection> found = new ArrayList<>();
        for (int along : new int[] {-1, 1}) {
            found.add(new Connection(ORIGIN.relative(facing, 2).relative(right, along), facing));
        }
        for (int along : new int[] {-2, 0, 2}) {
            found.add(new Connection(ORIGIN.relative(back, 2).relative(right, along), back));
        }
        return found;
    }

    /** An Oil Refinery placed in a test, with the player who placed it and the way it faces. */
    record Placed(GameTestHelper helper, FakeBuilder player, Direction facing) {

        FluidMachineBlockEntity machine() {
            return helper.getBlockEntity(ORIGIN, FluidMachineBlockEntity.class);
        }

        EnergyHandler energy(Direction side) {
            return helper.getLevel().getCapability(Capabilities.Energy.BLOCK, corner(), side);
        }

        List<Connection> connections() {
            return OilRefineryTests.connections(facing);
        }

        private BlockPos corner() {
            List<BlockPos> blocks = footprint(helper, facing);
            return blocks.get(blocks.size() - 1);
        }
    }

    private static List<BlockPos> footprint(GameTestHelper helper, Direction facing) {
        return OilRefineries.footprint().positions(helper.absolutePos(ORIGIN), facing);
    }

    private static Placed place(GameTestHelper helper, Direction facing) {
        return placeItem(helper, new ItemStack(OilRefineries.ITEM.get()), facing);
    }

    /** Places the stack by a click on the floor under the origin, failing unless the whole footprint stands. */
    private static Placed placeItem(GameTestHelper helper, ItemStack stack, Direction facing) {
        FakeBuilder player = new FakeBuilder(helper);
        player.setYRot(facing.getOpposite().toYRot());
        player.setYHeadRot(facing.getOpposite().toYRot());
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlockPos floor = helper.absolutePos(ORIGIN.below());
        player.gameMode.useItemOn(player, helper.getLevel(), stack, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(floor).relative(Direction.UP, 0.5), Direction.UP, floor, false));
        var footprint = OilRefineries.footprint();
        List<BlockPos> positions = footprint(helper, facing);
        helper.assertTrue(positions.size() == 75, "a 5x5x3 footprint has " + positions.size() + " blocks, not 75 (the origin and 74 parts)");
        for (int i = 0; i < positions.size(); i++) {
            helper.assertTrue(helper.getLevel().getBlockState(positions.get(i)).equals(footprint.stateAt(i, facing)),
                    "block " + i + " at " + positions.get(i) + " is " + helper.getLevel().getBlockState(positions.get(i)));
        }
        return new Placed(helper, player, facing);
    }

    private static HoldVerdict request(Placed refinery, Identifier recipe) {
        FakeBuilder player = refinery.player();
        // Not openMenu: a fake player's opens nothing, so the menu the block entity makes is set by hand.
        FluidMachineMenu menu = (FluidMachineMenu) refinery.machine().createMenu(1, player.getInventory(), player);
        player.containerMenu = menu;
        return menu.request(player, recipe);
    }

    /** Asks once, since each refusal tells the player, and fails unless the answer is {@code expected}. */
    private static void expect(Placed refinery, Identifier recipe, HoldVerdict expected) {
        HoldVerdict verdict = request(refinery, recipe);
        refinery.helper().assertTrue(verdict == expected, recipe + " was " + verdict + ", not " + expected);
    }

    private static void hold(Placed refinery, Identifier recipe) {
        HoldVerdict verdict = request(refinery, recipe);
        refinery.helper().assertTrue(verdict == HoldVerdict.HELD, "Fill Recipe on " + recipe + " was " + verdict);
    }

    private static TestTank.Entity tank(GameTestHelper helper, Connection at, Fluid fluid, int amount, TestTank.Mode mode) {
        helper.setBlock(at.beyond(), TestTank.BLOCK.get().defaultBlockState());
        TestTank.Entity tank = helper.getBlockEntity(at.beyond(), TestTank.Entity.class);
        tank.fill(fluid, amount);
        tank.mode = mode;
        return tank;
    }

    private static TestTank.Entity source(GameTestHelper helper, Connection at, Fluid fluid, int amount) {
        return tank(helper, at, fluid, amount, TestTank.Mode.SOURCE);
    }

    private static TestTank.Entity drain(GameTestHelper helper, Connection at) {
        return tank(helper, at, Fluids.EMPTY, 0, TestTank.Mode.SINK);
    }

    private static int inTank(TestTank.Entity tank) {
        return tank.tank.getAmountAsInt(0);
    }

    private static FluidStack box(Placed refinery, int box) {
        return refinery.machine().fluids().contents(box);
    }

    private static int amount(Placed refinery, int box) {
        return box(refinery, box).getAmount();
    }

    /** One tick, powered: the supply tops the buffer up first, as a source of power would. */
    private static void tick(Placed refinery, SimpleEnergyHandler supply) {
        EnergyHandlerUtil.move(supply, refinery.energy(Direction.UP), 1000, null);
        refinery.machine().serverTick(refinery.helper().getLevel());
    }

    /** What the fluid capability answers on every side of every footprint block, as "offset from the origin, side". */
    private static Set<String> exposed(GameTestHelper helper, Direction facing) {
        Set<String> found = new TreeSet<>();
        BlockPos origin = helper.absolutePos(ORIGIN);
        for (BlockPos pos : footprint(helper, facing)) {
            for (Direction side : Direction.values()) {
                if (helper.getLevel().getCapability(Capabilities.Fluid.BLOCK, pos, side) != null) {
                    found.add(pos.subtract(origin).toShortString() + " " + side);
                }
            }
        }
        return found;
    }

    private static Set<String> faces(Direction facing) {
        Set<String> faces = new TreeSet<>();
        for (Connection connection : connections(facing)) {
            faces.add(connection.block().subtract(ORIGIN).toShortString() + " " + connection.side());
        }
        return faces;
    }

    // -- the craft ------------------------------------------------------------------------------

    /**
     * Water at one connection, lava at another and a drain at each of the other three: the refinery fills its two
     * input boxes by the recipe's order, crafts in the recipe's 20 ticks at speed 1, and pushes its three results
     * out through the connections.
     */
    private static void craftsThroughItsConnections(GameTestHelper helper) {
        Placed refinery = place(helper, Direction.WEST);
        List<Connection> at = refinery.connections();
        TestTank.Entity water = source(helper, at.get(0), Fluids.WATER, 5000);
        TestTank.Entity lava = source(helper, at.get(1), Fluids.LAVA, 5000);
        List<TestTank.Entity> sinks = List.of(drain(helper, at.get(2)), drain(helper, at.get(3)), drain(helper, at.get(4)));
        hold(refinery, FULL);

        SimpleEnergyHandler supply = AssemblerMachineTests.supply();
        int ran = 0;
        while (sinks.stream().mapToInt(OilRefineryTests::inTank).sum() == 0 && ran < 100) {
            tick(refinery, supply);
            ran++;
        }
        helper.assertTrue(ran == 20, "it finished its craft after " + ran + " ticks, not 20");
        // The first tick filled each input box to 4 crafts' worth, 400 mB each, and the craft took 100.
        helper.assertTrue(box(refinery, 0).getFluid() == Fluids.WATER && amount(refinery, 0) == 300, "input box 1 holds " + box(refinery, 0));
        helper.assertTrue(box(refinery, 1).getFluid() == Fluids.LAVA && amount(refinery, 1) == 300, "input box 2 holds " + box(refinery, 1));
        helper.assertTrue(inTank(water) == 4600 && inTank(lava) == 4600,
                "the tanks hold " + inTank(water) + " and " + inTank(lava) + " mB, not 4600 and 4600");
        // The results went out as they were made: 30 mB of lava and 100 of water, whichever drain took each.
        int lavaOut = 0;
        int waterOut = 0;
        for (TestTank.Entity sink : sinks) {
            Fluid held = sink.tank.getResource(0).getFluid();
            if (held == Fluids.LAVA) {
                lavaOut += inTank(sink);
            } else if (held == Fluids.WATER) {
                waterOut += inTank(sink);
            }
        }
        helper.assertTrue(lavaOut == 30 && waterOut == 100, "the drains took " + lavaOut + " mB of lava and " + waterOut + " of water, not 30 and 100");
        for (int box = 2; box < 5; box++) {
            helper.assertTrue(amount(refinery, box) == 0, "output box " + (box - 1) + " still holds " + box(refinery, box));
        }
        helper.succeed();
    }

    /**
     * With no way out, each output box in turn at its volume stalls the craft: no energy drawn, no progress made,
     * no input taken, the state Output full. Given a drain, the box empties and the craft goes on.
     */
    private static void stallsOnEachFullBox(GameTestHelper helper) {
        for (int out = 0; out < 3; out++) {
            Placed refinery = place(helper, Direction.WEST);
            List<Connection> at = refinery.connections();
            source(helper, at.get(0), Fluids.WATER, 8000);
            source(helper, at.get(1), Fluids.LAVA, 8000);
            hold(refinery, FULL);
            int box = refinery.machine().description().outputBox(out);
            helper.assertTrue(refinery.machine().fluids().capacity(box) == OUTPUT_VOLUME[out],
                    "output box " + (out + 1) + " holds " + refinery.machine().fluids().capacity(box) + " mB, not " + OUTPUT_VOLUME[out]);
            refinery.machine().fluids().set(box, new FluidStack(OUTPUT_FLUID[out], OUTPUT_VOLUME[out]));

            SimpleEnergyHandler supply = AssemblerMachineTests.supply();
            tick(refinery, supply);
            int energy = refinery.machine().energy();
            int water = amount(refinery, 0);
            int lava = amount(refinery, 1);
            for (int tick = 0; tick < 30; tick++) {
                refinery.machine().serverTick(helper.getLevel());
            }
            helper.assertTrue(refinery.machine().state() == MachineState.OUTPUT_FULL,
                    "a refinery with output box " + (out + 1) + " full reported " + refinery.machine().state());
            helper.assertTrue(refinery.machine().energy() == energy && refinery.machine().craftProgress() == 0
                            && amount(refinery, 0) == water && amount(refinery, 1) == lava && amount(refinery, box) == OUTPUT_VOLUME[out],
                    "a refinery with output box " + (out + 1) + " full went on: progress " + refinery.machine().craftProgress());

            // A drain at a connection: the box empties through it, and the craft goes on.
            drain(helper, at.get(3));
            for (int ran = 0; ran < 60 && refinery.machine().craftProgress() == 0; ran++) {
                tick(refinery, supply);
            }
            helper.assertTrue(refinery.machine().craftProgress() > 0, "output box " + (out + 1) + " drained and the craft still did not go on");
            helper.destroyBlock(ORIGIN);
            helper.setBlock(at.get(0).beyond(), net.minecraft.world.level.block.Blocks.AIR);
            helper.setBlock(at.get(1).beyond(), net.minecraft.world.level.block.Blocks.AIR);
            helper.setBlock(at.get(3).beyond(), net.minecraft.world.level.block.Blocks.AIR);
        }
        helper.succeed();
    }

    // -- Fill Recipe ----------------------------------------------------------------------------

    private static void refusesWhatItCannotHold(GameTestHelper helper) {
        Placed refinery = place(helper, Direction.WEST);
        expect(refinery, STICK, HoldVerdict.WRONG_CATEGORY);
        expect(refinery, CHEMISTRY, HoldVerdict.WRONG_CATEGORY);
        // The refinery's own recipe is an Assembling recipe, in crafting.
        expect(refinery, ITS_OWN_RECIPE, HoldVerdict.WRONG_CATEGORY);
        expect(refinery, WITH_ITEM, HoldVerdict.HAS_ITEMS);
        expect(refinery, ITEM_RESULT, HoldVerdict.HAS_ITEMS);
        helper.assertTrue(refinery.machine().heldRecipe().isEmpty(), "a refused recipe was held");
        helper.assertTrue(refinery.player().heard.equals(List.of(
                "craftworks.oil_refinery.refused.wrong_category", "craftworks.oil_refinery.refused.wrong_category",
                "craftworks.oil_refinery.refused.wrong_category", "craftworks.oil_refinery.refused.has_items",
                "craftworks.oil_refinery.refused.has_items")), "the player was told " + refinery.player().heard);
        expect(refinery, FULL, HoldVerdict.HELD);
        helper.assertTrue(refinery.machine().heldRecipe().equals(Optional.of(FULL)), "the oil-processing recipe was not held");
        helper.succeed();
    }

    private static void refusesWhatTheBoxesCannotHold(GameTestHelper helper) {
        Placed refinery = place(helper, Direction.WEST);
        expect(refinery, THREE_FLUIDS, HoldVerdict.TOO_MANY_FLUIDS);
        expect(refinery, FOUR_RESULTS, HoldVerdict.TOO_MANY_FLUIDS);
        expect(refinery, BIG_FLUID, HoldVerdict.FLUID_TOO_LARGE);
        helper.assertTrue(refinery.machine().heldRecipe().isEmpty(), "a refused recipe was held");
        helper.assertTrue(refinery.player().heard.equals(List.of("craftworks.oil_refinery.refused.too_many_fluids",
                "craftworks.oil_refinery.refused.too_many_fluids", "craftworks.oil_refinery.refused.fluid_too_large")),
                "the player was told " + refinery.player().heard);
        // Two fluids in and three out at exactly the boxes' size are taken.
        expect(refinery, FULL_BOXES, HoldVerdict.HELD);
        helper.succeed();
    }

    // -- the footprint --------------------------------------------------------------------------

    private static void standsWholeAndBreaksAsOne(GameTestHelper helper, Direction facing) {
        Placed refinery = place(helper, facing);
        helper.assertTrue(helper.getBlockState(ORIGIN).getBlock() == OilRefineries.BLOCK.get(), "the origin is " + helper.getBlockState(ORIGIN));
        helper.assertTrue(helper.getBlockState(ORIGIN).getValue(OilRefineryBlock.FACING) == facing, "the origin faces " + helper.getBlockState(ORIGIN));
        // The far top corner, a part and not the origin: the whole footprint goes with it.
        BlockPos corner = ORIGIN.above(2).relative(facing, 2).relative(facing.getClockWise(), 2);
        refinery.player().gameMode.destroyBlock(helper.absolutePos(corner));
        for (BlockPos pos : footprint(helper, facing)) {
            helper.assertTrue(helper.getLevel().getBlockState(pos).isAir(), "the break left " + helper.getLevel().getBlockState(pos) + " at " + pos);
        }
        List<ItemStack> drops = helper.getEntities(EntityType.ITEM).stream().map(ItemEntity::getItem).toList();
        helper.assertTrue(drops.stream().filter(stack -> stack.is(OilRefineries.ITEM.get())).mapToInt(ItemStack::getCount).sum() == 1,
                "the break dropped " + drops + ", not one Oil Refinery");
        helper.succeed();
    }

    private static void itemKeepsTheHeldRecipe(GameTestHelper helper) {
        Placed refinery = place(helper, Direction.WEST);
        hold(refinery, FULL);
        refinery.player().gameMode.destroyBlock(helper.absolutePos(ORIGIN));
        List<ItemStack> drops = helper.getEntities(EntityType.ITEM).stream().map(ItemEntity::getItem).toList();
        ItemStack dropped = drops.stream().filter(stack -> stack.is(OilRefineries.ITEM.get())).findFirst().orElse(ItemStack.EMPTY);
        helper.assertTrue(!dropped.isEmpty(), "the break dropped " + drops);
        Placed again = placeItem(helper, dropped, Direction.EAST);
        helper.assertTrue(again.machine().heldRecipe().equals(Optional.of(FULL)), "the placed item holds " + again.machine().heldRecipe());
        helper.succeed();
    }

    // -- the connections ------------------------------------------------------------------------

    private static void connectionsFollowFacing(GameTestHelper helper, Direction facing) {
        Placed refinery = place(helper, facing);
        helper.assertTrue(exposed(helper, facing).isEmpty(), "a refinery with no recipe answered a fluid lookup at " + exposed(helper, facing));
        hold(refinery, FULL);
        Set<String> expected = faces(facing);
        helper.assertTrue(expected.size() == 5, "a refinery has five connections, not " + expected);
        helper.assertTrue(exposed(helper, facing).equals(expected),
                "facing " + facing + " the fluid capability answered at " + exposed(helper, facing) + ", not at " + expected);
        // On the bottom layer, on the two opposite edges: two of them on the one the refinery faces.
        for (Connection connection : connections(facing)) {
            helper.assertTrue(connection.block().getY() == ORIGIN.getY(), "a connection is above the bottom layer: " + connection);
        }
        helper.assertTrue(connections(facing).stream().filter(connection -> connection.side() == facing).count() == 2,
                "two connections face the way the refinery does");
        helper.assertTrue(helper.getBlockState(ORIGIN).getValue(OilRefineryBlock.FLUID_CONNECTIONS), "the origin does not show its rings");
        helper.succeed();
    }

    private static void anyConnectionDoes(GameTestHelper helper) {
        for (int index = 0; index < 5; index++) {
            Placed refinery = place(helper, Direction.WEST);
            Connection connection = refinery.connections().get(index);
            hold(refinery, FULL);
            refinery.machine().fluids().set(2, new FluidStack(Fluids.LAVA, 20));
            TestTank.Entity water = source(helper, connection, Fluids.WATER, 5000);
            refinery.machine().serverTick(helper.getLevel());
            helper.assertTrue(amount(refinery, 0) == 400 && inTank(water) == 4600,
                    "the connection at " + connection + " alone left " + amount(refinery, 0) + " mB in the box and " + inTank(water) + " in the tank");
            // The same face, with a drain in the source's place, takes the output.
            TestTank.Entity sink = drain(helper, connection);
            refinery.machine().serverTick(helper.getLevel());
            helper.assertTrue(inTank(sink) == 20 && amount(refinery, 2) == 0,
                    "the connection at " + connection + " alone drained " + inTank(sink) + " mB, leaving " + amount(refinery, 2));
            helper.setBlock(connection.beyond(), net.minecraft.world.level.block.Blocks.AIR);
            helper.destroyBlock(ORIGIN);
        }
        helper.succeed();
    }

    private static void hasNoItemFace(GameTestHelper helper) {
        Placed refinery = place(helper, Direction.WEST);
        hold(refinery, FULL);
        for (BlockPos pos : footprint(helper, Direction.WEST)) {
            for (Direction side : Direction.values()) {
                ResourceHandler<?> items = helper.getLevel().getCapability(Capabilities.Item.BLOCK, pos, side);
                helper.assertTrue(items == null, "an item capability at " + pos + " " + side);
            }
        }
        helper.assertTrue(refinery.machine().inventory().size() == 0, "the refinery has " + refinery.machine().inventory().size() + " item slots");
        helper.succeed();
    }

    private static void configDefaults(GameTestHelper helper) {
        helper.assertTrue(CraftworksConfig.speed(OilRefineryDefaults.INSTANCE) == 1.0, "speed is " + CraftworksConfig.speed(OilRefineryDefaults.INSTANCE));
        helper.assertTrue(CraftworksConfig.power(OilRefineryDefaults.INSTANCE) == 210.0, "power is " + CraftworksConfig.power(OilRefineryDefaults.INSTANCE));
        helper.assertTrue(CraftworksConfig.buffer(OilRefineryDefaults.INSTANCE) == 50_000, "buffer is " + CraftworksConfig.buffer(OilRefineryDefaults.INSTANCE));
        helper.assertTrue(CraftworksConfig.categories(OilRefineryDefaults.INSTANCE).equals(List.of(AssemblingCategory.OIL_PROCESSING)),
                "categories are " + CraftworksConfig.categories(OilRefineryDefaults.INSTANCE));
        helper.succeed();
    }
}
