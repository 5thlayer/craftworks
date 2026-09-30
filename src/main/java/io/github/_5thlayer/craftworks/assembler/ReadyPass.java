// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.assembler;

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
 * spread over ticks answers for one inventory throughout.
 */
public final class ReadyPass implements ReadyRefresh.Pass {

    private final Resolver resolver;
    private final ItemBag stock;
    private final List<String> candidates;
    private final Set<String> ready = new HashSet<>();
    private int next;

    public ReadyPass(Resolver resolver, ItemBag stock, List<String> candidates) {
        this.resolver = resolver;
        this.stock = stock;
        this.candidates = List.copyOf(candidates);
    }

    @Override
    public boolean done() {
        return next >= candidates.size();
    }

    @Override
    public void resolveNext() {
        String recipe = candidates.get(next++);
        if (resolver.resolve(recipe, 1, stock).complete()) ready.add(recipe);
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
