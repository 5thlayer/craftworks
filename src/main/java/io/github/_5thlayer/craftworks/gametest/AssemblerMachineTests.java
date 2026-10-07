// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.gametest;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import com.mojang.authlib.GameProfile;
import io.github._5thlayer.craftworks.Craftworks;
import io.github._5thlayer.craftworks.CraftworksConfig;
import io.github._5thlayer.craftworks.api.LockHooks;
import io.github._5thlayer.craftworks.machine.AssemblerBlockEntity;
import io.github._5thlayer.craftworks.machine.AssemblerMenu;
import io.github._5thlayer.craftworks.machine.HeldRecipeView;
import io.github._5thlayer.craftworks.machine.AssemblerSlots;
import io.github._5thlayer.craftworks.machine.AssemblerTier;
import io.github._5thlayer.craftworks.machine.Assemblers;
import io.github._5thlayer.craftworks.machine.HeldRecipes;
import io.github._5thlayer.craftworks.machine.HoldVerdict;
import io.github._5thlayer.craftworks.network.HeldRecipeSyncPacket;
import io.github._5thlayer.craftworks.recipe.AssemblingCategory;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.game.ClientboundContainerSetDataPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.network.payload.AdvancedContainerSetDataPayload;
import net.neoforged.neoforge.network.payload.AdvancedOpenScreenPayload;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandlerUtil;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jspecify.annotations.Nullable;

/**
 * The Assemblers on a real player and a real server (#21): placed whole from their item, powered through
 * a plain energy handler, set with Fill Recipe on the open screen, and crafting their Held recipe.
 *
 * <p>The craft is ticked by hand in a loop where the exact tick it finishes on is the point; one test lets
 * the world's own ticker run instead. The loop is the block entity's own {@code serverTick}, so what it
 * checks is the production path with a counted clock.
 */
final class AssemblerMachineTests {

    /** Two oak logs make one sapling, in 10 ticks. */
    private static final Identifier SAPLING = AssemblerTests.OAK_SAPLING;
    private static final Identifier STICK = Identifier.parse("minecraft:stick");
    private static final Identifier CAKE = Identifier.parse("minecraft:cake");
    private static final Identifier MACHINE_ONLY = id("gametest/machine_only");
    private static final Identifier SIX_INGREDIENTS = id("gametest/six_ingredients");
    private static final Identifier TWO_REMAINDERS = id("gametest/two_remainders");

    /** Only players carrying this tag are locked out of the sapling by the tests' hook. */
    private static final String LOCKED = "craftworks.gametest.assembler_locked";

    /** The origin stands at the middle of the platform's floor, so the 3x2x3 footprint fits whichever way it faces. */
    static final BlockPos ORIGIN = new BlockPos(4, 1, 4);

    private AssemblerMachineTests() {
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(Craftworks.MOD_ID, path);
    }

    static void register(CraftworksGameTests.Registrar tests) {
        LockHooks.register((player, recipe) -> player.entityTags().contains(LOCKED) && recipe.equals(SAPLING));
        // Oak sapling: 10 ticks, so 20, 14 and 8 at 0.5, 0.75 and 1.25; 37.5, 75 and 187.5 FE a tick make 750, 1000 and 1500.
        tests.test("an_assembler_1_crafts_its_held_recipe_in_the_recipe_time_over_its_speed", 20,
                helper -> craftsInTime(helper, AssemblerTier.ONE, 20, 750));
        tests.test("an_assembler_2_crafts_its_held_recipe_in_the_recipe_time_over_its_speed", 20,
                helper -> craftsInTime(helper, AssemblerTier.TWO, 14, 1000));
        tests.test("an_assembler_3_crafts_its_held_recipe_in_the_recipe_time_over_its_speed", 20,
                helper -> craftsInTime(helper, AssemblerTier.THREE, 8, 1500));
        tests.test("an_assembler_ticks_and_crafts_on_its_own_when_powered", 100, AssemblerMachineTests::craftsOnItsOwn);
        tests.test("an_unpowered_assembler_makes_no_progress", 120, AssemblerMachineTests::unpowered);
        tests.test("an_assembler_with_no_recipe_draws_nothing", 20, AssemblerMachineTests::idleDrawsNothing);
        tests.test("fill_recipe_with_a_recipe_locked_for_that_player_is_refused", 20, AssemblerMachineTests::lockedIsRefused);
        tests.test("a_held_recipe_is_never_checked_against_the_lock_again", 20, AssemblerMachineTests::heldIsNeverRechecked);
        tests.test("fill_recipe_refuses_more_than_five_ingredients_and_remainders_that_dont_fit", 20, AssemblerMachineTests::cannotRun);
        tests.test("an_assembler_holds_and_crafts_a_recipe_the_player_cannot_hand_craft", 20, AssemblerMachineTests::machineOnly);
        tests.test("breaking_an_assembler_drops_its_contents_and_the_item_keeps_its_held_recipe", 20, AssemblerMachineTests::breaking);
        tests.test("a_cake_craft_puts_its_buckets_in_the_remainder_slot", 20, AssemblerMachineTests::cake);
        tests.test("a_craft_waits_while_the_product_slot_is_full", 20, AssemblerMachineTests::waitsOnAFullProduct);
        tests.test("a_craft_waits_while_the_remainder_slot_cannot_take_its_remainders", 20, AssemblerMachineTests::waitsOnRemainders);
        tests.test("changing_the_held_recipe_hands_the_inputs_back", 20, AssemblerMachineTests::changingHandsBack);
        tests.test("the_item_capability_is_on_every_face_of_every_block_and_only_the_outputs_extract", 20, AssemblerMachineTests::itemFaces);
        tests.test("inserts_are_filtered_to_the_held_recipe_and_capped_by_the_overload_limit", 20, AssemblerMachineTests::filteredAndCapped);
        tests.test("a_higher_tier_placed_over_an_assembler_swaps_it_whole_and_a_lower_one_swaps_it_back", 20, AssemblerMachineTests::fastReplace);
        tests.test("the_held_recipe_and_contents_survive_a_save_and_reload", 20, AssemblerMachineTests::survivesReload);
        tests.test("the_open_assemblers_held_recipe_crosses_to_the_client", 20, AssemblerMachineTests::heldReachesTheClient);
        tests.test("the_assemblers_energy_above_a_short_crosses_to_the_client_whole", 20, AssemblerMachineTests::energyReachesTheClient);
        tests.test("each_tier_starts_from_its_default_speed_power_and_buffer", 20, AssemblerMachineTests::configDefaults);
        tests.test("craftworks_names_no_wireworks_pipeworks_or_factoryworks_type", 20, AssemblerMachineTests::namesNoSiblingType);
    }

    // -- the craft ------------------------------------------------------------------------------

    /** Powered through a plain handler, an Assembler finishes on the tick its tier's speed gives, spending the price once. */
    private static void craftsInTime(GameTestHelper helper, AssemblerTier tier, int ticks, int fe) {
        Placed assembler = place(helper, tier);
        hold(assembler, SAPLING);
        insert(assembler, 0, Items.OAK_LOG, 2);
        SimpleEnergyHandler supply = supply();
        int inserted = 0;
        int ran = 0;
        while (count(assembler, AssemblerSlots.PRODUCT) == 0 && ran < 100) {
            inserted += feed(assembler, supply, 1000);
            assembler.machine().serverTick(helper.getLevel());
            ran++;
        }
        helper.assertTrue(ran == ticks, tier + " finished its craft after " + ran + " ticks, not " + ticks);
        helper.assertTrue(assembler.machine().inventory().getResource(AssemblerSlots.PRODUCT).getItem() == Items.OAK_SAPLING,
                "the product was not a sapling");
        helper.assertTrue(count(assembler, 0) == 0, "the craft left " + count(assembler, 0) + " log(s)");
        int spent = inserted - assembler.machine().energy();
        helper.assertTrue(spent == fe, tier + " spent " + spent + " FE on a craft, not " + fe);
        helper.succeed();
    }

    /** The world's own ticker runs the Assembler, so what the loop above counts is what a server does. */
    private static void craftsOnItsOwn(GameTestHelper helper) {
        Placed assembler = place(helper, AssemblerTier.THREE);
        hold(assembler, SAPLING);
        insert(assembler, 0, Items.OAK_LOG, 2);
        SimpleEnergyHandler supply = supply();
        helper.onEachTick(() -> feed(assembler, supply, 1000));
        helper.succeedWhen(() -> helper.assertTrue(count(assembler, AssemblerSlots.PRODUCT) == 1, "no sapling made yet"));
    }

    private static void unpowered(GameTestHelper helper) {
        Placed assembler = place(helper, AssemblerTier.ONE);
        hold(assembler, SAPLING);
        insert(assembler, 0, Items.OAK_LOG, 2);
        // Nothing in the buffer, and then less than a tick's share of a craft: neither moves it on.
        helper.runAtTickTime(60, () -> {
            helper.assertTrue(assembler.machine().craftProgress() == 0, "an unpowered Assembler made progress");
            EnergyHandler face = assembler.energy(Direction.UP);
            try (Transaction tx = Transaction.openRoot()) {
                helper.assertTrue(face.insert(1, tx) == 1, "the buffer took no energy");
                tx.commit();
            }
        });
        helper.runAtTickTime(110, () -> {
            helper.assertTrue(assembler.machine().craftProgress() == 0, "a tick it could not pay in full made progress");
            helper.assertTrue(assembler.machine().energy() == 1, "a tick it could not pay in full took the energy it had");
            helper.assertTrue(count(assembler, 0) == 2 && count(assembler, AssemblerSlots.PRODUCT) == 0, "it spent its inputs");
            helper.succeed();
        });
    }

    private static void idleDrawsNothing(GameTestHelper helper) {
        Placed assembler = place(helper, AssemblerTier.TWO);
        SimpleEnergyHandler supply = supply();
        feed(assembler, supply, 5000);
        for (int tick = 0; tick < 20; tick++) {
            assembler.machine().serverTick(helper.getLevel());
        }
        helper.assertTrue(assembler.machine().energy() == 5000, "an Assembler with no recipe drew power");
        helper.succeed();
    }

    // -- Fill Recipe ----------------------------------------------------------------------------

    private static void lockedIsRefused(GameTestHelper helper) {
        Placed assembler = place(helper, AssemblerTier.ONE);
        assembler.player().addTag(LOCKED);
        HoldVerdict verdict = request(assembler, SAPLING);
        helper.assertTrue(verdict == HoldVerdict.LOCKED, "a recipe Locked for the player was " + verdict);
        helper.assertTrue(assembler.machine().heldRecipe().isEmpty(), "a refused recipe was held");
        helper.assertTrue(assembler.player().heard.contains("craftworks.assembler.refused.locked"),
                "the player was told " + assembler.player().heard);
        // The same recipe for a player it is not Locked for is held.
        assembler.player().removeTag(LOCKED);
        helper.assertTrue(request(assembler, SAPLING) == HoldVerdict.HELD, "an unlocked recipe was refused");
        helper.assertTrue(assembler.machine().heldRecipe().equals(Optional.of(SAPLING)), "the recipe was not held");
        helper.succeed();
    }

    /** Once held, the recipe crafts whoever is online and whatever the Lock source says now (ADR-0013). */
    private static void heldIsNeverRechecked(GameTestHelper helper) {
        Placed assembler = place(helper, AssemblerTier.THREE);
        hold(assembler, SAPLING);
        assembler.player().addTag(LOCKED);
        insert(assembler, 0, Items.OAK_LOG, 2);
        SimpleEnergyHandler supply = supply();
        for (int tick = 0; tick < 8; tick++) {
            feed(assembler, supply, 1000);
            assembler.machine().serverTick(helper.getLevel());
        }
        helper.assertTrue(count(assembler, AssemblerSlots.PRODUCT) == 1, "a held recipe stopped crafting once it was Locked");
        helper.succeed();
    }

    private static void cannotRun(GameTestHelper helper) {
        Placed assembler = place(helper, AssemblerTier.TWO);
        HoldVerdict many = request(assembler, SIX_INGREDIENTS);
        HoldVerdict remainders = request(assembler, TWO_REMAINDERS);
        HoldVerdict none = request(assembler, id("gametest/not_a_recipe"));
        helper.assertTrue(many == HoldVerdict.TOO_MANY_INGREDIENTS, "six ingredients was " + many);
        helper.assertTrue(remainders == HoldVerdict.REMAINDERS_DONT_FIT, "a bucket and a bottle left behind was " + remainders);
        helper.assertTrue(none == HoldVerdict.NOT_ASSEMBLING, "an id naming no recipe was " + none);
        helper.assertTrue(assembler.machine().heldRecipe().isEmpty(), "a refused recipe was held");
        helper.assertTrue(assembler.player().heard.containsAll(List.of("craftworks.assembler.refused.too_many_ingredients",
                "craftworks.assembler.refused.remainders_dont_fit", "craftworks.assembler.refused.not_assembling")),
                "the player was told " + assembler.player().heard);
        helper.succeed();
    }

    /** Every tier makes every other Assembling recipe, Hand-craftable or not. */
    private static void machineOnly(GameTestHelper helper) {
        Placed assembler = place(helper, AssemblerTier.ONE);
        helper.assertTrue(request(assembler, MACHINE_ONLY) == HoldVerdict.HELD, "a recipe only machines make was refused");
        insert(assembler, 0, Items.IRON_INGOT, 1);
        SimpleEnergyHandler supply = supply();
        for (int tick = 0; tick < 20; tick++) {
            feed(assembler, supply, 1000);
            assembler.machine().serverTick(helper.getLevel());
        }
        helper.assertTrue(count(assembler, AssemblerSlots.PRODUCT) == 9, "an iron ingot made " + count(assembler, AssemblerSlots.PRODUCT) + " nuggets, not 9");
        helper.succeed();
    }

    // -- contents -------------------------------------------------------------------------------

    private static void breaking(GameTestHelper helper) {
        Placed assembler = place(helper, AssemblerTier.TWO);
        hold(assembler, SAPLING);
        insert(assembler, 0, Items.OAK_LOG, 4);
        assembler.machine().inventory().set(AssemblerSlots.PRODUCT, ItemResource.of(Items.OAK_SAPLING), 3);
        feed(assembler, supply(), 1000);
        // A part, not the origin: the whole footprint goes, and the contents with it.
        BlockPos part = helper.absolutePos(ORIGIN.above().north().east());
        assembler.player().gameMode.destroyBlock(part);
        for (BlockPos pos : footprint(helper, assembler.tier(), assembler.facing())) {
            helper.assertTrue(helper.getLevel().getBlockState(pos).isAir(), "the break left " + helper.getLevel().getBlockState(pos) + " at " + pos);
        }
        List<ItemStack> drops = helper.getEntities(EntityType.ITEM).stream().map(ItemEntity::getItem).toList();
        helper.assertTrue(total(drops, Items.OAK_LOG) == 4, "the break dropped " + total(drops, Items.OAK_LOG) + " log(s), not the 4 inside");
        helper.assertTrue(total(drops, Items.OAK_SAPLING) == 3, "the break dropped " + total(drops, Items.OAK_SAPLING) + " sapling(s), not the 3 inside");
        ItemStack item = drops.stream().filter(stack -> stack.is(Assemblers.item(AssemblerTier.TWO).get())).findFirst().orElse(ItemStack.EMPTY);
        helper.assertTrue(total(drops, Assemblers.item(AssemblerTier.TWO).get()) == 1, "the break dropped " + total(drops, Assemblers.item(AssemblerTier.TWO).get()) + " Assembler items, not 1");
        helper.assertTrue(SAPLING.equals(item.get(Assemblers.HELD_RECIPE.get())), "the dropped item holds " + item.get(Assemblers.HELD_RECIPE.get()) + ", not the Held recipe");

        // Placed again, the item brings its Held recipe back, and the energy was lost.
        Placed again = placeItem(helper, AssemblerTier.TWO, item, Direction.WEST);
        helper.assertTrue(again.machine().heldRecipe().equals(Optional.of(SAPLING)), "the item placed again held " + again.machine().heldRecipe());
        helper.assertTrue(again.machine().energy() == 0, "the energy survived the break");
        helper.succeed();
    }

    private static void cake(GameTestHelper helper) {
        Placed assembler = place(helper, AssemblerTier.THREE);
        hold(assembler, CAKE);
        // Cake is 3 milk buckets, 2 sugar, an egg and 3 wheat, a bag read off the recipe: a bucket stacks to 1,
        // so a slot that takes it must still hold the 3 a craft needs.
        List<SizedIngredient> bag = HeldRecipes.find(helper.getLevel(), CAKE).orElseThrow().value().ingredients();
        helper.assertTrue(bag.size() == 4, "cake has " + bag.size() + " distinct ingredients, not 4");
        for (int slot = 0; slot < bag.size(); slot++) {
            insert(assembler, slot, firstItem(bag.get(slot)), bag.get(slot).count());
            helper.assertTrue(count(assembler, slot) == bag.get(slot).count(),
                    "slot " + slot + " took " + count(assembler, slot) + " of " + firstItem(bag.get(slot)) + ", not " + bag.get(slot).count());
        }
        SimpleEnergyHandler supply = supply();
        for (int tick = 0; tick < 8 && count(assembler, AssemblerSlots.PRODUCT) == 0; tick++) {
            feed(assembler, supply, 1000);
            assembler.machine().serverTick(helper.getLevel());
        }
        helper.assertTrue(assembler.machine().inventory().getResource(AssemblerSlots.PRODUCT).getItem() == Items.CAKE, "no cake was made");
        helper.assertTrue(assembler.machine().inventory().getResource(AssemblerSlots.REMAINDERS).getItem() == Items.BUCKET
                        && count(assembler, AssemblerSlots.REMAINDERS) == 3,
                "the remainder slot holds " + count(assembler, AssemblerSlots.REMAINDERS) + " of "
                        + assembler.machine().inventory().getResource(AssemblerSlots.REMAINDERS).getItem() + ", not 3 buckets");
        helper.succeed();
    }

    private static Item firstItem(SizedIngredient sized) {
        return sized.ingredient().items().findFirst().orElseThrow().value();
    }

    private static void waitsOnAFullProduct(GameTestHelper helper) {
        Placed assembler = place(helper, AssemblerTier.THREE);
        hold(assembler, SAPLING);
        insert(assembler, 0, Items.OAK_LOG, 2);
        assembler.machine().inventory().set(AssemblerSlots.PRODUCT, ItemResource.of(Items.OAK_SAPLING), 64);
        expectWaiting(helper, assembler);
    }

    /** Cake's buckets cannot join a stick in the remainder slot, so the cake isn't started. */
    private static void waitsOnRemainders(GameTestHelper helper) {
        Placed assembler = place(helper, AssemblerTier.THREE);
        hold(assembler, CAKE);
        List<SizedIngredient> bag = HeldRecipes.find(helper.getLevel(), CAKE).orElseThrow().value().ingredients();
        for (int slot = 0; slot < bag.size(); slot++) {
            insert(assembler, slot, firstItem(bag.get(slot)), bag.get(slot).count());
        }
        assembler.machine().inventory().set(AssemblerSlots.REMAINDERS, ItemResource.of(Items.STICK), 1);
        expectWaiting(helper, assembler);
        helper.assertTrue(count(assembler, 0) == 3, "the milk buckets were spent");
    }

    /** Powered for 30 ticks, an Assembler that can't place its craft makes no progress, spends no input and draws nothing. */
    private static void expectWaiting(GameTestHelper helper, Placed assembler) {
        SimpleEnergyHandler supply = supply();
        int inserted = 0;
        for (int tick = 0; tick < 30; tick++) {
            inserted += feed(assembler, supply, 1000);
            assembler.machine().serverTick(helper.getLevel());
        }
        helper.assertTrue(assembler.machine().craftProgress() == 0, "a craft that could not be placed made progress");
        helper.assertTrue(assembler.machine().energy() == inserted, "a craft that could not be placed drew power");
        helper.assertTrue(count(assembler, AssemblerSlots.PRODUCT) <= 64 && count(assembler, AssemblerSlots.REMAINDERS) <= 1,
                "a craft that could not be placed put something in an output");
        helper.succeed();
    }

    private static void changingHandsBack(GameTestHelper helper) {
        Placed assembler = place(helper, AssemblerTier.ONE);
        hold(assembler, SAPLING);
        insert(assembler, 0, Items.OAK_LOG, 5);
        helper.assertTrue(AssemblerTests.count(assembler.player(), Items.OAK_LOG) == 0, "the player started with logs");
        HoldVerdict verdict = request(assembler, STICK);
        helper.assertTrue(verdict == HoldVerdict.HELD, "a new recipe was " + verdict);
        helper.assertTrue(AssemblerTests.count(assembler.player(), Items.OAK_LOG) == 5,
                "the player was handed " + AssemblerTests.count(assembler.player(), Items.OAK_LOG) + " log(s), not the 5 inside");
        helper.assertTrue(count(assembler, 0) == 0, "the inputs stayed in the slots");
        helper.assertTrue(assembler.machine().craftProgress() == 0, "the craft carried over to the new recipe");
        // Holding the same recipe again moves nothing.
        insert(assembler, 0, Items.OAK_PLANKS, 2);
        request(assembler, STICK);
        helper.assertTrue(count(assembler, 0) == 2, "holding the same recipe again handed the inputs back");
        helper.succeed();
    }

    // -- faces ----------------------------------------------------------------------------------

    private static void itemFaces(GameTestHelper helper) {
        Placed assembler = place(helper, AssemblerTier.ONE);
        hold(assembler, SAPLING);
        int checked = 0;
        for (BlockPos pos : footprint(helper, assembler.tier(), assembler.facing())) {
            for (Direction side : Direction.values()) {
                helper.assertTrue(helper.getLevel().getCapability(Capabilities.Item.BLOCK, pos, side) != null,
                        "no item capability at " + pos + " from the " + side);
                helper.assertTrue(helper.getLevel().getCapability(Capabilities.Energy.BLOCK, pos, side) != null,
                        "no energy capability at " + pos + " from the " + side);
                checked++;
            }
        }
        helper.assertTrue(checked == 18 * 6, "checked " + checked + " faces, not 108");

        ResourceHandler<ItemResource> face = assembler.items(Direction.NORTH);
        assembler.machine().inventory().set(0, ItemResource.of(Items.OAK_LOG), 2);
        assembler.machine().inventory().set(AssemblerSlots.PRODUCT, ItemResource.of(Items.OAK_SAPLING), 4);
        assembler.machine().inventory().set(AssemblerSlots.REMAINDERS, ItemResource.of(Items.BUCKET), 2);
        try (Transaction tx = Transaction.openRoot()) {
            helper.assertTrue(face.extract(0, ItemResource.of(Items.OAK_LOG), 2, tx) == 0, "an input extracted");
            helper.assertTrue(face.extract(AssemblerSlots.PRODUCT, ItemResource.of(Items.OAK_SAPLING), 4, tx) == 4, "the product did not extract");
            helper.assertTrue(face.extract(AssemblerSlots.REMAINDERS, ItemResource.of(Items.BUCKET), 2, tx) == 2, "the remainders did not extract");
            helper.assertTrue(face.insert(AssemblerSlots.PRODUCT, ItemResource.of(Items.OAK_SAPLING), 1, tx) == 0, "an output took an insert");
        }
        helper.assertTrue(count(assembler, AssemblerSlots.PRODUCT) == 4, "a simulated extract moved items");
        helper.succeed();
    }

    private static void filteredAndCapped(GameTestHelper helper) {
        Placed assembler = place(helper, AssemblerTier.ONE);
        // With no recipe, nothing goes in.
        helper.assertTrue(insert(assembler, 0, Items.OAK_LOG, 8) == 0, "an Assembler with no recipe took an item");
        hold(assembler, SAPLING);
        helper.assertTrue(insert(assembler, 1, Items.OAK_LOG, 8) == 0, "a slot the recipe doesn't use took an item");
        helper.assertTrue(insert(assembler, 0, Items.STONE, 8) == 0, "a slot took an item its ingredient doesn't match");
        // Sapling: 10 ticks at 0.5 is 3 crafts' worth of 2 logs.
        int first = insert(assembler, 0, Items.OAK_LOG, 64);
        helper.assertTrue(first == 6, "automation put in " + first + " logs, not the Overload Limit's 6");
        helper.assertTrue(insert(assembler, 0, Items.OAK_LOG, 64) == 0, "automation went past the Overload Limit");
        helper.succeed();
    }

    // -- fast replace ---------------------------------------------------------------------------

    private static void fastReplace(GameTestHelper helper) {
        Placed assembler = place(helper, AssemblerTier.ONE);
        hold(assembler, SAPLING);
        insert(assembler, 0, Items.OAK_LOG, 4);
        feed(assembler, supply(), 700);
        AssemblerBlockEntity machine = assembler.machine();

        swap(helper, assembler, AssemblerTier.THREE, ORIGIN.above().north().east());
        helper.assertTrue(helper.getBlockEntity(ORIGIN, AssemblerBlockEntity.class) == machine, "the swap replaced the block entity");
        expectStanding(helper, AssemblerTier.THREE, assembler.facing());
        helper.assertTrue(machine.tier() == AssemblerTier.THREE, "the block entity did not follow to tier 3");
        helper.assertTrue(machine.heldRecipe().equals(Optional.of(SAPLING)), "the swap lost the Held recipe");
        helper.assertTrue(count(assembler, 0) == 4 && machine.energy() == 700, "the swap lost the contents");
        helper.assertTrue(AssemblerTests.count(assembler.player(), Assemblers.item(AssemblerTier.ONE).get()) == 1,
                "the player was not handed the tier 1 item back");
        helper.assertTrue(AssemblerTests.count(assembler.player(), Assemblers.item(AssemblerTier.THREE).get()) == 0,
                "the tier 3 item was not spent");
        helper.assertTrue(helper.getEntities(EntityType.ITEM).isEmpty(), "the swap dropped " + helper.getEntities(EntityType.ITEM));

        // And back down, aimed at the origin this time.
        swap(helper, assembler, AssemblerTier.TWO, ORIGIN);
        expectStanding(helper, AssemblerTier.TWO, assembler.facing());
        helper.assertTrue(helper.getBlockEntity(ORIGIN, AssemblerBlockEntity.class) == machine, "the second swap replaced the block entity");
        helper.assertTrue(machine.heldRecipe().equals(Optional.of(SAPLING)) && count(assembler, 0) == 4, "the second swap lost the Held recipe or contents");
        helper.assertTrue(AssemblerTests.count(assembler.player(), Assemblers.item(AssemblerTier.THREE).get()) == 1,
                "the player was not handed the tier 3 item back");
        helper.succeed();
    }

    static void swap(GameTestHelper helper, Placed assembler, AssemblerTier to, BlockPos aimed) {
        FakeBuilder player = assembler.player();
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Assemblers.item(to).get()));
        BlockPos at = helper.absolutePos(aimed);
        player.gameMode.useItemOn(player, helper.getLevel(), player.getMainHandItem(), InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(at), Direction.UP, at, false));
    }

    // -- saving ---------------------------------------------------------------------------------

    /** A codec that dropped the Held recipe would not crash: it would empty every Assembler over a reload. */
    private static void survivesReload(GameTestHelper helper) {
        Placed assembler = place(helper, AssemblerTier.TWO);
        hold(assembler, SAPLING);
        insert(assembler, 0, Items.OAK_LOG, 3);
        feed(assembler, supply(), 900);
        for (int tick = 0; tick < 3; tick++) {
            assembler.machine().serverTick(helper.getLevel());
        }
        TagValueOutput saved = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, helper.getLevel().registryAccess());
        assembler.machine().saveCustomOnly(saved);

        AssemblerBlockEntity loaded = new AssemblerBlockEntity(helper.absolutePos(ORIGIN), helper.getBlockState(ORIGIN));
        loaded.setLevel(helper.getLevel());
        loaded.loadCustomOnly(TagValueInput.create(ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), saved.buildResult()));
        helper.assertTrue(loaded.heldRecipe().equals(Optional.of(SAPLING)), "the Held recipe read back as " + loaded.heldRecipe());
        helper.assertTrue(loaded.craftProgress() == assembler.machine().craftProgress() && loaded.craftProgress() > 0,
                "the progress read back as " + loaded.craftProgress());
        helper.assertTrue(loaded.inventory().getAmountAsInt(0) == 3, "the inputs read back as " + loaded.inventory().getAmountAsInt(0));
        helper.assertTrue(loaded.energy() == assembler.machine().energy(), "the energy read back as " + loaded.energy());
        helper.succeed();
    }

    // -- the client -----------------------------------------------------------------------------

    /** The screen is ghosted from a packet, so its codec round-trips and the menu sends it from its own tick. */
    private static void heldReachesTheClient(GameTestHelper helper) {
        Placed assembler = place(helper, AssemblerTier.ONE);
        hold(assembler, SAPLING);
        var recipe = HeldRecipes.find(helper.getLevel(), SAPLING).orElseThrow().value();
        HeldRecipeView sent = new HeldRecipeView(SAPLING, recipe.ingredients(), recipe.results());
        HeldRecipeSyncPacket read = crossed(helper, HeldRecipeSyncPacket.STREAM_CODEC, new HeldRecipeSyncPacket(7, Optional.of(sent)));
        HeldRecipeView held = read.held().orElseThrow();
        helper.assertTrue(read.containerId() == 7 && held.id().equals(SAPLING), "the packet read back as " + read);
        helper.assertTrue(held.ingredients().size() == 1 && held.ingredients().get(0).count() == 2, "the ingredients read back as " + held.ingredients());
        helper.assertTrue(held.product().is(recipe.product().getItem()), "the product read back as " + held.results());

        AssemblerMenu menu = (AssemblerMenu) assembler.machine().createMenu(1, assembler.player().getInventory(), assembler.player());
        assembler.player().containerMenu = menu;
        helper.assertTrue(menu.held().isEmpty(), "a menu knew the Held recipe before the server sent it");
        menu.broadcastChanges();
        menu.show(Optional.of(held));
        helper.assertTrue(menu.held().map(HeldRecipeView::id).equals(Optional.of(SAPLING)), "the menu did not take the Held recipe");
        helper.succeed();
    }

    /**
     * The energy bar reads data slots, which vanilla's packet sends as shorts, so 50,000 FE would wrap; the
     * server player NeoForge patches sends the whole int instead to a client with its channel. The Assembler
     * is opened for a real server player once with that channel and once without, and each time the client's
     * menu is built from what the server sent. The test's connection says which channels the client has, so
     * this guards the server's side, not that a NeoForge client still offers the channel.
     */
    private static void energyReachesTheClient(GameTestHelper helper) {
        Placed assembler = place(helper, AssemblerTier.ONE);
        int capacity = CraftworksConfig.buffer(AssemblerTier.ONE);
        helper.assertTrue(capacity > Short.MAX_VALUE, "a buffer of " + capacity + " FE fits a short, so nothing here can wrap");
        SimpleEnergyHandler supply = supply();
        for (int ran = 0; ran < 100 && assembler.machine().energy() < capacity; ran++) {
            feed(assembler, supply, capacity);
        }
        int stored = assembler.machine().energy();
        helper.assertTrue(stored == capacity, "the buffer took " + stored + " of " + capacity + " FE");

        AssemblerMenu vanilla = openOnTheClient(assembler);
        helper.assertTrue(vanilla.energy() != capacity, "vanilla's packet carried " + capacity + " FE whole, so this test shows nothing");
        AssemblerMenu neoforge = openOnTheClient(assembler, AdvancedContainerSetDataPayload.TYPE);
        helper.assertTrue(neoforge.energy() == capacity && neoforge.energyCapacity() == capacity,
                "the client's energy bar reads " + neoforge.energy() + " of " + neoforge.energyCapacity() + " FE");
        helper.succeed();
    }

    // -- config and names -----------------------------------------------------------------------

    private static void configDefaults(GameTestHelper helper) {
        double[] speed = {0.5, 0.75, 1.25};
        double[] power = {37.5, 75.0, 187.5};
        for (AssemblerTier tier : AssemblerTier.values()) {
            int n = tier.ordinal();
            helper.assertTrue(CraftworksConfig.speed(tier) == speed[n], tier + " speed is " + CraftworksConfig.speed(tier));
            helper.assertTrue(CraftworksConfig.power(tier) == power[n], tier + " power is " + CraftworksConfig.power(tier));
            helper.assertTrue(CraftworksConfig.buffer(tier) == 50_000, tier + " buffer is " + CraftworksConfig.buffer(tier));
        }
        helper.succeed();
    }

    /** Craftworks stands alone: no class of it names a type of a sibling mod or of the Pack. */
    private static void namesNoSiblingType(GameTestHelper helper) {
        // Built at run time, so this class doesn't contain the words it looks for.
        List<String> names = List.of(String.join("", "wire", "works"), String.join("", "pipe", "works"),
                String.join("", "factory", "works"));
        List<String> found = new ArrayList<>();
        int scanned = 0;
        try {
            for (Path root : ModList.get().getModFileById(Craftworks.MOD_ID).getFile().getContents().getContentRoots()) {
                Path classes = root.resolve("io").resolve("github").resolve("_5thlayer").resolve("craftworks");
                if (!Files.isDirectory(classes)) {
                    continue;
                }
                try (Stream<Path> files = Files.walk(classes)) {
                    // Not this class, whose test names say the words it looks for.
                    for (Path file : files.filter(path -> path.toString().endsWith(".class"))
                            .filter(path -> !path.getFileName().toString().startsWith("AssemblerMachineTests")).toList()) {
                        String contents = new String(Files.readAllBytes(file), StandardCharsets.ISO_8859_1).toLowerCase();
                        scanned++;
                        for (String name : names) {
                            if (contents.contains(name)) {
                                found.add(file.getFileName() + " names " + name);
                            }
                        }
                    }
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        helper.assertTrue(scanned > 50, "scanned only " + scanned + " classes, so the search looked in the wrong place");
        helper.assertTrue(found.isEmpty(), "Craftworks names a sibling's type: " + found);
        helper.succeed();
    }

    // -- fixtures -------------------------------------------------------------------------------

    /** A server player of its own that keeps the translation key of every message it is sent. */
    static final class FakeBuilder extends FakePlayer {

        final List<String> heard = new ArrayList<>();

        FakeBuilder(GameTestHelper helper) {
            super(helper.getLevel(), new GameProfile(UUID.randomUUID(), "craftworks_assembler"));
            setGameMode(GameType.SURVIVAL);
            Vec3 feet = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(0, 1, 0)));
            setPos(feet.x, feet.y, feet.z);
            setYRot(Direction.EAST.toYRot());
            setYHeadRot(Direction.EAST.toYRot());
        }

        @Override
        public void sendSystemMessage(Component message, boolean actionBar) {
            if (message.getContents() instanceof TranslatableContents translatable) {
                heard.add(translatable.getKey());
            }
        }

        @Override
        public void sendSystemMessage(Component message) {
            sendSystemMessage(message, false);
        }
    }

    /** An Assembler placed in a test, with the player who placed it and the way it faces. */
    record Placed(GameTestHelper helper, FakeBuilder player, AssemblerTier tier, Direction facing) {

        AssemblerBlockEntity machine() {
            return helper.getBlockEntity(ORIGIN, AssemblerBlockEntity.class);
        }

        /** The energy capability from a block of the footprint's far corner, to show the parts forward it. */
        EnergyHandler energy(Direction side) {
            return helper.getLevel().getCapability(Capabilities.Energy.BLOCK, corner(), side);
        }

        ResourceHandler<ItemResource> items(Direction side) {
            return helper.getLevel().getCapability(Capabilities.Item.BLOCK, corner(), side);
        }

        private BlockPos corner() {
            List<BlockPos> blocks = footprint(helper, tier, facing);
            return blocks.get(blocks.size() - 1);
        }
    }

    static Placed place(GameTestHelper helper, AssemblerTier tier) {
        return place(helper, tier, Direction.WEST);
    }

    /** Placed facing this way: a footprint faces opposite the player who lays it. */
    static Placed place(GameTestHelper helper, AssemblerTier tier, Direction facing) {
        return placeItem(helper, tier, new ItemStack(Assemblers.item(tier).get()), facing);
    }

    /** Places the stack by a click on the floor under {@link #ORIGIN}, failing unless the whole footprint stands. */
    static Placed placeItem(GameTestHelper helper, AssemblerTier tier, ItemStack stack, Direction facing) {
        FakeBuilder player = new FakeBuilder(helper);
        player.setYRot(facing.getOpposite().toYRot());
        player.setYHeadRot(facing.getOpposite().toYRot());
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlockPos floor = helper.absolutePos(ORIGIN.below());
        player.gameMode.useItemOn(player, helper.getLevel(), stack, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(floor).relative(Direction.UP, 0.5), Direction.UP, floor, false));
        expectStanding(helper, tier, facing);
        return new Placed(helper, player, tier, facing);
    }

    private static void expectStanding(GameTestHelper helper, AssemblerTier tier, Direction facing) {
        var footprint = Assemblers.footprint(tier);
        List<BlockPos> positions = footprint.positions(helper.absolutePos(ORIGIN), facing);
        helper.assertTrue(positions.size() == 18, "a 3x2x3 footprint has " + positions.size() + " blocks");
        for (int i = 0; i < positions.size(); i++) {
            helper.assertTrue(helper.getLevel().getBlockState(positions.get(i)).equals(footprint.stateAt(i, facing)),
                    "block " + i + " at " + positions.get(i) + " is " + helper.getLevel().getBlockState(positions.get(i)));
        }
    }

    private static List<BlockPos> footprint(GameTestHelper helper, AssemblerTier tier, Direction facing) {
        return Assemblers.footprint(tier).positions(helper.absolutePos(ORIGIN), facing);
    }

    /** Opens the Assembler's screen for its player and presses Fill Recipe, as the recipe viewer's button does. */
    static HoldVerdict request(Placed assembler, Identifier recipe) {
        FakeBuilder player = assembler.player();
        // Not openMenu: a fake player's opens nothing, so the menu the block entity makes is set by hand.
        AssemblerMenu menu = (AssemblerMenu) assembler.machine().createMenu(1, player.getInventory(), player);
        player.containerMenu = menu;
        return menu.request(player, recipe);
    }

    /**
     * Opens the Assembler, as a click on it does, for a server player whose client has these channels, and
     * builds the client's menu from what the server sent: the menu from the open screen's extra data, and each
     * data packet through its codec, then as the client handles it. Not the fake player: it opens no menu, and
     * its connection drops what it is sent.
     */
    private static AssemblerMenu openOnTheClient(Placed assembler, CustomPacketPayload.Type<?>... channels) {
        return openOnTheClient(assembler.helper(), Assemblers.MENU.get(), channels);
    }

    /** The same for any machine standing at {@link #ORIGIN}: the menu of {@code type} the client builds. */
    @SuppressWarnings("unchecked")
    static <M extends AbstractContainerMenu> M openOnTheClient(GameTestHelper helper, MenuType<M> type, CustomPacketPayload.Type<?>... channels) {
        ServerPlayer player = TestConnection.player(helper, channels);
        BlockPos at = helper.absolutePos(ORIGIN);
        helper.getBlockState(ORIGIN).useWithoutItem(helper.getLevel(), player, new BlockHitResult(Vec3.atCenterOf(at), Direction.UP, at, false));

        M client = null;
        for (Packet<?> packet : TestConnection.sentTo(player)) {
            if (packet instanceof ClientboundCustomPayloadPacket(AdvancedOpenScreenPayload open)) {
                helper.assertTrue(open.menuType() == type, "the screen opened is " + open.menuType());
                client = (M) open.menuType().create(open.windowId(), player.getInventory(),
                        new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(open.additionalData()), helper.getLevel().registryAccess()));
            } else if (packet instanceof ClientboundCustomPayloadPacket(AdvancedContainerSetDataPayload data)) {
                // NeoForge's client hands its payload on as vanilla's packet, in memory and never written.
                setData(helper, client, crossed(helper, AdvancedContainerSetDataPayload.STREAM_CODEC, data).toVanillaPacket());
            } else if (packet instanceof ClientboundContainerSetDataPacket data) {
                setData(helper, client, crossed(helper, ClientboundContainerSetDataPacket.STREAM_CODEC, data));
            }
        }
        helper.assertTrue(client != null, "opening the machine sent the client no screen: " + TestConnection.sentTo(player));
        return client;
    }

    /** What the client does with a data packet: sets the slot of the menu it names, which must be the one open. */
    private static void setData(GameTestHelper helper, @Nullable AbstractContainerMenu client, ClientboundContainerSetDataPacket packet) {
        helper.assertTrue(client != null && packet.getContainerId() == client.containerId,
                "a data packet for container " + packet.getContainerId() + " came with no such screen open");
        client.setData(packet.getId(), packet.getValue());
    }

    /** A value after a trip over the network: written by its codec and read back. */
    private static <T> T crossed(GameTestHelper helper, StreamCodec<? super RegistryFriendlyByteBuf, T> codec, T value) {
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        codec.encode(buffer, value);
        return codec.decode(buffer);
    }

    /** Runs {@code body} with {@code tier}'s server config listing only these categories, and restores it after, as a world's config would. */
    static void withCategories(AssemblerTier tier, List<AssemblingCategory> categories, Runnable body) {
        var setting = CraftworksConfig.settings(tier).categories();
        List<? extends String> before = setting.get();
        setting.set(categories.stream().map(AssemblingCategory::id).toList());
        try {
            body.run();
        } finally {
            setting.set(before);
        }
    }

    static void hold(Placed assembler, Identifier recipe) {
        HoldVerdict verdict = request(assembler, recipe);
        assembler.helper().assertTrue(verdict == HoldVerdict.HELD, "Fill Recipe on " + recipe + " was " + verdict);
    }

    /** Puts items in through the item capability of a block of the footprint, the way a pipe would; how many went in. */
    static int insert(Placed assembler, int slot, Item item, int amount) {
        int inserted;
        try (Transaction tx = Transaction.openRoot()) {
            inserted = assembler.items(Direction.UP).insert(slot, ItemResource.of(item), amount, tx);
            tx.commit();
        }
        return inserted;
    }

    static int count(Placed assembler, int slot) {
        return assembler.machine().inventory().getAmountAsInt(slot);
    }

    private static int total(List<ItemStack> stacks, Item item) {
        return stacks.stream().filter(stack -> stack.is(item)).mapToInt(ItemStack::getCount).sum();
    }

    static SimpleEnergyHandler supply() {
        return new SimpleEnergyHandler(1_000_000, 1_000_000, 1_000_000, 1_000_000);
    }

    /** Moves energy from a plain handler into the Assembler's face; how much went in. */
    static int feed(Placed assembler, SimpleEnergyHandler supply, int amount) {
        return EnergyHandlerUtil.move(supply, assembler.energy(Direction.UP), amount, null);
    }
}
