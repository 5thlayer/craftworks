// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.compat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import io.github._5thlayer.craftworks.CraftworksConfig;
import io.github._5thlayer.craftworks.machine.AssemblerTier;
import io.github._5thlayer.craftworks.machine.Assemblers;
import io.github._5thlayer.craftworks.machine.ChemicalPlantDefaults;
import io.github._5thlayer.craftworks.machine.ChemicalPlants;
import io.github._5thlayer.craftworks.machine.MachineKind;
import io.github._5thlayer.craftworks.machine.MachineTabs;
import io.github._5thlayer.craftworks.recipe.AssemblingCategory;
import io.github._5thlayer.craftworks.recipe.AssemblingRecipe;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link MachineTabs} from the server config as the client has it (ADR-0016), for EMI and JEI to read when
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
    public static MachineTabs current() {
        List<List<AssemblingCategory>> tiers = new ArrayList<>();
        for (AssemblerTier tier : AssemblerTier.values()) tiers.add(CraftworksConfig.categories(tier));
        return MachineTabs.of(tiers, CraftworksConfig.categories(ChemicalPlantDefaults.INSTANCE), oilRefinery());
    }

    /**
     * The categories the Oil Refinery holds: none, for now. There is no refinery block and so no {@code oil_refinery}
     * config section to read them from, so its tab is inert and {@code oil-processing} recipes show in no tab, as
     * before. When the section lands this reads {@code CraftworksConfig.categories} of the refinery's defaults
     * ({@code oil-processing}) as the plant's line above does.
     */
    private static List<AssemblingCategory> oilRefinery() {
        return List.of();
    }

    /** The recipes sorted into their tabs under the current config, quietly. */
    public static MachineTabs.Sorted<RecipeHolder<AssemblingRecipe>> sort(Collection<RecipeHolder<AssemblingRecipe>> recipes) {
        return current().sort(recipes, holder -> holder.value().category());
    }

    /**
     * The same, as a viewer builds its lists: logs how many are in no tab, once each time, and nothing when
     * every recipe has one.
     */
    public static MachineTabs.Sorted<RecipeHolder<AssemblingRecipe>> sortLogged(String viewer,
            Collection<RecipeHolder<AssemblingRecipe>> recipes) {
        MachineTabs.Sorted<RecipeHolder<AssemblingRecipe>> sorted = sort(recipes);
        if (sorted.leftOut() > 0) {
            LOGGER.info("Craftworks: {} of {} Assembling recipes are in no {} tab, since no machine holds their category",
                    sorted.leftOut(), recipes.size(), viewer);
        }
        return sorted;
    }

    /**
     * The item a machine's tab shows: Assembler 1 for the Assembler's, the Chemical Plant for its own, and the
     * Chemical Plant again for the Oil Refinery's until its item exists.
     */
    public static Item icon(MachineKind machine) {
        return switch (machine) {
            case ASSEMBLER -> Assemblers.item(AssemblerTier.ONE).get();
            case CHEMICAL_PLANT, OIL_REFINERY -> ChemicalPlants.ITEM.get();
        };
    }

    /** The workstations of a machine's tab, and of no other: every Assembler tier, or the Chemical Plant; none for the Oil Refinery until its block exists. */
    public static List<Item> workstations(MachineKind machine) {
        return switch (machine) {
            case ASSEMBLER -> Arrays.stream(AssemblerTier.values()).map(tier -> (Item) Assemblers.item(tier).get()).toList();
            case CHEMICAL_PLANT -> List.of(ChemicalPlants.ITEM.get());
            case OIL_REFINERY -> List.of();
        };
    }
}
