// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.mixin.minecraft;

import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.ShapedRecipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** A shaped recipe's result, which it keeps private; for its Converted recipe (ADR-0011). */
@Mixin(ShapedRecipe.class)
public interface ShapedRecipeAccessor {

    @Accessor("result")
    ItemStackTemplate craftworks$result();
}
