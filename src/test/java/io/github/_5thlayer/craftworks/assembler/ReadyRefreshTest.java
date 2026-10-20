// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.assembler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * When the Ready set is worked out again and how a refresh is spread over ticks (ADR-0024), against a
 * fake clock: a pass costs what the test says each resolve costs.
 */
class ReadyRefreshTest {

    private static final long MS = 1_000_000L;

    /** Time only moves when a resolve says it does. */
    private static final class FakeClock implements ReadyRefresh.Clock {
        long now;

        @Override
        public long nanos() {
            return now;
        }
    }

    /** A pass of {@code total} candidates, each costing {@code cost} ns, whose answer is named by {@code generation}. */
    private static final class FakePass implements ReadyRefresh.Pass {
        private final FakeClock clock;
        private final int total;
        private final long cost;
        private final String generation;
        int resolved;

        FakePass(FakeClock clock, int total, long cost, String generation) {
            this.clock = clock;
            this.total = total;
            this.cost = cost;
            this.generation = generation;
        }

        @Override
        public boolean done() {
            return resolved >= total;
        }

        @Override
        public void resolveNext() {
            resolved++;
            clock.now += cost;
        }

        @Override
        public Set<String> ready() {
            if (done()) return Set.of(generation);
            Set<String> partial = new HashSet<>();
            for (int i = 0; i < resolved; i++) partial.add(generation + "_found" + i);
            return partial;
        }

        @Override
        public int total() {
            return total;
        }
    }

    private final FakeClock clock = new FakeClock();
    private final List<FakePass> begun = new ArrayList<>();
    private final List<ReadyRefresh.Refreshed> published = new ArrayList<>();
    private final List<Set<String>> streamed = new ArrayList<>();
    private int candidates = 4;
    private long cost = 0;

    private ReadyRefresh refresh() {
        return new ReadyRefresh(clock, () -> {
            FakePass pass = new FakePass(clock, candidates, cost, "pass" + begun.size());
            begun.add(pass);
            return pass;
        }, streamed::add, published::add);
    }

    private static void tick(ReadyRefresh refresh, int times) {
        for (int i = 0; i < times; i++) refresh.tick();
    }

    @Test
    void nothingIsResolvedBeforeTheScreenOpens() {
        ReadyRefresh refresh = refresh();
        refresh.inventoryChanged();
        tick(refresh, 200);
        assertEquals(0, begun.size());
        assertEquals(0, published.size());
    }

    @Test
    void openingStartsAPassAtOnceAndPublishesIt() {
        ReadyRefresh refresh = refresh();
        refresh.open();
        refresh.tick();
        assertEquals(1, begun.size());
        assertEquals(List.of(Set.of("pass0")), published.stream().map(ReadyRefresh.Refreshed::ready).toList());
    }

    @Test
    void closingPausesTheRunningPassAndOpeningResumesIt() {
        candidates = 100;
        cost = MS;
        ReadyRefresh refresh = refresh();
        refresh.open();
        tick(refresh, 2);
        int before = begun.get(0).resolved;
        refresh.close();
        tick(refresh, 500);
        assertEquals(before, begun.get(0).resolved, "a closed screen kept resolving");
        assertEquals(0, published.size());
        assertTrue(refresh.working(), "closing dropped the pass");
        refresh.open();
        tick(refresh, 60);
        assertEquals(1, begun.size(), "opening started a pass over the one in flight");
        assertEquals(List.of(Set.of("pass0")), published.stream().map(ReadyRefresh.Refreshed::ready).toList());
    }

    @Test
    void aChangeWaitsForTenQuietTicksBeforeResolving() {
        ReadyRefresh refresh = refresh();
        refresh.open();
        refresh.tick();
        refresh.inventoryChanged();
        tick(refresh, ReadyRefresh.QUIET_TICKS - 1);
        assertEquals(1, begun.size(), "resolved before the inventory had been quiet");
        refresh.tick();
        assertEquals(2, begun.size());
        assertEquals(2, published.size());
    }

    @Test
    void aFurtherChangeRestartsTheQuietCount() {
        ReadyRefresh refresh = refresh();
        refresh.open();
        refresh.tick();
        refresh.inventoryChanged();
        tick(refresh, 6);
        refresh.inventoryChanged();
        tick(refresh, ReadyRefresh.QUIET_TICKS - 1);
        assertEquals(1, begun.size());
        refresh.tick();
        assertEquals(2, begun.size());
    }

    @Test
    void theHeartbeatResolvesAgainWithNoChange() {
        ReadyRefresh refresh = refresh();
        refresh.open();
        refresh.tick();
        tick(refresh, ReadyRefresh.HEARTBEAT_TICKS - 1);
        assertEquals(1, begun.size());
        refresh.tick();
        assertEquals(2, begun.size());
        tick(refresh, ReadyRefresh.HEARTBEAT_TICKS);
        assertEquals(3, begun.size());
    }

    /** A queue delivering every few ticks never lets the inventory go quiet; the heartbeat still refreshes. */
    @Test
    void theHeartbeatStillRefreshesWhileTheInventoryKeepsChanging() {
        ReadyRefresh refresh = refresh();
        refresh.open();
        refresh.tick();
        for (int t = 0; t < 4 * ReadyRefresh.HEARTBEAT_TICKS; t++) {
            if (t % 3 == 0) refresh.inventoryChanged();
            refresh.tick();
        }
        assertTrue(published.size() >= 4, "only " + published.size() + " refreshes published while the inventory kept changing");
    }

    /** A heartbeat pass spread over many ticks finishes, though changes keep arriving while it runs. */
    @Test
    void aHeartbeatPassOutlastsChangesThatArriveWhileItRuns() {
        candidates = 40;
        cost = MS;
        ReadyRefresh refresh = refresh();
        refresh.open();
        tick(refresh, 20);
        int before = published.size();
        for (int t = 0; t < 4 * ReadyRefresh.HEARTBEAT_TICKS; t++) {
            if (t % 3 == 0) refresh.inventoryChanged();
            refresh.tick();
        }
        assertTrue(published.size() > before, "no pass finished while the inventory kept changing");
    }

    @Test
    void theHeartbeatCountsFromTheEndOfALongPass() {
        candidates = 30;
        cost = MS;
        ReadyRefresh refresh = refresh();
        refresh.open();
        // 30 resolves at 1 ms in a 2 ms budget: 2 per tick, 15 ticks.
        tick(refresh, 15);
        assertEquals(1, published.size());
        tick(refresh, ReadyRefresh.HEARTBEAT_TICKS - 1);
        assertEquals(1, begun.size(), "the next pass began before a full heartbeat had passed since the last ended");
        refresh.tick();
        assertEquals(2, begun.size());
    }

    @Test
    void aTickResolvesUntilItsBudgetIsSpentAndNoFurther() {
        candidates = 100;
        cost = MS / 2;
        ReadyRefresh refresh = refresh();
        refresh.open();
        refresh.tick();
        // 0.5 ms each: the fourth resolve reaches 2 ms, so the tick stops there.
        assertEquals(4, begun.get(0).resolved);
        refresh.tick();
        assertEquals(8, begun.get(0).resolved);
    }

    @Test
    void theBudgetIsCheckedBetweenWholeResolves() {
        candidates = 3;
        cost = 5 * MS;
        ReadyRefresh refresh = refresh();
        refresh.open();
        refresh.tick();
        // One resolve alone is over budget, and it is not cut short; it is the only one that tick.
        assertEquals(1, begun.get(0).resolved);
        assertEquals(0, published.size());
        tick(refresh, 2);
        assertEquals(1, published.size());
        assertEquals(3, published.get(0).ticks());
        assertEquals(15 * MS, published.get(0).nanos());
    }

    @Test
    void aRefreshReportsItsTimeAndSize() {
        candidates = 6;
        cost = MS;
        ReadyRefresh refresh = refresh();
        refresh.open();
        tick(refresh, 3);
        ReadyRefresh.Refreshed done = published.get(0);
        assertEquals(6, done.total());
        assertEquals(6 * MS, done.nanos());
        assertEquals(3, done.ticks());
    }

    @Test
    void aChangeDuringAPassDoesNotDropItAndTheNextPassFollowsOnceQuiet() {
        candidates = 20;
        cost = MS;
        ReadyRefresh refresh = refresh();
        refresh.open();
        tick(refresh, 3);
        refresh.inventoryChanged();
        // The pass in flight runs on to the end and publishes, however many changes land meanwhile.
        tick(refresh, 7);
        assertEquals(1, begun.size());
        assertEquals(List.of(Set.of("pass0")), published.stream().map(ReadyRefresh.Refreshed::ready).toList());
        // The change then waits out its quiet ticks, counted from the change, and gets its own pass.
        tick(refresh, 15);
        assertEquals(2, begun.size());
        assertEquals(List.of(Set.of("pass0"), Set.of("pass1")), published.stream().map(ReadyRefresh.Refreshed::ready).toList());
    }

    @Test
    void aPassThatChangesKeepArrivingDuringStillFinishes() {
        candidates = 200;
        cost = MS;
        ReadyRefresh refresh = refresh();
        refresh.open();
        for (int t = 0; t < 130; t++) {
            if (t % 2 == 0) refresh.inventoryChanged();
            refresh.tick();
        }
        assertEquals(Set.of("pass0"), published.get(0).ready());
    }

    @Test
    void reopeningResumesARunningPassRatherThanRestartingIt() {
        candidates = 20;
        cost = MS;
        ReadyRefresh refresh = refresh();
        refresh.open();
        tick(refresh, 2);
        refresh.open();
        tick(refresh, 10);
        assertEquals(1, begun.size());
        assertEquals(List.of(Set.of("pass0")), published.stream().map(ReadyRefresh.Refreshed::ready).toList());
    }

    @Test
    void aChangeWhileClosedWithNoPassInFlightIsSeenByThePassOpeningStarts() {
        ReadyRefresh refresh = refresh();
        refresh.inventoryChanged();
        refresh.open();
        refresh.tick();
        assertEquals(1, begun.size());
        tick(refresh, ReadyRefresh.QUIET_TICKS);
        assertEquals(1, begun.size(), "a change from before the screen opened started a second pass");
    }

    @Test
    void aChangeWhileClosedWithAPassInFlightFollowsTheResumedPass() {
        candidates = 20;
        cost = MS;
        ReadyRefresh refresh = refresh();
        refresh.open();
        tick(refresh, 2);
        refresh.close();
        refresh.inventoryChanged();
        tick(refresh, 100);
        assertEquals(1, begun.size());
        refresh.open();
        tick(refresh, 10);
        assertEquals(1, published.size());
        tick(refresh, ReadyRefresh.QUIET_TICKS);
        assertEquals(2, begun.size(), "the change made while closed never got its pass");
    }

    @Test
    void nothingRunsAtLoginUntilTheScreenOpens() {
        ReadyRefresh refresh = refresh();
        tick(refresh, 500);
        assertEquals(0, begun.size());
    }

    @Test
    void additionsStreamEveryTwentyTicksAndRemovalsWaitForTheEnd() {
        candidates = 100;
        cost = MS;
        ReadyRefresh refresh = refresh();
        refresh.open();
        tick(refresh, ReadyRefresh.STREAM_TICKS - 1);
        assertEquals(0, streamed.size());
        refresh.tick();
        assertEquals(1, streamed.size());
        assertEquals(40, streamed.get(0).size(), "the first batch is everything found so far");
        tick(refresh, ReadyRefresh.STREAM_TICKS);
        assertEquals(2, streamed.size());
        assertTrue(Collections.disjoint(streamed.get(0), streamed.get(1)), "an id was streamed twice");
        assertEquals(40, streamed.get(1).size());
        assertEquals(0, published.size());
        tick(refresh, 10);
        assertEquals(1, published.size());
        assertEquals(Set.of("pass0"), published.get(0).ready());
    }

    @Test
    void aTickThatFindsNothingNewStreamsNothing() {
        candidates = 100;
        cost = 0;
        ReadyRefresh refresh = refresh();
        refresh.open();
        refresh.tick();
        assertEquals(0, streamed.size());
    }

    @Test
    void aRefreshReportsItsSlowestTick() {
        ReadyRefresh refresh = refresh();
        candidates = 4;
        cost = 3 * MS;
        refresh.open();
        // 3 ms per resolve against a 2 ms budget: a tick is one resolve of 3 ms.
        tick(refresh, 4);
        assertEquals(3 * MS, published.get(0).maxTickNanos());
        assertEquals(12 * MS, published.get(0).nanos());
    }

}
