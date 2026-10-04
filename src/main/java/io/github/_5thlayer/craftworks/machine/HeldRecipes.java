// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.machine;

import java.util.Optional;

import io.github._5thlayer.craftworks.assembler.RuntimePlanSource;
import io.github._5thlayer.craftworks.recipe.AssemblingRecipe;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.crafting.RecipeHolder;

/**
 * What an Assembler may hold, read off the server's recipe manager when asked: a Held recipe is an id,
 * resolved lazily because a block entity loads before the recipes do.
 *
 * <p>Every Assembling recipe is one, Hand-craftable or not, except two kinds this tier cannot run: one
 * naming a fluid (tiers 2 and 3 take them in 5thlayer/factoryworks#580) and one with more distinct
 * ingredients than the five input slots.
 */
public final class HeldRecipes {

    private HeldRecipes() {
    }

    /** The Assembling recipe at this id, whatever it needs. */
    public static Optional<RecipeHolder<AssemblingRecipe>> find(ServerLevel level, Identifier id) {
        RecipeHolder<?> holder = level.getServer().getRecipeManager().recipeMap()
                .byKey(ResourceKey.create(Registries.RECIPE, id));
        if (holder == null || !(holder.value() instanceof AssemblingRecipe)) {
            return Optional.empty();
        }
        @SuppressWarnings("unchecked")
        RecipeHolder<AssemblingRecipe> assembling = (RecipeHolder<AssemblingRecipe>) holder;
        return Optional.of(assembling);
    }

    public static boolean namesFluid(AssemblingRecipe recipe) {
        return !recipe.fluidIngredients().isEmpty() || !recipe.fluidResults().isEmpty();
    }

    public static boolean fitsSlots(AssemblingRecipe recipe) {
        return recipe.ingredients().size() <= AssemblerSlots.INPUTS;
    }

    /** The recipe at this id if an Assembler can run it, else empty: what a Held recipe that cannot run idles on. */
    public static Optional<AssemblingRecipe> runnable(ServerLevel level, Identifier id) {
        return find(level, id).map(RecipeHolder::value).filter(recipe -> !namesFluid(recipe) && fitsSlots(recipe));
    }

    /** What Fill Recipe on an open Assembler would answer for this player: the Lock source is asked here, and only here. */
    public static HoldVerdict verdict(ServerPlayer player, Identifier id) {
        Optional<AssemblingRecipe> recipe = find(player.level(), id).map(RecipeHolder::value);
        return HoldVerdict.of(recipe.isPresent(), recipe.map(HeldRecipes::namesFluid).orElse(false),
                recipe.map(HeldRecipes::fitsSlots).orElse(false),
                recipe.isPresent() && RuntimePlanSource.lockedFor(player).test(id.toString()));
    }

    /** The recipe's name for a message: its product's, or else its id. */
    public static Component name(ServerLevel level, Identifier id) {
        return find(level, id).map(holder -> holder.value().result().create().getHoverName())
                .orElse(Component.literal(id.toString()));
    }
}
