// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.compat.jei;

import io.github._5thlayer.craftworks.recipe.AssemblingRecipe;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.recipe.types.IRecipeType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.common.crafting.SizedIngredient;

/**
 * One Assembling recipe as JEI draws it, laid out as in EMI: the ingredients in a row, an arrow timed
 * to the craft, the result. A row and not a grid, because an ingredient count is what a grid cannot say.
 *
 * <p>The row is as wide as the widest recipe allows, {@link #MAX_INPUTS} ingredients; a JEI category has
 * one width for all its recipes.
 */
final class AssemblingJeiCategory implements IRecipeCategory<RecipeHolder<AssemblingRecipe>> {

    private static final int SLOT = 18;
    private static final int MAX_INPUTS = 9;
    private static final int ARROW = 30;

    private final IDrawable icon;

    AssemblingJeiCategory(IGuiHelper guiHelper) {
        this.icon = guiHelper.createDrawableItemLike(Items.CRAFTER);
    }

    @Override
    public IRecipeType<RecipeHolder<AssemblingRecipe>> getRecipeType() {
        return AssemblingJeiPlugin.ASSEMBLING;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("jei.category.craftworks.assembling");
    }

    @Override
    public int getWidth() {
        return MAX_INPUTS * SLOT + ARROW + SLOT;
    }

    @Override
    public int getHeight() {
        return 32;
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, RecipeHolder<AssemblingRecipe> holder, IFocusGroup focuses) {
        AssemblingRecipe recipe = holder.value();
        int x = 0;
        for (SizedIngredient sized : recipe.ingredients()) {
            builder.addSlot(RecipeIngredientRole.INPUT, x + 1, 5)
                    .setStandardSlotBackground()
                    .addItemStacks(sized.ingredient().items().map(item -> new ItemStack(item, sized.count())).toList());
            x += SLOT;
        }
        builder.addSlot(RecipeIngredientRole.OUTPUT, x + ARROW + 1, 5)
                .setOutputSlotBackground()
                .add(recipe.result());
    }

    @Override
    public void createRecipeExtras(IRecipeExtrasBuilder builder, RecipeHolder<AssemblingRecipe> holder, IFocusGroup focuses) {
        AssemblingRecipe recipe = holder.value();
        int x = recipe.ingredients().size() * SLOT;
        builder.addAnimatedRecipeArrow(Math.max(recipe.time(), 1)).setPosition(x + 3, 5);
        builder.addText(Component.translatable("jei.craftworks.assembling.seconds",
                String.format("%.1f", recipe.time() / 20F)), ARROW + SLOT, 10)
                .setPosition(x + 3, 24)
                .setColor(0xFF808080);
    }

    @Override
    public boolean needsRecipeBorder() {
        return true;
    }
}
