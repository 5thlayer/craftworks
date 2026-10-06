// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.gametest;

import io.github._5thlayer.craftworks.CraftworksConfig;
import io.github._5thlayer.craftworks.machine.AssemblerBlockEntity;
import io.github._5thlayer.craftworks.machine.AssemblerSlots;
import io.github._5thlayer.craftworks.machine.AssemblerTier;
import io.github._5thlayer.craftworks.machine.Assemblers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * The Creative Energy Source on a real server (#23): it powers an Assembler that has no other power, fills
 * any neighbour's energy handler, and accepts nothing.
 */
final class CreativeEnergySourceTests {

    /** The source's own position, touching the east face of the Assembler placed at the platform's middle. */
    private static final BlockPos SOURCE = new BlockPos(6, 1, 4);

    private static final BlockPos ABOVE = new BlockPos(4, 3, 4);

    private CreativeEnergySourceTests() {
    }

    static void register(CraftworksGameTests.Registrar tests) {
        tests.test("an_assembler_next_to_a_creative_energy_source_crafts_its_held_recipe_with_no_other_power", 100,
                CreativeEnergySourceTests::powersAnAssembler);
        tests.test("a_creative_energy_source_fills_any_neighbours_energy_handler_and_accepts_nothing", 20,
                CreativeEnergySourceTests::fillsAndAcceptsNothing);
    }

    private static void powersAnAssembler(GameTestHelper helper) {
        AssemblerMachineTests.Placed assembler = AssemblerMachineTests.place(helper, AssemblerTier.ONE);
        AssemblerMachineTests.hold(assembler, AssemblerTests.OAK_SAPLING);
        AssemblerMachineTests.insert(assembler, 0, Items.OAK_LOG, 2);
        helper.setBlock(SOURCE, Assemblers.CREATIVE_ENERGY_SOURCE.get());
        // The world's own ticker runs both: nothing here feeds the Assembler.
        helper.succeedWhen(() -> {
            AssemblerBlockEntity machine = assembler.machine();
            helper.assertTrue(machine.inventory().getAmountAsInt(AssemblerSlots.PRODUCT) == 1, "no sapling made yet");
        });
    }

    /** Whatever a neighbour's handler takes, it is given, on every face; and the source's own takes none. */
    private static void fillsAndAcceptsNothing(GameTestHelper helper) {
        AssemblerMachineTests.Placed assembler = AssemblerMachineTests.place(helper, AssemblerTier.ONE);
        // One beside the footprint and one on top of it, so two different faces feed the one handler.
        helper.setBlock(SOURCE, Assemblers.CREATIVE_ENERGY_SOURCE.get());
        helper.setBlock(ABOVE, Assemblers.CREATIVE_ENERGY_SOURCE.get());
        for (Direction side : Direction.values()) {
            EnergyHandler source = helper.getLevel().getCapability(Capabilities.Energy.BLOCK, helper.absolutePos(SOURCE), side);
            helper.assertTrue(source != null, "the source showed no energy capability on " + side);
            helper.assertTrue(source.getAmountAsLong() == Long.MAX_VALUE && source.getCapacityAsLong() == Long.MAX_VALUE,
                    "the source reported " + source.getAmountAsLong() + " of " + source.getCapacityAsLong());
            helper.assertTrue(source.getAmountAsInt() == Integer.MAX_VALUE && source.getCapacityAsInt() == Integer.MAX_VALUE,
                    "the source's int forms were " + source.getAmountAsInt() + " of " + source.getCapacityAsInt());
            try (Transaction tx = Transaction.openRoot()) {
                helper.assertTrue(source.insert(1000, tx) == 0, "the source accepted energy on " + side);
                helper.assertTrue(source.extract(Integer.MAX_VALUE, tx) == Integer.MAX_VALUE, "the source ran short on " + side);
            }
        }
        // The first tick fills the buffer: the Assembler takes what it can hold, and the source gives it all.
        int capacity = CraftworksConfig.buffer(AssemblerTier.ONE);
        helper.succeedWhen(() -> helper.assertTrue(assembler.machine().energy() == capacity,
                "the Assembler held " + assembler.machine().energy() + " FE, not its " + capacity));
    }
}
