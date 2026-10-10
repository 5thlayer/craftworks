// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.compat.jei;

import java.util.List;
import java.util.Optional;

import io.github._5thlayer.craftworks.Craftworks;
import io.github._5thlayer.craftworks.compat.ConfiguredTabs;
import io.github._5thlayer.craftworks.machine.MachineGhosts;
import io.github._5thlayer.craftworks.machine.Refiners;
import io.github._5thlayer.craftworks.machine.AssemblerTab;
import io.github._5thlayer.craftworks.machine.client.AssemblerScreen;
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
import mezz.jei.api.constants.RecipeTypes;
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
 * Puts {@code craftworks:assembling} in JEI, in one tab (ADR-0016), {@code craftworks:assembler}, holding the
 * recipes an Assembler tier's categories name (see {@link AssemblerTab}),
 * read from the config as JEI builds its lists. It has a recipe button that queues on the Personal Assembler the
 * way EMI's Fill Recipe does (#12, ADR-0004).
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

    /** The Assembler's tab, {@code craftworks:} its {@link AssemblerTab#NAME} (ADR-0016). */
    static final IRecipeHolderType<AssemblingRecipe> TAB =
            IRecipeHolderType.create(Identifier.fromNamespaceAndPath(Craftworks.MOD_ID, AssemblerTab.NAME));

    /** Whether the JEI category uid is the Assembler's tab. */
    static boolean isTab(Identifier uid) {
        return TAB.getUid().equals(uid);
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
        registration.addRecipeCategories(new AssemblingJeiCategory(registration.getJeiHelpers().getGuiHelper(),
                () -> AssemblingJeiCategory.widthOf(ConfiguredTabs.sort(assemblingRecipes()).recipes())));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        var sorted = ConfiguredTabs.sortLogged("JEI", assemblingRecipes());
        registration.addRecipes(TAB, sorted.recipes());
    }

    /** All three Assembler tiers are workstations of the tab. */
    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        ConfiguredTabs.workstations().forEach(item -> registration.addCraftingStation(TAB, new ItemStack(item)));
        // The Refiner smelts what vanilla's furnace and blast furnace do, so it is their tabs' workstation (ADR-0020).
        registration.addCraftingStation(RecipeTypes.SMELTING, new ItemStack(Refiners.ITEM.get()));
        registration.addCraftingStation(RecipeTypes.BLASTING, new ItemStack(Refiners.ITEM.get()));
    }

    /** With an Assembler open, the recipe's {@code +} sets its Held recipe (see {@link AssemblerTransferHandler}). */
    @Override
    public void registerRecipeTransferHandlers(IRecipeTransferRegistration registration) {
        registration.addRecipeTransferHandler(new AssemblerTransferHandler(TAB), TAB);
    }

    /** The Assembler screen's ghosts answer Recipe and Uses as a real stack does (see {@link #ghostAt}). */
    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addGuiContainerHandler(AssemblerScreen.class, ghosts());
    }

    private static IGuiContainerHandler<AssemblerScreen> ghosts() {
        return new IGuiContainerHandler<>() {
            @Override
            public Optional<? extends IClickableIngredient<?>> getClickableIngredientUnderMouse(
                    IClickableIngredientFactory factory, AssemblerScreen screen, double mouseX, double mouseY) {
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
