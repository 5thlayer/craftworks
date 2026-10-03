// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.recipe;

import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
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
 * kind the Personal Assembler plans with (CONTEXT.md).
 *
 * <p>{@code time} is ticks per craft and defaults to 10; {@code priority} is the recipe's Route priority
 * and defaults to 0. A pack changes either by overriding the recipe.
 *
 * <p>{@code fluid_ingredients} and {@code fluid_results} default to empty and {@code hand_craftable} to
 * true, so a recipe written before they existed reads unchanged. Fluids are NeoForge's own types, so a
 * recipe names no Library. The Personal Assembler plans only through a {@link #plannable} recipe
 * (ADR-0109); what a placed machine does with the fluids is the machine's.
 *
 * <p>The result is a template, not a stack: {@code ItemStack.CODEC} refuses an item whose components
 * are not bound yet, which they are not during the datapack load that reads recipes.
 *
 * <p>{@link #matches} is false: nothing looks one up by its inputs. The Assembler plans over it by id.
 */
public record AssemblingRecipe(
        List<SizedIngredient> ingredients, ItemStackTemplate result, int time, int priority,
        List<SizedFluidIngredient> fluidIngredients, List<FluidStackTemplate> fluidResults, boolean handCraftable)
        implements Recipe<RecipeInput> {

    public static final int DEFAULT_TIME = 10;
    public static final int DEFAULT_PRIORITY = 0;

    public AssemblingRecipe(List<SizedIngredient> ingredients, ItemStackTemplate result, int time, int priority) {
        this(ingredients, result, time, priority, List.of(), List.of(), true);
    }

    public AssemblingRecipe {
        fluidIngredients = List.copyOf(fluidIngredients);
        fluidResults = List.copyOf(fluidResults);
    }

    /** Whether the Personal Assembler may plan through this recipe: hand-craftable and with no fluid in or out. */
    public boolean plannable() {
        return handCraftable && fluidIngredients.isEmpty() && fluidResults.isEmpty();
    }

    @Override
    public boolean matches(RecipeInput input, Level level) {
        return false;
    }

    @Override
    public ItemStack assemble(RecipeInput input) {
        return result.create();
    }

    /** Not a grid recipe: nothing places ingredients for it. */
    @Override
    public PlacementInfo placementInfo() {
        return PlacementInfo.NOT_PLACEABLE;
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

    static final MapCodec<AssemblingRecipe> CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(
                    SizedIngredient.NESTED_CODEC.listOf().fieldOf("ingredients").forGetter(AssemblingRecipe::ingredients),
                    ItemStackTemplate.CODEC.fieldOf("result").forGetter(AssemblingRecipe::result),
                    Codec.INT.optionalFieldOf("time", DEFAULT_TIME).forGetter(AssemblingRecipe::time),
                    Codec.INT.optionalFieldOf("priority", DEFAULT_PRIORITY).forGetter(AssemblingRecipe::priority),
                    SizedFluidIngredient.CODEC.listOf().optionalFieldOf("fluid_ingredients", List.of())
                            .forGetter(AssemblingRecipe::fluidIngredients),
                    FluidStackTemplate.CODEC.listOf().optionalFieldOf("fluid_results", List.of())
                            .forGetter(AssemblingRecipe::fluidResults),
                    Codec.BOOL.optionalFieldOf("hand_craftable", true).forGetter(AssemblingRecipe::handCraftable))
                    .apply(instance, AssemblingRecipe::new));

    private static final StreamCodec<RegistryFriendlyByteBuf, AssemblingRecipe> STREAM_CODEC = StreamCodec.composite(
            SizedIngredient.STREAM_CODEC.apply(ByteBufCodecs.list()), AssemblingRecipe::ingredients,
            ItemStackTemplate.STREAM_CODEC, AssemblingRecipe::result,
            ByteBufCodecs.VAR_INT, AssemblingRecipe::time,
            ByteBufCodecs.VAR_INT, AssemblingRecipe::priority,
            SizedFluidIngredient.STREAM_CODEC.apply(ByteBufCodecs.list()), AssemblingRecipe::fluidIngredients,
            FluidStackTemplate.STREAM_CODEC.apply(ByteBufCodecs.list()), AssemblingRecipe::fluidResults,
            ByteBufCodecs.BOOL, AssemblingRecipe::handCraftable,
            AssemblingRecipe::new);
}
