// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.recipe;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

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
public final class DataCarryingRecipes {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static volatile Map<Identifier, CraftingRecipe> originals = Map.of();

    private DataCarryingRecipes() {
    }

    static void replace(Map<Identifier, CraftingRecipe> kept) {
        originals = Map.copyOf(kept);
    }

    public static boolean isDataCarrying(Identifier id) {
        return id != null && originals.containsKey(id);
    }

    /**
     * What the original recipe makes of the stacks one craft consumed, one stack per item, or empty when
     * {@code id} assembles nothing of its own or the stacks do not fit its grid (a warning names the recipe
     * then, and the caller falls back to the listed result). A shaped recipe's are laid back in its pattern,
     * any valid placement being found.
     */
    public static Optional<ItemStack> assemble(Identifier id, List<ItemStack> consumed) {
        CraftingRecipe original = id == null ? null : originals.get(id);
        if (original == null) return Optional.empty();
        CraftingInput input = null;
        if (original instanceof ShapedRecipe shaped) {
            List<ItemStack> grid = layOut(shaped.pattern.ingredients(), consumed);
            if (grid != null) input = CraftingInput.of(shaped.getWidth(), shaped.getHeight(), grid);
        } else if (!consumed.isEmpty()) {
            int width = Math.min(3, consumed.size());
            List<ItemStack> grid = new ArrayList<>(consumed);
            while (grid.size() % width != 0) grid.add(ItemStack.EMPTY);
            input = CraftingInput.of(width, grid.size() / width, grid);
        }
        ItemStack made = input == null ? ItemStack.EMPTY : original.assemble(input);
        if (made.isEmpty()) {
            LOGGER.warn("Personal Assembler: {} is Data-carrying but assembled nothing from {}; the listed result is made instead.",
                    id, consumed);
            return Optional.empty();
        }
        return Optional.of(made);
    }

    /**
     * The stacks in the pattern's slots, each empty slot an empty stack and each ingredient given a stack it
     * accepts, or null when no assignment of stacks to slots exists. Backtracks over the slots, since slots that
     * overlap would strand a stack under a first-fit.
     */
    public static List<ItemStack> layOut(List<Optional<Ingredient>> slots, List<ItemStack> stacks) {
        ItemStack[] grid = new ItemStack[slots.size()];
        return place(slots, stacks, new boolean[stacks.size()], grid, 0) ? Arrays.asList(grid) : null;
    }

    private static boolean place(List<Optional<Ingredient>> slots, List<ItemStack> stacks, boolean[] used,
                                 ItemStack[] grid, int slot) {
        if (slot == grid.length) return true;
        Optional<Ingredient> wanted = slots.get(slot);
        if (wanted.isEmpty()) {
            grid[slot] = ItemStack.EMPTY;
            return place(slots, stacks, used, grid, slot + 1);
        }
        for (int i = 0; i < stacks.size(); i++) {
            if (used[i] || !wanted.get().test(stacks.get(i))) continue;
            used[i] = true;
            grid[slot] = stacks.get(i);
            if (place(slots, stacks, used, grid, slot + 1)) return true;
            used[i] = false;
        }
        return false;
    }
}
