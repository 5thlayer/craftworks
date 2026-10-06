// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.machine;

import java.util.Optional;

import io.github._5thlayer.craftworks.CraftworksConfig;
import io.github._5thlayer.craftworks.assembler.RuntimePlanSource;
import io.github._5thlayer.craftworks.recipe.AssemblingRecipe;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.common.crafting.SizedIngredient;

/**
 * What a fluid machine may hold, as {@link HeldRecipes} says it for an Assembler: every Assembling recipe in a
 * category the machine's config lists, except one that its input and output boxes and its input and product
 * slots cannot take. It binds ingredients and results to boxes and slots by order, so each of these is a plain
 * count.
 */
final class FluidMachineRecipes {

    private FluidMachineRecipes() {
    }

    /** Whether the machine's config lists the recipe's category. */
    static boolean takesCategory(FluidMachine description, AssemblingRecipe recipe) {
        return CraftworksConfig.categories(description.defaults()).contains(recipe.category());
    }

    /** Whether the recipe has no more fluid ingredients than input boxes and no more fluid results than output boxes. */
    static boolean fluidsFit(FluidMachine description, AssemblingRecipe recipe) {
        return recipe.fluidIngredients().size() <= description.fluidInputs() && recipe.fluidResults().size() <= description.fluidOutputs();
    }

    static boolean fitsSlots(FluidMachine description, AssemblingRecipe recipe) {
        return recipe.ingredients().size() <= description.itemInputs();
    }

    /**
     * Whether a craft leaves nothing for a slot the machine has none of: one item result at most, the product's,
     * and none if the machine has no product slot, and no ingredient that leaves a remainder, whichever item it
     * is that is put in. Not cheap: a machine asks once per recipe instance, through {@link #canRun}.
     */
    static boolean itemsFit(FluidMachine description, AssemblingRecipe recipe) {
        if (recipe.results().size() > (description.product() ? 1 : 0)) {
            return false;
        }
        for (SizedIngredient sized : recipe.ingredients()) {
            for (Holder<Item> item : sized.ingredient().items().toList()) {
                if (item.value().getCraftingRemainder(new ItemStack(item)) != null) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Whether a machine can run this recipe in a category it holds: what its boxes and slots take. */
    static boolean canRun(FluidMachine description, AssemblingRecipe recipe) {
        return fluidsFit(description, recipe) && HeldRecipes.fluidFits(recipe) && fitsSlots(description, recipe) && itemsFit(description, recipe);
    }

    /** What Fill Recipe on an open fluid machine would answer for this player: the Lock source is asked here, and only here. */
    static HoldVerdict verdict(FluidMachine description, ServerPlayer player, Identifier id) {
        Optional<AssemblingRecipe> recipe = HeldRecipes.find(player.level(), id).map(RecipeHolder::value);
        return HoldVerdict.of(recipe.map(found -> HoldVerdict.Checks.passing()
                .categoryHeld(takesCategory(description, found))
                .oneFluid(fluidsFit(description, found))
                .fluidFits(HeldRecipes.fluidFits(found))
                .fitsSlots(fitsSlots(description, found))
                .remaindersFit(itemsFit(description, found))
                .locked(RuntimePlanSource.lockedFor(player).test(id.toString())))
                .orElse(HoldVerdict.Checks.passing().resolves(false)));
    }
}
