// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.assembler;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.github._5thlayer.craftworks.planner.Planning;

/**
 * One refresh of the Ready set: one craft of every candidate recipe asked of a {@link Planning},
 * one at a time, so {@link ReadyRefresh} can stop between any two.
 *
 * <p>A recipe is Ready when its plan has nothing Missing or Locked, which is the Planning's own
 * {@code complete()}: exactly what Fill Recipe would queue. Nothing about Ready is decided here.
 *
 * <p>The Planning is the one the pass was made with, and it only reads its stock, so a pass spread over
 * ticks answers for one inventory throughout. It remembers the items that cannot be made from that stock:
 * those no chain of recipes reaches, found when it was made, and those a probe found short on quantity,
 * found as the pass goes. That is why a resolve stays cheap however deep the chain behind it (ADR-0024).
 */
public final class ReadyPass implements ReadyRefresh.Pass {

    private final Planning planning;
    private final List<String> candidates;
    private final Set<String> ready = new HashSet<>();
    private int next;

    public ReadyPass(Planning planning, List<String> candidates) {
        this.planning = planning;
        this.candidates = List.copyOf(candidates);
    }

    /**
     * The candidates in the order a pass should resolve them (ADR-0024): the last pass's Ready recipes
     * first, sorted, then the rest, sorted. A pass that streams what it adds shows the list a player
     * already had before anything new, and most of a list survives an inventory change.
     */
    public static List<String> ordered(Collection<String> ids, Set<String> lastReady) {
        List<String> first = new ArrayList<>();
        List<String> rest = new ArrayList<>();
        for (String id : ids) (lastReady.contains(id) ? first : rest).add(id);
        first.sort(null);
        rest.sort(null);
        first.addAll(rest);
        return first;
    }

    @Override
    public boolean done() {
        return next >= candidates.size();
    }

    @Override
    public void resolveNext() {
        String recipe = candidates.get(next++);
        if (planning.ready(recipe)) ready.add(recipe);
    }

    @Override
    public Set<String> ready() {
        return Set.copyOf(ready);
    }

    @Override
    public int total() {
        return candidates.size();
    }
}
