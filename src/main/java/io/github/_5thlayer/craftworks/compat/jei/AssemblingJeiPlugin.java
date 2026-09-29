// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.compat.jei;

import java.util.List;

import io.github._5thlayer.craftworks.Craftworks;
import io.github._5thlayer.craftworks.recipe.AssemblingRecipe;
import io.github._5thlayer.craftworks.recipe.CraftworksRecipes;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.recipe.types.IRecipeHolderType;
import mezz.jei.api.registration.IAdvancedRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeMap;
import net.neoforged.neoforge.client.event.RecipesReceivedEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Puts {@code craftworks:assembling} in JEI, in a category of its own, with a recipe button that queues
 * on the Personal Assembler the way EMI's Fill Recipe does (#12, ADR-0004).
 *
 * <p>JEI finds this by its annotation and loads it only when JEI is installed; nothing else in the Mod
 * names a JEI type, so the Mod loads without it.
 *
 * <p>The recipes are the ones the server sends the client (they are asked for in
 * {@code CraftworksNetwork}). JEI keeps its own copy of them out of its API, so this keeps one too, from
 * the same {@link RecipesReceivedEvent}: JEI listens at the lowest priority and starts after it, so the
 * copy is in place when {@link #registerRecipes} reads it.
 */
@JeiPlugin
public final class AssemblingJeiPlugin implements IModPlugin {

    static final IRecipeHolderType<AssemblingRecipe> ASSEMBLING =
            IRecipeHolderType.create(Identifier.fromNamespaceAndPath(Craftworks.MOD_ID, CraftworksRecipes.ASSEMBLING));

    private static volatile RecipeMap received = RecipeMap.EMPTY;

    public AssemblingJeiPlugin() {
        NeoForge.EVENT_BUS.addListener(RecipesReceivedEvent.class, event -> received = event.getRecipeMap());
    }

    @Override
    public Identifier getPluginUid() {
        return Identifier.fromNamespaceAndPath(Craftworks.MOD_ID, "jei");
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        int maxInputs = assemblingRecipes().stream().mapToInt(holder -> holder.value().ingredients().size()).max().orElse(1);
        registration.addRecipeCategories(new AssemblingJeiCategory(registration.getJeiHelpers().getGuiHelper(), maxInputs));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        registration.addRecipes(ASSEMBLING, assemblingRecipes());
    }

    private static List<RecipeHolder<AssemblingRecipe>> assemblingRecipes() {
        return List.copyOf(received.byType(CraftworksRecipes.ASSEMBLING_TYPE.get()));
    }

    /** The recipe button, on Assembling recipes and no other (see {@link AssemblingRecipeButton}). */
    @Override
    public void registerAdvanced(IAdvancedRegistration registration) {
        registration.addRecipeButtonFactory(AssemblingRecipeButton::forLayout);
    }
}
