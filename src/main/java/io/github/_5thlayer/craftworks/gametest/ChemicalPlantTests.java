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
import io.github._5thlayer.craftworks.machine.Assemblers;
import io.github._5thlayer.craftworks.machine.MachineState;
import io.github._5thlayer.craftworks.machine.ChemicalPlantBlock;
import io.github._5thlayer.craftworks.machine.ChemicalPlantBlockEntity;
import io.github._5thlayer.craftworks.machine.ChemicalPlantFluids;
import io.github._5thlayer.craftworks.machine.ChemicalPlantMenu;
import io.github._5thlayer.craftworks.machine.ChemicalPlantDefaults;
import io.github._5thlayer.craftworks.machine.ChemicalPlantSlots;
import io.github._5thlayer.craftworks.machine.ChemicalPlants;
import io.github._5thlayer.craftworks.machine.HoldVerdict;
import io.github._5thlayer.craftworks.recipe.AssemblingCategory;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.network.payload.AdvancedContainerSetDataPayload;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandlerUtil;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * The Chemical Plant (#26) on a real player and a real server: placed whole from its item, set with Fill
 * Recipe on the open screen, and crafting a chemistry recipe over vanilla water and lava through its four
 * Fluid Connections. The neighbours are {@link TestTank}s, sources that only give and drains that only take,
 * and the recipes are the game tests' own.
 */
final class ChemicalPlantTests {

    /** Iron and 200 mB of water and 100 mB of lava make a gold ingot and 600 mB of water, in 20 ticks. */
    private static final Identifier FULL = id("gametest/plant_full");
    /** 100 mB of water make 400 mB of water and 100 mB of lava: two output boxes, each bound to a result. */
    private static final Identifier TWO_OUTPUTS = id("gametest/plant_two_outputs");
    /** 1,000 mB of each of two fluids make 1,000 mB of each of two others. */
    private static final Identifier FULL_BOXES = id("gametest/plant_full_boxes");
    private static final Identifier THREE_FLUIDS = id("gametest/plant_three_fluids");
    private static final Identifier THREE_RESULTS = id("gametest/plant_three_results");
    private static final Identifier BIG_FLUID = id("gametest/plant_big_fluid");
    private static final Identifier THREE_ITEMS = id("gametest/plant_three_items");
    private static final Identifier TWO_ITEM_RESULTS = id("gametest/plant_two_item_results");
    private static final Identifier REMAINDER = id("gametest/plant_remainder");
    private static final Identifier STICK = Identifier.parse("minecraft:stick");
    private static final Identifier WATER_CRAFT = AssemblerFluidTests.WATER_CRAFT;

    private static final BlockPos ORIGIN = AssemblerMachineTests.ORIGIN;
    private static final List<Direction> FACINGS = List.of(Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST);

    private ChemicalPlantTests() {
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(Craftworks.MOD_ID, path);
    }

    static void register(CraftworksGameTests.Registrar tests) {
        tests.test("a_chemical_plant_crafts_two_fluids_and_an_item_into_an_item_and_a_fluid_fed_and_drained_through_its_fluid_connections",
                40, ChemicalPlantTests::craftsThroughItsConnections);
        tests.test("a_chemical_plant_stalls_with_an_output_box_full_and_goes_on_once_it_is_drained", 200, ChemicalPlantTests::stallsOnAFullBox);
        tests.test("a_chemical_plant_waits_while_the_product_slot_is_full", 20, ChemicalPlantTests::waitsOnAFullProduct);
        tests.test("fill_recipe_on_a_chemical_plant_refuses_a_recipe_outside_chemistry", 20, ChemicalPlantTests::refusesOtherCategories);
        for (Direction facing : FACINGS) {
            tests.test("a_chemical_plant_facing_" + facing.getName() + "_stands_whole_on_a_3x3x2_footprint_and_breaks_as_one", 20,
                    helper -> standsWholeAndBreaksAsOne(helper, facing));
            tests.test("the_fluid_connections_of_a_chemical_plant_facing_" + facing.getName() + "_stand_two_to_an_edge_on_the_two_opposite_bottom_edges", 20,
                    helper -> connectionsFollowFacing(helper, facing));
        }
        tests.test("the_item_keeps_the_held_recipe_and_places_it_back", 20, ChemicalPlantTests::itemKeepsTheHeldRecipe);
        tests.test("breaking_a_chemical_plant_drops_its_items_and_voids_its_fluid_and_energy", 20, ChemicalPlantTests::breakingDropsItems);
        tests.test("any_one_of_the_four_connections_alone_feeds_and_drains_a_chemical_plant", 40, ChemicalPlantTests::anyConnectionDoes);
        tests.test("a_chemical_plant_pulls_each_fluid_into_the_input_box_its_order_names_and_nothing_else", 20, ChemicalPlantTests::pullsByOrder);
        tests.test("a_neighbour_that_no_connection_faces_is_neither_pulled_from_nor_pushed_to", 20, ChemicalPlantTests::onlyConnectionsAreUsed);
        tests.test("the_connections_come_and_go_with_a_fluid_in_the_held_recipe_and_a_pipe_that_asked_is_told", 20, ChemicalPlantTests::connectionsComeAndGo);
        tests.test("a_connection_fills_an_input_box_and_drains_an_output_box_and_does_neither_the_other_way", 20, ChemicalPlantTests::connectionFaces);
        tests.test("an_input_box_holds_1000_mb_and_an_output_box_the_larger_of_1000_and_the_overload_limits_crafts_of_its_result", 20, ChemicalPlantTests::boxSizes);
        tests.test("changing_the_held_recipe_voids_the_four_boxes", 20, ChemicalPlantTests::voidsOnChange);
        tests.test("fill_recipe_refuses_what_the_boxes_and_slots_of_a_chemical_plant_cannot_hold", 20, ChemicalPlantTests::refusals);
        tests.test("the_item_capability_takes_the_held_recipes_two_ingredients_to_the_overload_limit_and_gives_only_the_product", 20, ChemicalPlantTests::itemFaces);
        tests.test("a_chemical_plant_draws_its_price_once_over_the_ticks_of_its_craft", 40, ChemicalPlantTests::spendsItsPrice);
        tests.test("the_held_recipe_progress_and_all_four_boxes_survive_a_save_and_reload", 20, ChemicalPlantTests::survivesReload);
        tests.test("the_open_chemical_plants_boxes_and_held_recipe_cross_to_the_client", 20, ChemicalPlantTests::crossesToTheClient);
        tests.test("the_chemical_plant_starts_from_speed_1_105_fe_a_tick_a_50000_fe_buffer_and_the_chemistry_category", 20, ChemicalPlantTests::configDefaults);
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
     * The four connections of a plant facing {@code facing}: on the bottom layer, at either end of the edge that
     * faces the way it does and of the one that faces away. The first two are on the facing side.
     */
    private static List<Connection> connections(Direction facing) {
        List<Connection> found = new ArrayList<>();
        for (Direction side : List.of(facing, facing.getOpposite())) {
            for (Direction along : List.of(facing.getClockWise(), facing.getCounterClockWise())) {
                found.add(new Connection(ORIGIN.relative(side).relative(along), side));
            }
        }
        return found;
    }

    /** A Chemical Plant placed in a test, with the player who placed it and the way it faces. */
    record Placed(GameTestHelper helper, FakeBuilder player, Direction facing) {

        ChemicalPlantBlockEntity machine() {
            return helper.getBlockEntity(ORIGIN, ChemicalPlantBlockEntity.class);
        }

        EnergyHandler energy(Direction side) {
            return helper.getLevel().getCapability(Capabilities.Energy.BLOCK, corner(), side);
        }

        ResourceHandler<ItemResource> items(Direction side) {
            return helper.getLevel().getCapability(Capabilities.Item.BLOCK, corner(), side);
        }

        ResourceHandler<FluidResource> fluid(Connection at) {
            return helper.getLevel().getCapability(Capabilities.Fluid.BLOCK, helper.absolutePos(at.block()), at.side());
        }

        List<Connection> connections() {
            return ChemicalPlantTests.connections(facing);
        }

        private BlockPos corner() {
            List<BlockPos> blocks = footprint(helper, facing);
            return blocks.get(blocks.size() - 1);
        }
    }

    private static List<BlockPos> footprint(GameTestHelper helper, Direction facing) {
        return ChemicalPlants.footprint().positions(helper.absolutePos(ORIGIN), facing);
    }

    static Placed place(GameTestHelper helper) {
        return place(helper, Direction.WEST);
    }

    /** Placed facing this way: a footprint faces opposite the player who lays it. */
    static Placed place(GameTestHelper helper, Direction facing) {
        return placeItem(helper, new ItemStack(ChemicalPlants.ITEM.get()), facing);
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
        var footprint = ChemicalPlants.footprint();
        List<BlockPos> positions = footprint(helper, facing);
        helper.assertTrue(positions.size() == 18, "a 3x2x3 footprint has " + positions.size() + " blocks");
        for (int i = 0; i < positions.size(); i++) {
            helper.assertTrue(helper.getLevel().getBlockState(positions.get(i)).equals(footprint.stateAt(i, facing)),
                    "block " + i + " at " + positions.get(i) + " is " + helper.getLevel().getBlockState(positions.get(i)));
        }
        return new Placed(helper, player, facing);
    }

    private static HoldVerdict request(Placed plant, Identifier recipe) {
        FakeBuilder player = plant.player();
        // Not openMenu: a fake player's opens nothing, so the menu the block entity makes is set by hand.
        ChemicalPlantMenu menu = (ChemicalPlantMenu) plant.machine().createMenu(1, player.getInventory(), player);
        player.containerMenu = menu;
        return menu.request(player, recipe);
    }

    private static void hold(Placed plant, Identifier recipe) {
        HoldVerdict verdict = request(plant, recipe);
        plant.helper().assertTrue(verdict == HoldVerdict.HELD, "Fill Recipe on " + recipe + " was " + verdict);
    }

    /** Puts items in through the item capability, the way a pipe would; how many went in. */
    private static int insert(Placed plant, int slot, Item item, int amount) {
        try (Transaction tx = Transaction.openRoot()) {
            int inserted = plant.items(Direction.UP).insert(slot, ItemResource.of(item), amount, tx);
            tx.commit();
            return inserted;
        }
    }

    private static int count(Placed plant, int slot) {
        return plant.machine().inventory().getAmountAsInt(slot);
    }

    private static TestTank.Entity tank(GameTestHelper helper, BlockPos at, Fluid fluid, int amount, TestTank.Mode mode) {
        helper.setBlock(at, TestTank.BLOCK.get().defaultBlockState());
        TestTank.Entity tank = helper.getBlockEntity(at, TestTank.Entity.class);
        tank.fill(fluid, amount);
        tank.mode = mode;
        return tank;
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

    private static FluidStack box(Placed plant, int box) {
        return plant.machine().fluids().contents(box);
    }

    private static int amount(Placed plant, int box) {
        return box(plant, box).getAmount();
    }

    /** One tick, powered: the supply tops the buffer up first, as a source of power would. */
    private static void tick(Placed plant, SimpleEnergyHandler supply) {
        EnergyHandlerUtil.move(supply, plant.energy(Direction.UP), 1000, null);
        plant.machine().serverTick(plant.helper().getLevel());
    }

    private static void tick(Placed plant, int ticks) {
        SimpleEnergyHandler supply = AssemblerMachineTests.supply();
        for (int tick = 0; tick < ticks; tick++) {
            tick(plant, supply);
        }
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

    private static int insertFluid(ResourceHandler<FluidResource> handler, Fluid fluid, int amount) {
        try (Transaction tx = Transaction.openRoot()) {
            int inserted = handler.insert(FluidResource.of(fluid), amount, tx);
            tx.commit();
            return inserted;
        }
    }

    private static int extractFluid(ResourceHandler<FluidResource> handler, Fluid fluid, int amount) {
        try (Transaction tx = Transaction.openRoot()) {
            int extracted = handler.extract(FluidResource.of(fluid), amount, tx);
            tx.commit();
            return extracted;
        }
    }

    // -- the craft ------------------------------------------------------------------------------

    /**
     * Water at one connection, lava at another and a drain at a third: the plant fills its two input boxes by
     * the recipe's order, crafts in the recipe's 20 ticks at speed 1, and pushes the 600 mB of water it made
     * out through a connection of its own.
     */
    private static void craftsThroughItsConnections(GameTestHelper helper) {
        Placed plant = place(helper);
        List<Connection> at = plant.connections();
        TestTank.Entity water = source(helper, at.get(0), Fluids.WATER, 5000);
        TestTank.Entity lava = source(helper, at.get(3), Fluids.LAVA, 5000);
        TestTank.Entity sink = drain(helper, at.get(1));
        hold(plant, FULL);
        helper.assertTrue(insert(plant, 0, Items.IRON_INGOT, 3) == 3, "the iron did not go in");

        SimpleEnergyHandler supply = AssemblerMachineTests.supply();
        int ran = 0;
        while (count(plant, ChemicalPlantSlots.PRODUCT) == 0 && ran < 100) {
            tick(plant, supply);
            ran++;
        }
        helper.assertTrue(ran == 20, "it finished its craft after " + ran + " ticks, not 20");
        helper.assertTrue(plant.machine().inventory().getResource(ChemicalPlantSlots.PRODUCT).getItem() == Items.GOLD_INGOT,
                "the product was not a gold ingot");
        helper.assertTrue(count(plant, 0) == 2, "the craft left " + count(plant, 0) + " iron, not 2");
        // The first tick filled each input box from its tank, and the craft took 200 and 100.
        helper.assertTrue(box(plant, 0).getFluid() == Fluids.WATER && amount(plant, 0) == 800, "input box 1 holds " + box(plant, 0));
        helper.assertTrue(box(plant, 1).getFluid() == Fluids.LAVA && amount(plant, 1) == 900, "input box 2 holds " + box(plant, 1));
        helper.assertTrue(inTank(water) == 4000 && inTank(lava) == 4000,
                "the tanks hold " + inTank(water) + " and " + inTank(lava) + " mB, not 4000 each");
        // The result went out through the connections, as it was made.
        helper.assertTrue(inTank(sink) == 600 && sink.tank.getResource(0).getFluid() == Fluids.WATER, "the drain holds " + sink.tank.getResource(0) + " x " + inTank(sink));
        helper.assertTrue(amount(plant, 2) == 0 && amount(plant, 3) == 0, "the output boxes still hold " + amount(plant, 2) + " and " + amount(plant, 3));
        helper.succeed();
    }

    /**
     * With no way out, the output box fills at 1,800 mB (three crafts of 600), and the fourth craft waits: no
     * energy drawn, no progress made, no input taken. Given a drain, the box empties and the craft goes on.
     */
    private static void stallsOnAFullBox(GameTestHelper helper) {
        Placed plant = place(helper);
        List<Connection> at = plant.connections();
        TestTank.Entity water = source(helper, at.get(0), Fluids.WATER, 8000);
        source(helper, at.get(3), Fluids.LAVA, 8000);
        hold(plant, FULL);
        SimpleEnergyHandler supply = AssemblerMachineTests.supply();
        for (int ran = 0; ran < 150; ran++) {
            if (count(plant, 0) == 0) {
                insert(plant, 0, Items.IRON_INGOT, 1);
            }
            tick(plant, supply);
        }
        helper.assertTrue(count(plant, ChemicalPlantSlots.PRODUCT) == 3, "it made " + count(plant, ChemicalPlantSlots.PRODUCT) + " crafts, not the 3 the box holds");
        helper.assertTrue(amount(plant, 2) == 1800, "the output box holds " + amount(plant, 2) + " mB, not its 1800");
        helper.assertTrue(plant.machine().state() == MachineState.OUTPUT_FULL, "a plant with a full output box reported " + plant.machine().state());

        // Stalled, it draws nothing, makes no progress and keeps its inputs.
        int energy = plant.machine().energy();
        int progress = plant.machine().craftProgress();
        int iron = count(plant, 0);
        int inWater = amount(plant, 0);
        for (int tick = 0; tick < 10; tick++) {
            plant.machine().serverTick(helper.getLevel());
        }
        helper.assertTrue(plant.machine().energy() == energy, "a stalled plant drew " + (energy - plant.machine().energy()) + " FE");
        helper.assertTrue(plant.machine().craftProgress() == progress && count(plant, 0) == iron && amount(plant, 0) == inWater,
                "a stalled plant moved: progress " + plant.machine().craftProgress() + ", iron " + count(plant, 0) + ", water " + amount(plant, 0));

        // A drain at a connection: the box empties through it, and the craft goes on.
        TestTank.Entity sink = drain(helper, at.get(2));
        for (int ran = 0; ran < 60 && count(plant, ChemicalPlantSlots.PRODUCT) < 4; ran++) {
            if (count(plant, 0) == 0) {
                insert(plant, 0, Items.IRON_INGOT, 1);
            }
            tick(plant, supply);
        }
        helper.assertTrue(count(plant, ChemicalPlantSlots.PRODUCT) == 4, "it made " + count(plant, ChemicalPlantSlots.PRODUCT) + " crafts, not 4, once drained");
        helper.assertTrue(inTank(sink) >= 1800, "the drain took " + inTank(sink) + " mB");
        helper.assertTrue(inTank(water) < 8000, "no water was pulled");
        helper.succeed();
    }

    private static void waitsOnAFullProduct(GameTestHelper helper) {
        Placed plant = place(helper);
        List<Connection> at = plant.connections();
        source(helper, at.get(0), Fluids.WATER, 5000);
        source(helper, at.get(3), Fluids.LAVA, 5000);
        hold(plant, FULL);
        insert(plant, 0, Items.IRON_INGOT, 1);
        plant.machine().inventory().set(ChemicalPlantSlots.PRODUCT, ItemResource.of(Items.GOLD_INGOT), 64);
        tick(plant, 30);
        helper.assertTrue(count(plant, ChemicalPlantSlots.PRODUCT) == 64 && count(plant, 0) == 1, "a craft went on with a full product slot");
        helper.assertTrue(plant.machine().state() == MachineState.OUTPUT_FULL, "a plant with a full product slot reported " + plant.machine().state());
        helper.assertTrue(amount(plant, 2) == 0, "output went into a box for a craft that never was");
        helper.succeed();
    }

    private static void refusesOtherCategories(GameTestHelper helper) {
        Placed plant = place(helper);
        HoldVerdict crafting = request(plant, STICK);
        HoldVerdict withFluid = request(plant, WATER_CRAFT);
        helper.assertTrue(crafting == HoldVerdict.WRONG_CATEGORY, "a crafting recipe was " + crafting);
        helper.assertTrue(withFluid == HoldVerdict.WRONG_CATEGORY, "a crafting-with-fluid recipe was " + withFluid);
        helper.assertTrue(plant.machine().heldRecipe().isEmpty(), "a refused recipe was held");
        helper.assertTrue(plant.player().heard.equals(List.of("craftworks.chemical_plant.refused.wrong_category",
                "craftworks.chemical_plant.refused.wrong_category")), "the player was told " + plant.player().heard);
        helper.assertTrue(request(plant, FULL) == HoldVerdict.HELD, "a chemistry recipe was refused");
        helper.assertTrue(plant.machine().heldRecipe().equals(Optional.of(FULL)), "the chemistry recipe was not held");
        helper.succeed();
    }

    private static void spendsItsPrice(GameTestHelper helper) {
        Placed plant = place(helper);
        List<Connection> at = plant.connections();
        source(helper, at.get(0), Fluids.WATER, 5000);
        source(helper, at.get(3), Fluids.LAVA, 5000);
        drain(helper, at.get(1));
        hold(plant, FULL);
        insert(plant, 0, Items.IRON_INGOT, 1);
        SimpleEnergyHandler supply = AssemblerMachineTests.supply();
        int fed = 0;
        for (int ran = 0; ran < 20; ran++) {
            fed += EnergyHandlerUtil.move(supply, plant.energy(Direction.UP), 1000, null);
            plant.machine().serverTick(helper.getLevel());
        }
        // 105 FE a tick for the 20 ticks of the craft.
        helper.assertTrue(count(plant, ChemicalPlantSlots.PRODUCT) == 1, "the craft did not finish in 20 ticks");
        helper.assertTrue(fed - plant.machine().energy() == 2100, "the craft cost " + (fed - plant.machine().energy()) + " FE, not 2100");
        helper.succeed();
    }

    // -- the footprint --------------------------------------------------------------------------

    private static void standsWholeAndBreaksAsOne(GameTestHelper helper, Direction facing) {
        Placed plant = place(helper, facing);
        helper.assertTrue(helper.getBlockState(ORIGIN).getBlock() == ChemicalPlants.BLOCK.get(), "the origin is " + helper.getBlockState(ORIGIN));
        helper.assertTrue(helper.getBlockState(ORIGIN).getValue(ChemicalPlantBlock.FACING) == facing, "the origin faces " + helper.getBlockState(ORIGIN));
        // A part, not the origin: the whole footprint goes with it.
        plant.player().gameMode.destroyBlock(helper.absolutePos(ORIGIN.above().relative(facing).relative(facing.getClockWise())));
        for (BlockPos pos : footprint(helper, facing)) {
            helper.assertTrue(helper.getLevel().getBlockState(pos).isAir(), "the break left " + helper.getLevel().getBlockState(pos) + " at " + pos);
        }
        List<ItemStack> drops = helper.getEntities(EntityType.ITEM).stream().map(ItemEntity::getItem).toList();
        helper.assertTrue(drops.stream().filter(stack -> stack.is(ChemicalPlants.ITEM.get())).mapToInt(ItemStack::getCount).sum() == 1,
                "the break dropped " + drops + ", not one Chemical Plant");
        helper.succeed();
    }

    private static void itemKeepsTheHeldRecipe(GameTestHelper helper) {
        Placed plant = place(helper);
        hold(plant, FULL);
        plant.player().gameMode.destroyBlock(helper.absolutePos(ORIGIN));
        ItemStack item = helper.getEntities(EntityType.ITEM).stream().map(ItemEntity::getItem)
                .filter(stack -> stack.is(ChemicalPlants.ITEM.get())).findFirst().orElse(ItemStack.EMPTY);
        helper.assertTrue(FULL.equals(item.get(Assemblers.HELD_RECIPE.get())), "the dropped item holds " + item.get(Assemblers.HELD_RECIPE.get()) + ", not the Held recipe");
        Placed again = placeItem(helper, item, Direction.EAST);
        helper.assertTrue(again.machine().heldRecipe().equals(Optional.of(FULL)), "the item placed again held " + again.machine().heldRecipe());
        helper.succeed();
    }

    private static void breakingDropsItems(GameTestHelper helper) {
        Placed plant = place(helper);
        hold(plant, FULL);
        insert(plant, 0, Items.IRON_INGOT, 3);
        plant.machine().inventory().set(ChemicalPlantSlots.PRODUCT, ItemResource.of(Items.GOLD_INGOT), 5);
        plant.machine().fluids().set(0, new FluidStack(Fluids.WATER, 500));
        plant.machine().fluids().set(2, new FluidStack(Fluids.WATER, 700));
        EnergyHandlerUtil.move(AssemblerMachineTests.supply(), plant.energy(Direction.UP), 1000, null);
        plant.player().gameMode.destroyBlock(helper.absolutePos(ORIGIN));
        List<ItemStack> drops = helper.getEntities(EntityType.ITEM).stream().map(ItemEntity::getItem).toList();
        helper.assertTrue(drops.stream().filter(stack -> stack.is(Items.IRON_INGOT)).mapToInt(ItemStack::getCount).sum() == 3, "the break dropped " + drops + ", not the 3 iron");
        helper.assertTrue(drops.stream().filter(stack -> stack.is(Items.GOLD_INGOT)).mapToInt(ItemStack::getCount).sum() == 5, "the break dropped " + drops + ", not the 5 gold");
        Placed again = placeItem(helper, drops.stream().filter(stack -> stack.is(ChemicalPlants.ITEM.get())).findFirst().orElseThrow(), Direction.WEST);
        helper.assertTrue(again.machine().energy() == 0 && amount(again, 0) == 0 && amount(again, 2) == 0, "the energy or the fluid survived the break");
        helper.succeed();
    }

    // -- the connections ------------------------------------------------------------------------

    private static void connectionsFollowFacing(GameTestHelper helper, Direction facing) {
        Placed plant = place(helper, facing);
        helper.assertTrue(exposed(helper, facing).isEmpty(), "a plant with no recipe answered a fluid lookup at " + exposed(helper, facing));
        hold(plant, FULL);
        Set<String> expected = faces(facing);
        helper.assertTrue(expected.size() == 4, "a plant has four connections, not " + expected);
        helper.assertTrue(exposed(helper, facing).equals(expected),
                "facing " + facing + " the fluid capability answered at " + exposed(helper, facing) + ", not at " + expected);
        // On the bottom layer, off the origin's own column, on the two edges that face its two ways.
        for (Connection connection : connections(facing)) {
            helper.assertTrue(connection.block().getY() == ORIGIN.getY(), "a connection is above the bottom layer: " + connection);
            helper.assertTrue(!connection.block().equals(ORIGIN.relative(connection.side())), "a connection stands at an edge's centre: " + connection);
        }
        helper.assertTrue(helper.getBlockState(ORIGIN).getValue(ChemicalPlantBlock.FLUID_CONNECTIONS), "the origin does not show its rings");
        helper.succeed();
    }

    private static void anyConnectionDoes(GameTestHelper helper) {
        for (int index = 0; index < 4; index++) {
            Placed plant = place(helper);
            Connection connection = plant.connections().get(index);
            hold(plant, FULL);
            plant.machine().fluids().set(2, new FluidStack(Fluids.WATER, 300));
            TestTank.Entity water = source(helper, connection, Fluids.WATER, 5000);
            tick(plant, 1);
            helper.assertTrue(amount(plant, 0) == 1000 && inTank(water) == 4000,
                    "the connection at " + connection + " alone left " + amount(plant, 0) + " mB in the box and " + inTank(water) + " in the tank");
            // The same face, with a drain in the source's place, takes the output.
            TestTank.Entity sink = drain(helper, connection);
            tick(plant, 1);
            helper.assertTrue(inTank(sink) == 300 && amount(plant, 2) == 0,
                    "the connection at " + connection + " alone drained " + inTank(sink) + " mB, leaving " + amount(plant, 2));
            helper.setBlock(connection.beyond(), Blocks.AIR);
            helper.destroyBlock(ORIGIN);
        }
        helper.succeed();
    }

    private static void pullsByOrder(GameTestHelper helper) {
        Placed plant = place(helper);
        List<Connection> at = plant.connections();
        hold(plant, FULL);
        // Lava first and alone: it goes to the box the recipe's second ingredient names, and water has none yet.
        TestTank.Entity lava = source(helper, at.get(2), Fluids.LAVA, 5000);
        tick(plant, 1);
        helper.assertTrue(amount(plant, 0) == 0 && box(plant, 1).getFluid() == Fluids.LAVA && amount(plant, 1) == 1000,
                "lava went to " + box(plant, 0) + " and " + box(plant, 1));
        helper.assertTrue(inTank(lava) == 4000, "the lava tank holds " + inTank(lava));
        // A fluid the recipe doesn't use is left where it is.
        helper.setBlock(at.get(2).beyond(), Blocks.AIR);
        TestTank.Entity other = source(helper, at.get(0), Fluids.FLOWING_LAVA, 5000);
        tick(plant, 1);
        helper.assertTrue(amount(plant, 0) == 0 && inTank(other) == 5000, "a fluid the recipe doesn't use was pulled: " + box(plant, 0));
        helper.setBlock(at.get(0).beyond(), Blocks.AIR);
        TestTank.Entity water = source(helper, at.get(0), Fluids.WATER, 5000);
        tick(plant, 1);
        helper.assertTrue(box(plant, 0).getFluid() == Fluids.WATER && amount(plant, 0) == 1000, "water went to " + box(plant, 0));
        helper.assertTrue(inTank(water) == 4000, "the water tank holds " + inTank(water));
        helper.succeed();
    }

    /** A connection is one face of one block: its other faces, its block's neighbours and the middle of its edge are not. */
    private static void onlyConnectionsAreUsed(GameTestHelper helper) {
        Placed plant = place(helper);
        Direction facing = plant.facing();
        hold(plant, FULL);
        plant.machine().fluids().set(2, new FluidStack(Fluids.WATER, 300));
        Connection connection = plant.connections().get(0);
        // Beside the connection block along its edge's line, beside the edge's centre, above it, and the corner block's side face.
        List<BlockPos> elsewhere = List.of(
                ORIGIN.relative(facing, 2),
                ORIGIN.relative(facing.getOpposite(), 2),
                connection.block().above(2),
                connection.block().relative(facing.getClockWise()),
                ORIGIN.relative(facing.getClockWise(), 2));
        for (BlockPos at : elsewhere) {
            if (at.equals(connection.beyond())) {
                continue;
            }
            TestTank.Entity water = tank(helper, at, Fluids.WATER, 5000, TestTank.Mode.BOTH);
            tick(plant, 2);
            helper.assertTrue(amount(plant, 0) == 0 && inTank(water) == 5000, "a tank at " + at + " was pulled from, or pushed to: " + inTank(water));
            helper.setBlock(at, Blocks.AIR);
        }
        helper.succeed();
    }

    private static void connectionsComeAndGo(GameTestHelper helper) {
        Placed plant = place(helper);
        List<Connection> at = plant.connections();
        BlockCapabilityCache<ResourceHandler<FluidResource>, Direction> pipe =
                BlockCapabilityCache.create(Capabilities.Fluid.BLOCK, helper.getLevel(), helper.absolutePos(at.get(0).block()), at.get(0).side());
        helper.assertTrue(exposed(helper, plant.facing()).isEmpty(), "with no Held recipe there were connections at " + exposed(helper, plant.facing()));
        helper.assertTrue(pipe.getCapability() == null, "the cache saw a capability before any recipe");

        hold(plant, id("gametest/plant_items_only"));
        helper.assertTrue(exposed(helper, plant.facing()).isEmpty(), "a recipe with no fluid gave connections at " + exposed(helper, plant.facing()));
        helper.assertTrue(!helper.getBlockState(ORIGIN).getValue(ChemicalPlantBlock.FLUID_CONNECTIONS), "the origin shows rings with no fluid");

        hold(plant, FULL);
        helper.assertTrue(exposed(helper, plant.facing()).size() == 4, "a fluid recipe gave connections at " + exposed(helper, plant.facing()));
        helper.assertTrue(pipe.getCapability() != null, "the cache did not see the connection appear");

        // One with a fluid only in its results still has them, to push through.
        hold(plant, id("gametest/fluid_only"));
        helper.assertTrue(exposed(helper, plant.facing()).size() == 4, "a recipe with a fluid result gave connections at " + exposed(helper, plant.facing()));

        // A held recipe the plant cannot run takes them away again, as an Assembler's go with its fluid.
        plant.machine().setHeldRecipe(STICK, plant.player());
        helper.assertTrue(exposed(helper, plant.facing()).isEmpty(), "a recipe the plant cannot run left connections at " + exposed(helper, plant.facing()));
        helper.assertTrue(pipe.getCapability() == null, "the cache did not see the connection go");
        helper.assertTrue(!helper.getBlockState(ORIGIN).getValue(ChemicalPlantBlock.FLUID_CONNECTIONS), "the origin still shows its rings");
        helper.succeed();
    }

    private static void connectionFaces(GameTestHelper helper) {
        Placed plant = place(helper);
        hold(plant, FULL);
        ResourceHandler<FluidResource> face = plant.fluid(plant.connections().get(1));
        helper.assertTrue(face != null && face.size() == 4, "the connection shows " + (face == null ? "nothing" : face.size() + " boxes"));
        // A mod that pushes fills the input box the recipe's order names, and only with the fluid it takes there.
        helper.assertTrue(insertFluid(face, Fluids.WATER, 150) == 150 && box(plant, 0).getFluid() == Fluids.WATER, "water did not reach input box 1");
        helper.assertTrue(insertFluid(face, Fluids.LAVA, 50) == 50 && box(plant, 1).getFluid() == Fluids.LAVA, "lava did not reach input box 2");
        helper.assertTrue(insertFluid(face, Fluids.WATER, 5000) == 850, "input box 1 took more than its 1000 mB");
        helper.assertTrue(insertFluid(face, Fluids.FLOWING_LAVA, 100) == 0, "a fluid the recipe doesn't use went in");
        // Nothing comes out of an input box.
        helper.assertTrue(extractFluid(face, Fluids.WATER, 100) == 0 && extractFluid(face, Fluids.LAVA, 10) == 0, "an input box gave fluid out");
        // An output box is drained and never filled from outside.
        plant.machine().fluids().set(2, new FluidStack(Fluids.WATER, 400));
        helper.assertTrue(extractFluid(face, Fluids.WATER, 150) == 150 && amount(plant, 2) == 250, "an output box gave " + (400 - amount(plant, 2)));
        plant.machine().fluids().set(0, FluidStack.EMPTY);
        helper.assertTrue(insertFluid(face, Fluids.WATER, 100) == 100 && amount(plant, 2) == 250, "water went into an output box, or did not go into the input");
        helper.succeed();
    }

    private static void boxSizes(GameTestHelper helper) {
        Placed plant = place(helper);
        ChemicalPlantFluids boxes = plant.machine().fluids();
        for (int box = 0; box < 4; box++) {
            helper.assertTrue(boxes.capacity(box) == 1000, "box " + box + " holds " + boxes.capacity(box) + " mB with no Held recipe, not 1000");
        }
        // Speed 1 and 20 ticks make the Overload Limit 3 crafts.
        hold(plant, FULL);
        helper.assertTrue(boxes.capacity(0) == 1000 && boxes.capacity(1) == 1000, "the input boxes hold " + boxes.capacity(0) + " and " + boxes.capacity(1));
        helper.assertTrue(boxes.capacity(2) == 1800, "output box 1 holds " + boxes.capacity(2) + " mB, not 3 crafts of 600");
        helper.assertTrue(boxes.capacity(3) == 1000, "output box 2 has no result bound to it and holds " + boxes.capacity(3) + " mB, not 1000");
        hold(plant, TWO_OUTPUTS);
        helper.assertTrue(boxes.capacity(2) == 1200, "output box 1 holds " + boxes.capacity(2) + " mB, not 3 crafts of 400");
        helper.assertTrue(boxes.capacity(3) == 1000, "3 crafts of 100 mB are under the box, which holds " + boxes.capacity(3));
        hold(plant, FULL_BOXES);
        helper.assertTrue(boxes.capacity(0) == 1000 && boxes.capacity(2) == 3000 && boxes.capacity(3) == 3000,
                "1000 mB a craft gave boxes of " + boxes.capacity(0) + ", " + boxes.capacity(2) + " and " + boxes.capacity(3));
        // The results bind by order, so each box takes only its own result.
        hold(plant, TWO_OUTPUTS);
        ResourceHandler<FluidResource> face = plant.fluid(plant.connections().get(0));
        helper.assertTrue(extractFluid(face, Fluids.WATER, 1) == 0, "an empty box gave water");
        boxes.set(2, new FluidStack(Fluids.WATER, 1200));
        boxes.set(3, new FluidStack(Fluids.LAVA, 700));
        helper.assertTrue(extractFluid(face, Fluids.WATER, 5000) == 1200 && extractFluid(face, Fluids.LAVA, 5000) == 700, "the outputs were not drained whole");
        helper.succeed();
    }

    private static void voidsOnChange(GameTestHelper helper) {
        Placed plant = place(helper);
        hold(plant, FULL);
        ChemicalPlantFluids boxes = plant.machine().fluids();
        boxes.set(0, new FluidStack(Fluids.WATER, 600));
        boxes.set(1, new FluidStack(Fluids.LAVA, 600));
        boxes.set(2, new FluidStack(Fluids.WATER, 600));
        // The recipe it already holds moves nothing.
        hold(plant, FULL);
        helper.assertTrue(amount(plant, 0) == 600 && amount(plant, 1) == 600 && amount(plant, 2) == 600, "Fill Recipe on the recipe already held changed the boxes");
        hold(plant, TWO_OUTPUTS);
        for (int box = 0; box < 4; box++) {
            helper.assertTrue(amount(plant, box) == 0, "box " + box + " held " + amount(plant, box) + " mB after the recipe changed");
        }
        helper.succeed();
    }

    // -- Fill Recipe ----------------------------------------------------------------------------

    private static void refusals(GameTestHelper helper) {
        Placed plant = place(helper);
        helper.assertTrue(request(plant, THREE_FLUIDS) == HoldVerdict.TOO_MANY_FLUIDS, "three fluid ingredients was " + request(plant, THREE_FLUIDS));
        helper.assertTrue(request(plant, THREE_RESULTS) == HoldVerdict.TOO_MANY_FLUIDS, "three fluid results was " + request(plant, THREE_RESULTS));
        helper.assertTrue(request(plant, BIG_FLUID) == HoldVerdict.FLUID_TOO_LARGE, "1,001 mB a craft was " + request(plant, BIG_FLUID));
        helper.assertTrue(request(plant, THREE_ITEMS) == HoldVerdict.TOO_MANY_INGREDIENTS, "three item ingredients was " + request(plant, THREE_ITEMS));
        helper.assertTrue(request(plant, TWO_ITEM_RESULTS) == HoldVerdict.REMAINDERS_DONT_FIT, "two item results was " + request(plant, TWO_ITEM_RESULTS));
        helper.assertTrue(request(plant, REMAINDER) == HoldVerdict.REMAINDERS_DONT_FIT, "an ingredient that leaves a bucket was " + request(plant, REMAINDER));
        helper.assertTrue(request(plant, id("gametest/not_a_recipe")) == HoldVerdict.NOT_ASSEMBLING, "an id naming no recipe was taken");
        helper.assertTrue(plant.machine().heldRecipe().isEmpty(), "a refused recipe was held");
        helper.assertTrue(plant.player().heard.containsAll(List.of("craftworks.chemical_plant.refused.too_many_fluids",
                "craftworks.chemical_plant.refused.fluid_too_large", "craftworks.chemical_plant.refused.too_many_ingredients",
                "craftworks.chemical_plant.refused.remainders_dont_fit")), "the player was told " + plant.player().heard);
        // Two fluids in and two out at exactly the boxes' size are taken, and so is a recipe that only makes fluid.
        helper.assertTrue(request(plant, FULL_BOXES) == HoldVerdict.HELD, "1,000 mB of two fluids in and out was refused");
        helper.assertTrue(request(plant, id("gametest/fluid_only")) == HoldVerdict.HELD, "a recipe with no item result was refused");
        helper.succeed();
    }

    private static void itemFaces(GameTestHelper helper) {
        Placed plant = place(helper);
        hold(plant, FULL);
        helper.assertTrue(insert(plant, 1, Items.IRON_INGOT, 1) == 0, "iron went into the slot the recipe leaves unused");
        helper.assertTrue(insert(plant, 0, Items.COPPER_INGOT, 1) == 0, "copper went into the iron's slot");
        helper.assertTrue(insert(plant, ChemicalPlantSlots.PRODUCT, Items.GOLD_INGOT, 1) == 0, "an insert reached the product slot");
        // The Overload Limit: 3 crafts of one iron each.
        helper.assertTrue(insert(plant, 0, Items.IRON_INGOT, 64) == 3, "the iron slot took more than the Overload Limit's 3");
        plant.machine().inventory().set(ChemicalPlantSlots.PRODUCT, ItemResource.of(Items.GOLD_INGOT), 4);
        try (Transaction tx = Transaction.openRoot()) {
            helper.assertTrue(plant.items(Direction.DOWN).extract(0, ItemResource.of(Items.IRON_INGOT), 1, tx) == 0, "an input extracted");
            helper.assertTrue(plant.items(Direction.DOWN).extract(ChemicalPlantSlots.PRODUCT, ItemResource.of(Items.GOLD_INGOT), 4, tx) == 4,
                    "the product did not extract");
        }
        helper.assertTrue(plant.items(Direction.NORTH).size() == ChemicalPlantSlots.SIZE, "the item capability has " + plant.items(Direction.NORTH).size() + " slots");
        helper.succeed();
    }

    // -- saving and the screen --------------------------------------------------------------------

    private static void survivesReload(GameTestHelper helper) {
        Placed plant = place(helper);
        List<Connection> at = plant.connections();
        source(helper, at.get(0), Fluids.WATER, 5000);
        source(helper, at.get(3), Fluids.LAVA, 5000);
        hold(plant, FULL);
        insert(plant, 0, Items.IRON_INGOT, 3);
        tick(plant, 5);
        plant.machine().fluids().set(2, new FluidStack(Fluids.WATER, 1500));
        TagValueOutput saved = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, helper.getLevel().registryAccess());
        plant.machine().saveCustomOnly(saved);

        ChemicalPlantBlockEntity loaded = new ChemicalPlantBlockEntity(helper.absolutePos(ORIGIN), helper.getBlockState(ORIGIN));
        loaded.setLevel(helper.getLevel());
        loaded.loadCustomOnly(TagValueInput.create(ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), saved.buildResult()));
        helper.assertTrue(loaded.heldRecipe().equals(Optional.of(FULL)), "the Held recipe read back as " + loaded.heldRecipe());
        helper.assertTrue(loaded.craftProgress() == plant.machine().craftProgress() && loaded.craftProgress() > 0, "the progress read back as " + loaded.craftProgress());
        helper.assertTrue(loaded.inventory().getAmountAsInt(0) == 3, "the iron read back as " + loaded.inventory().getAmountAsInt(0));
        helper.assertTrue(loaded.energy() == plant.machine().energy() && loaded.energy() > 0, "the energy read back as " + loaded.energy());
        for (int box = 0; box < 4; box++) {
            helper.assertTrue(FluidStack.matches(loaded.fluids().contents(box), plant.machine().fluids().contents(box)),
                    "box " + box + " read back as " + loaded.fluids().contents(box) + ", not " + box(plant, box));
        }
        helper.assertTrue(loaded.fluids().contents(2).getAmount() == 1500, "an output box over 1,000 mB read back as " + loaded.fluids().contents(2));
        helper.succeed();
    }

    private static void crossesToTheClient(GameTestHelper helper) {
        Placed plant = place(helper);
        hold(plant, FULL);
        plant.machine().fluids().set(0, new FluidStack(Fluids.WATER, 400));
        plant.machine().fluids().set(1, new FluidStack(Fluids.LAVA, 25));
        plant.machine().fluids().set(2, new FluidStack(Fluids.WATER, 1700));
        EnergyHandlerUtil.move(AssemblerMachineTests.supply(), plant.energy(Direction.UP), 40_000, null);

        ChemicalPlantMenu client = AssemblerMachineTests.openOnTheClient(helper, ChemicalPlants.MENU.get(), AdvancedContainerSetDataPayload.TYPE);
        helper.assertTrue(client.fluid(0).getFluid() == Fluids.WATER && client.fluid(0).getAmount() == 400, "the screen shows " + client.fluid(0) + " in box 1");
        helper.assertTrue(client.fluid(1).getFluid() == Fluids.LAVA && client.fluid(1).getAmount() == 25, "the screen shows " + client.fluid(1) + " in box 2");
        helper.assertTrue(client.fluid(2).getAmount() == 1700 && client.fluid(3).isEmpty(), "the screen shows " + client.fluid(2) + " and " + client.fluid(3));
        helper.assertTrue(client.fluidCapacity(2) == 1800 && client.fluidCapacity(0) == 1000, "the screen shows capacities " + client.fluidCapacity(0) + " and " + client.fluidCapacity(2));
        helper.assertTrue(client.energy() == plant.machine().energy() && client.energyCapacity() == 50_000, "the screen shows " + client.energy() + " of " + client.energyCapacity() + " FE");
        helper.succeed();
    }

    private static void configDefaults(GameTestHelper helper) {
        helper.assertTrue(CraftworksConfig.speed(ChemicalPlantDefaults.INSTANCE) == 1.0, "speed is " + CraftworksConfig.speed(ChemicalPlantDefaults.INSTANCE));
        helper.assertTrue(CraftworksConfig.power(ChemicalPlantDefaults.INSTANCE) == 105.0, "power is " + CraftworksConfig.power(ChemicalPlantDefaults.INSTANCE));
        helper.assertTrue(CraftworksConfig.buffer(ChemicalPlantDefaults.INSTANCE) == 50_000, "buffer is " + CraftworksConfig.buffer(ChemicalPlantDefaults.INSTANCE));
        helper.assertTrue(CraftworksConfig.categories(ChemicalPlantDefaults.INSTANCE).equals(List.of(AssemblingCategory.CHEMISTRY)),
                "categories are " + CraftworksConfig.categories(ChemicalPlantDefaults.INSTANCE));
        helper.succeed();
    }
}
