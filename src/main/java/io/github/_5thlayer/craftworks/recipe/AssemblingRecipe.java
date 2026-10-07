// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.recipe;

import java.util.List;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeBookCategories;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import net.neoforged.neoforge.fluids.FluidStackTemplate;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;

/**
 * An Assembling recipe as the recipe manager holds it: the {@code craftworks:assembling} type, the only
 * kind the Personal Assembler plans with (GLOSSARY.md).
 *
 * <p>{@code time} is ticks per craft and defaults to 10; {@code priority} is the recipe's Route priority
 * and defaults to 0. A pack changes either by overriding the recipe.
 *
 * <p>{@code ingredients}, {@code results}, {@code fluid_ingredients} and {@code fluid_results} default to empty,
 * so a recipe that uses and makes only fluids need not name its empty item lists, and {@code hand_craftable}
 * defaults to true, so a recipe written before it existed reads unchanged. Fluids are NeoForge's own types so that
 * Craftworks names no fluid Library (5thlayer/factoryworks#578).
 *
 * <p>{@code category} names the recipe's kind, one of {@link AssemblingCategory}'s three, and decides which
 * Assemblers may hold it; omitted, it is {@code crafting}. {@code results} is a list of item stacks, empty only when
 * {@code fluid_results} is not. The first goes to an Assembler's product slot and the rest to its remainder
 * slot; the Personal Assembler plans only a recipe with exactly one (GLOSSARY.md, Hand-craftable).
 *
 * <p>{@code pinned_fluid_results} is false when omitted. A machine gives the first fluid result the boxes the
 * recipe leaves unused unless the recipe is Pinned, which keeps each fluid result to its own output box
 * (GLOSSARY.md, Pinned).
 *
 * <p>A result is a template, not a stack: {@code ItemStack.CODEC} refuses an item whose components
 * are not bound yet, which they are not during the datapack load that reads recipes.
 *
 * <p>{@link #matches} is false: nothing looks one up by its inputs. The Assembler plans over it by id.
 */
public record AssemblingRecipe(
        List<SizedIngredient> ingredients, List<ItemStackTemplate> results, int time, int priority,
        List<SizedFluidIngredient> fluidIngredients, List<FluidStackTemplate> fluidResults, boolean handCraftable,
        AssemblingCategory category, boolean pinnedFluidResults) implements Recipe<RecipeInput> {

    public static final int DEFAULT_TIME = 10;
    public static final int DEFAULT_PRIORITY = 0;

    public AssemblingRecipe {
        results = List.copyOf(results);
        fluidIngredients = List.copyOf(fluidIngredients);
        fluidResults = List.copyOf(fluidResults);
    }

    @Override
    public boolean matches(RecipeInput input, Level level) {
        return false;
    }

    @Override
    public ItemStack assemble(RecipeInput input) {
        return product();
    }

    /** What an Assembler's product slot gets: the first result, or nothing for a recipe that makes only fluids. */
    public ItemStack product() {
        return productOf(results);
    }

    /** The first result, empty for a recipe that makes only fluids. */
    public Optional<ItemStackTemplate> productTemplate() {
        return firstOf(results);
    }

    /** The first of {@code results} as a stack, or nothing: the one rule, for the recipe and for the client's copy. */
    public static ItemStack productOf(List<ItemStackTemplate> results) {
        return firstOf(results).map(ItemStackTemplate::create).orElse(ItemStack.EMPTY);
    }

    private static Optional<ItemStackTemplate> firstOf(List<ItemStackTemplate> results) {
        return results.stream().findFirst();
    }

    /** The results after the first, which an Assembler puts in its remainder slot. */
    public List<ItemStackTemplate> extraResults() {
        return results.isEmpty() ? List.of() : results.subList(1, results.size());
    }

    /**
     * Its item ingredients, one each. Nothing places them, since no recipe book shows an Assembling recipe
     * ({@code display()} is empty), but vanilla warns at every load of a recipe that is neither placeable
     * nor special, and a special one is never unlocked, which the {@code recipeBook} Lock source needs.
     * A recipe with no item ingredient still can't be placed, and still draws the warning.
     */
    @Override
    public PlacementInfo placementInfo() {
        return PlacementInfo.create(ingredients.stream().map(SizedIngredient::ingredient).toList());
    }

    @Override
    public boolean showNotification() {
        return false;
    }

    @Override
    public String group() {
        return "";
    }

    @Override
    public RecipeBookCategory recipeBookCategory() {
        return RecipeBookCategories.CRAFTING_MISC;
    }

    @Override
    public RecipeSerializer<AssemblingRecipe> getSerializer() {
        return CraftworksRecipes.ASSEMBLING_SERIALIZER.get();
    }

    @Override
    public RecipeType<AssemblingRecipe> getType() {
        return CraftworksRecipes.ASSEMBLING_TYPE.get();
    }

    static RecipeSerializer<AssemblingRecipe> serializer() {
        return new RecipeSerializer<>(CODEC, STREAM_CODEC);
    }

    /** A recipe that makes nothing, no item and no fluid, is not a recipe. */
    private static DataResult<AssemblingRecipe> makesSomething(AssemblingRecipe recipe) {
        return recipe.results().isEmpty() && recipe.fluidResults().isEmpty()
                ? DataResult.error(() -> "an Assembling recipe needs a result or a fluid result")
                : DataResult.success(recipe);
    }

    private static final MapCodec<AssemblingRecipe> CODEC = RecordCodecBuilder.<AssemblingRecipe>mapCodec(
            instance -> instance.group(
                    SizedIngredient.NESTED_CODEC.listOf().optionalFieldOf("ingredients", List.of())
                            .forGetter(AssemblingRecipe::ingredients),
                    ItemStackTemplate.CODEC.listOf().optionalFieldOf("results", List.of()).forGetter(AssemblingRecipe::results),
                    Codec.INT.optionalFieldOf("time", DEFAULT_TIME).forGetter(AssemblingRecipe::time),
                    Codec.INT.optionalFieldOf("priority", DEFAULT_PRIORITY).forGetter(AssemblingRecipe::priority),
                    SizedFluidIngredient.CODEC.listOf().optionalFieldOf("fluid_ingredients", List.of())
                            .forGetter(AssemblingRecipe::fluidIngredients),
                    FluidStackTemplate.CODEC.listOf().optionalFieldOf("fluid_results", List.of())
                            .forGetter(AssemblingRecipe::fluidResults),
                    Codec.BOOL.optionalFieldOf("hand_craftable", true).forGetter(AssemblingRecipe::handCraftable),
                    AssemblingCategory.CODEC.optionalFieldOf("category", AssemblingCategory.CRAFTING)
                            .forGetter(AssemblingRecipe::category),
                    Codec.BOOL.optionalFieldOf("pinned_fluid_results", false).forGetter(AssemblingRecipe::pinnedFluidResults))
                    .apply(instance, AssemblingRecipe::new))
            .flatXmap(AssemblingRecipe::makesSomething, AssemblingRecipe::makesSomething);

    /** The category's ordinal; one that names no constant is a decode error, not an array index. */
    private static final StreamCodec<ByteBuf, AssemblingCategory> CATEGORY_STREAM_CODEC = ByteBufCodecs.VAR_INT.map(
            ordinal -> {
                if (ordinal < 0 || ordinal >= AssemblingCategory.values().length) {
                    throw new DecoderException("Unknown category " + ordinal);
                }
                return AssemblingCategory.values()[ordinal];
            },
            AssemblingCategory::ordinal);

    private static final StreamCodec<RegistryFriendlyByteBuf, AssemblingRecipe> STREAM_CODEC = StreamCodec.composite(
            SizedIngredient.STREAM_CODEC.apply(ByteBufCodecs.list()), AssemblingRecipe::ingredients,
            ItemStackTemplate.STREAM_CODEC.apply(ByteBufCodecs.list()), AssemblingRecipe::results,
            ByteBufCodecs.VAR_INT, AssemblingRecipe::time,
            ByteBufCodecs.VAR_INT, AssemblingRecipe::priority,
            SizedFluidIngredient.STREAM_CODEC.apply(ByteBufCodecs.list()), AssemblingRecipe::fluidIngredients,
            FluidStackTemplate.STREAM_CODEC.apply(ByteBufCodecs.list()), AssemblingRecipe::fluidResults,
            ByteBufCodecs.BOOL, AssemblingRecipe::handCraftable,
            CATEGORY_STREAM_CODEC, AssemblingRecipe::category,
            ByteBufCodecs.BOOL, AssemblingRecipe::pinnedFluidResults,
            AssemblingRecipe::new);
}
