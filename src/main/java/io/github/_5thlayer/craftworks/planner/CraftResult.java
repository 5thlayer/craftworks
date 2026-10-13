// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.planner;

import java.util.List;

/**
 * What a finished craft of a Data-carrying recipe really made (ADR-0022): the Minecraft side asks the mod's
 * original recipe, so the planner stays free of Minecraft.
 *
 * <p>The queue asks it once per craft of a Data-carrying step, intermediates included, since what a plan made
 * pays for the step it was made for.
 */
@FunctionalInterface
public interface CraftResult {

    /** Every craft makes what it planned. */
    CraftResult PLANNED = (recipe, consumed, planned) -> planned;

    /**
     * The key and count one craft of {@code recipe} made, given the exact keys that craft consumed;
     * {@code planned} (the listed result) when nothing changes.
     */
    ItemAmount made(String recipe, List<ItemAmount> consumed, ItemAmount planned);
}
