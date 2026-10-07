// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.machine;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import io.github._5thlayer.craftworks.recipe.AssemblingCategory;

/**
 * Which Recipe viewer tabs an Assembling recipe goes in (ADR-0016): one tab per machine, which is the
 * Assembler's alone (shared by its three tiers). A recipe is in every tab whose machine holds its
 * {@link AssemblingCategory category}, and in none when no machine does.
 *
 * <p>Built from the categories the server config gives each machine, read when the viewer builds its
 * lists, so it is the config of that moment and never live. Pure: no Minecraft or viewer types.
 */
public final class MachineTabs {

    private final Map<MachineKind, Set<AssemblingCategory>> held = new EnumMap<>(MachineKind.class);

    private MachineTabs(Map<MachineKind, List<AssemblingCategory>> categories) {
        for (MachineKind machine : MachineKind.values()) {
            Set<AssemblingCategory> categoriesHeld = EnumSet.noneOf(AssemblingCategory.class);
            categoriesHeld.addAll(categories.getOrDefault(machine, List.of()));
            held.put(machine, categoriesHeld);
        }
    }

    /**
     * @param categories the categories each machine holds, which for the Assembler is the union over its tiers;
     *     a machine missing from the map holds none
     */
    public static MachineTabs of(Map<MachineKind, List<AssemblingCategory>> categories) {
        return new MachineTabs(categories);
    }

    /** The tabs a recipe of this category is in, Assembler first; empty when no machine holds it. */
    public List<MachineKind> tabsFor(AssemblingCategory category) {
        List<MachineKind> tabs = new ArrayList<>();
        for (MachineKind machine : MachineKind.values()) {
            if (held.get(machine).contains(category)) tabs.add(machine);
        }
        return tabs;
    }

    /** Every recipe put in its tabs, in the order given, and how many went in none. */
    public <R> Sorted<R> sort(Collection<R> recipes, Function<R, AssemblingCategory> categoryOf) {
        Map<MachineKind, List<R>> byTab = new EnumMap<>(MachineKind.class);
        for (MachineKind machine : MachineKind.values()) byTab.put(machine, new ArrayList<>());
        int leftOut = 0;
        for (R recipe : recipes) {
            List<MachineKind> tabs = tabsFor(categoryOf.apply(recipe));
            if (tabs.isEmpty()) leftOut++;
            tabs.forEach(tab -> byTab.get(tab).add(recipe));
        }
        return new Sorted<>(byTab, leftOut);
    }

    /** The recipes of each tab, and the count of those in no tab. */
    public record Sorted<R>(Map<MachineKind, List<R>> byTab, int leftOut) {

        public List<R> in(MachineKind tab) {
            return byTab.get(tab);
        }
    }
}
