// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.machine;

import java.util.Optional;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.RecipePropertySet;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;

/**
 * What the Refiner smelts: vanilla's smelting and blasting recipes and no type of its own (ADR-0020). An input
 * that has both is blasted, so the Refiner's one speed gives the blast furnace's halved time where a recipe
 * offers it.
 */
final class RefinerRecipes {

    private RefinerRecipes() {
    }

    /** The recipe that smelts {@code input}: blasting where there is one, smelting otherwise. Server only. */
    static Optional<RecipeHolder<? extends AbstractCookingRecipe>> find(ServerLevel level, ItemStack input) {
        var recipes = level.getServer().getRecipeManager();
        SingleRecipeInput single = new SingleRecipeInput(input);
        Optional<RecipeHolder<? extends AbstractCookingRecipe>> blasting =
                recipes.getRecipeFor(RecipeType.BLASTING, single, level).map(holder -> holder);
        return blasting.isPresent() ? blasting : recipes.getRecipeFor(RecipeType.SMELTING, single, level).map(holder -> holder);
    }

    /**
     * Whether some smelting or blasting recipe takes {@code input}, however many are held: a hopper hands over
     * one at a time. Read from the property sets the server syncs, so the menu's slot can ask it on the client.
     */
    static boolean isIngredient(Level level, ItemStack input) {
        return !input.isEmpty()
                && (level.recipeAccess().propertySet(RecipePropertySet.FURNACE_INPUT).test(input)
                        || level.recipeAccess().propertySet(RecipePropertySet.BLAST_FURNACE_INPUT).test(input));
    }
}
