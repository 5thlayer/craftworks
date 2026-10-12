// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.gametest;

import java.util.Optional;

import io.github._5thlayer.craftworks.Craftworks;
import io.github._5thlayer.craftworks.gametest.AssemblerMachineTests.Placed;
import io.github._5thlayer.craftworks.machine.AssemblerBlockEntity;
import io.github._5thlayer.craftworks.machine.AssemblerMenu;
import io.github._5thlayer.craftworks.machine.AssemblerSlots;
import io.github._5thlayer.craftworks.machine.AssemblerTier;
import io.github._5thlayer.craftworks.machine.Assemblers;
import io.github._5thlayer.craftworks.machine.HeldRecipes;
import io.github._5thlayer.craftworks.machine.MachineFluids;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.network.payload.AdvancedContainerSetDataPayload;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * The drain and fill slots of tiers 2 and 3, the four shared outputs, and the item face that routes into them (#57,
 * ADR-0021): a container moves whole or waits, tier 1 has neither slot, an ingredient goes to its input first, and a
 * save from before the shared outputs keeps its product and remainders.
 */
final class AssemblerContainerTests {

    /** A dirt and 250 mB of water make a clay ball. */
    private static final Identifier WATER_CRAFT = AssemblerFluidTests.WATER_CRAFT;
    /** 100 mB of water make 400 mB of water and 100 mB of lava: two output boxes and no item. */
    private static final Identifier TWO_OUTPUTS = id("gametest/assembler_two_outputs");
    /** A water bucket, itself an ingredient, and 250 mB of water make a clay ball. */
    private static final Identifier BUCKET_AND_WATER = id("gametest/bucket_and_water");
    private static final Identifier CAKE = Identifier.parse("minecraft:cake");
    private static final Identifier SAPLING = AssemblerTests.OAK_SAPLING;

    private static final BlockPos ORIGIN = AssemblerMachineTests.ORIGIN;
    private static final int OUTPUT_BOXES_START = 2;

    private AssemblerContainerTests() {
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(Craftworks.MOD_ID, path);
    }

    static void register(CraftworksGameTests.Registrar tests) {
        tests.test("a_filled_container_in_the_drain_slot_fills_its_input_box_and_the_emptied_one_goes_to_the_outputs", 20, AssemblerContainerTests::drains);
        tests.test("a_container_in_the_drain_slot_waits_while_the_box_has_no_room_for_all_of_it", 20, AssemblerContainerTests::drainWaitsOnTheBox);
        tests.test("a_container_in_the_drain_slot_waits_while_the_outputs_have_no_room_and_stacks_onto_a_slot_with_room", 20, AssemblerContainerTests::drainWaitsOnTheOutputs);
        tests.test("a_container_in_the_drain_slot_waits_when_no_input_box_takes_its_fluid", 20, AssemblerContainerTests::drainWaitsOnTheFluid);
        tests.test("an_empty_container_in_the_fill_slot_fills_from_the_first_output_box_with_a_whole_containers_worth", 20, AssemblerContainerTests::fills);
        tests.test("a_container_in_the_fill_slot_waits_while_no_box_or_no_output_has_room", 20, AssemblerContainerTests::fillWaits);
        tests.test("tier_1_has_neither_a_drain_slot_nor_a_fill_slot", 20, AssemblerContainerTests::tierOne);
        tests.test("the_menus_container_slots_take_only_what_they_are_for_and_cross_to_the_client", 20, AssemblerContainerTests::menuSlots);
        tests.test("the_item_face_routes_a_filled_container_to_the_drain_slot_and_an_empty_one_to_the_fill_slot", 20, AssemblerContainerTests::routes);
        tests.test("an_item_the_held_recipe_names_goes_to_its_input_before_the_drain_slot", 20, AssemblerContainerTests::ingredientFirst);
        tests.test("a_milk_bucket_stays_an_ingredient_of_a_cake", 20, AssemblerContainerTests::milkBucket);
        tests.test("only_the_shared_outputs_extract_through_the_item_face", 20, AssemblerContainerTests::onlyOutputsExtract);
        tests.test("shift_click_routes_as_the_item_face_does", 20, AssemblerContainerTests::shiftClick);
        tests.test("a_craft_places_its_product_and_remainders_in_any_output_with_room_and_stacks_first", 20, AssemblerContainerTests::sharedOutputs);
        tests.test("a_save_with_a_product_and_remainders_slot_loads_them_as_the_first_two_outputs", 20, AssemblerContainerTests::oldSave);
        tests.test("a_fast_replace_keeps_the_drain_and_fill_slots", 20, AssemblerContainerTests::fastReplace);
    }

    // -- fixtures -------------------------------------------------------------------------------

    private static void set(Placed assembler, int slot, Item item, int count) {
        assembler.machine().inventory().set(slot, ItemResource.of(item), count);
    }

    private static int count(Placed assembler, int slot) {
        return AssemblerMachineTests.count(assembler, slot);
    }

    private static Item item(Placed assembler, int slot) {
        return assembler.machine().inventory().getResource(slot).getItem();
    }

    private static int outputs(Placed assembler, Item item) {
        int total = 0;
        for (int output = 0; output < AssemblerSlots.OUTPUTS; output++) {
            if (item(assembler, AssemblerSlots.OUTPUT + output) == item) {
                total += count(assembler, AssemblerSlots.OUTPUT + output);
            }
        }
        return total;
    }

    private static MachineFluids boxes(Placed assembler) {
        return assembler.machine().fluids();
    }

    private static int tryInsert(ResourceHandler<ItemResource> face, int slot, Item item, int amount) {
        try (Transaction tx = Transaction.openRoot()) {
            int inserted = face.insert(slot, ItemResource.of(item), amount, tx);
            tx.commit();
            return inserted;
        }
    }

    /** What an insert with no slot named, a hopper's or a pipe's, puts in: where it goes is what the slots take in order. */
    private static int tryInsertAnywhere(ResourceHandler<ItemResource> face, Item item, int amount) {
        try (Transaction tx = Transaction.openRoot()) {
            int inserted = face.insert(ItemResource.of(item), amount, tx);
            tx.commit();
            return inserted;
        }
    }

    // -- draining -------------------------------------------------------------------------------

    private static void drains(GameTestHelper helper) {
        for (AssemblerTier tier : new AssemblerTier[] {AssemblerTier.TWO, AssemblerTier.THREE}) {
            Placed assembler = AssemblerMachineTests.place(helper, tier);
            AssemblerMachineTests.hold(assembler, WATER_CRAFT);
            set(assembler, AssemblerSlots.DRAIN, Items.WATER_BUCKET, 1);
            AssemblerFluidTests.tick(helper, assembler, 1);
            helper.assertTrue(boxes(assembler).contents(0).getFluid() == Fluids.WATER && boxes(assembler).contents(0).getAmount() == 1000,
                    tier + ": input box 1 holds " + boxes(assembler).contents(0) + ", not the bucket's 1000 mB of water");
            helper.assertTrue(count(assembler, AssemblerSlots.DRAIN) == 0, tier + ": the drained bucket stayed in the drain slot");
            helper.assertTrue(outputs(assembler, Items.BUCKET) == 1, tier + ": the emptied bucket is not in the outputs");
            helper.destroyBlock(ORIGIN);
        }
        helper.succeed();
    }

    private static void drainWaitsOnTheBox(GameTestHelper helper) {
        Placed assembler = AssemblerMachineTests.place(helper, AssemblerTier.TWO);
        AssemblerMachineTests.hold(assembler, WATER_CRAFT);
        boxes(assembler).set(0, new FluidStack(Fluids.WATER, 1500));
        set(assembler, AssemblerSlots.DRAIN, Items.WATER_BUCKET, 1);
        AssemblerFluidTests.tick(helper, assembler, 5);
        helper.assertTrue(count(assembler, AssemblerSlots.DRAIN) == 1 && boxes(assembler).contents(0).getAmount() == 1500,
                "a bucket went in a box with room for half of it: the box holds " + boxes(assembler).contents(0));
        helper.assertTrue(outputs(assembler, Items.BUCKET) == 0 && outputs(assembler, Items.WATER_BUCKET) == 0, "something reached the outputs");
        boxes(assembler).set(0, new FluidStack(Fluids.WATER, 1000));
        AssemblerFluidTests.tick(helper, assembler, 1);
        helper.assertTrue(count(assembler, AssemblerSlots.DRAIN) == 0 && boxes(assembler).contents(0).getAmount() == 2000,
                "with room for it the bucket did not drain whole: the box holds " + boxes(assembler).contents(0));
        helper.succeed();
    }

    private static void drainWaitsOnTheOutputs(GameTestHelper helper) {
        Placed assembler = AssemblerMachineTests.place(helper, AssemblerTier.TWO);
        AssemblerMachineTests.hold(assembler, WATER_CRAFT);
        AssemblerMachineTests.fillOutputs(assembler, 0, AssemblerSlots.OUTPUTS, Items.STICK);
        set(assembler, AssemblerSlots.DRAIN, Items.WATER_BUCKET, 1);
        AssemblerFluidTests.tick(helper, assembler, 5);
        helper.assertTrue(count(assembler, AssemblerSlots.DRAIN) == 1 && boxes(assembler).contents(0).isEmpty(),
                "a bucket drained with nowhere for the empty one to go: the box holds " + boxes(assembler).contents(0));
        // Room in the last output: it goes there, and a bucket already in the outputs is grown before an empty slot is taken.
        set(assembler, AssemblerSlots.OUTPUT + 2, Items.BUCKET, 3);
        AssemblerFluidTests.tick(helper, assembler, 1);
        helper.assertTrue(count(assembler, AssemblerSlots.DRAIN) == 0 && count(assembler, AssemblerSlots.OUTPUT + 2) == 4
                        && item(assembler, AssemblerSlots.OUTPUT + 2) == Items.BUCKET,
                "the emptied bucket stacked to " + count(assembler, AssemblerSlots.OUTPUT + 2) + " of " + item(assembler, AssemblerSlots.OUTPUT + 2) + ", not 4 buckets");
        helper.succeed();
    }

    private static void drainWaitsOnTheFluid(GameTestHelper helper) {
        Placed assembler = AssemblerMachineTests.place(helper, AssemblerTier.TWO);
        AssemblerMachineTests.hold(assembler, WATER_CRAFT);
        set(assembler, AssemblerSlots.DRAIN, Items.LAVA_BUCKET, 1);
        AssemblerFluidTests.tick(helper, assembler, 5);
        helper.assertTrue(count(assembler, AssemblerSlots.DRAIN) == 1 && item(assembler, AssemblerSlots.DRAIN) == Items.LAVA_BUCKET
                        && boxes(assembler).contents(0).isEmpty() && boxes(assembler).contents(1).isEmpty(),
                "a lava bucket went into a box no ingredient binds lava to");
        helper.succeed();
    }

    // -- filling --------------------------------------------------------------------------------

    private static void fills(GameTestHelper helper) {
        Placed assembler = AssemblerMachineTests.place(helper, AssemblerTier.THREE);
        AssemblerMachineTests.hold(assembler, TWO_OUTPUTS);
        // Output box 1 (water) holds too little for a bucket, output box 2 (lava) enough: lava, the first that does.
        boxes(assembler).set(OUTPUT_BOXES_START, new FluidStack(Fluids.WATER, 500));
        boxes(assembler).set(OUTPUT_BOXES_START + 1, new FluidStack(Fluids.LAVA, 1500));
        set(assembler, AssemblerSlots.FILL, Items.BUCKET, 1);
        AssemblerFluidTests.tick(helper, assembler, 1);
        helper.assertTrue(count(assembler, AssemblerSlots.FILL) == 0 && outputs(assembler, Items.LAVA_BUCKET) == 1,
                "the bucket was not filled with lava and sent to the outputs: " + describe(assembler));
        helper.assertTrue(boxes(assembler).contents(OUTPUT_BOXES_START).getAmount() == 500 && boxes(assembler).contents(OUTPUT_BOXES_START + 1).getAmount() == 500,
                "the boxes hold " + boxes(assembler).contents(OUTPUT_BOXES_START) + " and " + boxes(assembler).contents(OUTPUT_BOXES_START + 1));
        // Both holding a whole bucket's worth, the first in order is drawn from.
        boxes(assembler).set(OUTPUT_BOXES_START, new FluidStack(Fluids.WATER, 1200));
        boxes(assembler).set(OUTPUT_BOXES_START + 1, new FluidStack(Fluids.LAVA, 1200));
        set(assembler, AssemblerSlots.FILL, Items.BUCKET, 1);
        AssemblerFluidTests.tick(helper, assembler, 1);
        helper.assertTrue(outputs(assembler, Items.WATER_BUCKET) == 1 && boxes(assembler).contents(OUTPUT_BOXES_START).getAmount() == 200
                        && boxes(assembler).contents(OUTPUT_BOXES_START + 1).getAmount() == 1200,
                "the first box in order was not the one drawn from: " + describe(assembler));
        helper.succeed();
    }

    private static void fillWaits(GameTestHelper helper) {
        Placed assembler = AssemblerMachineTests.place(helper, AssemblerTier.TWO);
        AssemblerMachineTests.hold(assembler, TWO_OUTPUTS);
        boxes(assembler).set(OUTPUT_BOXES_START, new FluidStack(Fluids.WATER, 900));
        set(assembler, AssemblerSlots.FILL, Items.BUCKET, 1);
        AssemblerFluidTests.tick(helper, assembler, 5);
        helper.assertTrue(count(assembler, AssemblerSlots.FILL) == 1 && boxes(assembler).contents(OUTPUT_BOXES_START).getAmount() == 900,
                "a bucket was filled from a box holding less than a bucket: " + describe(assembler));
        // Enough fluid now, but the outputs hold sticks and a filled bucket stacks on nothing.
        boxes(assembler).set(OUTPUT_BOXES_START, new FluidStack(Fluids.WATER, 1900));
        AssemblerMachineTests.fillOutputs(assembler, 0, AssemblerSlots.OUTPUTS, Items.STICK);
        AssemblerFluidTests.tick(helper, assembler, 5);
        helper.assertTrue(count(assembler, AssemblerSlots.FILL) == 1 && boxes(assembler).contents(OUTPUT_BOXES_START).getAmount() == 1900,
                "a bucket was filled with nowhere for it to go: " + describe(assembler));
        assembler.machine().inventory().set(AssemblerSlots.OUTPUT + 3, ItemResource.EMPTY, 0);
        AssemblerFluidTests.tick(helper, assembler, 1);
        helper.assertTrue(count(assembler, AssemblerSlots.FILL) == 0 && item(assembler, AssemblerSlots.OUTPUT + 3) == Items.WATER_BUCKET,
                "with room, the bucket was not filled: " + describe(assembler));
        helper.succeed();
    }

    private static String describe(Placed assembler) {
        StringBuilder text = new StringBuilder("drain ").append(assembler.machine().inventory().getResource(AssemblerSlots.DRAIN).getItem())
                .append(", fill ").append(count(assembler, AssemblerSlots.FILL)).append(" of ").append(item(assembler, AssemblerSlots.FILL));
        for (int output = 0; output < AssemblerSlots.OUTPUTS; output++) {
            text.append(", out ").append(count(assembler, AssemblerSlots.OUTPUT + output)).append(" of ").append(item(assembler, AssemblerSlots.OUTPUT + output));
        }
        for (int box = 0; box < 5; box++) {
            text.append(", box ").append(boxes(assembler).contents(box));
        }
        return text.toString();
    }

    // -- tier 1 ---------------------------------------------------------------------------------

    private static void tierOne(GameTestHelper helper) {
        Placed assembler = AssemblerMachineTests.place(helper, AssemblerTier.ONE);
        AssemblerMachineTests.hold(assembler, TWO_OUTPUTS);
        helper.assertTrue(!AssemblerTier.ONE.hasContainerSlots() && AssemblerTier.TWO.hasContainerSlots() && AssemblerTier.THREE.hasContainerSlots(),
                "the tiers disagree on which have container slots");
        AssemblerMenu menu = menuOf(assembler);
        helper.assertTrue(!menu.hasContainerSlots(), "a tier 1 menu has container slots");
        Slot drain = menu.getSlot(AssemblerSlots.DRAIN);
        Slot fill = menu.getSlot(AssemblerSlots.FILL);
        helper.assertTrue(!drain.isActive() && !fill.isActive(), "a tier 1 menu shows a drain or fill slot");
        helper.assertTrue(!drain.mayPlace(new ItemStack(Items.WATER_BUCKET)) && !fill.mayPlace(new ItemStack(Items.BUCKET)), "a hand put a container in a tier 1 slot");
        ResourceHandler<ItemResource> face = assembler.items(Direction.UP);
        helper.assertTrue(tryInsert(face, AssemblerSlots.DRAIN, Items.WATER_BUCKET, 1) == 0 && tryInsert(face, AssemblerSlots.FILL, Items.BUCKET, 1) == 0,
                "the item face of a tier 1 Assembler took a container");
        helper.assertTrue(tryInsertAnywhere(face, Items.WATER_BUCKET, 1) == 0 && tryInsertAnywhere(face, Items.BUCKET, 1) == 0,
                "an insert with no slot named put a container in a tier 1 Assembler");

        // Whatever sits in the slots, perhaps from a tier above, waits there untouched.
        set(assembler, AssemblerSlots.DRAIN, Items.WATER_BUCKET, 1);
        boxes(assembler).set(OUTPUT_BOXES_START, new FluidStack(Fluids.WATER, 1500));
        set(assembler, AssemblerSlots.FILL, Items.BUCKET, 1);
        AssemblerFluidTests.tick(helper, assembler, 5);
        helper.assertTrue(count(assembler, AssemblerSlots.DRAIN) == 1 && count(assembler, AssemblerSlots.FILL) == 1
                        && boxes(assembler).contents(OUTPUT_BOXES_START).getAmount() == 1500,
                "a tier 1 Assembler moved a container: " + describe(assembler));
        helper.succeed();
    }

    // -- the screen -----------------------------------------------------------------------------

    private static void menuSlots(GameTestHelper helper) {
        Placed assembler = AssemblerMachineTests.place(helper, AssemblerTier.TWO);
        AssemblerMenu menu = menuOf(assembler);
        Slot drain = menu.getSlot(AssemblerSlots.DRAIN);
        Slot fill = menu.getSlot(AssemblerSlots.FILL);
        helper.assertTrue(menu.hasContainerSlots() && drain.isActive() && fill.isActive(), "a tier 2 menu has no container slots");
        helper.assertTrue(drain.x == AssemblerMenu.CONTAINER_X && drain.y == AssemblerMenu.DRAIN_Y
                        && fill.x == AssemblerMenu.CONTAINER_X && fill.y == AssemblerMenu.FILL_Y,
                "the container slots stand at " + drain.x + "," + drain.y + " and " + fill.x + "," + fill.y);
        // A hand may put any filled container in the drain slot and any empty one in the fill slot, whatever the recipe.
        helper.assertTrue(drain.mayPlace(new ItemStack(Items.WATER_BUCKET)) && drain.mayPlace(new ItemStack(Items.LAVA_BUCKET)),
                "the drain slot refused a filled bucket");
        helper.assertTrue(!drain.mayPlace(new ItemStack(Items.BUCKET)) && !drain.mayPlace(new ItemStack(Items.DIRT)) && !drain.mayPlace(ItemStack.EMPTY),
                "the drain slot took an empty bucket or a plain item");
        helper.assertTrue(fill.mayPlace(new ItemStack(Items.BUCKET)) && !fill.mayPlace(new ItemStack(Items.WATER_BUCKET)) && !fill.mayPlace(new ItemStack(Items.DIRT)),
                "the fill slot took the wrong thing");
        for (int output = 0; output < AssemblerSlots.OUTPUTS; output++) {
            helper.assertTrue(!menu.getSlot(AssemblerSlots.OUTPUT + output).mayPlace(new ItemStack(Items.BUCKET)), "output " + output + " took an item from a hand");
        }

        // What the client builds from the open screen's data agrees: tier 2 has the slots, tier 1 has not.
        AssemblerMenu client = AssemblerMachineTests.openOnTheClient(helper, Assemblers.MENU.get(), AdvancedContainerSetDataPayload.TYPE);
        helper.assertTrue(client.hasContainerSlots() && client.getSlot(AssemblerSlots.DRAIN).isActive()
                        && client.getSlot(AssemblerSlots.DRAIN).mayPlace(new ItemStack(Items.WATER_BUCKET))
                        && !client.getSlot(AssemblerSlots.DRAIN).mayPlace(new ItemStack(Items.BUCKET)),
                "the client's tier 2 menu has no working drain slot");
        helper.destroyBlock(ORIGIN);

        Placed one = AssemblerMachineTests.place(helper, AssemblerTier.ONE);
        AssemblerMenu clientOne = AssemblerMachineTests.openOnTheClient(helper, Assemblers.MENU.get(), AdvancedContainerSetDataPayload.TYPE);
        helper.assertTrue(!clientOne.hasContainerSlots() && !clientOne.getSlot(AssemblerSlots.DRAIN).isActive()
                        && !clientOne.getSlot(AssemblerSlots.FILL).isActive(),
                "the client's tier 1 menu shows the container slots");
        helper.assertTrue(one.tier() == AssemblerTier.ONE, "placed the wrong tier");
        helper.succeed();
    }

    private static AssemblerMenu menuOf(Placed assembler) {
        AssemblerMenu menu = (AssemblerMenu) assembler.machine().createMenu(1, assembler.player().getInventory(), assembler.player());
        assembler.player().containerMenu = menu;
        return menu;
    }

    // -- the item face --------------------------------------------------------------------------

    private static void routes(GameTestHelper helper) {
        Placed assembler = AssemblerMachineTests.place(helper, AssemblerTier.TWO);
        ResourceHandler<ItemResource> face = assembler.items(Direction.EAST);
        // No Held recipe: nothing to pour into or fill from.
        helper.assertTrue(tryInsertAnywhere(face, Items.WATER_BUCKET, 1) == 0 && tryInsertAnywhere(face, Items.BUCKET, 1) == 0,
                "an Assembler with no Held recipe took a container");
        AssemblerMachineTests.hold(assembler, TWO_OUTPUTS);
        // Water is the recipe's one ingredient, so a water bucket drains; lava is nobody's.
        helper.assertTrue(tryInsertAnywhere(face, Items.LAVA_BUCKET, 1) == 0, "a lava bucket went in, though no input box takes lava");
        helper.assertTrue(tryInsertAnywhere(face, Items.WATER_BUCKET, 1) == 1 && item(assembler, AssemblerSlots.DRAIN) == Items.WATER_BUCKET,
                "a water bucket did not go to the drain slot: " + describe(assembler));
        helper.assertTrue(tryInsertAnywhere(face, Items.BUCKET, 1) == 1 && item(assembler, AssemblerSlots.FILL) == Items.BUCKET,
                "an empty bucket did not go to the fill slot: " + describe(assembler));
        // Each slot by itself takes only its kind, and a second container waits outside.
        helper.assertTrue(tryInsert(face, AssemblerSlots.FILL, Items.WATER_BUCKET, 1) == 0 && tryInsert(face, AssemblerSlots.DRAIN, Items.BUCKET, 1) == 0,
                "a slot took the other's kind");
        helper.assertTrue(tryInsertAnywhere(face, Items.WATER_BUCKET, 1) == 0, "a second filled bucket went in with the drain slot full");
        helper.assertTrue(tryInsert(face, 1, Items.WATER_BUCKET, 1) == 0 && tryInsert(face, AssemblerSlots.OUTPUT, Items.BUCKET, 1) == 0,
                "an unused input or an output took a container");

        // An empty bucket is no use to a recipe that makes no fluid.
        helper.destroyBlock(ORIGIN);
        Placed clay = AssemblerMachineTests.place(helper, AssemblerTier.THREE);
        AssemblerMachineTests.hold(clay, WATER_CRAFT);
        ResourceHandler<ItemResource> clayFace = clay.items(Direction.EAST);
        helper.assertTrue(tryInsertAnywhere(clayFace, Items.BUCKET, 1) == 0, "an empty bucket went to the fill slot of an Assembler with no fluid result");
        helper.assertTrue(tryInsertAnywhere(clayFace, Items.WATER_BUCKET, 1) == 1, "a water bucket did not go to the drain slot beside a recipe that pours water");
        helper.succeed();
    }

    /** A water bucket that is an ingredient goes to its input while that has room, and only then to the drain slot. */
    private static void ingredientFirst(GameTestHelper helper) {
        Placed assembler = AssemblerMachineTests.place(helper, AssemblerTier.THREE);
        AssemblerMachineTests.hold(assembler, BUCKET_AND_WATER);
        ResourceHandler<ItemResource> face = assembler.items(Direction.NORTH);
        helper.assertTrue(tryInsert(face, AssemblerSlots.DRAIN, Items.WATER_BUCKET, 1) == 0,
                "a water bucket went to the drain slot while its input had room");
        int inInput = tryInsertAnywhere(face, Items.WATER_BUCKET, 1);
        helper.assertTrue(inInput == 1 && item(assembler, 0) == Items.WATER_BUCKET && count(assembler, 0) == 1 && count(assembler, AssemblerSlots.DRAIN) == 0,
                "the water bucket was not the ingredient first: " + describe(assembler));
        // Fill the input to the Overload Limit; the next one has nowhere to be an ingredient, so it drains.
        while (tryInsert(face, 0, Items.WATER_BUCKET, 1) == 1) {
            // the input takes buckets up to the Overload Limit
        }
        int held = count(assembler, 0);
        helper.assertTrue(held >= 2, "the input took only " + held + " bucket(s)");
        helper.assertTrue(tryInsertAnywhere(face, Items.WATER_BUCKET, 1) == 1 && item(assembler, AssemblerSlots.DRAIN) == Items.WATER_BUCKET
                        && count(assembler, 0) == held,
                "a bucket the full input could not take did not go on to the drain slot: " + describe(assembler));
        helper.succeed();
    }

    private static void milkBucket(GameTestHelper helper) {
        Placed assembler = AssemblerMachineTests.place(helper, AssemblerTier.THREE);
        AssemblerMachineTests.hold(assembler, CAKE);
        ResourceHandler<ItemResource> face = assembler.items(Direction.WEST);
        helper.assertTrue(tryInsert(face, AssemblerSlots.DRAIN, Items.MILK_BUCKET, 1) == 0, "a milk bucket went to the drain slot of a cake");
        helper.assertTrue(tryInsertAnywhere(face, Items.MILK_BUCKET, 3) == 3 && item(assembler, 0) == Items.MILK_BUCKET && count(assembler, 0) == 3,
                "the three milk buckets did not go to the input: " + describe(assembler));
        // A fourth: the input holds the Overload Limit's worth at most, and no input box takes milk, so it stays out.
        helper.assertTrue(count(assembler, AssemblerSlots.DRAIN) == 0, "a milk bucket reached the drain slot");
        // Cake makes no fluid: an empty bucket, which a craft leaves behind, is not the fill slot's either.
        helper.assertTrue(tryInsertAnywhere(face, Items.BUCKET, 1) == 0, "an empty bucket went to the fill slot of a cake");
        helper.succeed();
    }

    private static void onlyOutputsExtract(GameTestHelper helper) {
        Placed assembler = AssemblerMachineTests.place(helper, AssemblerTier.TWO);
        AssemblerMachineTests.hold(assembler, TWO_OUTPUTS);
        ResourceHandler<ItemResource> face = assembler.items(Direction.SOUTH);
        set(assembler, AssemblerSlots.DRAIN, Items.WATER_BUCKET, 1);
        set(assembler, AssemblerSlots.FILL, Items.BUCKET, 1);
        set(assembler, AssemblerSlots.OUTPUT + 3, Items.BUCKET, 2);
        try (Transaction tx = Transaction.openRoot()) {
            helper.assertTrue(face.extract(AssemblerSlots.DRAIN, ItemResource.of(Items.WATER_BUCKET), 1, tx) == 0, "the drain slot extracted");
            helper.assertTrue(face.extract(AssemblerSlots.FILL, ItemResource.of(Items.BUCKET), 1, tx) == 0, "the fill slot extracted");
            helper.assertTrue(face.extract(AssemblerSlots.OUTPUT + 3, ItemResource.of(Items.BUCKET), 2, tx) == 2, "the last output did not extract");
        }
        // With no slot named, as a hopper asks: the two empty buckets in the outputs, and not the one in the fill slot.
        try (Transaction tx = Transaction.openRoot()) {
            int took = face.extract(ItemResource.of(Items.BUCKET), 5, tx);
            helper.assertTrue(took == 2, "an extract with no slot named took " + took + " buckets, not the 2 in the outputs");
        }
        helper.succeed();
    }

    private static void shiftClick(GameTestHelper helper) {
        Placed assembler = AssemblerMachineTests.place(helper, AssemblerTier.TWO);
        AssemblerMachineTests.hold(assembler, BUCKET_AND_WATER);
        AssemblerMenu menu = menuOf(assembler);
        var player = assembler.player();
        // A water bucket is the ingredient first; with the input at its limit, the drain slot.
        int moved = 0;
        for (int tries = 0; tries < 4; tries++) {
            player.getInventory().setItem(0, new ItemStack(Items.WATER_BUCKET));
            menu.quickMoveStack(player, hotbarSlot(menu, 0));
            moved++;
        }
        helper.assertTrue(item(assembler, 0) == Items.WATER_BUCKET && count(assembler, 0) >= 1, "shift-click did not put the ingredient in its input: " + describe(assembler));
        helper.assertTrue(item(assembler, AssemblerSlots.DRAIN) == Items.WATER_BUCKET && count(assembler, AssemblerSlots.DRAIN) == 1,
                "shift-click did not send the bucket the input could not take to the drain slot after " + moved + " tries: " + describe(assembler));
        // An empty bucket goes to the fill slot, and a plain item nowhere.
        player.getInventory().setItem(1, new ItemStack(Items.BUCKET));
        menu.quickMoveStack(player, hotbarSlot(menu, 1));
        helper.assertTrue(item(assembler, AssemblerSlots.FILL) == Items.BUCKET, "shift-click did not send an empty bucket to the fill slot");
        player.getInventory().setItem(2, new ItemStack(Items.DIRT));
        menu.quickMoveStack(player, hotbarSlot(menu, 2));
        helper.assertTrue(player.getInventory().getItem(2).is(Items.DIRT), "shift-click moved a dirt");
        helper.succeed();
    }

    private static int hotbarSlot(AssemblerMenu menu, int hotbar) {
        for (Slot slot : menu.slots) {
            if (slot.index >= AssemblerSlots.SIZE && slot.getContainerSlot() == hotbar && slot.container instanceof Inventory) {
                return slot.index;
            }
        }
        throw new IllegalStateException("no hotbar slot " + hotbar);
    }

    // -- the shared outputs ---------------------------------------------------------------------

    private static void sharedOutputs(GameTestHelper helper) {
        Placed assembler = AssemblerMachineTests.place(helper, AssemblerTier.THREE);
        AssemblerMachineTests.hold(assembler, SAPLING);
        // Output 1 holds sticks, output 2 is empty, output 3 holds saplings: the product stacks onto its own first.
        set(assembler, AssemblerSlots.OUTPUT, Items.STICK, 64);
        set(assembler, AssemblerSlots.OUTPUT + 2, Items.OAK_SAPLING, 3);
        AssemblerMachineTests.insert(assembler, 0, Items.OAK_LOG, 2);
        AssemblerFluidTests.tick(helper, assembler, 20);
        helper.assertTrue(count(assembler, AssemblerSlots.OUTPUT + 2) == 4 && count(assembler, AssemblerSlots.OUTPUT + 1) == 0,
                "the product did not stack onto its own slot: " + describe(assembler));

        // Cake: the product and the three empty buckets take two outputs, the buckets stacking in one.
        helper.destroyBlock(ORIGIN);
        Placed cake = AssemblerMachineTests.place(helper, AssemblerTier.THREE);
        AssemblerMachineTests.hold(cake, CAKE);
        var bag = HeldRecipes.find(helper.getLevel(), CAKE).orElseThrow().value().ingredients();
        for (int slot = 0; slot < bag.size(); slot++) {
            AssemblerMachineTests.insert(cake, slot, bag.get(slot).ingredient().items().findFirst().orElseThrow().value(), bag.get(slot).count());
        }
        AssemblerFluidTests.tick(helper, cake, 40);
        helper.assertTrue(outputs(cake, Items.CAKE) == 1 && outputs(cake, Items.BUCKET) == 3, "the cake craft left " + describe(cake));
        helper.succeed();
    }

    // -- old saves and fast replace -------------------------------------------------------------

    /** A save from before the shared outputs holds seven slots: five inputs, the product and the remainders. */
    private static void oldSave(GameTestHelper helper) {
        AssemblerMachineTests.place(helper, AssemblerTier.TWO);
        ItemStacksResourceHandler old = new ItemStacksResourceHandler(7);
        old.set(0, ItemResource.of(Items.OAK_LOG), 6);
        old.set(5, ItemResource.of(Items.OAK_SAPLING), 12);
        old.set(6, ItemResource.of(Items.BUCKET), 9);
        TagValueOutput saved = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, helper.getLevel().registryAccess());
        old.serialize(saved.child("items"));

        AssemblerBlockEntity loaded = new AssemblerBlockEntity(helper.absolutePos(ORIGIN), helper.getBlockState(ORIGIN));
        loaded.setLevel(helper.getLevel());
        loaded.loadCustomOnly(TagValueInput.create(ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), saved.buildResult()));
        var inventory = loaded.inventory();
        helper.assertTrue(inventory.size() == AssemblerSlots.SIZE, "an old save read back with " + inventory.size() + " slots, not " + AssemblerSlots.SIZE);
        helper.assertTrue(inventory.getResource(0).getItem() == Items.OAK_LOG && inventory.getAmountAsInt(0) == 6, "the old input was lost or moved");
        helper.assertTrue(inventory.getResource(AssemblerSlots.OUTPUT).getItem() == Items.OAK_SAPLING && inventory.getAmountAsInt(AssemblerSlots.OUTPUT) == 12,
                "the old product is not in the first output");
        helper.assertTrue(inventory.getResource(AssemblerSlots.OUTPUT + 1).getItem() == Items.BUCKET && inventory.getAmountAsInt(AssemblerSlots.OUTPUT + 1) == 9,
                "the old remainders are not in the second output");
        for (int slot = AssemblerSlots.OUTPUT + 2; slot < AssemblerSlots.SIZE; slot++) {
            helper.assertTrue(inventory.getAmountAsInt(slot) == 0, "slot " + slot + " read back holding something");
        }

        // Saved again it is whole, and a save with no items at all reads back empty.
        TagValueOutput resaved = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, helper.getLevel().registryAccess());
        loaded.saveCustomOnly(resaved);
        CompoundTag tag = resaved.buildResult();
        helper.assertTrue(tag.contains("items"), "the resave has the keys " + tag.keySet());
        AssemblerBlockEntity empty = new AssemblerBlockEntity(helper.absolutePos(ORIGIN), helper.getBlockState(ORIGIN));
        empty.setLevel(helper.getLevel());
        empty.loadCustomOnly(TagValueInput.create(ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), new CompoundTag()));
        helper.assertTrue(empty.inventory().size() == AssemblerSlots.SIZE, "a save with no items has " + empty.inventory().size() + " slots");
        helper.succeed();
    }

    private static void fastReplace(GameTestHelper helper) {
        Placed assembler = AssemblerMachineTests.place(helper, AssemblerTier.TWO);
        AssemblerMachineTests.hold(assembler, TWO_OUTPUTS);
        set(assembler, AssemblerSlots.DRAIN, Items.LAVA_BUCKET, 1);
        set(assembler, AssemblerSlots.FILL, Items.BUCKET, 1);
        set(assembler, AssemblerSlots.OUTPUT + 3, Items.STICK, 5);
        AssemblerBlockEntity machine = assembler.machine();

        AssemblerMachineTests.swap(helper, assembler, AssemblerTier.THREE, ORIGIN);
        helper.assertTrue(helper.getBlockEntity(ORIGIN, AssemblerBlockEntity.class) == machine, "the swap replaced the block entity");
        helper.assertTrue(item(assembler, AssemblerSlots.DRAIN) == Items.LAVA_BUCKET && item(assembler, AssemblerSlots.FILL) == Items.BUCKET
                        && count(assembler, AssemblerSlots.OUTPUT + 3) == 5,
                "the swap to tier 3 lost the slots: " + describe(assembler));
        AssemblerMachineTests.swap(helper, assembler, AssemblerTier.ONE, ORIGIN);
        helper.assertTrue(item(assembler, AssemblerSlots.DRAIN) == Items.LAVA_BUCKET && item(assembler, AssemblerSlots.FILL) == Items.BUCKET,
                "the swap to tier 1 lost the slots: " + describe(assembler));
        helper.assertTrue(Optional.of(TWO_OUTPUTS).equals(machine.heldRecipe()), "the swaps lost the Held recipe");
        helper.succeed();
    }
}
