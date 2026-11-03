// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.mixin.minecraft;

import java.util.Collection;

import io.github._5thlayer.craftworks.recipe.StorageCraftingRecipes;
import net.minecraft.stats.ServerRecipeBook;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Keeps the Storage crafting recipes out of the recipe book (#83): a player never learns one, whether by
 * making it at a crafting table, by an advancement or by {@code /recipe give}, so a metal is not listed twice.
 */
@Mixin(ServerRecipeBook.class)
public class ServerRecipeBookMixin {

    @ModifyVariable(method = "addRecipes", at = @At("HEAD"), argsOnly = true)
    private Collection<RecipeHolder<?>> craftworks$withoutStorageCrafting(Collection<RecipeHolder<?>> recipes) {
        if (recipes.stream().noneMatch(holder -> StorageCraftingRecipes.is(holder.id().identifier()))) return recipes;
        return recipes.stream().filter(holder -> !StorageCraftingRecipes.is(holder.id().identifier())).toList();
    }
}
