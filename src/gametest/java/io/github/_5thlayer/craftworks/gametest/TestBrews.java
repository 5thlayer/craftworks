// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.gametest;

import io.github._5thlayer.craftworks.Craftworks;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.brewing.BrewingRecipe;
import net.neoforged.neoforge.common.brewing.IBrewingRecipe;
import net.neoforged.neoforge.common.crafting.DataComponentIngredient;
import net.neoforged.neoforge.event.brewing.RegisterBrewingRecipesEvent;

/**
 * The brews the game tests add through NeoForge's event, as another mod would (#76): one from a water bottle, one
 * from an awkward potion, and one that is no {@link BrewingRecipe}. They stand in for a mod's, so only the Mod's own
 * runs add them.
 */
@EventBusSubscriber(modid = Craftworks.MOD_ID)
final class TestBrews {

    /** A water bottle and an amethyst shard make prismarine crystals: a result that is no potion. */
    static final String FROM_WATER = "craftworks:brewing/minecraft/prismarine_crystals/minecraft.amethyst_shard";
    /** An awkward potion and glow berries make a potion of luck, which no vanilla mix makes. */
    static final String FROM_AWKWARD = "craftworks:brewing/potion/minecraft/luck/minecraft.nether_wart/minecraft.glow_berries";

    private TestBrews() {
    }

    @SubscribeEvent
    static void register(RegisterBrewingRecipesEvent event) {
        var builder = event.getBuilder();
        builder.addRecipe(potionOf(Potions.WATER), Ingredient.of(Items.AMETHYST_SHARD), new ItemStack(Items.PRISMARINE_CRYSTALS));
        builder.addRecipe(potionOf(Potions.AWKWARD), Ingredient.of(Items.GLOW_BERRIES),
                PotionContents.createItemStack(Items.POTION, Potions.LUCK));
        builder.addRecipe(new Unlisted());
    }

    private static Ingredient potionOf(net.minecraft.core.Holder<net.minecraft.world.item.alchemy.Potion> potion) {
        return DataComponentIngredient.of(DataComponents.POTION_CONTENTS, new PotionContents(potion), Items.POTION);
    }

    /** An awkward potion and bone meal make coal, by code that cannot be listed. */
    static final class Unlisted implements IBrewingRecipe {

        @Override
        public boolean isInput(ItemStack input) {
            return input.is(Items.POTION);
        }

        @Override
        public boolean isIngredient(ItemStack ingredient) {
            return ingredient.is(Items.BONE_MEAL);
        }

        @Override
        public ItemStack getOutput(ItemStack input, ItemStack ingredient) {
            return isInput(input) && isIngredient(ingredient) ? new ItemStack(Items.COAL) : ItemStack.EMPTY;
        }
    }
}
