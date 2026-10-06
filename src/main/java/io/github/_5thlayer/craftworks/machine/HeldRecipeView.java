// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.machine;

import java.util.List;

import io.github._5thlayer.craftworks.recipe.AssemblingRecipe;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.neoforged.neoforge.common.crafting.SizedIngredient;

/**
 * What a machine's screen draws of the Held recipe, sent from the server since the client has no recipe manager:
 * its id, what each input slot takes, and its results, the first being its product.
 */
public record HeldRecipeView(Identifier id, List<SizedIngredient> ingredients, List<ItemStackTemplate> results) {

    public static final StreamCodec<RegistryFriendlyByteBuf, HeldRecipeView> STREAM_CODEC = StreamCodec.composite(
            Identifier.STREAM_CODEC, HeldRecipeView::id,
            SizedIngredient.STREAM_CODEC.apply(ByteBufCodecs.list()), HeldRecipeView::ingredients,
            ItemStackTemplate.STREAM_CODEC.apply(ByteBufCodecs.list()), HeldRecipeView::results,
            HeldRecipeView::new);

    /** The first result, the one the product slot gets; empty for a recipe that makes only fluids. */
    public ItemStack product() {
        return AssemblingRecipe.productOf(results);
    }
}
