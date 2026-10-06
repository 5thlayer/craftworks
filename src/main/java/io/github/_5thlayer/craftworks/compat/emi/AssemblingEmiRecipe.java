// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.compat.emi;

import dev.emi.emi.api.neoforge.NeoForgeEmiIngredient;
import dev.emi.emi.api.neoforge.NeoForgeEmiStack;
import dev.emi.emi.api.recipe.BasicEmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.render.EmiTexture;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.WidgetHolder;
import io.github._5thlayer.craftworks.Craftworks;
import io.github._5thlayer.craftworks.compat.RecipeRow;
import io.github._5thlayer.craftworks.machine.MachineKind;
import io.github._5thlayer.craftworks.recipe.AssemblingRecipe;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.crafting.RecipeHolder;

/**
 * One Assembling recipe as EMI draws it: the item and then the fluid ingredients in a row, an arrow timed
 * to the craft, every item and fluid result, and the recipe's category beneath. A row and not a grid,
 * because an ingredient count (eight plates) is what a grid cannot say.
 */
public class AssemblingEmiRecipe extends BasicEmiRecipe {

    private static final int SLOT = RecipeRow.SLOT;

    private final int time;
    private final Component secondsLine;
    private final Component categoryLine;
    private boolean measured;

    /** The recipe's own id, which Fill Recipe and the Ready set name it by; EMI's id for the copy in the plant's tab may differ. */
    private final Identifier recipeId;

    /**
     * @param tab the machine whose tab this is
     * @param sharedWithAssembler the recipe is also in the Assembler's tab, which keeps its own id as EMI's: EMI
     *     keys a recipe by id, so this copy's is {@code craftworks:<tab>/<namespace>/<path>}
     */
    public AssemblingEmiRecipe(EmiRecipeCategory category, MachineKind tab, RecipeHolder<AssemblingRecipe> holder, boolean sharedWithAssembler) {
        super(category, sharedWithAssembler ? copyId(tab, holder.id().identifier()) : holder.id().identifier(), 0, 44);
        this.recipeId = holder.id().identifier();
        AssemblingRecipe recipe = holder.value();
        this.time = recipe.time();
        this.secondsLine = Component.translatable("emi.craftworks.assembling.seconds", String.format("%.1f", time / 20F));
        this.categoryLine = Component.translatable("emi.craftworks.assembling.category", recipe.category().id());
        recipe.ingredients().forEach(sized -> inputs.add(NeoForgeEmiIngredient.of(sized)));
        recipe.fluidIngredients().forEach(fluid -> inputs.add(NeoForgeEmiIngredient.of(fluid)));
        recipe.results().forEach(result -> outputs.add(EmiStack.of(result.create())));
        recipe.fluidResults().forEach(result -> outputs.add(NeoForgeEmiStack.of(result.create())));
        this.width = RecipeRow.width(inputs.size(), outputs.size(), 0, 0);
    }

    /** EMI's id for the copy of {@code recipe} in {@code tab}'s, when the Assembler's tab has it under its own. */
    private static Identifier copyId(MachineKind tab, Identifier recipe) {
        return Identifier.fromNamespaceAndPath(Craftworks.MOD_ID, tab.tabName() + "/" + recipe.getNamespace() + "/" + recipe.getPath());
    }

    /** The recipe's own id, whichever tab the recipe is drawn in; an EMI recipe of another mod's is its {@code getId}. */
    public static Identifier recipeIdOf(EmiRecipe recipe) {
        return recipe instanceof AssemblingEmiRecipe ours ? ours.recipeId : recipe.getId();
    }

    /**
     * As wide as its widest line of text too, so a narrow recipe's category stays off the buttons beside it
     * (#33). Measured here, on the render thread, and not when EMI builds the recipe on its loading thread:
     * measuring a glyph the font hasn't baked yet uploads it, which only the render thread may do.
     */
    @Override
    public int getDisplayWidth() {
        if (!measured) {
            var font = Minecraft.getInstance().font;
            width = RecipeRow.width(inputs.size(), outputs.size(), font.width(secondsLine), font.width(categoryLine));
            measured = true;
        }
        return width;
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
        widgets.addText(secondsLine, x + RecipeRow.SECONDS_INSET, 24, 0xFF808080, false);
        widgets.addText(categoryLine, 0, 35, 0xFF808080, false);
        x += RecipeRow.ARROW;
        for (EmiStack output : outputs) {
            widgets.addSlot(output, x, 4).recipeContext(this);
            x += SLOT;
        }
    }
}
