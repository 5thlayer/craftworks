// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.compat;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import io.github._5thlayer.craftworks.CraftworksConfig;
import io.github._5thlayer.craftworks.machine.AssemblerTier;
import io.github._5thlayer.craftworks.machine.Assemblers;
import io.github._5thlayer.craftworks.machine.AssemblerTab;
import io.github._5thlayer.craftworks.recipe.AssemblingRecipe;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link AssemblerTab} from the server config as the client has it (ADR-0016), for EMI and JEI to read when
 * they build their recipe lists. NeoForge's {@code ConfigSync} sends the server config as the player logs in,
 * so a config edit shows after the next recipe reload and not live. With each tab's icon and workstations, so
 * the two viewers cannot disagree. No viewer types, and nothing that draws, so either viewer's plugin can call
 * it from its loading thread.
 */
public final class ConfiguredTabs {

    private static final Logger LOGGER = LoggerFactory.getLogger("craftworks");

    private ConfiguredTabs() {
    }

    /** The tabs under the config as it is now. */
    public static AssemblerTab current() {
        return AssemblerTab.of(Arrays.stream(AssemblerTier.values())
                .flatMap(tier -> CraftworksConfig.categories(tier).stream()).distinct().toList());
    }

    /** The recipes sorted into their tabs under the current config, quietly. */
    public static AssemblerTab.Sorted<RecipeHolder<AssemblingRecipe>> sort(Collection<RecipeHolder<AssemblingRecipe>> recipes) {
        return current().sort(recipes, holder -> holder.value().category());
    }

    /**
     * The same, as a viewer builds its lists: logs how many are in no tab, once each time, and nothing when
     * every recipe has one.
     */
    public static AssemblerTab.Sorted<RecipeHolder<AssemblingRecipe>> sortLogged(String viewer,
            Collection<RecipeHolder<AssemblingRecipe>> recipes) {
        AssemblerTab.Sorted<RecipeHolder<AssemblingRecipe>> sorted = sort(recipes);
        if (sorted.leftOut() > 0) {
            LOGGER.info("Craftworks: {} of {} Assembling recipes are in no {} tab, since no machine holds their category",
                    sorted.leftOut(), recipes.size(), viewer);
        }
        return sorted;
    }

    /** The item the Assembler's tab shows: Assembler 1. */
    public static Item icon() {
        return Assemblers.item(AssemblerTier.ONE).get();
    }

    /** The workstations of the Assembler's tab: every Assembler tier. */
    public static List<Item> workstations() {
        return Arrays.stream(AssemblerTier.values()).map(tier -> (Item) Assemblers.item(tier).get()).toList();
    }
}
