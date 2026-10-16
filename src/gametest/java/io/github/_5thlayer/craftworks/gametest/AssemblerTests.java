// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.gametest;

import io.github._5thlayer.craftworks.Craftworks;
import io.github._5thlayer.craftworks.assembler.FillRequest;
import io.github._5thlayer.craftworks.assembler.PersonalAssembler;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.neoforged.neoforge.event.EventHooks;

/**
 * The Plan queue on a real player: Fill Recipe queues on the server, the player's tick delivers,
 * the queue is saved with the player, and death refunds it before the items drop.
 *
 * <p>The queue's own rules (pausing on a full inventory, cancelling, remainders) are JUnit's, in
 * {@code PlanQueueTest}. What is checked here is the glue: the attachment, the tick listener and
 * the death listener.
 *
 * <p>The player is a real {@link ServerPlayer} whose connection sends nothing, placed in the test's level
 * but on no player list. Vanilla's mock player joins the player list over a connection that never
 * negotiated the Mod's payloads, so the first sync to it throws; a real client always has. Nothing ticks
 * a player with no client either, so a test that needs the queue to run posts the tick event itself.
 */
final class AssemblerTests {

    /** Two oak logs make one sapling, in 10 ticks: the game tests' own recipe. */
    static final Identifier OAK_SAPLING = Identifier.fromNamespaceAndPath(Craftworks.MOD_ID, "gametest/oak_sapling");

    private AssemblerTests() {
    }

    static void register(CraftworksGameTests.Registrar tests) {
        tests.test("fill_recipe_queues_and_the_tick_delivers", 100, AssemblerTests::queuesAndDelivers);
        tests.test("fill_recipe_counts_are_decided_on_the_server", 20, AssemblerTests::countsOnTheServer);
        tests.test("the_queue_survives_a_save_and_reload", 20, AssemblerTests::survivesSaveAndReload);
        tests.test("death_refunds_the_queue_before_items_drop", 20, AssemblerTests::deathRefunds);
    }

    private static void queuesAndDelivers(GameTestHelper helper) {
        ServerPlayer player = playerHolding(helper, new ItemStack(Items.OAK_LOG, 3));
        PersonalAssembler.fill(player, OAK_SAPLING, FillRequest.ONE);
        helper.assertTrue(PersonalAssembler.queueOf(player).entries().size() == 1,
                "a left click on Fill Recipe queued " + PersonalAssembler.queueOf(player).entries().size() + " row(s), not 1");
        helper.assertTrue(count(player, Items.OAK_LOG) == 1,
                "queueing takes the whole cost at once: 1 log should be left of 3, and " + count(player, Items.OAK_LOG) + " are");
        helper.onEachTick(() -> EventHooks.firePlayerTickPost(player));
        helper.succeedWhen(() -> {
            helper.assertTrue(count(player, Items.OAK_SAPLING) == 1, "no sapling delivered yet");
            helper.assertTrue(PersonalAssembler.queueOf(player).isEmpty(), "the finished plan is still queued");
        });
    }

    /** Five never becomes three, and Shift takes as many as the inventory covers. */
    private static void countsOnTheServer(GameTestHelper helper) {
        ServerPlayer player = playerHolding(helper, new ItemStack(Items.OAK_LOG, 6));
        PersonalAssembler.fill(player, OAK_SAPLING, FillRequest.FIVE);
        helper.assertTrue(PersonalAssembler.queueOf(player).isEmpty() && count(player, Items.OAK_LOG) == 6,
                "a right click with logs for 3 saplings queued something; five never becomes three");
        PersonalAssembler.fill(player, OAK_SAPLING, FillRequest.ALL);
        int queued = PersonalAssembler.queueOf(player).entries().stream().mapToInt(row -> row.remainingRootCrafts()).sum();
        helper.assertTrue(queued == 3 && count(player, Items.OAK_LOG) == 0,
                "Shift with logs for 3 saplings queued " + queued + " craft(s) and left " + count(player, Items.OAK_LOG) + " log(s)");
        helper.succeed();
    }

    private static void survivesSaveAndReload(GameTestHelper helper) {
        ServerPlayer player = playerHolding(helper, new ItemStack(Items.OAK_LOG, 4));
        PersonalAssembler.fill(player, OAK_SAPLING, FillRequest.ALL);
        var before = PersonalAssembler.queueOf(player).entries();
        helper.assertTrue(before.size() == 1, "nothing was queued to save");

        TagValueOutput saved = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, player.registryAccess());
        player.saveWithoutId(saved);

        // Read back into a new player, which is what a login does with a save.
        ServerPlayer reloaded = TestConnection.player(helper);
        reloaded.load(TagValueInput.create(ProblemReporter.DISCARDING, reloaded.registryAccess(), saved.buildResult()));
        var after = PersonalAssembler.queueOf(reloaded).entries();
        helper.assertTrue(after.equals(before), "the queue was saved as " + before + " and read back as " + after);
        helper.succeed();
    }

    /**
     * What a queued plan held follows the normal death rules: it drops with the rest of the inventory,
     * rather than vanishing with the queue.
     */
    private static void deathRefunds(GameTestHelper helper) {
        ServerPlayer player = playerHolding(helper, new ItemStack(Items.OAK_LOG, 4));
        PersonalAssembler.fill(player, OAK_SAPLING, FillRequest.ALL);
        helper.assertTrue(count(player, Items.OAK_LOG) == 0, "the plan did not take the logs, so a refund proves nothing");

        player.die(helper.getLevel().damageSources().generic());
        helper.assertTrue(PersonalAssembler.queueOf(player).isEmpty(), "the queue outlived its player's death");
        int dropped = helper.getLevel().getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(8)).stream()
                .map(ItemEntity::getItem)
                .filter(stack -> stack.is(Items.OAK_LOG))
                .mapToInt(ItemStack::getCount)
                .sum();
        helper.assertTrue(dropped == 4, "the queue held 4 logs and " + dropped + " dropped");
        helper.succeed();
    }

    static ServerPlayer playerHolding(GameTestHelper helper, ItemStack stack) {
        ServerPlayer player = TestConnection.player(helper);
        player.getInventory().add(stack);
        return player;
    }

    static int count(ServerPlayer player, Item item) {
        return player.getInventory().getNonEquipmentItems().stream()
                .filter(stack -> stack.is(item))
                .mapToInt(ItemStack::getCount)
                .sum();
    }
}
