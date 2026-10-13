// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.gametest;

import java.util.List;
import java.util.UUID;

import io.github._5thlayer.craftworks.assembler.PersonalAssembler;
import io.github._5thlayer.craftworks.planner.PlanQueue;
import net.minecraft.server.level.ServerPlayer;

import io.github._5thlayer.craftworks.machine.AssemblerSlots;
import io.github._5thlayer.craftworks.machine.AssemblerTier;
import io.github._5thlayer.craftworks.machine.HeldRecipes;
import io.github._5thlayer.craftworks.recipe.CraftworksRecipes;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;

/**
 * A mod's recipe that assembles its own result, {@code craftworks:gametest/keeps_data} (a diamond over an iron sword
 * makes a diamond sword with the sword's name and damage), is a Data-carrying recipe (ADR-0022): converted, and the
 * Assembler machine asks the original what each craft made.
 */
final class SelfAssemblingTests {

    static final Identifier KEEPS_DATA = Identifier.parse("craftworks:gametest/keeps_data");
    /** Shapeless and plain: two sand and a dirt make three clay balls. */
    private static final Identifier PLAIN = Identifier.parse("craftworks:gametest/converted_shapeless");

    private SelfAssemblingTests() {
    }

    static void register(CraftworksGameTests.Registrar tests) {
        tests.test("a_self_assembling_recipe_converts_at_its_id", 20, SelfAssemblingTests::converts);
        tests.test("the_machine_makes_what_a_self_assembling_recipe_assembles", 40, SelfAssemblingTests::machineKeeps);
        tests.test("a_named_sword_pays_for_a_data_carrying_recipe", 20, SelfAssemblingTests::namedSwordPays);
        tests.test("a_plain_sword_is_spent_before_a_named_one", 20, SelfAssemblingTests::plainBeforeNamed);
        tests.test("cancelling_refunds_the_named_sword_exactly", 20, SelfAssemblingTests::cancelRefundsNamed);
        tests.test("a_named_sword_makes_a_delivered_named_diamond_sword", 200, SelfAssemblingTests::namedSwordMakesANamedSword);
        tests.test("one_named_and_one_plain_sword_deliver_one_of_each", 200, SelfAssemblingTests::mixedCraftsDeliverOneOfEach);
        tests.test("the_machine_makes_the_listed_product_of_a_recipe_that_assembles_nothing", 40, SelfAssemblingTests::machinePlain);
    }

    private static void converts(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        RecipeType<?> type = ModRecipesTests.type(server, KEEPS_DATA.toString());
        helper.assertTrue(type == CraftworksRecipes.ASSEMBLING_TYPE.get(), KEEPS_DATA + " should be a Converted recipe, and is " + type);
        helper.succeed();
    }

    /** The machine takes the named, damaged sword as it is, and the original recipe copies both. */
    private static void machineKeeps(GameTestHelper helper) {
        ItemStack made = machineCraft(helper, KEEPS_DATA, named());
        helper.assertTrue(made.is(Items.DIAMOND_SWORD) && Component.literal("Biter").equals(made.get(DataComponents.CUSTOM_NAME))
                        && made.getDamageValue() == 42,
                "the machine should make a diamond sword named Biter at damage 42, and made " + made + " named "
                        + made.get(DataComponents.CUSTOM_NAME) + " at damage " + made.getDamageValue());
        helper.succeed();
    }

    /** Holding only a named sword and a diamond, the recipe queues and takes the named sword. */
    private static void namedSwordPays(GameTestHelper helper) {
        ServerPlayer player = holding(helper, named(), new ItemStack(Items.DIAMOND));
        helper.assertTrue(PersonalAssembler.craft(player, KEEPS_DATA, 1), "a named sword and a diamond should queue " + KEEPS_DATA);
        helper.assertTrue(AssemblerTests.count(player, Items.IRON_SWORD) == 0 && AssemblerTests.count(player, Items.DIAMOND) == 0,
                "queueing should have taken the named sword and the diamond");
        helper.succeed();
    }

    /** The Pristine sword pays first, so the named one stays with the player. */
    private static void plainBeforeNamed(GameTestHelper helper) {
        ServerPlayer player = holding(helper, named(), new ItemStack(Items.IRON_SWORD), new ItemStack(Items.DIAMOND));
        helper.assertTrue(PersonalAssembler.craft(player, KEEPS_DATA, 1), "the recipe should queue");
        List<ItemStack> swords = player.getInventory().getNonEquipmentItems().stream().filter(s -> s.is(Items.IRON_SWORD)).toList();
        helper.assertTrue(swords.size() == 1 && Component.literal("Biter").equals(swords.get(0).get(DataComponents.CUSTOM_NAME)),
                "only the named sword should be left, and " + swords + " are");
        helper.succeed();
    }

    /** A cancel gives back the sword with its name and damage. */
    private static void cancelRefundsNamed(GameTestHelper helper) {
        ServerPlayer player = holding(helper, named(), new ItemStack(Items.DIAMOND));
        helper.assertTrue(PersonalAssembler.craft(player, KEEPS_DATA, 1), "the recipe should queue");
        UUID row = PersonalAssembler.queueOf(player).entries().get(0).plan().id();
        helper.assertTrue(PersonalAssembler.cancel(player, row, PlanQueue.ALL), "the cancel should go through");
        List<ItemStack> swords = player.getInventory().getNonEquipmentItems().stream().filter(s -> s.is(Items.IRON_SWORD)).toList();
        helper.assertTrue(swords.size() == 1 && ItemStack.isSameItemSameComponents(swords.get(0), named()),
                "the refund should be the named sword at damage 42, and is " + swords);
        helper.succeed();
    }

    /** The named, damaged sword makes a diamond sword with the same name and damage, delivered as made. */
    private static void namedSwordMakesANamedSword(GameTestHelper helper) {
        ServerPlayer player = holding(helper, named(), new ItemStack(Items.DIAMOND));
        helper.assertTrue(PersonalAssembler.craft(player, KEEPS_DATA, 1), "the recipe should queue");
        helper.onEachTick(() -> EventHooks.firePlayerTickPost(player));
        helper.succeedWhen(() -> {
            helper.assertTrue(PersonalAssembler.queueOf(player).isEmpty(), "the plan is still queued");
            List<ItemStack> swords = diamondSwords(player);
            helper.assertTrue(swords.size() == 1 && Component.literal("Biter").equals(swords.get(0).get(DataComponents.CUSTOM_NAME))
                            && swords.get(0).getDamageValue() == 42,
                    "one diamond sword named Biter at damage 42 should be delivered, and " + swords + " are");
        });
    }

    /** Two crafts, one named sword and one plain: each craft makes what a crafting table would from its own stacks. */
    private static void mixedCraftsDeliverOneOfEach(GameTestHelper helper) {
        ServerPlayer player = holding(helper, named(), new ItemStack(Items.IRON_SWORD), new ItemStack(Items.DIAMOND, 2));
        helper.assertTrue(PersonalAssembler.craft(player, KEEPS_DATA, 2), "two crafts should queue");
        helper.onEachTick(() -> EventHooks.firePlayerTickPost(player));
        helper.succeedWhen(() -> {
            helper.assertTrue(PersonalAssembler.queueOf(player).isEmpty(), "the plan is still queued");
            List<ItemStack> swords = diamondSwords(player);
            long named = swords.stream().filter(s -> Component.literal("Biter").equals(s.get(DataComponents.CUSTOM_NAME))
                    && s.getDamageValue() == 42).count();
            long plain = swords.stream().filter(s -> !s.has(DataComponents.CUSTOM_NAME)).count();
            helper.assertTrue(swords.size() == 2 && named == 1 && plain == 1,
                    "one named and one plain diamond sword should be delivered, and " + swords + " are");
        });
    }

    private static List<ItemStack> diamondSwords(ServerPlayer player) {
        return player.getInventory().getNonEquipmentItems().stream().filter(s -> s.is(Items.DIAMOND_SWORD)).toList();
    }

    private static ServerPlayer holding(GameTestHelper helper, ItemStack... stacks) {
        ServerPlayer player = AssemblerTests.playerHolding(helper, stacks[0]);
        for (int i = 1; i < stacks.length; i++) player.getInventory().add(stacks[i]);
        return player;
    }

    private static void machinePlain(GameTestHelper helper) {
        ItemStack made = machineCraft(helper, PLAIN, ItemStack.EMPTY);
        helper.assertTrue(made.is(Items.CLAY_BALL), "the machine should make clay balls, and made " + made);
        helper.succeed();
    }

    static ItemStack named() {
        ItemStack sword = new ItemStack(Items.IRON_SWORD);
        sword.set(DataComponents.CUSTOM_NAME, Component.literal("Biter"));
        sword.setDamageValue(42);
        return sword;
    }

    /**
     * One craft on an Assembler 3: each slot is filled with {@code special} if its ingredient takes it, else
     * with an item its ingredient lists; what lands in the first output.
     */
    private static ItemStack machineCraft(GameTestHelper helper, Identifier recipe, ItemStack special) {
        var assembler = AssemblerMachineTests.place(helper, AssemblerTier.THREE);
        AssemblerMachineTests.hold(assembler, recipe);
        var bag = HeldRecipes.find(helper.getLevel(), recipe).orElseThrow().value().ingredients();
        for (int slot = 0; slot < bag.size(); slot++) {
            var sized = bag.get(slot);
            ItemStack stack = !special.isEmpty() && sized.ingredient().test(special) ? special
                    : sized.ingredient().items().findFirst().map(h -> new ItemStack(h.value())).orElseThrow();
            assembler.machine().inventory().set(slot, ItemResource.of(stack), sized.count());
        }
        SimpleEnergyHandler supply = AssemblerMachineTests.supply();
        for (int tick = 0; tick < 20 && AssemblerMachineTests.count(assembler, AssemblerSlots.OUTPUT) == 0; tick++) {
            AssemblerMachineTests.feed(assembler, supply, 1000);
            assembler.machine().serverTick(helper.getLevel());
        }
        return assembler.machine().inventory().getResource(AssemblerSlots.OUTPUT).toStack(1);
    }
}
