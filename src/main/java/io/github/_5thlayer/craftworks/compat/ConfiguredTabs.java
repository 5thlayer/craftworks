// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.compat;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;

import io.github._5thlayer.craftworks.CraftworksConfig;
import io.github._5thlayer.craftworks.machine.AssemblerTier;
import io.github._5thlayer.craftworks.machine.ChemicalPlantDefaults;
import io.github._5thlayer.craftworks.machine.MachineTabs;
import io.github._5thlayer.craftworks.recipe.AssemblingCategory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link MachineTabs} from the server config as the client has it (ADR-0016), for EMI and JEI to read when
 * they build their recipe lists. NeoForge's {@code ConfigSync} sends the server config as the player logs in,
 * so a config edit shows after the next recipe reload and not live. No viewer types, so either viewer's
 * plugin can call it from its loading thread.
 */
public final class ConfiguredTabs {

    private static final Logger LOGGER = LoggerFactory.getLogger("craftworks");

    private ConfiguredTabs() {
    }

    /** The tabs under the config as it is now. */
    public static MachineTabs current() {
        List<List<AssemblingCategory>> tiers = new ArrayList<>();
        for (AssemblerTier tier : AssemblerTier.values()) tiers.add(CraftworksConfig.categories(tier));
        return MachineTabs.of(tiers, CraftworksConfig.categories(ChemicalPlantDefaults.INSTANCE));
    }

    /**
     * The recipes sorted into their tabs under the current config. Logs how many are in none, once for each
     * call, so once each time a viewer builds its lists.
     */
    public static <R> MachineTabs.Sorted<R> sort(String viewer, Collection<R> recipes, Function<R, AssemblingCategory> categoryOf) {
        MachineTabs.Sorted<R> sorted = current().sort(recipes, categoryOf);
        LOGGER.info("{}: {} of {} Assembling recipes are in no tab, since no machine holds their category",
                viewer, sorted.leftOut(), recipes.size());
        return sorted;
    }
}
