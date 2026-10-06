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
 * What a Chemical Plant may hold, as {@link HeldRecipes} says it for an Assembler: every Assembling recipe in a
 * category the plant's config lists, except one that its two input boxes, two output boxes, two input slots and
 * one product slot cannot take. It binds ingredients and results to boxes and slots by order, so each of these
 * is a plain count.
 */
final class ChemicalPlantRecipes {

    private ChemicalPlantRecipes() {
    }

    /** Whether the plant's config lists the recipe's category. */
    static boolean takesCategory(AssemblingRecipe recipe) {
        return CraftworksConfig.categories(ChemicalPlantDefaults.INSTANCE).contains(recipe.category());
    }

    /** Whether the recipe has no more fluid ingredients than input boxes and no more fluid results than output boxes. */
    static boolean fluidsFit(AssemblingRecipe recipe) {
        return recipe.fluidIngredients().size() <= ChemicalPlantFluids.INPUTS && recipe.fluidResults().size() <= ChemicalPlantFluids.OUTPUTS;
    }

    /** Whether one craft's fluid ingredients each fit an input box. An output box grows to hold its result. */
    static boolean fluidFits(AssemblingRecipe recipe) {
        return recipe.fluidIngredients().stream().allMatch(fluid -> fluid.amount() <= ChemicalPlantFluids.INPUT_CAPACITY);
    }

    static boolean fitsSlots(AssemblingRecipe recipe) {
        return recipe.ingredients().size() <= ChemicalPlantSlots.INPUTS;
    }

    /**
     * Whether a craft leaves nothing for a slot the plant has none of: one item result at most, the product's,
     * and no ingredient that leaves a remainder, whichever item it is that is put in. Not cheap: a plant asks
     * once per recipe instance, through {@link #canRun}.
     */
    static boolean itemsFit(AssemblingRecipe recipe) {
        if (recipe.results().size() > 1) {
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

    /** Whether a plant can run this recipe in a category it holds: what its boxes and slots take. */
    static boolean canRun(AssemblingRecipe recipe) {
        return fluidsFit(recipe) && fluidFits(recipe) && fitsSlots(recipe) && itemsFit(recipe);
    }

    /** What Fill Recipe on an open Chemical Plant would answer for this player: the Lock source is asked here, and only here. */
    static HoldVerdict verdict(ServerPlayer player, Identifier id) {
        Optional<AssemblingRecipe> recipe = HeldRecipes.find(player.level(), id).map(RecipeHolder::value);
        return HoldVerdict.of(recipe.map(found -> HoldVerdict.Checks.passing()
                .categoryHeld(takesCategory(found))
                .oneFluid(fluidsFit(found))
                .fluidFits(fluidFits(found))
                .fitsSlots(fitsSlots(found))
                .remaindersFit(itemsFit(found))
                .locked(RuntimePlanSource.lockedFor(player).test(id.toString())))
                .orElse(HoldVerdict.Checks.passing().resolves(false)));
    }
}
