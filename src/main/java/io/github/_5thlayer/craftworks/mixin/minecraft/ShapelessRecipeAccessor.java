// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.mixin.minecraft;

import java.util.List;

import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** A shapeless recipe's ingredients, which it keeps private; for its Converted recipe (ADR-0011). */
@Mixin(ShapelessRecipe.class)
public interface ShapelessRecipeAccessor {

    @Accessor("ingredients")
    List<Ingredient> craftworks$ingredients();
}
