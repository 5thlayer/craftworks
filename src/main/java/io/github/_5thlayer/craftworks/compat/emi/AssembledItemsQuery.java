// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.compat.emi;

import java.util.Locale;

import dev.emi.emi.EmiUtil;
import dev.emi.emi.api.stack.EmiStack;
import io.github._5thlayer.craftworks.Craftworks;
import io.github._5thlayer.craftworks.assembler.AssemblingRecipeIds;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;

/**
 * What EMI's {@code @craftworks} adds to its mod search (#15): every item some Assembling recipe makes
 * counts as Craftworks', beside the stacks in the {@code craftworks} namespace EMI already finds.
 *
 * <p>The items come from the Assembling recipe set the server syncs, so they follow {@code /reload}, and
 * they are the same for every player: a Locked route, or nothing held towards it, still lists its item.
 */
public final class AssembledItemsQuery {

    private AssembledItemsQuery() {
    }

    /**
     * Whether an {@code @} term names Craftworks: its namespace or its mod name, whole. Not in part, as EMI
     * matches a mod: every recipe converts to an Assembling recipe, so {@code @c} would list nearly every item.
     */
    public static boolean namesCraftworks(String term) {
        String name = term.toLowerCase(Locale.ROOT);
        return Craftworks.MOD_ID.equals(name) || EmiUtil.getModName(Craftworks.MOD_ID).toLowerCase(Locale.ROOT).equals(name);
    }

    /** Whether the stack is an item some Assembling recipe makes. */
    public static boolean assembled(EmiStack stack) {
        if (!(stack.getKey() instanceof Item)) return false;
        Identifier id = stack.getId();
        return id != null && AssemblingRecipeIds.makes(id.toString());
    }
}
