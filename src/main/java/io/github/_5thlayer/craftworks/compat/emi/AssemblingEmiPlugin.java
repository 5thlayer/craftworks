// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.compat.emi;

import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.recipe.VanillaEmiRecipeCategories;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.stack.EmiStackInteraction;
import dev.emi.emi.screen.EmiScreenManager;
import dev.emi.emi.search.EmiSearch;
import io.github._5thlayer.craftworks.Craftworks;
import io.github._5thlayer.craftworks.assembler.AssemblingRecipeIds;
import io.github._5thlayer.craftworks.assembler.ReadyRecipeIds;
import io.github._5thlayer.craftworks.compat.ConfiguredTabs;
import io.github._5thlayer.craftworks.machine.Assemblers;
import io.github._5thlayer.craftworks.machine.Refiners;
import io.github._5thlayer.craftworks.machine.AssemblerTab;
import io.github._5thlayer.craftworks.machine.client.AssemblerScreen;
import io.github._5thlayer.craftworks.recipe.CraftworksRecipes;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

/**
 * Puts {@code craftworks:assembling} in EMI, in one tab, {@code craftworks:assembler}, holding the recipes whose
 * category an Assembler tier holds (see {@link AssemblerTab}). EMI has
 * never heard of the type, so without a category its recipes are in no viewer at all.
 *
 * <p>EMI finds this by its annotation and loads it only when EMI is installed; nothing else in the Mod
 * names an EMI type, so the Mod loads without it.
 *
 * <p>The Personal Assembler is the inventory screen rather than a block, so it is no workstation; all three
 * Assembler tiers are. The tab and workstations are read from the config as the recipe lists are built, on EMI's
 * loading thread: nothing here touches the font or {@code RenderSystem}.
 *
 * <p>Fill Recipe reaches the Assembler through a handler on the player's inventory, which EMI keys under a
 * null menu type since {@code InventoryMenu} has none. So Fill Recipe queues from the inventory screen and
 * nowhere else.
 *
 * <p>The Assembler screen's ghosts answer Recipe and Uses as a real stack does (see {@link #ghostAt}).
 */
@EmiEntrypoint
public final class AssemblingEmiPlugin implements EmiPlugin {

    /** The Assembler's tab, {@code craftworks:assembler}, icon Assembler 1; built at registration, when the items exist. */
    private static EmiRecipeCategory category() {
        return new EmiRecipeCategory(Identifier.fromNamespaceAndPath(Craftworks.MOD_ID, AssemblerTab.NAME),
                EmiStack.of(ConfiguredTabs.icon()));
    }

    /**
     * Runs the search again when the recipe set syncs, so {@code @craftworks} lists what it makes after
     * {@code /reload} without the player retyping it. Not before EMI has baked its search.
     */
    private static void searchAgain() {
        if (!EmiScreenManager.isDisabled()) EmiSearch.update();
    }

    /**
     * Works EMI's craftables out again when the Ready set changes. EMI does so only when the player's
     * items differ from the last time it looked, and a Lock lifting or a sync arriving changes no item.
     * Not before EMI has loaded, and not without a player: EMI reads the inventory to do it.
     */
    private static void craftablesAgain() {
        if (!EmiScreenManager.isDisabled() && Minecraft.getInstance().player != null) EmiScreenManager.forceRecalculate();
    }

    /**
     * The ghost under the mouse on the Assembler's screen, as EMI's hovered stack, or none
     * where there isn't one, so EMI's own slot lookup answers for a real stack. One item, whatever count the
     * ghost is drawn with: Recipe and Uses ask about the item.
     */
    private static EmiStackInteraction ghostAt(AssemblerScreen screen, int mouseX, int mouseY) {
        return screen.ghostAt(mouseX, mouseY)
                .map(shown -> new EmiStackInteraction(EmiStack.of(shown.stack().copyWithCount(1))))
                .orElse(EmiStackInteraction.EMPTY);
    }

    @Override
    public void register(EmiRegistry registry) {
        EmiRecipeCategory tab = category();
        registry.addCategory(tab);
        var sorted = ConfiguredTabs.sortLogged("EMI", registry.getRecipeMap().byType(CraftworksRecipes.ASSEMBLING_TYPE.get()));
        for (var holder : sorted.recipes()) {
            registry.addRecipe(new AssemblingEmiRecipe(tab, holder));
        }
        registry.addRecipeHandler(null, new PersonalAssemblerEmiHandler());
        registry.addRecipeHandler(Assemblers.MENU.get(), new AssemblerEmiHandler());
        registry.addStackProvider(AssemblerScreen.class, AssemblingEmiPlugin::ghostAt);
        ConfiguredTabs.workstations().forEach(item -> registry.addWorkstation(tab, EmiStack.of(item)));
        // The Refiner smelts what vanilla's furnace and blast furnace do, so it is their tabs' workstation (ADR-0127).
        registry.addWorkstation(VanillaEmiRecipeCategories.SMELTING, EmiStack.of(Refiners.ITEM.get()));
        registry.addWorkstation(VanillaEmiRecipeCategories.BLASTING, EmiStack.of(Refiners.ITEM.get()));
        AssemblingRecipeIds.onSync(AssemblingEmiPlugin::searchAgain);
        ReadyRecipeIds.onChange(AssemblingEmiPlugin::craftablesAgain);
    }
}
