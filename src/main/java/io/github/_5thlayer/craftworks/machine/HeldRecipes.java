// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.machine;

import java.util.Optional;

import io.github._5thlayer.craftworks.CraftworksConfig;
import io.github._5thlayer.craftworks.assembler.PlayerPlanning;
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
 * holds, one with more distinct ingredients than the five input slots, and one whose remainders don't fit the
 * shared output slots (at most one kind of item, at most three stacks of it: the outputs the product leaves).
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

    /** Whether the tier's config lists the recipe's category. */
    public static boolean takesCategory(AssemblerTier tier, AssemblingRecipe recipe) {
        return CraftworksConfig.categories(tier).contains(recipe.category());
    }

    /**
     * Whether one craft's remainders fit the shared outputs: all one item with the same components, so
     * they stack, and no more of it than a stack holds. The recipe's results after the first count with
     * them, since they share the leftovers' rule. Asked of every item each ingredient could be, so a
     * recipe held never jams on whichever one is put in. The product takes one of the four shared outputs, so
     * what is owed fits in the other three: two further potions of a brew that makes three, which stack to one.
     * Not cheap: an Assembler asks once per recipe
     * instance, through {@link #canRun}.
     */
    private static boolean remaindersFit(AssemblingRecipe recipe) {
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
        return kind == null || owed <= kind.create().getMaxStackSize() * (AssemblerSlots.OUTPUTS - 1);
    }

    /**
     * The first rule that keeps an Assembler of any tier from ever running this recipe, in the order of
     * {@link HoldVerdict}, or empty when it can: it has no more fluid ingredients than the boxes of {@link
     * FluidLayout#ASSEMBLER} have inputs and no more fluid results than outputs, no more of a fluid a craft
     * than a box holds ({@link FluidBoxes#VOLUME}), no more distinct ingredients than the input slots, and
     * remainders that fit. Each is asked only when the ones before it passed. What a tier adds, its
     * categories, is asked of the tier ({@link #takesCategory}).
     */
    public static Optional<HoldVerdict> assemblerRefusal(AssemblingRecipe recipe) {
        if (recipe.fluidIngredients().size() > FluidLayout.ASSEMBLER.fluidInputs()
                || recipe.fluidResults().size() > FluidLayout.ASSEMBLER.fluidOutputs()) {
            return Optional.of(HoldVerdict.TOO_MANY_FLUIDS);
        }
        if (!recipe.fluidIngredients().stream().allMatch(fluid -> FluidBoxes.fits(fluid.amount()))
                || !recipe.fluidResults().stream().allMatch(fluid -> FluidBoxes.fits(fluid.amount()))) {
            return Optional.of(HoldVerdict.FLUID_TOO_LARGE);
        }
        if (recipe.ingredients().size() > AssemblerSlots.INPUTS) {
            return Optional.of(HoldVerdict.TOO_MANY_INGREDIENTS);
        }
        return remaindersFit(recipe) ? Optional.empty() : Optional.of(HoldVerdict.REMAINDERS_DONT_FIT);
    }

    /** Whether an Assembler of any tier can run this recipe: it has no {@link #assemblerRefusal}. */
    public static boolean canRun(AssemblingRecipe recipe) {
        return assemblerRefusal(recipe).isEmpty();
    }

    /**
     * What Fill Recipe on an open Assembler of this tier would answer for this player. The Lock source is
     * asked here, and only here, and only when every rule before it passed.
     */
    public static HoldVerdict verdict(ServerPlayer player, AssemblerTier tier, Identifier id) {
        Optional<AssemblingRecipe> found = find(player.level(), id).map(RecipeHolder::value);
        if (found.isEmpty()) {
            return HoldVerdict.NOT_ASSEMBLING;
        }
        AssemblingRecipe recipe = found.get();
        if (!takesCategory(tier, recipe)) {
            return HoldVerdict.WRONG_CATEGORY;
        }
        Optional<HoldVerdict> refusal = assemblerRefusal(recipe);
        if (refusal.isPresent()) {
            return refusal.get();
        }
        return PlayerPlanning.lockedFor(player).test(id.toString()) ? HoldVerdict.LOCKED : HoldVerdict.HELD;
    }

    /** The recipe's name for a message: its product's, or else its id. */
    public static Component name(ServerLevel level, Identifier id) {
        return find(level, id).map(holder -> holder.value().product()).filter(product -> !product.isEmpty())
                .map(ItemStack::getHoverName)
                .orElse(Component.literal(id.toString()));
    }
}
