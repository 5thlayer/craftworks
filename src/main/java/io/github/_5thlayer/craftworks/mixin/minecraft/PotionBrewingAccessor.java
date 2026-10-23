// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.mixin.minecraft;

import java.util.List;

import net.minecraft.world.item.alchemy.PotionBrewing;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * The brewing table's potion and container mixes, which {@link PotionBrewing} keeps private and answers only
 * by a source and an ingredient; for the Brewed recipes (ADR-0025). Each is a list of
 * {@link PotionBrewingMixAccessor}.
 */
@Mixin(PotionBrewing.class)
public interface PotionBrewingAccessor {

    @Accessor("potionMixes")
    List<?> craftworks$potionMixes();

    @Accessor("containerMixes")
    List<?> craftworks$containerMixes();
}
