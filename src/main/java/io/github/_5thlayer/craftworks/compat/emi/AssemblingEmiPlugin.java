// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.compat.emi;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;

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
import io.github._5thlayer.craftworks.compat.ConfiguredTabs;
import io.github._5thlayer.craftworks.machine.Assemblers;
import io.github._5thlayer.craftworks.machine.FluidMachines;
import io.github._5thlayer.craftworks.machine.MachineKind;
import io.github._5thlayer.craftworks.machine.client.AssemblerScreen;
import io.github._5thlayer.craftworks.machine.client.FluidMachineScreen;
import io.github._5thlayer.craftworks.machine.client.HeldMachineScreen;
import io.github._5thlayer.craftworks.recipe.CraftworksRecipes;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

/**
 * Puts {@code craftworks:assembling} in EMI, in a tab for each machine (ADR-0016): {@code craftworks:assembler}
 * and {@code craftworks:chemical_plant}, each holding the recipes whose category its machine holds
 * (see {@link io.github._5thlayer.craftworks.machine.MachineTabs}). EMI has never heard of the type,
 * so without a category its recipes are in no viewer at all.
 *
 * <p>EMI finds this by its annotation and loads it only when EMI is installed; nothing else in the Mod
 * names an EMI type, so the Mod loads without it.
 *
 * <p>The Personal Assembler is the inventory screen rather than a block, so it is no workstation. Each
 * machine is a workstation of its own tab only, all three Assembler tiers of the Assembler's. The tabs and
 * workstations are read from the config as the recipe lists are built, on EMI's loading thread: nothing here
 * touches the font or {@code RenderSystem}.
 *
 * <p>Fill Recipe reaches the Assembler through a handler on the player's inventory, which EMI keys under a
 * null menu type since {@code InventoryMenu} has none. So Fill Recipe queues from the inventory screen and
 * nowhere else.
 *
 * <p>The machine screens' ghosts answer Recipe and Uses as a real stack does (see {@link #ghostAt}).
 */
@EmiEntrypoint
public final class AssemblingEmiPlugin implements EmiPlugin {

    /** The Assembler's tab, {@code craftworks:assembler}, icon Assembler 1; built at registration, when the items exist. */
    private static EmiRecipeCategory category(MachineKind machine) {
        return new EmiRecipeCategory(Identifier.fromNamespaceAndPath(Craftworks.MOD_ID, machine.tabName()),
                EmiStack.of(ConfiguredTabs.icon(machine)));
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
     * The ghost under the mouse on a machine's screen, as EMI's hovered stack, or none
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
        Map<MachineKind, EmiRecipeCategory> tabs = new EnumMap<>(MachineKind.class);
        for (MachineKind machine : MachineKind.values()) {
            tabs.put(machine, category(machine));
            registry.addCategory(tabs.get(machine));
        }
        var sorted = ConfiguredTabs.sortLogged("EMI", registry.getRecipeMap().byType(CraftworksRecipes.ASSEMBLING_TYPE.get()));
        var inAssembler = new HashSet<>(sorted.in(MachineKind.ASSEMBLER));
        for (MachineKind machine : MachineKind.values()) {
            for (var holder : sorted.in(machine)) {
                // A recipe in both tabs keeps its own id in the Assembler's and gets a derived one in the plant's,
                // since EMI keys a recipe by id (see AssemblingEmiRecipe).
                boolean sharedWithAssembler = machine != MachineKind.ASSEMBLER && inAssembler.contains(holder);
                registry.addRecipe(new AssemblingEmiRecipe(tabs.get(machine), machine, holder, sharedWithAssembler));
            }
        }
        registry.addRecipeHandler(null, new PersonalAssemblerEmiHandler());
        registry.addRecipeHandler(Assemblers.MENU.get(), new HeldMachineEmiHandler<>(MachineKind.ASSEMBLER));
        for (var machine : FluidMachines.all()) {
            registry.addRecipeHandler(machine.menu().get(), new HeldMachineEmiHandler<>(machine.machine().kind()));
        }
        registry.addStackProvider(AssemblerScreen.class, AssemblingEmiPlugin::ghostAt);
        registry.addStackProvider(FluidMachineScreen.class, AssemblingEmiPlugin::ghostAt);
        for (MachineKind machine : MachineKind.values()) {
            ConfiguredTabs.workstations(machine).forEach(item -> registry.addWorkstation(tabs.get(machine), EmiStack.of(item)));
        }
        AssemblingRecipeIds.onSync(AssemblingEmiPlugin::searchAgain);
        ReadyRecipeIds.onChange(AssemblingEmiPlugin::craftablesAgain);
    }
}
