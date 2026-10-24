// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.recipe;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.mojang.logging.LogUtils;
import io.github._5thlayer.craftworks.CraftworksConfig;
import io.github._5thlayer.craftworks.machine.AssemblerSlots;
import io.github._5thlayer.craftworks.mixin.minecraft.PotionBrewingAccessor;
import io.github._5thlayer.craftworks.mixin.minecraft.PotionBrewingMixAccessor;
import io.github._5thlayer.craftworks.recipe.BrewingChains.Chain;
import io.github._5thlayer.craftworks.recipe.BrewingChains.ContainerMix;
import io.github._5thlayer.craftworks.recipe.BrewingChains.ItemChain;
import io.github._5thlayer.craftworks.recipe.BrewingChains.ItemMix;
import io.github._5thlayer.craftworks.recipe.BrewingChains.PotionChain;
import io.github._5thlayer.craftworks.recipe.BrewingChains.PotionMix;
import io.github._5thlayer.craftworks.recipe.BrewingChains.Table;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionBrewing;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeMap;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.common.brewing.BrewingRecipe;
import net.neoforged.neoforge.common.brewing.IBrewingRecipe;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;

/**
 * The brewing stand's mixes as Brewed recipes (ADR-0026, #76): at the end of every recipe load, beside
 * {@link ModRecipes}, each chain of brews from a water bottle ({@link BrewingChains}) is added as an
 * Assembling recipe in the {@code brewing} category. It takes 3 glass bottles, 750 mB of water and one of each
 * ingredient along the chain, and makes 3 of what the chain ends in (as three results where the item does not stack), in 400 ticks for every step.
 * A chain with more distinct ingredients, the bottles included, than an Assembler has input slots is left out and counted in the log.
 *
 * <p>Read are vanilla's potion and container mixes and NeoForge's {@link BrewingRecipe}s, each of which is a step
 * from every potion its bottle ingredient accepts. Any other {@link IBrewingRecipe} answers only for an input
 * and cannot be listed, so it is skipped and logged, as is a brew whose bottles no chain from water reaches.
 *
 * <p>The table belongs to the server, which does not exist yet when the first recipes load: that load adds nothing,
 * and {@link ModRecipes#follow} reloads the recipes once the server has started.
 */
public final class BrewedRecipes {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** A water bottle's water, 250 mB a bottle, as the stand's three bottles take. */
    private static final int WATER = 750;
    private static final int BOTTLES = 3;
    /** The stand's brewing time, per step folded. */
    private static final int TICKS_A_STEP = PotionBrewing.BREWING_TIME_SECONDS * 20;

    /** Whether the last recipe load had a brewing table to read. */
    private static volatile boolean read;

    private BrewedRecipes() {
    }

    /** Whether the last load had the server's brewing table: if not, and Brewed recipes are wanted, the recipes must load again. */
    static boolean tableRead() {
        return read;
    }

    /** The recipes with each Brewed recipe added at its id, unless the flag is off, the id is excluded or the id is taken. */
    public static RecipeMap add(RecipeMap recipes, HolderLookup.Provider registries) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        read = server != null;
        if (server == null || !CraftworksConfig.brewingRecipes()) return recipes;
        var ops = ModRecipes.withItemTags(registries);
        List<RecipeHolder<?>> holders = new ArrayList<>(recipes.values());
        int added = 0;
        for (Map.Entry<Identifier, AssemblingRecipe> brewed : recipes(server.potionBrewing()).entrySet()) {
            Identifier id = brewed.getKey();
            ResourceKey<Recipe<?>> key = ResourceKey.create(Registries.RECIPE, id);
            if (ModRecipes.excludes(id) || recipes.byKey(key) != null || !ModRecipes.representable(ops, brewed.getValue())) continue;
            holders.add(new RecipeHolder<>(key, brewed.getValue()));
            added++;
        }
        LOGGER.info("Personal Assembler: {} Brewed recipe(s) added from the brewing stand's mixes.", added);
        return added == 0 ? recipes : RecipeMap.create(holders);
    }

    /** Every Brewed recipe of the table, by id. */
    static Map<Identifier, AssemblingRecipe> recipes(PotionBrewing brewing) {
        Map<Identifier, Holder<Potion>> potions = new HashMap<>();
        var accessor = (PotionBrewingAccessor) brewing;
        List<PotionMix<Identifier, Ingredient>> potionMixes = new ArrayList<>();
        for (Object mix : accessor.craftworks$potionMixes()) {
            var read = (PotionBrewingMixAccessor) mix;
            Identifier from = potionId(read.craftworks$from(), potions);
            Identifier to = potionId(read.craftworks$to(), potions);
            if (from != null && to != null) potionMixes.add(new PotionMix<>(from, read.craftworks$ingredient(), to));
        }
        List<ContainerMix<Item, Ingredient>> containerMixes = new ArrayList<>();
        for (Object mix : accessor.craftworks$containerMixes()) {
            var read = (PotionBrewingMixAccessor) mix;
            if (read.craftworks$from().value() instanceof Item from && read.craftworks$to().value() instanceof Item to) {
                containerMixes.add(new ContainerMix<>(from, read.craftworks$ingredient(), to));
            }
        }
        List<ItemMix<Identifier, Ingredient, ItemStackTemplate>> itemMixes = new ArrayList<>();
        List<BrewingRecipe> modded = new ArrayList<>();
        List<String> unlisted = new ArrayList<>();
        for (IBrewingRecipe recipe : brewing.getRecipes()) {
            if (recipe instanceof BrewingRecipe listed) {
                modded.add(listed);
            } else {
                unlisted.add(recipe.getClass().getName());
            }
        }
        if (!unlisted.isEmpty()) {
            LOGGER.warn("Personal Assembler: {} brewing recipe(s) left at the brewing stand, since only a BrewingRecipe can be "
                    + "listed: {}", unlisted.size(), unlisted);
        }
        Holder<Potion> water = Potions.WATER;
        potions.put(water.unwrapKey().orElseThrow().identifier(), water);
        Map<BrewingRecipe, Set<Identifier>> startsOf = new LinkedHashMap<>();
        for (BrewingRecipe recipe : modded) {
            Set<Identifier> starts = new HashSet<>();
            for (Holder.Reference<Potion> potion : BuiltInRegistries.POTION.listElements().toList()) {
                if (!recipe.getInput().test(PotionContents.createItemStack(Items.POTION, potion))) continue;
                Identifier from = potion.key().identifier();
                potions.put(from, potion);
                starts.add(from);
                Holder<Potion> brewedPotion = potionOf(recipe.getOutput());
                if (brewedPotion != null) {
                    Identifier to = potionId(brewedPotion, potions);
                    if (to != null) potionMixes.add(new PotionMix<>(from, recipe.getIngredient(), to));
                } else {
                    itemMixes.add(new ItemMix<>(from, recipe.getIngredient(), resultOf(recipe.getOutput())));
                }
            }
            startsOf.put(recipe, starts);
        }

        var table = new Table<>(water.unwrapKey().orElseThrow().identifier(), Items.POTION, potionMixes, itemMixes, containerMixes);
        Set<Identifier> reached = BrewingChains.reachable(table);
        startsOf.forEach((recipe, starts) -> {
            if (starts.stream().noneMatch(reached::contains)) {
                LOGGER.warn("Personal Assembler: the brewing recipe of {} with {} is left at the brewing stand: {}",
                        BuiltInRegistries.ITEM.getKey(recipe.getOutput().getItem()), name(recipe.getIngredient()),
                        starts.isEmpty() ? "no potion bottle is one of its inputs" : "no chain of brews from water reaches its input");
            }
        });

        Map<Identifier, AssemblingRecipe> brewed = new LinkedHashMap<>();
        int tooDeep = 0;
        for (Chain<Identifier, Ingredient, Item, ItemStackTemplate> chain : BrewingChains.chains(table)) {
            Identifier id = idOf(chain);
            AssemblingRecipe recipe = toRecipe(chain, potions);
            if (recipe.ingredients().size() > AssemblerSlots.INPUTS) {
                tooDeep++;
                continue;
            }
            if (brewed.putIfAbsent(id, recipe) != null) {
                LOGGER.warn("Personal Assembler: two brewing chains share the id {}; the first is kept.", id);
            }
        }
        if (tooDeep > 0) {
            LOGGER.info("Personal Assembler: {} brewing chain(s) left at the brewing stand, since they take more than the {} "
                    + "ingredients an Assembler holds.", tooDeep, AssemblerSlots.INPUTS);
        }
        return brewed;
    }

    /** The potion a brew makes when it makes a plain bottled potion, and null for any other result. */
    private static Holder<Potion> potionOf(ItemStack output) {
        PotionContents contents = output.get(DataComponents.POTION_CONTENTS);
        if (!output.is(Items.POTION) || contents == null || !contents.customEffects().isEmpty()) return null;
        return contents.potion().orElse(null);
    }

    private static ItemStackTemplate resultOf(ItemStack output) {
        return new ItemStackTemplate(output.getItem().builtInRegistryHolder(), output.getCount(), output.getComponentsPatch());
    }

    /** The potion's id, remembering its holder; null for one in no registry. */
    private static Identifier potionId(Holder<?> holder, Map<Identifier, Holder<Potion>> potions) {
        Optional<? extends ResourceKey<?>> key = holder.unwrapKey();
        if (key.isEmpty()) return null;
        @SuppressWarnings("unchecked")
        Holder<Potion> potion = (Holder<Potion>) holder;
        potions.putIfAbsent(key.get().identifier(), potion);
        return key.get().identifier();
    }

    private static AssemblingRecipe toRecipe(Chain<Identifier, Ingredient, Item, ItemStackTemplate> chain,
            Map<Identifier, Holder<Potion>> potions) {
        // One entry per distinct ingredient, counted: a bag.
        Map<Ingredient, Integer> bag = new LinkedHashMap<>();
        bag.put(Ingredient.of(Items.GLASS_BOTTLE), BOTTLES);
        for (Ingredient step : chain.ingredients()) bag.merge(step, 1, Integer::sum);
        List<SizedIngredient> ingredients = new ArrayList<>();
        bag.forEach((ingredient, count) -> ingredients.add(new SizedIngredient(ingredient, count)));
        List<ItemStackTemplate> results = switch (chain) {
            case PotionChain<Identifier, Ingredient, Item, ItemStackTemplate> potion -> split(new ItemStackTemplate(potion.container(), 1,
                    DataComponentPatch.builder()
                            .set(DataComponents.POTION_CONTENTS, new PotionContents(potions.get(potion.potion()))).build()), BOTTLES);
            case ItemChain<Identifier, Ingredient, Item, ItemStackTemplate> item -> split(item.result(), item.result().count() * BOTTLES);
        };
        return new AssemblingRecipe(ingredients, results, TICKS_A_STEP * chain.ingredients().size(),
                AssemblingRecipe.DEFAULT_PRIORITY, List.of(SizedFluidIngredient.of(Fluids.WATER, WATER)), List.of(),
                false, AssemblingCategory.BREWING);
    }

    /**
     * {@code total} of an item as result entries of at most a stack each: a potion stacks to one, and a template
     * of three of them is no stack at all, so the three bottles are three results.
     */
    private static List<ItemStackTemplate> split(ItemStackTemplate one, int total) {
        int stack = new ItemStackTemplate(one.item(), 1, one.components()).create().getMaxStackSize();
        List<ItemStackTemplate> results = new ArrayList<>();
        for (int left = total; left > 0; left -= stack) {
            results.add(new ItemStackTemplate(one.item(), Math.min(left, stack), one.components()));
        }
        return results;
    }

    /**
     * {@code craftworks:brewing/<bottle>/<potion namespace>/<potion path>/<ingredient namespace>.<ingredient path>/...}:
     * the bottle kind and the potion the chain ends in, or the item it ends in alone, then each step's ingredient in order.
     */
    private static Identifier idOf(Chain<Identifier, Ingredient, Item, ItemStackTemplate> chain) {
        StringBuilder path = new StringBuilder("brewing/");
        switch (chain) {
            case PotionChain<Identifier, Ingredient, Item, ItemStackTemplate> potion -> {
                Identifier bottle = BuiltInRegistries.ITEM.getKey(potion.container());
                path.append(dotted(bottle)).append('/').append(potion.potion().getNamespace()).append('/')
                        .append(potion.potion().getPath());
            }
            case ItemChain<Identifier, Ingredient, Item, ItemStackTemplate> item -> {
                Identifier made = item.result().item().unwrapKey().orElseThrow().identifier();
                path.append(made.getNamespace()).append('/').append(made.getPath());
            }
        }
        for (Ingredient step : chain.ingredients()) path.append('/').append(name(step));
        return Identifier.fromNamespaceAndPath("craftworks", path.toString());
    }

    /** A container's name in an id: its path when it is vanilla's, else with its namespace. */
    private static String dotted(Identifier id) {
        return id.getNamespace().equals(Identifier.DEFAULT_NAMESPACE) ? id.getPath() : id.getNamespace() + "." + id.getPath();
    }

    /** An ingredient as a segment of an id: its tag, or else its first item, as {@code namespace.path}. */
    private static String name(Ingredient ingredient) {
        Identifier id = null;
        if (!ingredient.isCustom()) id = ingredient.getValues().unwrapKey().map(TagKey::location).orElse(null);
        if (id == null) {
            id = ingredient.items().findFirst().flatMap(Holder::unwrapKey).map(ResourceKey::identifier).orElse(null);
        }
        return id == null ? "unknown" : id.getNamespace() + "." + id.getPath().replace('/', '.');
    }
}
