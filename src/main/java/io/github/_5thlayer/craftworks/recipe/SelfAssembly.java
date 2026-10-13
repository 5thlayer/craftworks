// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.recipe;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.ShapedRecipe;

/**
 * The crafting recipes behind Data-carrying recipes (ADR-0022), kept so a finished craft can ask the
 * original what it made. The recipe load replaces them whole; nothing here is
 * saved or synced.
 */
public final class SelfAssembly {

    private static volatile Map<Identifier, CraftingRecipe> originals = Map.of();

    private SelfAssembly() {
    }

    static void replace(Map<Identifier, CraftingRecipe> kept) {
        originals = Map.copyOf(kept);
    }

    public static boolean keeps(Identifier id) {
        return id != null && originals.containsKey(id);
    }

    /**
     * What the original recipe makes of the stacks one craft consumed, one stack per item, or empty when
     * {@code id} assembles nothing of its own or the stacks do not fit its grid. A shaped recipe's are laid
     * back in its pattern, each slot taking the first stack its ingredient accepts.
     */
    public static Optional<ItemStack> assemble(Identifier id, List<ItemStack> consumed) {
        CraftingRecipe original = id == null ? null : originals.get(id);
        if (original == null) return Optional.empty();
        List<ItemStack> pool = new ArrayList<>(consumed);
        CraftingInput input;
        if (original instanceof ShapedRecipe shaped) {
            List<ItemStack> grid = new ArrayList<>();
            for (Optional<Ingredient> slot : shaped.pattern.ingredients()) {
                grid.add(slot.map(ingredient -> takeFirst(pool, ingredient)).orElse(ItemStack.EMPTY));
            }
            if (grid.contains(null)) return Optional.empty();
            input = CraftingInput.of(shaped.getWidth(), shaped.getHeight(), grid);
        } else {
            int width = Math.min(3, pool.size());
            List<ItemStack> grid = new ArrayList<>(pool);
            while (grid.size() % width != 0) grid.add(ItemStack.EMPTY);
            input = CraftingInput.of(width, grid.size() / width, grid);
        }
        ItemStack made = original.assemble(input);
        return made.isEmpty() ? Optional.empty() : Optional.of(made);
    }

    private static ItemStack takeFirst(List<ItemStack> pool, Ingredient ingredient) {
        for (Iterator<ItemStack> it = pool.iterator(); it.hasNext(); ) {
            ItemStack stack = it.next();
            if (ingredient.test(stack)) {
                it.remove();
                return stack;
            }
        }
        return null;
    }
}
