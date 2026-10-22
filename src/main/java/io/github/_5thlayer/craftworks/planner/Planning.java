// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.planner;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Planning against one stock and one Lock source, as they stood when it was made: the one place "what can
 * this player make right now" is answered.
 *
 * <p>Made by {@link Resolver#planning}. It reads the stock once, copied and never changed, works out what
 * that stock cannot reach (see {@link Reach}) once, and remembers every {@link #plan} it has resolved by
 * recipe and craft count. That is sound because nothing a resolve reads can change under it: the stock is
 * its own copy, the Lock answers are fixed for the lifetime of the Lock source it is given (a
 * {@link Locks.Reasoned} asks each recipe once), and a {@link Resolver.Resolution} is immutable. So the
 * Crafting Plan's one craft and the ceiling's first resolve are one resolve, and queueing the ceiling reuses
 * the plan the search for it ended on.
 *
 * <p>A Planning is for one request. A caller whose stock has changed makes another; only a Ready pass keeps
 * one across ticks, and it answers for the moment it was made.
 *
 * <p>{@link #ready} is the other way in, for the Ready pass (ADR-0024). It probes, which {@link #plan} must
 * not: a plan reports every Missing and Locked item, and a probe stops at the first it finds. The two share
 * nothing but the stock, and a Ready answer never enters the plan cache.
 */
public final class Planning {

    private record Request(String recipeId, int crafts) {
    }

    private final Resolver resolver;
    private final Map<String, Integer> stock;
    private final Set<String> unreached;
    private final Map<Request, Resolver.Resolution> plans = new HashMap<>();
    /** Made on the first {@link #ready}, so a Planning that only plans never pays for it. */
    private Resolver.Memo memo;

    Planning(Resolver resolver, ItemBag stock) {
        this.resolver = resolver;
        this.stock = Collections.unmodifiableMap(new LinkedHashMap<>(stock.asMap()));
        this.unreached = resolver.unreachedFrom(this.stock);
    }

    /**
     * {@code crafts} runs of {@code recipeId} against the stock, with everything Missing and Locked named.
     *
     * <p>The stock is read, never spent: the queue takes the cost when the plan is queued.
     */
    public Resolver.Resolution plan(String recipeId, int crafts) {
        return plans.computeIfAbsent(new Request(recipeId, crafts),
                request -> resolver.resolveAgainst(recipeId, crafts, stock, unreached));
    }

    /**
     * The Crafting Plan's {@code all}: the largest count whose plan is complete, so {@code all} can
     * never queue a plan the stock then refuses (ADR-0003).
     *
     * <p>Found by doubling and then bisecting rather than by dividing the stock through the recipe, because
     * a chain's cost is not linear in the count: a recipe making two at a time leaves a surplus that the next
     * craft uses, so twice the crafts can cost less than twice the ingredients. Every step goes through
     * {@link #plan}, and the search always ends on a count it resolved complete, so {@code plan(id, result)}
     * is already cached.
     */
    public int largestAffordable(String recipeId) {
        if (!plan(recipeId, 1).complete()) return 0;
        int affordable = 1;
        int beyond = 2;
        while (beyond <= Resolver.MAX_CRAFTS && plan(recipeId, beyond).complete()) {
            affordable = beyond;
            beyond *= 2;
        }
        int high = Math.min(beyond, Resolver.MAX_CRAFTS + 1);
        while (affordable + 1 < high) {
            int middle = affordable + (high - affordable) / 2;
            if (plan(recipeId, middle).complete()) {
                affordable = middle;
            } else {
                high = middle;
            }
        }
        return affordable;
    }

    /**
     * Whether one craft of {@code recipeId} is complete, as a Ready pass asks it: the same answer as
     * {@code plan(recipeId, 1).complete()}, found without naming what is Missing, and cheap however deep the
     * chain behind it (ADR-0024).
     */
    public boolean ready(String recipeId) {
        if (memo == null) memo = new Resolver.Memo(stock, unreached);
        return resolver.resolveReady(recipeId, memo).complete();
    }
}
