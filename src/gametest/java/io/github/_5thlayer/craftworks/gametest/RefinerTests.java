// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.gametest;

import io.github._5thlayer.craftworks.CraftworksConfig;
import io.github._5thlayer.craftworks.machine.Assemblers;
import io.github._5thlayer.craftworks.machine.RefinerBlock;
import io.github._5thlayer.craftworks.machine.RefinerBlockEntity;
import io.github._5thlayer.craftworks.machine.RefinerMenu;
import io.github._5thlayer.craftworks.machine.RefinerSlots;
import io.github._5thlayer.craftworks.machine.Refiners;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * The Refiner on a real server (#46): the recipes it smelts, what its faces let through, its energy and its
 * screen. How the rates and the Overload Limit come out is {@code RefinerRatesTest}'s, on a plain JVM; what these
 * read is the wiring only a world has.
 */
final class RefinerTests {

    private static final BlockPos REFINER = new BlockPos(3, 1, 3);
    private static final BlockPos SOURCE = new BlockPos(4, 1, 3);

    /** Raw iron's blasting recipe: 100 cooking ticks at speed 2, and 90 FE a tick of the price. */
    private static final int BLAST_TICKS = 50;
    private static final int BLAST_FE = 4500;

    private RefinerTests() {
    }

    static void register(CraftworksGameTests.Registrar tests) {
        tests.test("a_refiner_smelts_cobblestone_to_stone_at_a_smelting_recipes_time_over_its_speed", 200, RefinerTests::smelts);
        tests.test("a_refiner_blasts_an_input_that_has_both_a_smelting_and_a_blasting_recipe", 200, RefinerTests::blastingWins);
        tests.test("a_refiner_with_no_power_makes_no_progress_and_keeps_its_input", 60, RefinerTests::needsPower);
        tests.test("a_refiner_whose_output_is_full_draws_nothing_and_keeps_its_progress", 60, RefinerTests::backsUp);
        tests.test("a_refiners_progress_resets_when_its_input_changes_to_a_different_recipe", 60, RefinerTests::resetsOnANewRecipe);
        tests.test("a_refiners_progress_survives_a_save_and_load", 60, RefinerTests::survivesReload);
        tests.test("a_refiners_item_face_takes_only_what_it_smelts_up_to_the_overload_limit_and_gives_only_its_output", 20, RefinerTests::itemFace);
        tests.test("a_refiners_energy_face_takes_energy_and_gives_none_back", 20, RefinerTests::energyFace);
        tests.test("a_refiners_screen_places_a_full_stack_and_refuses_what_no_recipe_smelts", 20, RefinerTests::screen);
        tests.test("a_refiner_next_to_a_creative_energy_source_smelts_with_no_other_power", 200, RefinerTests::poweredByTheSource);
    }

    private static RefinerBlockEntity place(GameTestHelper helper) {
        helper.setBlock(REFINER, Refiners.BLOCK.get());
        return helper.getBlockEntity(REFINER, RefinerBlockEntity.class);
    }

    private static void load(RefinerBlockEntity refiner, Item item, int amount) {
        refiner.inventory().set(RefinerSlots.INPUT, ItemResource.of(item), amount);
    }

    private static void charge(GameTestHelper helper, int fe) {
        EnergyHandler face = helper.getLevel().getCapability(Capabilities.Energy.BLOCK, helper.absolutePos(REFINER), Direction.UP);
        try (Transaction tx = Transaction.openRoot()) {
            int taken = face.insert(fe, tx);
            helper.assertTrue(taken == fe, "the Refiner took " + taken + " of " + fe + " FE");
            tx.commit();
        }
    }

    private static ResourceHandler<ItemResource> items(GameTestHelper helper, Direction side) {
        return helper.getLevel().getCapability(Capabilities.Item.BLOCK, helper.absolutePos(REFINER), side);
    }

    private static int output(RefinerBlockEntity refiner) {
        return refiner.inventory().getAmountAsInt(RefinerSlots.OUTPUT);
    }

    /** Cobblestone has a smelting recipe and no blasting one: 200 cooking ticks, 100 at speed 2, for 9,000 FE. */
    private static void smelts(GameTestHelper helper) {
        RefinerBlockEntity refiner = place(helper);
        load(refiner, Items.COBBLESTONE, 1);
        charge(helper, 9000);
        helper.runAfterDelay(5, () -> helper.assertTrue(refiner.smeltDuration() == 100,
                "a smelt took " + refiner.smeltDuration() + " ticks, not 100"));
        helper.succeedWhen(() -> {
            helper.assertTrue(refiner.inventory().getResource(RefinerSlots.OUTPUT).equals(ItemResource.of(Items.STONE))
                    && output(refiner) == 1, "no stone made yet");
            helper.assertTrue(refiner.inventory().getAmountAsInt(RefinerSlots.INPUT) == 0, "the cobblestone was not taken");
            helper.assertTrue(refiner.energy() == 0, "the smelt cost " + (9000 - refiner.energy()) + " FE, not 9000");
        });
    }

    /** Raw iron has both: blasting's 100 ticks, 50 at speed 2, for 4,500 FE where smelting would take 100 and 9,000. */
    private static void blastingWins(GameTestHelper helper) {
        RefinerBlockEntity refiner = place(helper);
        load(refiner, Items.RAW_IRON, 1);
        charge(helper, BLAST_FE);
        helper.runAfterDelay(5, () -> helper.assertTrue(refiner.smeltDuration() == BLAST_TICKS,
                "raw iron took " + refiner.smeltDuration() + " ticks, not blasting's " + BLAST_TICKS));
        helper.succeedWhen(() -> {
            helper.assertTrue(refiner.inventory().getResource(RefinerSlots.OUTPUT).equals(ItemResource.of(Items.IRON_INGOT))
                    && output(refiner) == 1, "no iron ingot made yet");
            helper.assertTrue(refiner.energy() == 0, "the blast cost " + (BLAST_FE - refiner.energy()) + " FE, not " + BLAST_FE);
        });
    }

    private static void needsPower(GameTestHelper helper) {
        RefinerBlockEntity refiner = place(helper);
        load(refiner, Items.RAW_IRON, 1);
        helper.runAfterDelay(40, () -> {
            helper.assertTrue(refiner.smeltProgress() == 0, "an unpowered Refiner made " + refiner.smeltProgress() + " ticks of progress");
            helper.assertTrue(refiner.inventory().getAmountAsInt(RefinerSlots.INPUT) == 1 && output(refiner) == 0,
                    "an unpowered Refiner moved its items");
            helper.assertFalse(helper.getBlockState(REFINER).getValue(RefinerBlock.LIT), "an unpowered Refiner was lit");
            helper.succeed();
        });
    }

    /** A different item fills the output, so the next result has nowhere to go: nothing is drawn, and nothing is voided. */
    private static void backsUp(GameTestHelper helper) {
        RefinerBlockEntity refiner = place(helper);
        refiner.inventory().set(RefinerSlots.OUTPUT, ItemResource.of(Items.DIAMOND), 64);
        load(refiner, Items.COBBLESTONE, 1);
        charge(helper, 5000);
        helper.runAfterDelay(30, () -> {
            helper.assertTrue(refiner.energy() == 5000, "a backed-up Refiner drew " + (5000 - refiner.energy()) + " FE");
            helper.assertTrue(refiner.smeltProgress() == 0, "a backed-up Refiner made progress");
            helper.assertTrue(refiner.inventory().getAmountAsInt(RefinerSlots.INPUT) == 1 && output(refiner) == 64,
                    "a backed-up Refiner voided or moved something");
            helper.succeed();
        });
    }

    private static void resetsOnANewRecipe(GameTestHelper helper) {
        RefinerBlockEntity refiner = place(helper);
        load(refiner, Items.COBBLESTONE, 1);
        charge(helper, 9000);
        helper.runAfterDelay(15, () -> {
            helper.assertTrue(refiner.smeltProgress() >= 10, "the cobblestone smelt had made only " + refiner.smeltProgress() + " ticks");
            load(refiner, Items.RAW_IRON, 1);
            helper.runAfterDelay(2, () -> {
                helper.assertTrue(refiner.smeltProgress() <= 2,
                        "a different recipe kept " + refiner.smeltProgress() + " ticks of the last one");
                helper.succeed();
            });
        });
    }

    private static void survivesReload(GameTestHelper helper) {
        RefinerBlockEntity refiner = place(helper);
        load(refiner, Items.COBBLESTONE, 1);
        charge(helper, 9000);
        helper.runAfterDelay(15, () -> {
            int progress = refiner.smeltProgress();
            helper.assertTrue(progress > 0, "no progress to save");
            TagValueOutput saved = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, helper.getLevel().registryAccess());
            refiner.saveCustomOnly(saved);
            RefinerBlockEntity loaded = new RefinerBlockEntity(helper.absolutePos(REFINER), helper.getBlockState(REFINER));
            loaded.setLevel(helper.getLevel());
            loaded.loadCustomOnly(TagValueInput.create(ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), saved.buildResult()));
            helper.assertTrue(loaded.smeltProgress() == progress, "the Refiner read back " + loaded.smeltProgress() + " ticks, not " + progress);
            helper.assertTrue(loaded.energy() == refiner.energy(), "the Refiner read back " + loaded.energy() + " FE, not " + refiner.energy());
            helper.assertTrue(loaded.inventory().getResource(RefinerSlots.INPUT).equals(ItemResource.of(Items.COBBLESTONE)),
                    "the Refiner read back a different input");
            helper.succeed();
        });
    }

    /**
     * Every side is the same face. A slot-less insert and extract go through the face's own rules, so a pipe cannot
     * take the input mid-smelt or put anything in the output.
     */
    private static void itemFace(GameTestHelper helper) {
        RefinerBlockEntity refiner = place(helper);
        for (Direction side : Direction.values()) {
            ResourceHandler<ItemResource> face = items(helper, side);
            helper.assertTrue(face != null, "the Refiner showed no item capability on " + side);
            ItemResource raw = ItemResource.of(Items.RAW_IRON);
            refiner.inventory().set(RefinerSlots.INPUT, ItemResource.EMPTY, 0);
            refiner.inventory().set(RefinerSlots.OUTPUT, ItemResource.EMPTY, 0);
            try (Transaction tx = Transaction.openRoot()) {
                helper.assertTrue(face.insert(ItemResource.of(Items.STICK), 64, tx) == 0, "a stick went in on " + side);
                helper.assertTrue(face.insert(ItemResource.of(Items.COAL), 64, tx) == 0, "coal went in on " + side);
                helper.assertTrue(face.insert(raw, 64, tx) == 2, "the Overload Limit let in another count of raw iron on " + side);
                helper.assertTrue(face.insert(raw, 64, tx) == 0, "raw iron went in past the limit on " + side);
                helper.assertTrue(face.insert(RefinerSlots.OUTPUT, ItemResource.of(Items.IRON_INGOT), 1, tx) == 0,
                        "an item was put in the output on " + side);
                helper.assertTrue(face.extract(raw, 64, tx) == 0, "the input was taken on " + side);
                helper.assertTrue(face.extract(RefinerSlots.INPUT, raw, 64, tx) == 0, "the input was taken by slot on " + side);
            }
            refiner.inventory().set(RefinerSlots.OUTPUT, ItemResource.of(Items.IRON_INGOT), 3);
            try (Transaction tx = Transaction.openRoot()) {
                helper.assertTrue(face.extract(ItemResource.of(Items.IRON_INGOT), 64, tx) == 3, "the output was not taken on " + side);
            }
        }
        helper.succeed();
    }

    /** A cable fills it from any side and cannot pull back what it delivered; a probe that aborts leaves nothing. */
    private static void energyFace(GameTestHelper helper) {
        RefinerBlockEntity refiner = place(helper);
        int capacity = CraftworksConfig.refinerBuffer();
        for (Direction side : Direction.values()) {
            EnergyHandler face = helper.getLevel().getCapability(Capabilities.Energy.BLOCK, helper.absolutePos(REFINER), side);
            helper.assertTrue(face != null, "the Refiner showed no energy capability on " + side);
            try (Transaction probe = Transaction.openRoot()) {
                helper.assertTrue(face.insert(Integer.MAX_VALUE, probe) == capacity, "the probe on " + side + " found no room for the buffer");
            }
            helper.assertTrue(refiner.energy() == 0, "an aborted probe on " + side + " left " + refiner.energy() + " FE");
        }
        charge(helper, 1000);
        for (Direction side : Direction.values()) {
            EnergyHandler face = helper.getLevel().getCapability(Capabilities.Energy.BLOCK, helper.absolutePos(REFINER), side);
            try (Transaction tx = Transaction.openRoot()) {
                helper.assertTrue(face.extract(1000, tx) == 0, "energy was taken back out on " + side);
            }
        }
        helper.assertTrue(refiner.energy() == 1000, "the Refiner held " + refiner.energy() + " FE, not 1000");
        helper.succeed();
    }

    private static void screen(GameTestHelper helper) {
        RefinerBlockEntity refiner = place(helper);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.getInventory().setItem(0, new ItemStack(Items.RAW_IRON, 64));
        player.getInventory().setItem(1, new ItemStack(Items.STICK, 64));
        RefinerMenu menu = (RefinerMenu) refiner.createMenu(0, player.getInventory(), player);
        helper.assertTrue(!menu.slots.get(RefinerSlots.INPUT).mayPlace(new ItemStack(Items.STICK)), "the input took a stick");
        helper.assertTrue(!menu.slots.get(RefinerSlots.OUTPUT).mayPlace(new ItemStack(Items.IRON_INGOT)), "the output took an item");
        menu.quickMoveStack(player, hotbar(menu, player, 1));
        helper.assertTrue(refiner.inventory().getAmountAsInt(RefinerSlots.INPUT) == 0, "a shift-click placed a stick");
        menu.quickMoveStack(player, hotbar(menu, player, 0));
        // The hand is not held to the Overload Limit: a stack of 64 goes in whole.
        helper.assertTrue(refiner.inventory().getAmountAsInt(RefinerSlots.INPUT) == 64,
                "a shift-click placed " + refiner.inventory().getAmountAsInt(RefinerSlots.INPUT) + " raw iron, not 64");
        helper.succeed();
    }

    private static int hotbar(RefinerMenu menu, Player player, int slotIndex) {
        for (Slot slot : menu.slots) {
            if (slot.container == player.getInventory() && slot.getContainerSlot() == slotIndex) {
                return slot.index;
            }
        }
        throw new IllegalStateException("the Refiner's menu has no hotbar slot " + slotIndex);
    }

    private static void poweredByTheSource(GameTestHelper helper) {
        RefinerBlockEntity refiner = place(helper);
        load(refiner, Items.RAW_IRON, 2);
        helper.setBlock(SOURCE, Assemblers.CREATIVE_ENERGY_SOURCE.get());
        helper.succeedWhen(() -> helper.assertTrue(output(refiner) == 2, "made " + output(refiner) + " of 2 iron ingots yet"));
    }
}
