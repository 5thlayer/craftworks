// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.assembler;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.github._5thlayer.craftworks.planner.ItemBag;
import io.github._5thlayer.craftworks.planner.Resolver;

/**
 * One refresh of the Ready set: one craft of every candidate recipe resolved by the {@link Resolver},
 * one at a time, so {@link ReadyRefresh} can stop between any two.
 *
 * <p>A recipe is Ready when its plan has nothing Missing or Locked, which is the Resolver's own
 * {@code complete()}: exactly what Fill Recipe would queue. Nothing about Ready is decided here.
 *
 * <p>The inventory is the snapshot the pass was made with, and the Resolver only reads it, so a pass
 * spread over ticks answers for one inventory throughout. The pass owns a {@link Resolver.Memo} of the
 * items it knows cannot be made from that snapshot: those no chain of recipes reaches, found when the pass
 * starts, and those a probe found short on quantity, found as it goes. That is why a resolve stays cheap
 * however deep the chain behind it (ADR-0024).
 */
public final class ReadyPass implements ReadyRefresh.Pass {

    private final Resolver resolver;
    private final List<String> candidates;
    private final Resolver.Memo memo;
    private final Set<String> ready = new HashSet<>();
    private int next;

    public ReadyPass(Resolver resolver, ItemBag stock, List<String> candidates) {
        this.resolver = resolver;
        this.memo = resolver.memo(stock);
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
        if (resolver.resolveReady(recipe, memo).complete()) ready.add(recipe);
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
