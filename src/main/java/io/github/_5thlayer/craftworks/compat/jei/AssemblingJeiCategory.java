// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.compat.jei;

import io.github._5thlayer.craftworks.recipe.AssemblingRecipe;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.neoforge.NeoForgeTypes;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.recipe.types.IRecipeType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import net.neoforged.neoforge.fluids.FluidStackTemplate;

/**
 * One Assembling recipe as JEI draws it, laid out as in EMI: the ingredients in a row, an arrow timed
 * to the craft, every item and fluid result, and the recipe's category beneath. A row and not a grid,
 * because an ingredient count is what a grid cannot say.
 *
 * <p>A JEI category has one width for all its recipes, so the row is as wide as the recipe with the
 * most ingredients plus the one with the most results, counted when the recipes are registered.
 */
final class AssemblingJeiCategory implements IRecipeCategory<RecipeHolder<AssemblingRecipe>> {

    private static final int SLOT = 18;
    private static final int ARROW = 30;

    private final IDrawable icon;
    private final int maxInputs;
    private final int maxOutputs;

    AssemblingJeiCategory(IGuiHelper guiHelper, int maxInputs, int maxOutputs) {
        this.maxInputs = maxInputs;
        this.maxOutputs = maxOutputs;
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
        return maxInputs * SLOT + ARROW + maxOutputs * SLOT;
    }

    @Override
    public int getHeight() {
        return 44;
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
        x += ARROW;
        for (ItemStackTemplate result : recipe.results()) {
            builder.addSlot(RecipeIngredientRole.OUTPUT, x + 1, 5)
                    .setOutputSlotBackground()
                    .add(result);
            x += SLOT;
        }
        for (FluidStackTemplate result : recipe.fluidResults()) {
            builder.addSlot(RecipeIngredientRole.OUTPUT, x + 1, 5)
                    .setOutputSlotBackground()
                    .add(NeoForgeTypes.FLUID_STACK, result.create());
            x += SLOT;
        }
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
        builder.addText(Component.translatable("jei.craftworks.assembling.category", recipe.category().id()), getWidth(), 10)
                .setPosition(0, 33)
                .setColor(0xFF808080);
    }

    @Override
    public boolean needsRecipeBorder() {
        return true;
    }
}
