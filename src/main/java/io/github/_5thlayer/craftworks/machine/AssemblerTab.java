// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.machine;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import io.github._5thlayer.craftworks.recipe.AssemblingCategory;

/**
 * The Assembler's Recipe viewer tab (ADR-0016), shared by its three tiers: an Assembling recipe is in it when some
 * tier holds its {@link AssemblingCategory category}, and in no tab when none does.
 *
 * <p>Built from the categories the server config gives the tiers, read when the viewer builds its lists, so it is
 * the config of that moment and never live. Pure: no Minecraft or viewer types.
 */
public final class AssemblerTab {

    /** The tab's name, {@code craftworks:} this in EMI and JEI: {@code assembler}. */
    public static final String NAME = "assembler";

    private final Set<AssemblingCategory> held = EnumSet.noneOf(AssemblingCategory.class);

    private AssemblerTab(Collection<AssemblingCategory> categories) {
        held.addAll(categories);
    }

    /** @param categories the categories the tiers hold, which may repeat across tiers; none held means no recipes */
    public static AssemblerTab of(Collection<AssemblingCategory> categories) {
        return new AssemblerTab(categories);
    }

    /** Whether a recipe of this category is in the tab: some tier holds it. */
    public boolean holds(AssemblingCategory category) {
        return held.contains(category);
    }

    /** The recipes in the tab, in the order given, and how many were left out. */
    public <R> Sorted<R> sort(Collection<R> recipes, Function<R, AssemblingCategory> categoryOf) {
        List<R> in = new ArrayList<>();
        int leftOut = 0;
        for (R recipe : recipes) {
            if (holds(categoryOf.apply(recipe))) {
                in.add(recipe);
            } else {
                leftOut++;
            }
        }
        return new Sorted<>(in, leftOut);
    }

    /** The recipes in the tab, and the count of those in none. */
    public record Sorted<R>(List<R> recipes, int leftOut) {
    }
}
