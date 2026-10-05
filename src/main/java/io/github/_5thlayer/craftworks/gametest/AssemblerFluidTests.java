// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.gametest;

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
import io.github._5thlayer.craftworks.machine.AssemblerState;
import io.github._5thlayer.craftworks.machine.AssemblerTier;
import io.github._5thlayer.craftworks.machine.Assemblers;
import io.github._5thlayer.craftworks.machine.HoldVerdict;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
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
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * Fluid through the Assembler's Fluid Connections (#25): the box that takes the Held recipe's fluid, the
 * two connections that pull it from a neighbour and where they stand for each facing, that they exist only
 * while the Held recipe has a fluid ingredient, what Fill Recipe refuses, and what a Fast Replace and a
 * save do to the box. The neighbour is {@link TestTank}, a plain fluid-handler block, and the recipes are
 * the game tests' own.
 */
final class AssemblerFluidTests {

    /** Dirt and 250 mB of water make a clay ball; in {@code crafting-with-fluid}. */
    private static final Identifier WATER_CRAFT = id("gametest/water_craft");
    private static final Identifier LAVA_CRAFT = id("gametest/lava_craft");
    /** The same, in the default category, which tier 1 holds. */
    private static final Identifier CRAFTING_FLUID = id("gametest/crafting_fluid");
    private static final Identifier TWO_FLUIDS = id("gametest/two_fluids");
    private static final Identifier BIG_FLUID = id("gametest/big_fluid");
    private static final Identifier FULL_BOX = id("gametest/full_box");
    /** One fluid ingredient that water and lava both match. */
    private static final Identifier WATER_OR_LAVA = id("gametest/water_or_lava");
    private static final Identifier FLUID_RESULT = id("gametest/fluid_recipe");
    private static final Identifier SAPLING = AssemblerTests.OAK_SAPLING;

    private static final List<Direction> FACINGS = List.of(Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST);

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
        tests.test("changing_the_held_recipe_voids_the_box", 20, AssemblerFluidTests::voidsOnChange);
        tests.test("the_fluid_box_survives_a_save_and_reload", 20, AssemblerFluidTests::survivesReload);
        for (Direction facing : FACINGS) {
            tests.test("the_fluid_connections_of_an_assembler_facing_" + facing.getName() + "_stand_on_its_two_bottom_edges", 20,
                    helper -> connectionsFollowFacing(helper, facing));
            tests.test("either_fluid_connection_alone_feeds_an_assembler_facing_" + facing.getName(), 20,
                    helper -> eitherConnectionFeeds(helper, facing));
        }
        tests.test("a_neighbour_at_a_corner_of_the_footprint_is_not_pulled_from", 20, AssemblerFluidTests::cornersDontPull);
        tests.test("tier_1_has_no_fluid_capability", 20, AssemblerFluidTests::tier1HasNone);
        tests.test("the_connections_come_and_go_with_a_fluid_ingredient_in_the_held_recipe", 20, AssemblerFluidTests::connectionsComeAndGo);
        tests.test("a_pipe_that_asked_before_the_recipe_is_told_when_the_connection_appears", 20, AssemblerFluidTests::cachesAreTold);
        tests.test("fill_recipe_on_tier_2_refuses_two_fluid_ingredients_and_more_than_1000_mb", 20, AssemblerFluidTests::refusals);
        tests.test("a_recipe_with_a_fluid_result_is_refused_on_every_tier", 20, AssemblerFluidTests::fluidResults);
        tests.test("tier_1_refuses_any_fluid_recipe", 20, AssemblerFluidTests::tier1Refuses);
        tests.test("a_fast_replace_from_tier_2_to_3_keeps_the_box_and_one_to_tier_1_voids_it", 20, AssemblerFluidTests::fastReplace);
        tests.test("breaking_an_assembler_voids_its_box", 20, AssemblerFluidTests::breaking);
        tests.test("the_screen_of_tiers_2_and_3_has_a_fluid_gauge_over_the_synced_box", 20, AssemblerFluidTests::gauge);
    }

    // -- pulling --------------------------------------------------------------------------------

    /** The block a connection faces, in test coordinates: two from the origin, the way it points. */
    private static BlockPos beyond(Direction side) {
        return AssemblerMachineTests.ORIGIN.relative(side, 2);
    }

    private static TestTank.Entity tank(GameTestHelper helper, BlockPos at, Fluid fluid, int amount) {
        helper.setBlock(at, TestTank.BLOCK.get().defaultBlockState());
        TestTank.Entity tank = helper.getBlockEntity(at, TestTank.Entity.class);
        tank.fill(fluid, amount);
        return tank;
    }

    private static int inTank(TestTank.Entity tank) {
        return tank.tank.getAmountAsInt(0);
    }

    private static int inBox(Placed assembler) {
        return assembler.machine().fluidBox().contents().getAmount();
    }

    private static void tick(GameTestHelper helper, Placed assembler, int ticks) {
        SimpleEnergyHandler supply = AssemblerMachineTests.supply();
        for (int tick = 0; tick < ticks; tick++) {
            AssemblerMachineTests.feed(assembler, supply, 1000);
            assembler.machine().serverTick(helper.getLevel());
        }
    }

    private static void pullsAndCrafts(GameTestHelper helper) {
        Placed assembler = AssemblerMachineTests.place(helper, AssemblerTier.TWO);
        TestTank.Entity water = tank(helper, beyond(assembler.facing()), Fluids.WATER, 5000);
        AssemblerMachineTests.hold(assembler, WATER_CRAFT);
        AssemblerMachineTests.insert(assembler, 0, Items.DIRT, 2);
        helper.assertTrue(assembler.machine().state() == AssemblerState.MISSING_INGREDIENTS,
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
        helper.assertTrue(inBox(assembler) == 750, "the box holds " + inBox(assembler) + " mB, not the 1000 it filled to less the 250 spent");
        helper.assertTrue(inTank(water) == 4000, "the tank holds " + inTank(water) + " mB, not the 1000 less");
        helper.succeed();
    }

    private static void onlyTheHeldFluid(GameTestHelper helper) {
        Placed assembler = AssemblerMachineTests.place(helper, AssemblerTier.TWO);
        TestTank.Entity lava = tank(helper, beyond(assembler.facing()), Fluids.LAVA, 5000);
        AssemblerMachineTests.hold(assembler, WATER_CRAFT);
        tick(helper, assembler, 5);
        helper.assertTrue(inBox(assembler) == 0, "a neighbour holding lava filled the box with " + inBox(assembler) + " mB");
        helper.assertTrue(inTank(lava) == 5000, "the lava tank lost " + (5000 - inTank(lava)) + " mB");

        // And what the box does take it takes through the capability too: only the Held recipe's fluid.
        ResourceHandler<FluidResource> face = helper.getLevel().getCapability(Capabilities.Fluid.BLOCK,
                helper.absolutePos(beyond(assembler.facing()).relative(assembler.facing().getOpposite())), assembler.facing());
        helper.assertTrue(face != null, "no fluid capability at the connection");
        helper.assertTrue(insert(face, Fluids.LAVA, 100) == 0, "the box took lava, which the Held recipe doesn't use");
        helper.assertTrue(insert(face, Fluids.WATER, 100) == 100, "a mod that pushes could not fill the box with the Held recipe's fluid");
        helper.assertTrue(inBox(assembler) == 100, "the pushed water is not in the box");
        helper.assertTrue(extract(face, Fluids.WATER, 100) == 0, "the box gave fluid out through its connection");
        helper.succeed();
    }

    /** Nothing mixes: the first fluid in fills the box, and the other member waits until the box empties. */
    private static void multiFluidIngredient(GameTestHelper helper) {
        Placed assembler = AssemblerMachineTests.place(helper, AssemblerTier.TWO);
        Direction facing = assembler.facing();
        // The connection on the facing side is asked first.
        TestTank.Entity water = tank(helper, beyond(facing), Fluids.WATER, 400);
        TestTank.Entity lava = tank(helper, beyond(facing.getOpposite()), Fluids.LAVA, 5000);
        AssemblerMachineTests.hold(assembler, WATER_OR_LAVA);
        AssemblerMachineTests.insert(assembler, 0, Items.DIRT, 1);
        tick(helper, assembler, 1);
        helper.assertTrue(assembler.machine().fluidBox().contents().getFluid() == Fluids.WATER && inBox(assembler) == 400,
                "the box holds " + assembler.machine().fluidBox().contents() + ", not the 400 mB of water first in");
        helper.assertTrue(inTank(lava) == 5000, "lava went into a box holding water: the tank lost " + (5000 - inTank(lava)));
        helper.assertTrue(inTank(water) == 0, "the water tank still holds " + inTank(water));

        // The recipe takes the water it holds, and the craft runs.
        SimpleEnergyHandler supply = AssemblerMachineTests.supply();
        for (int ran = 0; ran < 20 && AssemblerMachineTests.count(assembler, AssemblerSlots.PRODUCT) == 0; ran++) {
            AssemblerMachineTests.feed(assembler, supply, 1000);
            assembler.machine().serverTick(helper.getLevel());
        }
        helper.assertTrue(AssemblerMachineTests.count(assembler, AssemblerSlots.PRODUCT) == 1, "the craft did not run");
        helper.assertTrue(inBox(assembler) == 150, "the box holds " + inBox(assembler) + " mB, not the 400 less the 250 spent");
        helper.assertTrue(inTank(lava) == 5000, "lava was pulled while the box held water");

        // Emptied, the box takes whichever member comes next.
        assembler.machine().fluidBox().set(FluidStack.EMPTY);
        tick(helper, assembler, 1);
        helper.assertTrue(assembler.machine().fluidBox().contents().getFluid() == Fluids.LAVA && inBox(assembler) == 1000,
                "the emptied box holds " + assembler.machine().fluidBox().contents() + ", not a box of lava");
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
        helper.assertTrue(inBox(assembler) == 1000, "the box holds " + inBox(assembler) + " mB, not its 1000");
        helper.assertTrue(inTank(water) == 4000, "the tank gave " + (5000 - inTank(water)) + " mB, not 1000");
        helper.succeed();
    }

    private static void voidsOnChange(GameTestHelper helper) {
        Placed assembler = AssemblerMachineTests.place(helper, AssemblerTier.TWO);
        AssemblerMachineTests.hold(assembler, WATER_CRAFT);
        assembler.machine().fluidBox().set(new FluidStack(Fluids.WATER, 600));

        // The recipe it already holds moves nothing.
        AssemblerMachineTests.hold(assembler, WATER_CRAFT);
        helper.assertTrue(inBox(assembler) == 600, "Fill Recipe on the recipe already held changed the box to " + inBox(assembler));
        // Another with a fluid, and one with none: the box goes either way.
        AssemblerMachineTests.hold(assembler, LAVA_CRAFT);
        helper.assertTrue(inBox(assembler) == 0, "the box held " + inBox(assembler) + " mB after the recipe changed to lava");
        assembler.machine().fluidBox().set(new FluidStack(Fluids.LAVA, 600));
        AssemblerMachineTests.hold(assembler, SAPLING);
        helper.assertTrue(inBox(assembler) == 0, "the box held " + inBox(assembler) + " mB after the recipe changed to one with no fluid");
        helper.succeed();
    }

    private static void survivesReload(GameTestHelper helper) {
        Placed assembler = AssemblerMachineTests.place(helper, AssemblerTier.THREE);
        AssemblerMachineTests.hold(assembler, WATER_CRAFT);
        assembler.machine().fluidBox().set(new FluidStack(Fluids.WATER, 750));
        TagValueOutput saved = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, helper.getLevel().registryAccess());
        assembler.machine().saveCustomOnly(saved);

        AssemblerBlockEntity loaded = new AssemblerBlockEntity(helper.absolutePos(AssemblerMachineTests.ORIGIN),
                helper.getBlockState(AssemblerMachineTests.ORIGIN));
        loaded.setLevel(helper.getLevel());
        loaded.loadCustomOnly(TagValueInput.create(ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), saved.buildResult()));
        helper.assertTrue(loaded.fluidBox().contents().getFluid() == Fluids.WATER && loaded.fluidBox().contents().getAmount() == 750,
                "the box read back as " + loaded.fluidBox().contents());
        helper.succeed();
    }

    // -- where the connections are --------------------------------------------------------------

    /**
     * Every block of the footprint on every side, as "offset from the origin, side", where a fluid capability
     * answers.
     */
    private static Set<String> exposed(GameTestHelper helper, Placed assembler) {
        Set<String> found = new TreeSet<>();
        BlockPos origin = helper.absolutePos(AssemblerMachineTests.ORIGIN);
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

    /**
     * The two faces that are connections for a facing, as {@link #exposed} spells them: the bottom layer's
     * edge centres.
     */
    private static Set<String> connectionFaces(Direction facing) {
        Set<String> faces = new TreeSet<>();
        for (Direction side : List.of(facing, facing.getOpposite())) {
            faces.add(BlockPos.ZERO.relative(side).toShortString() + " " + side);
        }
        return faces;
    }

    private static void connectionsFollowFacing(GameTestHelper helper, Direction facing) {
        Placed assembler = AssemblerMachineTests.place(helper, AssemblerTier.TWO, facing);
        helper.assertTrue(exposed(helper, assembler).isEmpty(), "an Assembler with no recipe answered a fluid lookup at " + exposed(helper, assembler));
        AssemblerMachineTests.hold(assembler, WATER_CRAFT);
        Set<String> expected = connectionFaces(facing);
        helper.assertTrue(exposed(helper, assembler).equals(expected),
                "facing " + facing + " the fluid capability answered at " + exposed(helper, assembler) + ", not at " + expected);
        helper.assertTrue(helper.getBlockState(AssemblerMachineTests.ORIGIN).getValue(AssemblerBlock.FLUID_CONNECTIONS),
                "the origin does not show its connections");
        helper.succeed();
    }

    private static void eitherConnectionFeeds(GameTestHelper helper, Direction facing) {
        Placed assembler = AssemblerMachineTests.place(helper, AssemblerTier.TWO, facing);
        AssemblerMachineTests.hold(assembler, WATER_CRAFT);
        for (Direction side : List.of(facing, facing.getOpposite())) {
            TestTank.Entity water = tank(helper, beyond(side), Fluids.WATER, 5000);
            tick(helper, assembler, 1);
            helper.assertTrue(inBox(assembler) == 1000 && inTank(water) == 4000,
                    "the connection at " + side + " alone left " + inBox(assembler) + " mB in the box and " + inTank(water) + " in the tank");
            assembler.machine().fluidBox().set(FluidStack.EMPTY);
            helper.setBlock(beyond(side), Blocks.AIR);
        }
        helper.succeed();
    }

    private static void cornersDontPull(GameTestHelper helper) {
        Placed assembler = AssemblerMachineTests.place(helper, AssemblerTier.TWO);
        AssemblerMachineTests.hold(assembler, WATER_CRAFT);
        Direction facing = assembler.facing();
        Direction across = facing.getClockWise();
        // Beside the corner, beside the edge centre that is not a connection, and above a connection.
        for (BlockPos at : List.of(AssemblerMachineTests.ORIGIN.relative(facing, 2).relative(across),
                AssemblerMachineTests.ORIGIN.relative(across, 2),
                AssemblerMachineTests.ORIGIN.relative(facing).above(2))) {
            TestTank.Entity water = tank(helper, at, Fluids.WATER, 5000);
            tick(helper, assembler, 2);
            helper.assertTrue(inBox(assembler) == 0 && inTank(water) == 5000, "a tank at " + at + " was pulled from");
            helper.setBlock(at, Blocks.AIR);
        }
        helper.succeed();
    }

    private static void tier1HasNone(GameTestHelper helper) {
        Placed assembler = AssemblerMachineTests.place(helper, AssemblerTier.ONE);
        AssemblerMachineTests.hold(assembler, SAPLING);
        helper.assertTrue(exposed(helper, assembler).isEmpty(), "tier 1 answered a fluid lookup at " + exposed(helper, assembler));
        // Not through Fill Recipe, which refuses it: a swap or a reload can leave a fluid recipe held on tier 1.
        assembler.machine().setHeldRecipe(WATER_CRAFT, assembler.player());
        helper.assertTrue(exposed(helper, assembler).isEmpty(), "tier 1 holding a fluid recipe answered a fluid lookup at " + exposed(helper, assembler));
        TestTank.Entity water = tank(helper, beyond(assembler.facing()), Fluids.WATER, 5000);
        tick(helper, assembler, 3);
        helper.assertTrue(inTank(water) == 5000, "tier 1 pulled fluid");
        helper.assertTrue(!helper.getBlockState(AssemblerMachineTests.ORIGIN).getValue(AssemblerBlock.FLUID_CONNECTIONS),
                "tier 1 shows connections");
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
        helper.assertTrue(exposed(helper, assembler).size() == 2, "a fluid recipe gave connections at " + exposed(helper, assembler));
        helper.assertTrue(connectionsShown(helper), "the origin does not show connections with a fluid recipe");

        AssemblerMachineTests.hold(assembler, SAPLING);
        helper.assertTrue(exposed(helper, assembler).isEmpty(), "after the recipe changed to one with no fluid there were connections at " + exposed(helper, assembler));
        helper.assertTrue(!connectionsShown(helper), "the origin still shows connections after the recipe lost its fluid");
        helper.succeed();
    }

    private static boolean connectionsShown(GameTestHelper helper) {
        return helper.getBlockState(AssemblerMachineTests.ORIGIN).getValue(AssemblerBlock.FLUID_CONNECTIONS);
    }

    /** A pipe keeps what it was told for a face until the level says it changed (NeoForge's capability caches). */
    private static void cachesAreTold(GameTestHelper helper) {
        Placed assembler = AssemblerMachineTests.place(helper, AssemblerTier.TWO);
        Direction facing = assembler.facing();
        BlockPos connection = helper.absolutePos(AssemblerMachineTests.ORIGIN.relative(facing));
        BlockCapabilityCache<ResourceHandler<FluidResource>, Direction> pipe =
                BlockCapabilityCache.create(Capabilities.Fluid.BLOCK, helper.getLevel(), connection, facing);
        helper.assertTrue(pipe.getCapability() == null, "the cache saw a capability before any recipe");
        AssemblerMachineTests.hold(assembler, WATER_CRAFT);
        helper.assertTrue(pipe.getCapability() != null, "the cache did not see the connection appear");
        AssemblerMachineTests.hold(assembler, SAPLING);
        helper.assertTrue(pipe.getCapability() == null, "the cache did not see the connection go");
        helper.succeed();
    }

    // -- Fill Recipe ----------------------------------------------------------------------------

    private static void refusals(GameTestHelper helper) {
        Placed assembler = AssemblerMachineTests.place(helper, AssemblerTier.TWO);
        HoldVerdict two = AssemblerMachineTests.request(assembler, TWO_FLUIDS);
        HoldVerdict big = AssemblerMachineTests.request(assembler, BIG_FLUID);
        helper.assertTrue(two == HoldVerdict.TOO_MANY_FLUIDS, "two fluid ingredients was " + two);
        helper.assertTrue(big == HoldVerdict.FLUID_TOO_LARGE, "1,001 mB a craft was " + big);
        helper.assertTrue(assembler.machine().heldRecipe().isEmpty(), "a refused recipe was held");
        helper.assertTrue(assembler.player().heard.containsAll(List.of("craftworks.assembler.refused.too_many_fluids",
                "craftworks.assembler.refused.fluid_too_large")), "the player was told " + assembler.player().heard);
        // One fluid ingredient is taken, whatever its category and at exactly the box's size.
        helper.assertTrue(AssemblerMachineTests.request(assembler, WATER_CRAFT) == HoldVerdict.HELD, "one fluid ingredient was refused");
        helper.assertTrue(AssemblerMachineTests.request(assembler, FULL_BOX) == HoldVerdict.HELD, "1,000 mB a craft was refused");
        helper.assertTrue(AssemblerMachineTests.request(assembler, CRAFTING_FLUID) == HoldVerdict.HELD,
                "a fluid ingredient in the crafting category was refused on tier 2");
        helper.succeed();
    }

    private static void fluidResults(GameTestHelper helper) {
        for (AssemblerTier tier : List.of(AssemblerTier.TWO, AssemblerTier.THREE)) {
            Placed assembler = AssemblerMachineTests.place(helper, tier);
            HoldVerdict verdict = AssemblerMachineTests.request(assembler, FLUID_RESULT);
            helper.assertTrue(verdict == HoldVerdict.HAS_FLUID, tier + " answered a recipe with a fluid result with " + verdict);
            helper.assertTrue(assembler.machine().heldRecipe().isEmpty(), tier + " held a recipe with a fluid result");
            helper.destroyBlock(AssemblerMachineTests.ORIGIN);
        }
        helper.succeed();
    }

    private static void tier1Refuses(GameTestHelper helper) {
        Placed assembler = AssemblerMachineTests.place(helper, AssemblerTier.ONE);
        HoldVerdict crafting = AssemblerMachineTests.request(assembler, CRAFTING_FLUID);
        HoldVerdict category = AssemblerMachineTests.request(assembler, WATER_CRAFT);
        helper.assertTrue(crafting == HoldVerdict.HAS_FLUID, "tier 1 answered a fluid ingredient in a category it holds with " + crafting);
        helper.assertTrue(category == HoldVerdict.WRONG_CATEGORY, "tier 1 answered a crafting-with-fluid recipe with " + category);
        helper.assertTrue(assembler.machine().heldRecipe().isEmpty(), "tier 1 held a fluid recipe");
        helper.assertTrue(assembler.player().heard.contains("craftworks.assembler.refused.has_fluid"),
                "the player was told " + assembler.player().heard);
        helper.succeed();
    }

    // -- Fast Replace and breaking --------------------------------------------------------------

    private static void fastReplace(GameTestHelper helper) {
        Placed assembler = AssemblerMachineTests.place(helper, AssemblerTier.TWO);
        AssemblerBlockEntity machine = assembler.machine();
        AssemblerMachineTests.hold(assembler, WATER_CRAFT);
        machine.fluidBox().set(new FluidStack(Fluids.WATER, 400));

        AssemblerMachineTests.swap(helper, assembler, AssemblerTier.THREE, AssemblerMachineTests.ORIGIN.above().north().east());
        helper.assertTrue(helper.getBlockEntity(AssemblerMachineTests.ORIGIN, AssemblerBlockEntity.class) == machine, "the swap replaced the block entity");
        helper.assertTrue(machine.tier() == AssemblerTier.THREE, "the block entity did not follow to tier 3");
        helper.assertTrue(inBox(assembler) == 400 && machine.fluidBox().contents().getFluid() == Fluids.WATER,
                "a swap from tier 2 to 3 left " + inBox(assembler) + " mB in the box");
        // The ring and the capability change together: no tick between the swap and the ring.
        helper.assertTrue(connectionsShown(helper), "tier 3 did not show its connections the moment of the swap");
        helper.assertTrue(exposed(helper, new Placed(helper, assembler.player(), AssemblerTier.THREE, assembler.facing())).size() == 2,
                "tier 3 did not keep its connections after the swap");

        AssemblerMachineTests.swap(helper, assembler, AssemblerTier.ONE, AssemblerMachineTests.ORIGIN);
        helper.assertTrue(machine.tier() == AssemblerTier.ONE, "the block entity did not follow to tier 1");
        helper.assertTrue(inBox(assembler) == 0, "a swap to tier 1 left " + inBox(assembler) + " mB in the box");
        helper.assertTrue(machine.heldRecipe().equals(Optional.of(WATER_CRAFT)), "the swap to tier 1 lost the Held recipe");
        helper.assertTrue(!connectionsShown(helper), "tier 1 shows connections the moment of the swap");

        // And back up: nothing comes back with the tier.
        AssemblerMachineTests.swap(helper, assembler, AssemblerTier.TWO, AssemblerMachineTests.ORIGIN);
        helper.assertTrue(inBox(assembler) == 0, "the box was not empty after a round trip through tier 1");
        helper.succeed();
    }

    private static void breaking(GameTestHelper helper) {
        Placed assembler = AssemblerMachineTests.place(helper, AssemblerTier.TWO);
        AssemblerMachineTests.hold(assembler, WATER_CRAFT);
        assembler.machine().fluidBox().set(new FluidStack(Fluids.WATER, 400));
        assembler.player().gameMode.destroyBlock(helper.absolutePos(AssemblerMachineTests.ORIGIN.above().north().east()));
        List<ItemStack> drops = helper.getEntities(EntityType.ITEM).stream().map(ItemEntity::getItem).toList();
        ItemStack item = drops.stream().filter(stack -> stack.is(Assemblers.item(AssemblerTier.TWO).get())).findFirst().orElse(ItemStack.EMPTY);
        helper.assertTrue(!item.isEmpty() && WATER_CRAFT.equals(item.get(Assemblers.HELD_RECIPE.get())), "the dropped item does not hold the Held recipe");
        Placed again = AssemblerMachineTests.placeItem(helper, AssemblerTier.TWO, item, Direction.WEST);
        helper.assertTrue(again.machine().heldRecipe().equals(Optional.of(WATER_CRAFT)), "the item placed again lost the Held recipe");
        helper.assertTrue(inBox(again) == 0, "the box was not voided by the break: " + inBox(again) + " mB");
        helper.succeed();
    }

    // -- the screen -----------------------------------------------------------------------------

    private static void gauge(GameTestHelper helper) {
        Placed tier1 = AssemblerMachineTests.place(helper, AssemblerTier.ONE);
        helper.assertTrue(!menuOf(tier1).hasFluidBox(), "tier 1's menu has a fluid box");
        helper.destroyBlock(AssemblerMachineTests.ORIGIN);

        Placed assembler = AssemblerMachineTests.place(helper, AssemblerTier.TWO);
        AssemblerMachineTests.hold(assembler, WATER_CRAFT);
        assembler.machine().fluidBox().set(new FluidStack(Fluids.WATER, 400));
        AssemblerMenu menu = menuOf(assembler);
        helper.assertTrue(menu.hasFluidBox(), "tier 2's menu has no fluid box");
        helper.assertTrue(menu.fluid().getFluid() == Fluids.WATER && menu.fluid().getAmount() == 400,
                "the menu shows " + menu.fluid() + " of the box's 400 mB of water");

        // The client's menu, opened from the buffer the block writes, knows its tier from the block at its position.
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        buffer.writeBlockPos(helper.absolutePos(AssemblerMachineTests.ORIGIN));
        AssemblerMenu client = new AssemblerMenu(2, assembler.player().getInventory(), buffer);
        helper.assertTrue(client.hasFluidBox(), "the client's menu of a tier 2 Assembler has no fluid box");
        helper.succeed();
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
