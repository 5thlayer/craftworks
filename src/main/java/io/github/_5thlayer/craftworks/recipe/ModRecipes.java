// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.recipe;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import com.google.gson.JsonElement;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import io.github._5thlayer.craftworks.CraftworksConfig;
import io.github._5thlayer.craftworks.mixin.minecraft.ShapedRecipeAccessor;
import io.github._5thlayer.craftworks.mixin.minecraft.ShapelessRecipeAccessor;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeMap;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import org.slf4j.Logger;

/**
 * Other mods' crafting recipes as Converted recipes (ADR-0011, #13): at the end of every recipe load,
 * after every datapack and KubeJS script has had its say, each shaped or shapeless crafting recipe is
 * replaced at its own id by an Assembling recipe, its slots counted into a bag, with the default time
 * and Route priority.
 *
 * <p>Left alone: anything already an Assembling recipe, the built-in vanilla pack's among them; vanilla's
 * ids while that pack is off; a subclass that assembles its own result; the namespaces and ids {@code modRecipesExcluded} names; and a recipe
 * with an ingredient or result the Assembling codec cannot write, which is logged.
 *
 * <p>The server config loads after the first recipe load, so that load converts with the defaults.
 * {@link #follow} reloads the recipes once the server has started, and on a config edit, whenever the
 * settings the last load used are not the ones configured now.
 */
public final class ModRecipes {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final String VANILLA = Identifier.DEFAULT_NAMESPACE;

    /** The settings the last recipe load converted with. */
    private static volatile Settings applied;

    private ModRecipes() {
    }

    private record Settings(boolean enabled, boolean vanillaPack, List<String> excluded) {

        static Settings configured() {
            return new Settings(CraftworksConfig.modRecipes(), CraftworksConfig.vanillaRecipes(),
                    CraftworksConfig.modRecipesExcluded());
        }

        boolean excludes(Identifier id) {
            return excluded.contains(id.getNamespace()) || excluded.contains(id.toString());
        }
    }

    /** The recipes with each crafting recipe that converts replaced at its id. */
    public static RecipeMap convert(RecipeMap recipes, HolderLookup.Provider registries) {
        Settings settings = Settings.configured();
        applied = settings;
        if (!settings.enabled()) {
            DataCarryingRecipes.replace(Map.of());
            return recipes;
        }
        var ops = withItemTags(registries);
        List<String> skipped = new ArrayList<>();
        int converted = 0;
        List<RecipeHolder<?>> holders = new ArrayList<>(recipes.values().size());
        Map<Identifier, CraftingRecipe> kept = new LinkedHashMap<>();
        for (RecipeHolder<?> holder : recipes.values()) {
            Identifier id = holder.id().identifier();
            // With the vanilla pack off, vanilla's ids stay at the table: that flag answers for them, not this one.
            boolean vanillaKept = !settings.vanillaPack() && id.getNamespace().equals(VANILLA);
            AssemblingRecipe assembling = vanillaKept || settings.excludes(id) ? null : toAssembling(holder.value());
            if (assembling != null && (assembling.results().stream().anyMatch(result -> ItemStackTemplate.CODEC.encodeStart(ops, result).isError())
                    || assembling.ingredients().stream()
                            .anyMatch(sized -> SizedIngredient.NESTED_CODEC.encodeStart(ops, sized).isError()))) {
                skipped.add(id.toString());
                assembling = null;
            }
            if (assembling == null) {
                holders.add(holder);
            } else {
                holders.add(new RecipeHolder<>(holder.id(), assembling));
                converted++;
                // A Data-carrying recipe: the original is kept, for a craft to ask what it made (ADR-0022).
                if (WrapperRecipes.unwrap(holder.value()) != null || assemblesItsOwn(holder.value())) kept.put(id, (CraftingRecipe) holder.value());
            }
        }
        DataCarryingRecipes.replace(kept);
        LOGGER.info("Personal Assembler: {} mod crafting recipe(s) converted to Assembling recipes, {} of them Data-carrying (the original assembles the result).", converted, kept.size());
        if (!skipped.isEmpty()) {
            LOGGER.warn("Personal Assembler: {} mod crafting recipe(s) left at the crafting table, with an ingredient or "
                    + "result an Assembling recipe cannot hold: {}", skipped.size(), skipped);
        }
        return converted == 0 ? recipes : RecipeMap.create(holders);
    }

    /**
     * The recipe as a Converted recipe, or null when it is not a shaped or shapeless one, or a wrapper of
     * one, with a fixed result.
     */
    private static AssemblingRecipe toAssembling(Recipe<?> recipe) {
        // A wrapper converts through the recipe it wraps, and that one says whether it is special (ADR-0023).
        Recipe<?> inner = WrapperRecipes.unwrap(recipe);
        if (inner == null) inner = recipe;
        if (inner.isSpecial()) return null;
        List<Ingredient> slots;
        ItemStackTemplate result;
        if (inner instanceof ShapedRecipe shaped) {
            slots = shaped.getIngredients().stream().flatMap(Optional::stream).toList();
            result = ((ShapedRecipeAccessor) shaped).craftworks$result();
        } else if (inner instanceof ShapelessRecipe shapeless) {
            slots = ((ShapelessRecipeAccessor) shapeless).craftworks$ingredients();
            result = shapeless.result();
        } else {
            return null;
        }
        if (result == null || slots.isEmpty()) return null;
        // One entry per distinct ingredient, counted: a bag, not a grid.
        Map<Ingredient, Integer> bag = new LinkedHashMap<>();
        for (Ingredient slot : slots) bag.merge(slot, 1, Integer::sum);
        List<SizedIngredient> ingredients = new ArrayList<>();
        bag.forEach((ingredient, count) -> ingredients.add(new SizedIngredient(ingredient, count)));
        return new AssemblingRecipe(ingredients, List.of(result), AssemblingRecipe.DEFAULT_TIME,
                AssemblingRecipe.DEFAULT_PRIORITY, List.of(), List.of(), true, AssemblingCategory.CRAFTING);
    }

    /**
     * Ops over the registries that can encode an item tag while the recipes load (#58). The recipes' tags
     * belong to the item registry, but the reload hands out a stand-in lookup holding the tags it hasn't bound
     * yet, and a tag only encodes in its own registry's ops; so the items are looked up in the registry itself.
     */
    private static DynamicOps<JsonElement> withItemTags(HolderLookup.Provider registries) {
        RegistryOps.RegistryInfoLookup rest = new RegistryOps.HolderLookupAdapter(registries);
        return RegistryOps.create(JsonOps.INSTANCE, new RegistryOps.RegistryInfoLookup() {
            @Override
            @SuppressWarnings("unchecked")
            public <E> Optional<RegistryOps.RegistryInfo<E>> lookup(ResourceKey<? extends Registry<? extends E>> key) {
                return key.equals(Registries.ITEM)
                        ? Optional.of((RegistryOps.RegistryInfo<E>) RegistryOps.RegistryInfo.fromRegistryLookup(BuiltInRegistries.ITEM))
                        : rest.lookup(key);
            }
        });
    }

    private static boolean assemblesItsOwn(Recipe<?> recipe) {
        try {
            Class<?> declaring = recipe.getClass().getMethod("assemble", CraftingInput.class).getDeclaringClass();
            return declaring != ShapedRecipe.class && declaring != ShapelessRecipe.class;
        } catch (NoSuchMethodException e) {
            return true;
        }
    }

    /** Reloads the recipes with the same packs when the last load converted with other settings; done at once otherwise. */
    public static CompletableFuture<Void> follow(MinecraftServer server) {
        Settings wanted = Settings.configured();
        if (wanted.equals(applied)) return CompletableFuture.completedFuture(null);
        LOGGER.info("Personal Assembler: modRecipes is {}, excluding {}; reloading the recipes", wanted.enabled(), wanted.excluded());
        return server.reloadResources(List.copyOf(server.getPackRepository().getSelectedIds()));
    }
}
