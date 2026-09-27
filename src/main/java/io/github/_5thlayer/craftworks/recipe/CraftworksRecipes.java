// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.recipe;

import io.github._5thlayer.craftworks.Craftworks;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The {@code craftworks:assembling} recipe type and its serializer (see {@link AssemblingRecipe}). */
public final class CraftworksRecipes {

    public static final String ASSEMBLING = "assembling";

    private static final DeferredRegister<RecipeType<?>> TYPES =
            DeferredRegister.create(Registries.RECIPE_TYPE, Craftworks.MOD_ID);

    private static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, Craftworks.MOD_ID);

    public static final DeferredHolder<RecipeType<?>, RecipeType<AssemblingRecipe>> ASSEMBLING_TYPE =
            TYPES.register(ASSEMBLING, () -> RecipeType.simple(
                    Identifier.fromNamespaceAndPath(Craftworks.MOD_ID, ASSEMBLING)));

    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<AssemblingRecipe>> ASSEMBLING_SERIALIZER =
            SERIALIZERS.register(ASSEMBLING, AssemblingRecipe::serializer);

    private CraftworksRecipes() {
    }

    public static void register(IEventBus modBus) {
        TYPES.register(modBus);
        SERIALIZERS.register(modBus);
    }
}
