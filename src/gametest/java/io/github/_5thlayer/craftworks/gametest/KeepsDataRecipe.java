// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.gametest;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github._5thlayer.craftworks.Craftworks;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * A shaped recipe that assembles its own result, as Mekanism's Atomic Disassembler and tier upgrades do (#58):
 * the result takes the custom name and damage of the first input that has a name. It stands in for
 * another mod's, so its serializer is registered only by the Mod's own runs.
 */
@EventBusSubscriber(modid = Craftworks.MOD_ID)
public final class KeepsDataRecipe extends ShapedRecipe {

    public static final Identifier ID = Identifier.fromNamespaceAndPath(Craftworks.MOD_ID, "gametest_keeps_data");

    private static final MapCodec<KeepsDataRecipe> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Recipe.CommonInfo.MAP_CODEC.forGetter(o -> o.commonInfo),
            CraftingRecipe.CraftingBookInfo.MAP_CODEC.forGetter(o -> o.bookInfo),
            ShapedRecipePattern.MAP_CODEC.forGetter(o -> o.pattern),
            ItemStackTemplate.CODEC.fieldOf("result").forGetter(o -> o.result))
            .apply(i, KeepsDataRecipe::new));
    private static final StreamCodec<RegistryFriendlyByteBuf, KeepsDataRecipe> STREAM_CODEC = StreamCodec.composite(
            Recipe.CommonInfo.STREAM_CODEC, o -> o.commonInfo,
            CraftingRecipe.CraftingBookInfo.STREAM_CODEC, o -> o.bookInfo,
            ShapedRecipePattern.STREAM_CODEC, o -> o.pattern,
            ItemStackTemplate.STREAM_CODEC, o -> o.result,
            KeepsDataRecipe::new);
    public static final RecipeSerializer<KeepsDataRecipe> SERIALIZER = new RecipeSerializer<>(MAP_CODEC, STREAM_CODEC);

    private final ItemStackTemplate result;

    KeepsDataRecipe(Recipe.CommonInfo commonInfo, CraftingRecipe.CraftingBookInfo bookInfo,
            ShapedRecipePattern pattern, ItemStackTemplate result) {
        super(commonInfo, bookInfo, pattern, result);
        this.result = result;
    }

    @Override
    public ItemStack assemble(CraftingInput input) {
        ItemStack made = result.create();
        for (ItemStack stack : input.items()) {
            if (!stack.has(DataComponents.CUSTOM_NAME)) continue;
            made.set(DataComponents.CUSTOM_NAME, stack.get(DataComponents.CUSTOM_NAME));
            made.setDamageValue(stack.getDamageValue());
            break;
        }
        return made;
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public RecipeSerializer<ShapedRecipe> getSerializer() {
        return (RecipeSerializer) SERIALIZER;
    }

    @SubscribeEvent
    static void register(RegisterEvent event) {
        event.register(Registries.RECIPE_SERIALIZER, ID, () -> SERIALIZER);
    }
}
