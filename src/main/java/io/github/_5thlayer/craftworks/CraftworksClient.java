// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks;

import io.github._5thlayer.craftworks.api.IndexTab;
import io.github._5thlayer.craftworks.api.IndexTabs;
import io.github._5thlayer.craftworks.assembler.client.AssemblerClient;
import io.github._5thlayer.craftworks.tabs.TabFiles;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.ModLoadingException;
import net.neoforged.fml.ModLoadingIssue;
import net.neoforged.fml.common.Mod;

/** The Mod's client half, which a dedicated server never loads. */
@Mod(value = Craftworks.MOD_ID, dist = Dist.CLIENT)
public final class CraftworksClient {

    /**
     * The message a client with no recipe viewer stops on. Passed as the issue's translation key,
     * which FML shows as written when nothing translates it: the loading screen appears before any
     * resource pack, so a key in the Mod's lang file would never be read there.
     */
    private static final String NO_RECIPE_VIEWER =
            "Craftworks needs a recipe viewer: install EMI or JEI. The Personal Assembler has no item list"
                    + " of its own, and asks for items through the viewer's Fill Recipe.";

    /** The index tab Craftworks ships: everything the Assembler makes, under the crafter's icon (#15). */
    private static final IndexTab ASSEMBLING_TAB = new IndexTab(
            Craftworks.MOD_ID + ":assembling", "minecraft:crafter", "craftworks.tab.assembling", "@" + Craftworks.MOD_ID, 0);

    public CraftworksClient(IEventBus modBus) {
        // Client only: a dedicated server resolves plans itself and never talks to the viewer (ADR-0009).
        if (!ModList.get().isLoaded("emi") && !ModList.get().isLoaded("jei")) {
            // Thrown, not added: FML checks added issues before mods are constructed, so one added here
            // would only be shown when load warnings are on.
            throw new ModLoadingException(ModLoadingIssue.error(NO_RECIPE_VIEWER));
        }
        AssemblerClient.register(modBus);
        IndexTabs.register(ASSEMBLING_TAB);
        TabFiles.register(modBus);
    }
}
