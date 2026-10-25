// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.recipe;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import com.mojang.logging.LogUtils;
import io.github._5thlayer.craftworks.machine.FluidBoxes;
import io.github._5thlayer.craftworks.machine.FluidLayout;
import io.github._5thlayer.libworks.machine.FluidContainers;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import net.neoforged.neoforge.fluids.FluidStackTemplate;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Poured recipes (ADR-0027, #80): at the end of every recipe load, after {@link ModRecipes} has converted, each
 * Converted recipe that takes or makes a filled bucket also gives an Assembling recipe at
 * {@code craftworks:fluid/<source namespace>/<source path>}, beside the Converted recipe, which stays. Each filled
 * bucket becomes its fluid, 1000 mB a bucket, and no bucket goes in or comes out: for every filled bucket made,
 * one empty bucket among the ingredients is dropped. It is in {@code crafting-with-fluid}, keeps its source's time
 * and Route priority and is never Hand-craftable.
 *
 * <p>A filled bucket is an item that, by NeoForge's fluid item capability, holds exactly 1000 mB of one fluid and
 * empties into {@code minecraft:bucket}. An ingredient pours only when every item it accepts holds the same fluid;
 * one that mixes a bucket with anything else leaves its recipe without a Poured recipe. A recipe whose fluids no
 * Assembler could hold (more fluids than it has boxes, or more than a box of one) has none either, and is counted in
 * the log. An id that is excluded or taken is left alone.
 */
public final class PouredRecipes {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Whether the last recipe load could read items' components, which a bucket's fluid handler needs. */
    private static volatile boolean read;

    private PouredRecipes() {
    }

    /**
     * Whether the last load could read buckets: the first load, as the server is made, runs before the items'
     * components are bound and adds nothing, so {@link ModRecipes#follow} reloads the recipes once.
     */
    static boolean bucketsRead() {
        return read;
    }

    private static boolean componentsBound() {
        try {
            Items.BUCKET.components();
            return true;
        } catch (NullPointerException e) {
            return false;
        }
    }

    /** Why a source has no Poured recipe, or that it has one. */
    enum Verdict {
        POURED,
        /** It takes and makes no filled bucket. */
        NO_BUCKET,
        /** An ingredient or result mixes a filled bucket with something else, or two fluids. */
        MIXED,
        /** Its fluids would not fit an Assembler's boxes. */
        TOO_MUCH
    }

    /** The verdict on one source, and the Poured recipe when it is {@link Verdict#POURED}. */
    record Pour(Verdict verdict, @Nullable AssemblingRecipe recipe) {
        static final Pour NO_BUCKET = new Pour(Verdict.NO_BUCKET, null);
        static final Pour MIXED = new Pour(Verdict.MIXED, null);
        static final Pour TOO_MUCH = new Pour(Verdict.TOO_MUCH, null);
    }

    /**
     * Adds a Poured recipe to {@code holders} for each of {@code sources}, the ids of the Converted recipes that may
     * have one, unless its id is excluded or already there; returns how many were added.
     */
    static int add(List<RecipeHolder<?>> holders, Set<Identifier> sources, HolderLookup.Provider registries) {
        read = componentsBound();
        if (!read) return 0;
        Map<Identifier, AssemblingRecipe> converted = new LinkedHashMap<>();
        Set<Identifier> taken = new HashSet<>();
        for (RecipeHolder<?> holder : holders) {
            Identifier id = holder.id().identifier();
            taken.add(id);
            if (sources.contains(id) && holder.value() instanceof AssemblingRecipe recipe) converted.put(id, recipe);
        }
        Map<Item, FluidResource> buckets = new HashMap<>();
        Function<Item, @Nullable FluidResource> lookup = item -> buckets.computeIfAbsent(item, PouredRecipes::filledBucket);
        Function<Ingredient, @Nullable List<Holder<Item>>> itemsOf = ingredient -> itemsOf(ingredient, registries);
        int added = 0;
        int tooMuch = 0;
        for (Map.Entry<Identifier, AssemblingRecipe> source : converted.entrySet()) {
            Identifier poured = idOf(source.getKey());
            if (ModRecipes.excludes(source.getKey()) || ModRecipes.excludes(poured) || taken.contains(poured)) continue;
            Pour pour = pour(source.getValue(), lookup, itemsOf);
            if (pour.verdict() == Verdict.TOO_MUCH) tooMuch++;
            if (pour.recipe() == null) continue;
            holders.add(new RecipeHolder<>(ResourceKey.create(Registries.RECIPE, poured), pour.recipe()));
            taken.add(poured);
            added++;
        }
        LOGGER.info("Personal Assembler: {} Poured recipe(s) added from bucket crafts, {} left as bucket crafts, since they "
                + "need more fluid than an Assembler holds.", added, tooMuch);
        return added;
    }

    /** {@code craftworks:fluid/<source namespace>/<source path>}. */
    static Identifier idOf(Identifier source) {
        return Identifier.fromNamespaceAndPath("craftworks", "fluid/" + source.getNamespace() + "/" + source.getPath());
    }

    /**
     * The Poured recipe of {@code source}, given what fluid an item holds as a filled bucket ({@code lookup} answers
     * null for an item that is none).
     */
    static Pour pour(AssemblingRecipe source, Function<Item, @Nullable FluidResource> lookup,
            Function<Ingredient, @Nullable List<Holder<Item>>> itemsOf) {
        if (!source.fluidIngredients().isEmpty() || !source.fluidResults().isEmpty()) return Pour.NO_BUCKET;
        Map<FluidResource, Integer> taken = new LinkedHashMap<>();
        Map<FluidResource, Integer> made = new LinkedHashMap<>();
        List<SizedIngredient> ingredients = new ArrayList<>();
        for (SizedIngredient sized : source.ingredients()) {
            Kind kind = classify(sized.ingredient(), lookup, itemsOf);
            if (kind == Kind.MIXED) return Pour.MIXED;
            if (kind.fluid == null) {
                ingredients.add(sized);
            } else {
                taken.merge(kind.fluid, sized.count() * FluidType.BUCKET_VOLUME, Integer::sum);
            }
        }
        List<ItemStackTemplate> results = new ArrayList<>();
        int bucketsMade = 0;
        for (ItemStackTemplate result : source.results()) {
            FluidResource fluid = lookup.apply(result.item().value());
            if (fluid == null) {
                results.add(result);
            } else if (!result.components().equals(DataComponentPatch.EMPTY)) {
                return Pour.MIXED;
            } else {
                made.merge(fluid, result.count() * FluidType.BUCKET_VOLUME, Integer::sum);
                bucketsMade += result.count();
            }
        }
        if (taken.isEmpty() && made.isEmpty()) return Pour.NO_BUCKET;
        if (taken.size() > FluidLayout.ASSEMBLER.fluidInputs() || made.size() > FluidLayout.ASSEMBLER.fluidOutputs()
                || !taken.values().stream().allMatch(FluidBoxes::fits) || !made.values().stream().allMatch(FluidBoxes::fits)) {
            return Pour.TOO_MUCH;
        }
        // No bucket carries the fluid out, so the empty bucket that would have is not asked for.
        for (int at = 0; at < ingredients.size() && bucketsMade > 0; at++) {
            SizedIngredient sized = ingredients.get(at);
            if (!isEmptyBucket(sized.ingredient(), itemsOf)) continue;
            int dropped = Math.min(sized.count(), bucketsMade);
            bucketsMade -= dropped;
            if (dropped == sized.count()) {
                ingredients.remove(at--);
            } else {
                ingredients.set(at, new SizedIngredient(sized.ingredient(), sized.count() - dropped));
            }
        }
        List<SizedFluidIngredient> fluidIngredients = new ArrayList<>();
        taken.forEach((fluid, amount) -> fluidIngredients.add(SizedFluidIngredient.of(fluid.getFluid(), amount)));
        List<FluidStackTemplate> fluidResults = new ArrayList<>();
        made.forEach((fluid, amount) -> fluidResults.add(new FluidStackTemplate(fluid.typeHolder(), amount)));
        return new Pour(Verdict.POURED, new AssemblingRecipe(ingredients, results, source.time(), source.priority(),
                fluidIngredients, fluidResults, false, AssemblingCategory.CRAFTING_WITH_FLUID));
    }

    /** What an ingredient is: plain, a filled bucket of one fluid, or a mix. */
    private record Kind(@Nullable FluidResource fluid) {
        static final Kind PLAIN = new Kind(null);
        static final Kind MIXED = new Kind(FluidResource.EMPTY);
    }

    private static Kind classify(Ingredient ingredient, Function<Item, @Nullable FluidResource> lookup,
            Function<Ingredient, @Nullable List<Holder<Item>>> itemsOf) {
        List<Holder<Item>> accepted = itemsOf.apply(ingredient);
        // An ingredient whose items cannot be listed might hold a bucket: it leaves its recipe unpoured.
        if (accepted == null) return Kind.MIXED;
        FluidResource found = null;
        boolean plain = false;
        for (Holder<Item> item : accepted) {
            FluidResource fluid = lookup.apply(item.value());
            if (fluid == null) {
                plain = true;
            } else if (found == null) {
                found = fluid;
            } else if (!found.equals(fluid)) {
                return Kind.MIXED;
            }
        }
        if (found == null) return Kind.PLAIN;
        return plain ? Kind.MIXED : new Kind(found);
    }

    /**
     * The items an ingredient accepts, or null when they cannot be listed. A tag is read through the load's own
     * lookup, since the item registry's tags are not bound while the recipes load (#58).
     */
    private static @Nullable List<Holder<Item>> itemsOf(Ingredient ingredient, HolderLookup.Provider registries) {
        try {
            if (!ingredient.isCustom()) {
                var tag = ingredient.getValues().unwrapKey();
                if (tag.isPresent()) {
                    return registries.lookupOrThrow(Registries.ITEM).get(tag.get()).map(set -> set.stream().toList()).orElse(List.of());
                }
            }
            return ingredient.items().toList();
        } catch (IllegalStateException | NullPointerException e) {
            return null;
        }
    }

    /** Whether the ingredient accepts the empty bucket and nothing else. */
    private static boolean isEmptyBucket(Ingredient ingredient, Function<Ingredient, @Nullable List<Holder<Item>>> itemsOf) {
        List<Holder<Item>> items = itemsOf.apply(ingredient);
        return items != null && !items.isEmpty() && items.stream().allMatch(item -> item.value() == Items.BUCKET);
    }

    /**
     * The fluid an item holds as a filled bucket: exactly 1000 mB of one fluid, with nothing else on it, that it
     * empties into a plain {@code minecraft:bucket}; null for any other item.
     */
    static @Nullable FluidResource filledBucket(Item item) {
        ItemStack stack = new ItemStack(item);
        if (stack.isEmpty()) return null;
        // Room for twice a bucket, so a container holding more than one shows as more.
        FluidStacksResourceHandler tank = new FluidStacksResourceHandler(1, FluidType.BUCKET_VOLUME * 2);
        ItemStack emptied = FluidContainers.drain(stack, ItemStack.EMPTY, tank);
        if (emptied == null || !emptied.is(Items.BUCKET) || emptied.getCount() != 1 || !emptied.getComponentsPatch().isEmpty()) return null;
        FluidResource fluid = tank.getResource(0);
        if (fluid.isEmpty() || tank.getAmountAsInt(0) != FluidType.BUCKET_VOLUME || !fluid.isComponentsPatchEmpty()) return null;
        return fluid;
    }
}
