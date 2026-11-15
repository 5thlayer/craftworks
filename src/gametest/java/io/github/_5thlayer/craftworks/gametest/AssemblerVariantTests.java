// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.gametest;

import io.github._5thlayer.craftworks.gametest.AssemblerMachineTests.Placed;
import io.github._5thlayer.craftworks.machine.AssemblerSlots;
import io.github._5thlayer.craftworks.machine.AssemblerTier;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * An Assembler takes its ingredient whatever components it carries (#87): a variant its input has no room for goes
 * to an input the Held recipe doesn't use, a spare, and a craft takes from it; a result the outputs have no room
 * for goes to a spare too, and extracts from there.
 */
final class AssemblerVariantTests {

    private AssemblerVariantTests() {
    }

    static void register(CraftworksGameTests.Registrar tests) {
        tests.test("variant_of_a_held_ingredient_goes_to_a_spare_input_and_is_crafted", 40, AssemblerVariantTests::spareInput);
        tests.test("variant_a_spare_holding_another_refuses", 20, AssemblerVariantTests::spareHoldsOne);
        tests.test("variant_in_a_spare_counts_toward_the_overload_limit", 20, AssemblerVariantTests::sharedLimit);
        tests.test("variant_result_with_no_output_room_goes_to_a_spare_and_extracts", 40, AssemblerVariantTests::spareOutput);
    }

    private static ItemResource named(Item item, String name) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        return ItemResource.of(stack);
    }

    private static int insert(ResourceHandler<ItemResource> face, ItemResource resource, int amount) {
        try (Transaction tx = Transaction.openRoot()) {
            int inserted = face.insert(resource, amount, tx);
            tx.commit();
            return inserted;
        }
    }

    private static void spareInput(GameTestHelper helper) {
        Placed assembler = AssemblerMachineTests.place(helper, AssemblerTier.THREE);
        AssemblerMachineTests.hold(assembler, AssemblerTests.OAK_SAPLING);
        var inventory = assembler.machine().inventory();
        inventory.set(0, ItemResource.of(Items.OAK_LOG), 1);
        ResourceHandler<ItemResource> face = assembler.items(Direction.SOUTH);
        ItemResource bark = named(Items.OAK_LOG, "Bark");
        int took = insert(face, bark, 1);
        helper.assertTrue(took == 1, "the item face took " + took + " named logs, not 1");
        helper.assertTrue(inventory.getResource(1).equals(bark), "the named log is not in the first spare: " + inventory.getResource(1));
        AssemblerFluidTests.tick(helper, assembler, 20);
        helper.assertTrue(AssemblerMachineTests.count(assembler, AssemblerSlots.OUTPUT) == 1,
                "a plain and a named log made no sapling");
        helper.assertTrue(AssemblerMachineTests.count(assembler, 0) == 0 && AssemblerMachineTests.count(assembler, 1) == 0,
                "the craft left logs behind");
        helper.succeed();
    }

    private static void spareHoldsOne(GameTestHelper helper) {
        Placed assembler = AssemblerMachineTests.place(helper, AssemblerTier.THREE);
        AssemblerMachineTests.hold(assembler, AssemblerTests.OAK_SAPLING);
        var inventory = assembler.machine().inventory();
        inventory.set(0, ItemResource.of(Items.OAK_LOG), 1);
        for (int spare = 1; spare < AssemblerSlots.INPUTS; spare++) {
            inventory.set(spare, named(Items.OAK_LOG, "Log " + spare), 1);
        }
        int took = insert(assembler.items(Direction.SOUTH), named(Items.OAK_LOG, "Another"), 1);
        helper.assertTrue(took == 0, "with every spare holding another variant the face took " + took);
        helper.succeed();
    }

    private static void spareOutput(GameTestHelper helper) {
        Placed assembler = AssemblerMachineTests.place(helper, AssemblerTier.THREE);
        AssemblerMachineTests.hold(assembler, AssemblerTests.OAK_SAPLING);
        var inventory = assembler.machine().inventory();
        for (int output = 0; output < AssemblerSlots.OUTPUTS; output++) {
            inventory.set(AssemblerSlots.output(output), named(Items.OAK_SAPLING, "Sapling " + output), 1);
        }
        inventory.set(0, ItemResource.of(Items.OAK_LOG), 2);
        AssemblerFluidTests.tick(helper, assembler, 20);
        helper.assertTrue(inventory.getResource(1).equals(ItemResource.of(Items.OAK_SAPLING))
                        && AssemblerMachineTests.count(assembler, 1) == 1,
                "the sapling did not go to the first spare: " + inventory.getResource(1));
        try (Transaction tx = Transaction.openRoot()) {
            int took = assembler.items(Direction.SOUTH).extract(ItemResource.of(Items.OAK_SAPLING), 5, tx);
            helper.assertTrue(took == 1, "an extract with no slot named took " + took + " plain saplings, not the 1 in the spare");
        }
        inventory.set(2, named(Items.OAK_LOG, "Bark"), 1);
        try (Transaction tx = Transaction.openRoot()) {
            int took = assembler.items(Direction.SOUTH).extract(2, named(Items.OAK_LOG, "Bark"), 1, tx);
            helper.assertTrue(took == 0, "a spare extracted an ingredient");
        }
        helper.succeed();
    }

    /** Sapling at tier 1 holds 6 logs: a plain 4 in the input leaves room for 2 named ones in a spare, and no more. */
    private static void sharedLimit(GameTestHelper helper) {
        Placed assembler = AssemblerMachineTests.place(helper, AssemblerTier.ONE);
        AssemblerMachineTests.hold(assembler, AssemblerTests.OAK_SAPLING);
        assembler.machine().inventory().set(0, ItemResource.of(Items.OAK_LOG), 4);
        int took = insert(assembler.items(Direction.SOUTH), named(Items.OAK_LOG, "Bark"), 64);
        helper.assertTrue(took == 2, "a spare took " + took + " named logs, not the 2 the Overload Limit leaves");
        helper.succeed();
    }
}
