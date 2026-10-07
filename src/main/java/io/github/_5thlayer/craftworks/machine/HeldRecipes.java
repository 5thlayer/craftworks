// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.machine;

import java.util.Optional;

import io.github._5thlayer.craftworks.CraftworksConfig;
import io.github._5thlayer.craftworks.assembler.RuntimePlanSource;
import io.github._5thlayer.craftworks.recipe.AssemblingRecipe;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.common.crafting.SizedIngredient;

/**
 * What an Assembler may hold, read off the server's recipe manager when asked: a Held recipe is an id,
 * resolved lazily because a block entity loads before the recipes do.
 *
 * <p>Every Assembling recipe is one, Hand-craftable or not, except the kinds this tier cannot run: one
 * whose category the tier's server config does not list, one with more fluid ingredients or results than the
 * boxes of {@link FluidLayout#ASSEMBLER} (two in, three out) or with more of a fluid a craft than an input box
 * holds, one with more distinct ingredients than the five input slots, and one whose remainders don't fit the one
 * remainder slot.
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

    /** Whether the recipe has no more fluid ingredients than the layout has input boxes and no more fluid results than output boxes. */
    public static boolean fluidsHaveBoxes(FluidLayout layout, AssemblingRecipe recipe) {
        return recipe.fluidIngredients().size() <= layout.fluidInputs() && recipe.fluidResults().size() <= layout.fluidOutputs();
    }

    /** Whether one craft's fluid fits an input box: at most its capacity. */
    public static boolean fluidVolumeFits(AssemblingRecipe recipe) {
        return recipe.fluidIngredients().stream().allMatch(fluid -> fluid.amount() <= FluidBoxes.INPUT_VOLUME);
    }

    /** Whether the tier's config lists the recipe's category. */
    public static boolean takesCategory(AssemblerTier tier, AssemblingRecipe recipe) {
        return CraftworksConfig.categories(tier).contains(recipe.category());
    }

    public static boolean fitsSlots(AssemblingRecipe recipe) {
        return recipe.ingredients().size() <= AssemblerSlots.INPUTS;
    }

    /**
     * Whether one craft's remainders fit the one remainder slot: all one item with the same components, so
     * they stack, and no more of it than a stack holds. The recipe's results after the first count with
     * them, since they share the slot. Asked of every item each ingredient could be, so a
     * recipe held never jams on whichever one is put in. Not cheap: an Assembler asks once per recipe
     * instance, through {@link #canRun}.
     */
    public static boolean remaindersFit(AssemblingRecipe recipe) {
        ItemStackTemplate kind = null;
        int owed = 0;
        for (ItemStackTemplate extra : recipe.extraResults()) {
            if (kind != null && !ItemStack.isSameItemSameComponents(kind.create(), extra.create())) {
                return false;
            }
            kind = extra;
            owed += extra.count();
        }
        for (SizedIngredient sized : recipe.ingredients()) {
            int most = 0;
            for (Holder<Item> item : sized.ingredient().items().toList()) {
                ItemStackTemplate remainder = item.value().getCraftingRemainder(new ItemStack(item));
                if (remainder == null) {
                    continue;
                }
                if (kind != null && !ItemStack.isSameItemSameComponents(kind.create(), remainder.create())) {
                    return false;
                }
                kind = remainder;
                most = Math.max(most, remainder.count() * sized.count());
            }
            owed += most;
        }
        return kind == null || owed <= kind.create().getMaxStackSize();
    }

    /**
     * Whether an Assembler of any tier can run this recipe: its fluids fit the boxes of {@link
     * FluidLayout#ASSEMBLER}, it fits the input slots, and its remainders fit. What a tier adds, its categories,
     * is asked of the tier ({@link #takesCategory}).
     */
    public static boolean canRun(AssemblingRecipe recipe) {
        return fluidsHaveBoxes(FluidLayout.ASSEMBLER, recipe) && fluidVolumeFits(recipe) && fitsSlots(recipe) && remaindersFit(recipe);
    }


    /** What Fill Recipe on an open Assembler of this tier would answer for this player: the Lock source is asked here, and only here. */
    public static HoldVerdict verdict(ServerPlayer player, AssemblerTier tier, Identifier id) {
        Optional<AssemblingRecipe> recipe = find(player.level(), id).map(RecipeHolder::value);
        return HoldVerdict.of(recipe.map(found -> HoldVerdict.Checks.passing()
                .categoryHeld(takesCategory(tier, found))
                .fluidsHaveBoxes(fluidsHaveBoxes(FluidLayout.ASSEMBLER, found))
                .fluidVolumeFits(fluidVolumeFits(found))
                .fitsSlots(fitsSlots(found))
                .remaindersFit(remaindersFit(found))
                .locked(RuntimePlanSource.lockedFor(player).test(id.toString())))
                .orElse(HoldVerdict.Checks.passing().resolves(false)));
    }

    /** The recipe's name for a message: its product's, or else its id. */
    public static Component name(ServerLevel level, Identifier id) {
        return find(level, id).map(holder -> holder.value().product()).filter(product -> !product.isEmpty())
                .map(ItemStack::getHoverName)
                .orElse(Component.literal(id.toString()));
    }
}
