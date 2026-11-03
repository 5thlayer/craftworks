// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.recipe;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import io.github._5thlayer.craftworks.Craftworks;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.neoforged.neoforge.common.crafting.SizedIngredient;

/**
 * The Storage crafting recipes (#83, GLOSSARY.md): a real crafting recipe beside each Storage recipe, at
 * {@code craftworks:storage/<namespace>/<path>} of the Storage recipe's id, so the mods that pack and unpack
 * items, which read the loaded crafting recipes themselves, still find the way up and down (ADR-0011, amended).
 *
 * <p>Built from the Storage recipe, not kept from an original, so the vanilla pack's and every mod's come out
 * alike: packing is a shaped grid of its one ingredient, since compacting drawers take only a shaped recipe for
 * the way down; unpacking is a shapeless recipe of one item. The recipe viewers and the recipe book leave them out.
 */
public final class StorageCraftingRecipes {

    private static final String PREFIX = "storage/";

    private StorageCraftingRecipes() {
    }

    /** The Storage crafting recipe's id for the Storage recipe at {@code storage}. */
    public static Identifier idOf(Identifier storage) {
        return Identifier.fromNamespaceAndPath(Craftworks.MOD_ID, PREFIX + storage.getNamespace() + "/" + storage.getPath());
    }

    /** Whether the id is a Storage crafting recipe's. */
    public static boolean is(Identifier id) {
        return id.getNamespace().equals(Craftworks.MOD_ID) && id.getPath().startsWith(PREFIX);
    }

    /** The Storage crafting recipe for the Storage recipe at {@code storage}, a plain trade of one ingredient. */
    static RecipeHolder<CraftingRecipe> of(Identifier storage, AssemblingRecipe recipe) {
        SizedIngredient sized = recipe.ingredients().getFirst();
        ItemStackTemplate result = recipe.results().getFirst();
        CraftingRecipe crafting = sized.count() > 1 ? packing(sized.ingredient(), sized.count(), result) : unpacking(sized.ingredient(), result);
        return new RecipeHolder<>(ResourceKey.create(Registries.RECIPE, idOf(storage)), crafting);
    }

    /** Nine or four of the ingredient in a square. */
    private static CraftingRecipe packing(Ingredient ingredient, int count, ItemStackTemplate result) {
        int side = count == 9 ? 3 : 2;
        String row = "#".repeat(side);
        ShapedRecipePattern pattern = ShapedRecipePattern.of(Map.of('#', ingredient), Collections.nCopies(side, row));
        return new ShapedRecipe(new Recipe.CommonInfo(false), bookInfo(), pattern, result);
    }

    private static CraftingRecipe unpacking(Ingredient ingredient, ItemStackTemplate result) {
        return new ShapelessRecipe(new Recipe.CommonInfo(false), bookInfo(), result, List.of(ingredient));
    }

    private static CraftingRecipe.CraftingBookInfo bookInfo() {
        return new CraftingRecipe.CraftingBookInfo(CraftingBookCategory.MISC, "");
    }
}
