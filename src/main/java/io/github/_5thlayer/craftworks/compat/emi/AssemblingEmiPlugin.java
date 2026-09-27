// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.compat.emi;

import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiStack;
import io.github._5thlayer.craftworks.Craftworks;
import io.github._5thlayer.craftworks.recipe.CraftworksRecipes;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Items;

/**
 * Puts {@code craftworks:assembling} in EMI, in a category of its own. EMI has never heard of the type,
 * so without one its recipes are in no viewer at all.
 *
 * <p>EMI finds this by its annotation and loads it only when EMI is installed; nothing else in the Mod
 * names an EMI type, so the Mod loads without it.
 *
 * <p>The Personal Assembler is the inventory screen rather than a block, so the category has no
 * workstation; the crafter is only its icon, until the category has one of its own.
 */
@EmiEntrypoint
public final class AssemblingEmiPlugin implements EmiPlugin {

    public static final EmiRecipeCategory ASSEMBLING = new EmiRecipeCategory(
            Identifier.fromNamespaceAndPath(Craftworks.MOD_ID, CraftworksRecipes.ASSEMBLING),
            EmiStack.of(Items.CRAFTER));

    @Override
    public void register(EmiRegistry registry) {
        registry.addCategory(ASSEMBLING);
        registry.getRecipeMap()
                .byType(CraftworksRecipes.ASSEMBLING_TYPE.get())
                .forEach(holder -> registry.addRecipe(new AssemblingEmiRecipe(ASSEMBLING, holder)));
    }
}
