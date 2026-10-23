// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.mixin.minecraft;

import net.minecraft.core.Holder;
import net.minecraft.world.item.crafting.Ingredient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** One mix of {@link PotionBrewingAccessor}'s lists, a record the brewing table keeps private. */
@Mixin(targets = "net.minecraft.world.item.alchemy.PotionBrewing$Mix")
public interface PotionBrewingMixAccessor {

    @Accessor("from")
    Holder<?> craftworks$from();

    @Accessor("ingredient")
    Ingredient craftworks$ingredient();

    @Accessor("to")
    Holder<?> craftworks$to();
}
