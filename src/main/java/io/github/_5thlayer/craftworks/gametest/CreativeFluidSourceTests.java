// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.gametest;

import java.util.List;

import io.github._5thlayer.craftworks.machine.AssemblerSlots;
import io.github._5thlayer.craftworks.machine.AssemblerTier;
import io.github._5thlayer.craftworks.machine.Assemblers;
import io.github._5thlayer.craftworks.machine.CreativeFluidSourceBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * The Creative Fluid Source on a real server (#32): it feeds an Assembler's Fluid Connections, is set and
 * cleared with a bucket, gives any amount without running down and takes none, keeps its fluid over a
 * reload, and drops itself.
 */
final class CreativeFluidSourceTests {

    /** Away from the Assembler, for the tests that need no machine. */
    private static final BlockPos SOURCE = new BlockPos(1, 1, 1);

    private CreativeFluidSourceTests() {
    }

    static void register(CraftworksGameTests.Registrar tests) {
        for (Direction side : List.of(Direction.WEST, Direction.EAST)) {
            tests.test("a_creative_fluid_source_set_to_water_feeds_a_tier_2_assembler_through_the_connection_on_the_" + side.getName(), 40,
                    helper -> feedsAnAssembler(helper, side));
        }
        tests.test("a_water_bucket_sets_the_fluid_and_stays_full_and_an_empty_bucket_clears_it", 20, CreativeFluidSourceTests::buckets);
        tests.test("a_creative_fluid_source_with_no_fluid_gives_nothing", 20, CreativeFluidSourceTests::givesNothingWhenEmpty);
        tests.test("a_creative_fluid_source_gives_any_amount_without_running_down_and_refuses_all_fluid", 20,
                CreativeFluidSourceTests::givesWithoutLimit);
        tests.test("a_creative_fluid_sources_fluid_survives_a_save_and_reload", 20, CreativeFluidSourceTests::survivesReload);
        tests.test("a_broken_creative_fluid_source_drops_itself_with_no_fluid", 20, CreativeFluidSourceTests::dropsItself);
    }

    private static CreativeFluidSourceBlockEntity place(GameTestHelper helper, BlockPos at, Fluid fluid) {
        helper.setBlock(at, Assemblers.CREATIVE_FLUID_SOURCE.get());
        CreativeFluidSourceBlockEntity source = helper.getBlockEntity(at, CreativeFluidSourceBlockEntity.class);
        source.setFluid(FluidResource.of(fluid));
        return source;
    }

    private static ResourceHandler<FluidResource> face(GameTestHelper helper, BlockPos at, Direction side) {
        ResourceHandler<FluidResource> face = helper.getLevel().getCapability(Capabilities.Fluid.BLOCK, helper.absolutePos(at), side);
        helper.assertTrue(face != null, "the source showed no fluid capability on " + side);
        return face;
    }

    /** The Assembler pulls from it as from any tank, and the craft runs. */
    private static void feedsAnAssembler(GameTestHelper helper, Direction side) {
        AssemblerMachineTests.Placed assembler = AssemblerMachineTests.place(helper, AssemblerTier.TWO);
        helper.assertTrue(side == assembler.facing() || side == assembler.facing().getOpposite(),
                side + " is not on the Assembler's connection axis");
        place(helper, AssemblerFluidTests.beyond(side), Fluids.WATER);
        AssemblerMachineTests.hold(assembler, AssemblerFluidTests.WATER_CRAFT);
        AssemblerMachineTests.insert(assembler, 0, Items.DIRT, 2);

        SimpleEnergyHandler supply = AssemblerMachineTests.supply();
        int ran = 0;
        while (AssemblerMachineTests.count(assembler, AssemblerSlots.PRODUCT) == 0 && ran < 100) {
            AssemblerMachineTests.feed(assembler, supply, 1000);
            assembler.machine().serverTick(helper.getLevel());
            ran++;
        }
        helper.assertTrue(assembler.machine().inventory().getResource(AssemblerSlots.PRODUCT).getItem() == Items.CLAY_BALL,
                "no clay ball after " + ran + " ticks");
        helper.assertTrue(assembler.machine().fluidBox().contents().getAmount() == 750,
                "the box holds " + assembler.machine().fluidBox().contents() + ", not the 1000 mB of water it filled to less the 250 spent");
        helper.succeed();
    }

    /** Used on it, a filled bucket sets the fluid and is not emptied, and an empty bucket clears it. */
    private static void buckets(GameTestHelper helper) {
        CreativeFluidSourceBlockEntity source = place(helper, SOURCE, Fluids.EMPTY);
        AssemblerMachineTests.FakeBuilder player = new AssemblerMachineTests.FakeBuilder(helper);
        BlockPos at = helper.absolutePos(SOURCE);
        helper.assertTrue(source.fluid().isEmpty(), "a placed source held " + source.fluid().getFluid());

        ItemStack water = new ItemStack(Items.WATER_BUCKET);
        player.setItemInHand(InteractionHand.MAIN_HAND, water);
        use(player, helper, water, at);
        helper.assertTrue(source.fluid().getFluid() == Fluids.WATER, "a water bucket left the source holding " + source.fluid().getFluid());
        helper.assertTrue(water.is(Items.WATER_BUCKET) && water.getCount() == 1 && player.getMainHandItem() == water,
                "the water bucket was emptied or replaced: " + player.getMainHandItem());

        ItemStack lava = new ItemStack(Items.LAVA_BUCKET);
        player.setItemInHand(InteractionHand.MAIN_HAND, lava);
        use(player, helper, lava, at);
        helper.assertTrue(source.fluid().getFluid() == Fluids.LAVA, "a lava bucket left the source holding " + source.fluid().getFluid());
        helper.assertTrue(lava.is(Items.LAVA_BUCKET), "the lava bucket was emptied");

        ItemStack empty = new ItemStack(Items.BUCKET);
        player.setItemInHand(InteractionHand.MAIN_HAND, empty);
        use(player, helper, empty, at);
        helper.assertTrue(source.fluid().isEmpty(), "an empty bucket left the source holding " + source.fluid().getFluid());
        helper.assertTrue(empty.is(Items.BUCKET) && empty.getCount() == 1, "the empty bucket became " + player.getMainHandItem());

        // Anything else used on it does nothing to its fluid.
        place(helper, SOURCE, Fluids.WATER);
        ItemStack stick = new ItemStack(Items.STICK);
        player.setItemInHand(InteractionHand.MAIN_HAND, stick);
        use(player, helper, stick, at);
        helper.assertTrue(helper.getBlockEntity(SOURCE, CreativeFluidSourceBlockEntity.class).fluid().getFluid() == Fluids.WATER,
                "a stick changed the source's fluid");
        helper.succeed();
    }

    private static void use(AssemblerMachineTests.FakeBuilder player, GameTestHelper helper, ItemStack stack, BlockPos at) {
        player.gameMode.useItemOn(player, helper.getLevel(), stack, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(at), Direction.UP, at, false));
    }

    private static void givesNothingWhenEmpty(GameTestHelper helper) {
        place(helper, SOURCE, Fluids.EMPTY);
        for (Direction side : Direction.values()) {
            ResourceHandler<FluidResource> source = face(helper, SOURCE, side);
            for (Fluid fluid : List.of(Fluids.WATER, Fluids.LAVA)) {
                helper.assertTrue(extract(source, fluid, 1000) == 0, "an empty source gave " + fluid + " on " + side);
            }
            helper.assertTrue(source.getAmountAsLong(0) == 0, "an empty source reported " + source.getAmountAsLong(0) + " mB");
        }
        helper.succeed();
    }

    /** Any amount asked is given, every time, on every face; nothing inserted is taken; another fluid is not given. */
    private static void givesWithoutLimit(GameTestHelper helper) {
        place(helper, SOURCE, Fluids.WATER);
        for (Direction side : Direction.values()) {
            ResourceHandler<FluidResource> source = face(helper, SOURCE, side);
            for (int ask : new int[] {1000, 1000, Integer.MAX_VALUE, Integer.MAX_VALUE}) {
                helper.assertTrue(extract(source, Fluids.WATER, ask) == ask, "the source ran short of " + ask + " mB on " + side);
            }
            helper.assertTrue(extract(source, Fluids.LAVA, 1000) == 0, "the source gave lava while set to water, on " + side);
            helper.assertTrue(source.getResource(0).getFluid() == Fluids.WATER && source.getAmountAsLong(0) > 0,
                    "the source stopped showing its water on " + side);
            for (Fluid fluid : List.of(Fluids.WATER, Fluids.LAVA)) {
                try (Transaction tx = Transaction.openRoot()) {
                    helper.assertTrue(source.insert(FluidResource.of(fluid), 1000, tx) == 0, "the source accepted " + fluid + " on " + side);
                }
            }
        }
        helper.succeed();
    }

    private static void survivesReload(GameTestHelper helper) {
        CreativeFluidSourceBlockEntity source = place(helper, SOURCE, Fluids.LAVA);
        TagValueOutput saved = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, helper.getLevel().registryAccess());
        source.saveCustomOnly(saved);

        CreativeFluidSourceBlockEntity loaded = new CreativeFluidSourceBlockEntity(helper.absolutePos(SOURCE), helper.getBlockState(SOURCE));
        loaded.setLevel(helper.getLevel());
        loaded.loadCustomOnly(TagValueInput.create(ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), saved.buildResult()));
        helper.assertTrue(loaded.fluid().getFluid() == Fluids.LAVA, "the source read back holding " + loaded.fluid().getFluid());

        // And one with no fluid reads back with none.
        source.setFluid(FluidResource.EMPTY);
        TagValueOutput none = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, helper.getLevel().registryAccess());
        source.saveCustomOnly(none);
        loaded.loadCustomOnly(TagValueInput.create(ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), none.buildResult()));
        helper.assertTrue(loaded.fluid().isEmpty(), "an empty source read back holding " + loaded.fluid().getFluid());
        helper.succeed();
    }

    /** The loot table gives the item back with no data: a fluid is not carried on it. */
    private static void dropsItself(GameTestHelper helper) {
        place(helper, SOURCE, Fluids.WATER);
        AssemblerMachineTests.FakeBuilder player = new AssemblerMachineTests.FakeBuilder(helper);
        player.gameMode.destroyBlock(helper.absolutePos(SOURCE));
        helper.assertTrue(helper.getBlockState(SOURCE).isAir(), "the break left " + helper.getBlockState(SOURCE));
        List<ItemStack> drops = helper.getEntities(EntityType.ITEM).stream().map(ItemEntity::getItem).toList();
        helper.assertTrue(drops.size() == 1 && drops.getFirst().is(Assemblers.CREATIVE_FLUID_SOURCE_ITEM.get())
                && drops.getFirst().getCount() == 1, "the break dropped " + drops);
        helper.assertTrue(drops.getFirst().getComponentsPatch().isEmpty(),
                "the dropped item carries " + drops.getFirst().getComponentsPatch());
        helper.succeed();
    }

    private static int extract(ResourceHandler<FluidResource> handler, Fluid fluid, int amount) {
        try (Transaction tx = Transaction.openRoot()) {
            int extracted = handler.extract(FluidResource.of(fluid), amount, tx);
            tx.commit();
            return extracted;
        }
    }
}
