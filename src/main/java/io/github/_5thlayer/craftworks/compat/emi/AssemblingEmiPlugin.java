// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.compat.emi;

import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.stack.EmiStackInteraction;
import dev.emi.emi.screen.EmiScreenManager;
import dev.emi.emi.search.EmiSearch;
import io.github._5thlayer.craftworks.Craftworks;
import io.github._5thlayer.craftworks.assembler.AssemblingRecipeIds;
import io.github._5thlayer.craftworks.assembler.ReadyRecipeIds;
import io.github._5thlayer.craftworks.machine.AssemblerTier;
import io.github._5thlayer.craftworks.machine.Assemblers;
import io.github._5thlayer.craftworks.machine.ChemicalPlants;
import io.github._5thlayer.craftworks.machine.MachineKind;
import io.github._5thlayer.craftworks.machine.client.AssemblerScreen;
import io.github._5thlayer.craftworks.machine.client.ChemicalPlantScreen;
import io.github._5thlayer.craftworks.machine.client.HeldMachineScreen;
import io.github._5thlayer.craftworks.recipe.CraftworksRecipes;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Items;

/**
 * Puts {@code craftworks:assembling} in EMI, in a category of its own. EMI has never heard of the type,
 * so without one its recipes are in no viewer at all.
 *
 * <p>EMI finds this by its annotation and loads it only when EMI is installed; nothing else in the Mod
 * names an EMI type, so the Mod loads without it.
 *
 * <p>The Personal Assembler is the inventory screen rather than a block, so the Assemblers and the Chemical Plant
 * are the category's workstations; the crafter is only its icon, until the category has one of its own.
 *
 * <p>Fill Recipe reaches the Assembler through a handler on the player's inventory, which EMI keys under a
 * null menu type since {@code InventoryMenu} has none. So Fill Recipe queues from the inventory screen and
 * nowhere else.
 *
 * <p>The Assembler and Chemical Plant screens' ghosts answer Recipe and Uses as a real stack does (see {@link #ghostAt}).
 */
@EmiEntrypoint
public final class AssemblingEmiPlugin implements EmiPlugin {

    public static final EmiRecipeCategory ASSEMBLING = new EmiRecipeCategory(
            Identifier.fromNamespaceAndPath(Craftworks.MOD_ID, CraftworksRecipes.ASSEMBLING),
            EmiStack.of(Items.CRAFTER));

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
     * The ghost under the mouse on an Assembler's or a Chemical Plant's screen, as EMI's hovered stack, or none
     * where there isn't one, so EMI's own slot lookup answers for a real stack. One item, whatever count the
     * ghost is drawn with: Recipe and Uses ask about the item.
     */
    private static EmiStackInteraction ghostAt(HeldMachineScreen<?> screen, int mouseX, int mouseY) {
        return screen.ghostAt(mouseX, mouseY)
                .map(shown -> new EmiStackInteraction(EmiStack.of(shown.stack().copyWithCount(1))))
                .orElse(EmiStackInteraction.EMPTY);
    }

    @Override
    public void register(EmiRegistry registry) {
        registry.addCategory(ASSEMBLING);
        registry.getRecipeMap()
                .byType(CraftworksRecipes.ASSEMBLING_TYPE.get())
                .forEach(holder -> registry.addRecipe(new AssemblingEmiRecipe(ASSEMBLING, holder)));
        registry.addRecipeHandler(null, new PersonalAssemblerEmiHandler());
        registry.addRecipeHandler(Assemblers.MENU.get(), new HeldMachineEmiHandler<>(MachineKind.ASSEMBLER));
        registry.addRecipeHandler(ChemicalPlants.MENU.get(), new HeldMachineEmiHandler<>(MachineKind.CHEMICAL_PLANT));
        registry.addStackProvider(AssemblerScreen.class, AssemblingEmiPlugin::ghostAt);
        registry.addStackProvider(ChemicalPlantScreen.class, AssemblingEmiPlugin::ghostAt);
        for (AssemblerTier tier : AssemblerTier.values()) {
            registry.addWorkstation(ASSEMBLING, EmiStack.of(Assemblers.item(tier).get()));
        }
        registry.addWorkstation(ASSEMBLING, EmiStack.of(ChemicalPlants.ITEM.get()));
        AssemblingRecipeIds.onSync(AssemblingEmiPlugin::searchAgain);
        ReadyRecipeIds.onChange(AssemblingEmiPlugin::craftablesAgain);
    }
}
