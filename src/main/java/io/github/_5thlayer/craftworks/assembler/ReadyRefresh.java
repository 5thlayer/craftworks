// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.assembler;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * When one watching player's Ready set is worked out again, and how it is spread over ticks (ADR-0024,
 * which supersedes ADR-0012's schedule and budget).
 *
 * <p>Free of Minecraft, so the schedule is checked with a fake clock. What is resolved is the
 * {@link Pass}'s business; this class decides only when a pass starts, how much of it runs in a tick,
 * and what is published when.
 *
 * <p><b>A pass always finishes and publishes.</b> An inventory change during it marks the inventory dirty
 * and nothing more; the next pass starts once the inventory has been quiet for {@value #QUIET_TICKS}
 * ticks, so a queue delivering every few ticks does not restart a pass on each delivery. A set stale by
 * one pass beats the empty one a pack too big to finish between changes would otherwise leave.
 *
 * <p><b>Closing pauses, opening resumes.</b> Nothing runs while the screen is closed, and a pass in flight
 * is kept, not dropped. A change while closed is pending like any other, so a fresh pass follows the
 * resumed one; with no pass in flight, opening starts one at once, which sees the change. Nothing runs at
 * login: a player who never opens the screen costs nothing.
 *
 * <p><b>Triggers.</b> A pass starts when the screen opens with none in flight, once the inventory has been
 * quiet for {@value #QUIET_TICKS} ticks after a change, and on a {@value #HEARTBEAT_TICKS}-tick heartbeat,
 * which is the only thing that notices a Lock source changing its mind, since none of them says when. The
 * heartbeat counts from the last pass's end.
 *
 * <p><b>Streaming.</b> Every {@value #STREAM_TICKS} ticks a pass runs, the ids it has newly found Ready are
 * handed to the stream consumer; removals wait for the end, when the whole set is published, so a recipe
 * never vanishes and comes back mid-pass.
 *
 * <p><b>Budget.</b> A tick resolves candidates until {@value #BUDGET_NANOS} ns are spent, checked between
 * whole resolves, because one resolve cannot be paused partway; making each cheap is the Resolver's job.
 * Every tick that has a pass running resolves at least one candidate, so a resolve slower than the budget
 * still finishes.
 *
 * <p>Runs on the server thread and nowhere else.
 */
public final class ReadyRefresh {

    public static final int QUIET_TICKS = 10;
    public static final int HEARTBEAT_TICKS = 40;
    public static final int STREAM_TICKS = 20;
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
     * {@code ticks} is how many ticks it was spread over; {@code maxTickNanos} is the most any one of those
     * ticks spent, measured as {@code nanos} is, which is what a player feels as a stutter.
     */
    public record Refreshed(Set<String> ready, int total, long nanos, int ticks, long maxTickNanos) {
    }

    private final Clock clock;
    private final Supplier<Pass> begin;
    private final Consumer<Set<String>> stream;
    private final Consumer<Refreshed> publish;

    private boolean watching;
    private boolean startNow;
    private boolean changePending;
    private int tick;
    private int lastChange;
    private int lastRefresh;

    private Pass pass;
    private long spent;
    private long maxTick;
    private int passTicks;
    private int lastStream;
    private final Set<String> streamed = new HashSet<>();

    /**
     * @param begin snapshots the inventory and lists the candidates; called only when a pass starts
     * @param stream handed the ids a running pass newly found Ready, every {@value #STREAM_TICKS} ticks
     * @param publish handed each pass that finishes, with its whole set
     */
    public ReadyRefresh(Clock clock, Supplier<Pass> begin, Consumer<Set<String>> stream, Consumer<Refreshed> publish) {
        this.clock = clock;
        this.begin = begin;
        this.stream = stream;
        this.publish = publish;
    }

    /** The ids in {@code found} that a client already holding {@code held} lacks: all a streamed batch need send. */
    static Set<String> notHeld(Set<String> found, Set<String> held) {
        Set<String> lacking = new HashSet<>(found);
        lacking.removeAll(held);
        return lacking;
    }

    /** The client reports the inventory screen open: a pass in flight resumes, and with none a pass starts on the next tick. */
    public void open() {
        watching = true;
        if (pass == null) startNow = true;
    }

    /** The screen closed: nothing is resolved from here until it opens again, and a pass in flight waits. */
    public void close() {
        watching = false;
    }

    public boolean watching() {
        return watching;
    }

    /** Whether a pass is in flight, paused or not. */
    public boolean working() {
        return pass != null;
    }

    /** The inventory changed. Counts while closed too: the pass that follows is about the inventory as it is then. */
    public void inventoryChanged() {
        changePending = true;
        lastChange = tick;
    }

    /** One server tick. Does nothing, not even counting, unless the screen is open. */
    public void tick() {
        if (!watching) return;
        tick++;
        long tickStart = clock.nanos();
        if (pass == null) {
            if (!due()) return;
            start();
        }
        while (!pass.done()) {
            pass.resolveNext();
            if (clock.nanos() - tickStart >= BUDGET_NANOS) break;
        }
        long thisTick = clock.nanos() - tickStart;
        spent += thisTick;
        maxTick = Math.max(maxTick, thisTick);
        passTicks++;
        if (!pass.done()) {
            if (passTicks - lastStream >= STREAM_TICKS) {
                lastStream = passTicks;
                Set<String> added = new HashSet<>(pass.ready());
                added.removeAll(streamed);
                if (!added.isEmpty()) {
                    streamed.addAll(added);
                    stream.accept(added);
                }
            }
            return;
        }
        Pass finished = pass;
        pass = null;
        lastRefresh = tick;
        publish.accept(new Refreshed(Set.copyOf(finished.ready()), finished.total(), spent, passTicks, maxTick));
    }

    private boolean due() {
        if (startNow) return true;
        if (changePending && tick - lastChange >= QUIET_TICKS) return true;
        return tick - lastRefresh >= HEARTBEAT_TICKS;
    }

    private void start() {
        startNow = false;
        changePending = false;
        lastRefresh = tick;
        spent = 0;
        maxTick = 0;
        passTicks = 0;
        lastStream = 0;
        streamed.clear();
        pass = begin.get();
    }
}
