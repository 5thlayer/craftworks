// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.compat.emi;

import dev.emi.emi.api.neoforge.NeoForgeEmiIngredient;
import dev.emi.emi.api.neoforge.NeoForgeEmiStack;
import dev.emi.emi.api.recipe.BasicEmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.render.EmiTexture;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.WidgetHolder;
import io.github._5thlayer.craftworks.recipe.AssemblingCategory;
import io.github._5thlayer.craftworks.recipe.AssemblingRecipe;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.crafting.RecipeHolder;

/**
 * One Assembling recipe as EMI draws it: the ingredients in a row, an arrow timed to the craft, every
 * item and fluid result, and the recipe's category beneath. A row and not a grid, because an ingredient
 * count (eight plates) is what a grid cannot say.
 */
public class AssemblingEmiRecipe extends BasicEmiRecipe {

    private static final int SLOT = 18;

    private final int time;
    private final AssemblingCategory assemblingCategory;

    public AssemblingEmiRecipe(EmiRecipeCategory category, RecipeHolder<AssemblingRecipe> holder) {
        super(category, holder.id().identifier(), 0, 44);
        AssemblingRecipe recipe = holder.value();
        this.time = recipe.time();
        this.assemblingCategory = recipe.category();
        recipe.ingredients().forEach(sized -> inputs.add(NeoForgeEmiIngredient.of(sized)));
        recipe.results().forEach(result -> outputs.add(EmiStack.of(result.create())));
        recipe.fluidResults().forEach(result -> outputs.add(NeoForgeEmiStack.of(result.create())));
        this.width = inputs.size() * SLOT + 30 + outputs.size() * SLOT;
    }

    @Override
    public void addWidgets(WidgetHolder widgets) {
        int x = 0;
        for (EmiIngredient input : inputs) {
            widgets.addSlot(input, x, 4);
            x += SLOT;
        }
        widgets.addTexture(EmiTexture.EMPTY_ARROW, x + 3, 5);
        widgets.addFillingArrow(x + 3, 5, Math.max(time, 1) * 50);
        widgets.addText(Component.translatable("emi.craftworks.assembling.seconds",
                String.format("%.1f", time / 20F)), x + 3, 24, 0xFF808080, false);
        widgets.addText(Component.translatable("emi.craftworks.assembling.category", assemblingCategory.id()), 0, 35, 0xFF808080, false);
        x += 30;
        for (EmiStack output : outputs) {
            widgets.addSlot(output, x, 4).recipeContext(this);
            x += SLOT;
        }
    }
}
