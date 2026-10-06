// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.compat.jei;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.github._5thlayer.craftworks.Craftworks;
import io.github._5thlayer.craftworks.compat.ConfiguredTabs;
import io.github._5thlayer.craftworks.machine.MachineGhosts;
import io.github._5thlayer.craftworks.machine.MachineKind;
import io.github._5thlayer.craftworks.machine.AssemblerMenu;
import io.github._5thlayer.craftworks.machine.Assemblers;
import io.github._5thlayer.craftworks.machine.FluidMachineMenu;
import io.github._5thlayer.craftworks.machine.ChemicalPlants;
import io.github._5thlayer.craftworks.machine.client.AssemblerScreen;
import io.github._5thlayer.craftworks.machine.client.ChemicalPlantScreen;
import io.github._5thlayer.craftworks.machine.client.HeldMachineScreen;
import io.github._5thlayer.craftworks.recipe.AssemblingRecipe;
import io.github._5thlayer.craftworks.recipe.CraftworksRecipes;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.builder.IClickableIngredientFactory;
import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import mezz.jei.api.recipe.types.IRecipeHolderType;
import mezz.jei.api.runtime.IClickableIngredient;
import mezz.jei.api.registration.IAdvancedRegistration;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeMap;
import net.neoforged.neoforge.client.event.RecipesReceivedEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Puts {@code craftworks:assembling} in JEI, in a tab for each machine (ADR-0016), {@code craftworks:assembler}
 * and {@code craftworks:chemical_plant}, each holding the recipes its machine's categories name (see
 * {@link io.github._5thlayer.craftworks.machine.MachineTabs}), read from the config as JEI builds its lists.
 * Each has a recipe button that queues on the Personal Assembler the way EMI's Fill Recipe does (#12, ADR-0004).
 *
 * <p>JEI finds this by its annotation and loads it only when JEI is installed; nothing else in the Mod
 * names a JEI type, so the Mod loads without it.
 *
 * <p>The recipes are the ones the server sends the client (they are asked for in
 * {@code CraftworksNetwork}). JEI keeps its own copy of them out of its API, so this keeps one too, from
 * the same {@link RecipesReceivedEvent}: JEI listens at the lowest priority and starts after it, so the
 * copy is in place when {@link #registerRecipes} reads it.
 */
@JeiPlugin
public final class AssemblingJeiPlugin implements IModPlugin {

    private static final Map<MachineKind, IRecipeHolderType<AssemblingRecipe>> TABS = new EnumMap<>(MachineKind.class);

    static {
        for (MachineKind machine : MachineKind.values()) {
            TABS.put(machine, IRecipeHolderType.create(Identifier.fromNamespaceAndPath(Craftworks.MOD_ID, machine.tabName())));
        }
    }

    /** The machine's tab, {@code craftworks:assembler} or {@code craftworks:chemical_plant} (ADR-0016). */
    static IRecipeHolderType<AssemblingRecipe> tab(MachineKind machine) {
        return TABS.get(machine);
    }

    /** Whether the JEI category uid is one of the machines' tabs. */
    static boolean isTab(Identifier uid) {
        return TABS.values().stream().anyMatch(type -> type.getUid().equals(uid));
    }

    private static volatile RecipeMap received = RecipeMap.EMPTY;

    public AssemblingJeiPlugin() {
        NeoForge.EVENT_BUS.addListener(RecipesReceivedEvent.class, event -> received = event.getRecipeMap());
    }

    @Override
    public Identifier getPluginUid() {
        return Identifier.fromNamespaceAndPath(Craftworks.MOD_ID, "jei");
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        for (MachineKind machine : MachineKind.values()) {
            registration.addRecipeCategories(new AssemblingJeiCategory(registration.getJeiHelpers().getGuiHelper(), machine,
                    () -> AssemblingJeiCategory.widthOf(ConfiguredTabs.sort(assemblingRecipes()).in(machine))));
        }
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        var sorted = ConfiguredTabs.sortLogged("JEI", assemblingRecipes());
        for (MachineKind machine : MachineKind.values()) registration.addRecipes(tab(machine), sorted.in(machine));
    }

    /** Each machine is a workstation of its own tab only, all three Assembler tiers of the Assembler's. */
    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        for (MachineKind machine : MachineKind.values()) {
            ConfiguredTabs.workstations(machine).forEach(item -> registration.addCraftingStation(tab(machine), new ItemStack(item)));
        }
    }

    /** With an Assembler or a Chemical Plant open, the recipe's {@code +} sets its Held recipe (see {@link HeldMachineTransferHandler}). */
    @Override
    public void registerRecipeTransferHandlers(IRecipeTransferRegistration registration) {
        for (MachineKind machine : MachineKind.values()) {
            registration.addRecipeTransferHandler(
                    new HeldMachineTransferHandler<>(AssemblerMenu.class, Assemblers.MENU.get(), tab(machine)), tab(machine));
            registration.addRecipeTransferHandler(
                    new HeldMachineTransferHandler<>(FluidMachineMenu.class, ChemicalPlants.MENU.get(), tab(machine)), tab(machine));
        }
    }

    /** The Assembler and Chemical Plant screens' ghosts answer Recipe and Uses as a real stack does (see {@link #ghostAt}). */
    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addGuiContainerHandler(AssemblerScreen.class, ghosts());
        registration.addGuiContainerHandler(ChemicalPlantScreen.class, ghosts());
    }

    private static <S extends HeldMachineScreen<?>> IGuiContainerHandler<S> ghosts() {
        return new IGuiContainerHandler<>() {
            @Override
            public Optional<? extends IClickableIngredient<?>> getClickableIngredientUnderMouse(
                    IClickableIngredientFactory factory, S screen, double mouseX, double mouseY) {
                return ghostAt(factory, screen.ghostAt(mouseX, mouseY));
            }
        };
    }

    /**
     * The ghost under the mouse as JEI's hovered ingredient, or none where there isn't one, so JEI's own
     * slot lookup answers for a real stack. One item, whatever count the ghost is drawn with: Recipe and
     * Uses ask about the item.
     */
    private static Optional<IClickableIngredient<ItemStack>> ghostAt(
            IClickableIngredientFactory factory, Optional<MachineGhosts.Ghost> ghost) {
        return ghost.flatMap(shown -> factory.createBuilder(shown.stack().copyWithCount(1)).buildWithArea(shown.x(), shown.y(), 16, 16));
    }

    private static List<RecipeHolder<AssemblingRecipe>> assemblingRecipes() {
        return List.copyOf(received.byType(CraftworksRecipes.ASSEMBLING_TYPE.get()));
    }

    /** The recipe button, on Assembling recipes and no other (see {@link AssemblingRecipeButton}). */
    @Override
    public void registerAdvanced(IAdvancedRegistration registration) {
        registration.addRecipeButtonFactory(AssemblingRecipeButton::forLayout);
    }
}
