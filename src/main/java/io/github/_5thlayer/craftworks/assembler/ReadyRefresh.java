// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.assembler;

import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * When one watching player's Ready set is worked out again, and how it is spread over ticks (ADR-0012).
 *
 * <p>Free of Minecraft, so the schedule is checked with a fake clock. What is resolved is the
 * {@link Pass}'s business; this class decides only when a pass starts, how much of it runs in a tick,
 * and whether its answer may be published.
 *
 * <p><b>Triggers.</b> A pass starts when the screen opens, once the inventory has been quiet for
 * {@value #QUIET_TICKS} ticks after a change, and on a {@value #HEARTBEAT_TICKS}-tick heartbeat, which is
 * the only thing that notices a Lock source changing its mind, since none of them says when. The
 * heartbeat counts from the last pass's start or end, whichever is later: a pack whose pass takes longer
 * than a heartbeat would otherwise never stop resolving.
 *
 * <p><b>Budget.</b> A tick resolves candidates until {@value #BUDGET_NANOS} ns are spent, checked between
 * whole resolves, because one resolve cannot be paused partway. Every tick that has a pass running
 * resolves at least one candidate, so a resolve slower than the budget still finishes.
 *
 * <p><b>Supersession.</b> A change to the inventory, a new open or a close drops the pass in flight: its
 * answer is about an inventory that is gone. It is never published, so a set is never published over a
 * newer one. A change waits out the quiet ticks before the next pass starts, so a queue delivering
 * every few ticks does not restart a pass on each one.
 *
 * <p>Runs on the server thread and nowhere else.
 */
public final class ReadyRefresh {

    public static final int QUIET_TICKS = 10;
    public static final int HEARTBEAT_TICKS = 40;
    public static final long BUDGET_NANOS = 2_000_000L;

    /** Nanoseconds, from any origin. */
    public interface Clock {
        long nanos();
    }

    /** One refresh's work: every candidate resolved once, against the inventory as it was when the pass began. */
    public interface Pass {

        /** Whether every candidate has been resolved. */
        boolean done();

        /** Resolves the next candidate, whole. */
        void resolveNext();

        /** The recipe ids found Ready so far; complete once {@link #done()}. */
        Set<String> ready();

        /** How many candidates the pass resolves. */
        int total();
    }

    /**
     * A finished pass. {@code nanos} is the time spent resolving, not the time between start and end;
     * {@code ticks} is how many ticks it was spread over.
     */
    public record Refreshed(Set<String> ready, int total, long nanos, int ticks) {
    }

    private final Clock clock;
    private final Supplier<Pass> begin;
    private final Consumer<Refreshed> publish;

    private boolean watching;
    private boolean startNow;
    private boolean changePending;
    private int tick;
    private int lastChange;
    private int lastRefresh;

    private Pass pass;
    private long spent;
    private int passTicks;

    /**
     * @param begin snapshots the inventory and lists the candidates; called only when a pass starts
     * @param publish handed each pass that finishes without being superseded
     */
    public ReadyRefresh(Clock clock, Supplier<Pass> begin, Consumer<Refreshed> publish) {
        this.clock = clock;
        this.begin = begin;
        this.publish = publish;
    }

    /** The client reports the inventory screen open: a pass starts on the next tick. */
    public void open() {
        watching = true;
        startNow = true;
        changePending = false;
        pass = null;
    }

    /** The screen closed: nothing is resolved from here until it opens again. */
    public void close() {
        watching = false;
        startNow = false;
        changePending = false;
        pass = null;
    }

    public boolean watching() {
        return watching;
    }

    /** The inventory changed. Ignored while nobody is watching. */
    public void inventoryChanged() {
        if (!watching) return;
        changePending = true;
        lastChange = tick;
        pass = null;
    }

    /** One server tick. Does nothing, not even counting, unless the screen is open. */
    public void tick() {
        if (!watching) return;
        tick++;
        long tickStart = clock.nanos();
        if (pass == null && due()) start();
        if (pass == null) return;
        while (!pass.done()) {
            pass.resolveNext();
            if (clock.nanos() - tickStart >= BUDGET_NANOS) break;
        }
        spent += clock.nanos() - tickStart;
        passTicks++;
        if (!pass.done()) return;
        Pass finished = pass;
        pass = null;
        lastRefresh = tick;
        publish.accept(new Refreshed(Set.copyOf(finished.ready()), finished.total(), spent, passTicks));
    }

    private boolean due() {
        if (startNow) return true;
        if (changePending) return tick - lastChange >= QUIET_TICKS;
        return tick - lastRefresh >= HEARTBEAT_TICKS;
    }

    private void start() {
        startNow = false;
        changePending = false;
        lastRefresh = tick;
        spent = 0;
        passTicks = 0;
        pass = begin.get();
    }
}
