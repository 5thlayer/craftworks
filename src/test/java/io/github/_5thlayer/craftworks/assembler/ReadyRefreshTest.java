// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.assembler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * When the Ready set is worked out again and how a refresh is spread over ticks (ADR-0012), against a
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
            return done() ? Set.of(generation) : Set.of();
        }

        @Override
        public int total() {
            return total;
        }
    }

    private final FakeClock clock = new FakeClock();
    private final List<FakePass> begun = new ArrayList<>();
    private final List<ReadyRefresh.Refreshed> published = new ArrayList<>();
    private int candidates = 4;
    private long cost = 0;

    private ReadyRefresh refresh() {
        return new ReadyRefresh(clock, () -> {
            FakePass pass = new FakePass(clock, candidates, cost, "pass" + begun.size());
            begun.add(pass);
            return pass;
        }, published::add);
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
    void closingStopsResolvingAndDropsTheRunningPass() {
        candidates = 100;
        cost = MS;
        ReadyRefresh refresh = refresh();
        refresh.open();
        refresh.tick();
        refresh.close();
        tick(refresh, 500);
        assertEquals(1, begun.size());
        assertEquals(0, published.size());
        assertTrue(begun.get(0).resolved < 100, "a closed screen kept resolving");
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

    /** The change a heartbeat pass could not see still gets its quiet refresh once the inventory settles. */
    @Test
    void aChangeDuringAHeartbeatPassIsRefreshedOnceQuiet() {
        ReadyRefresh refresh = refresh();
        refresh.open();
        refresh.tick();
        for (int t = 0; t < ReadyRefresh.HEARTBEAT_TICKS + 1; t++) {
            if (t % 3 == 0) refresh.inventoryChanged();
            refresh.tick();
        }
        int afterTrickle = published.size();
        tick(refresh, ReadyRefresh.QUIET_TICKS);
        assertEquals(afterTrickle + 1, published.size(), "the last change was never refreshed");
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
    void aStaleRefreshNeverPublishesOverANewerOne() {
        candidates = 20;
        cost = MS;
        ReadyRefresh refresh = refresh();
        refresh.open();
        tick(refresh, 3);
        assertEquals(6, begun.get(0).resolved);
        refresh.inventoryChanged();
        // The pass in flight is about an inventory that is gone: it stops, and the quiet ticks pass.
        tick(refresh, ReadyRefresh.QUIET_TICKS - 1);
        assertEquals(6, begun.get(0).resolved);
        assertEquals(1, begun.size());
        // The newer pass runs to the end; only it is published.
        tick(refresh, 10);
        assertEquals(2, begun.size());
        Set<String> seen = new HashSet<>();
        published.forEach(refreshed -> seen.addAll(refreshed.ready()));
        assertEquals(Set.of("pass1"), seen);
    }

    @Test
    void reopeningRestartsARunningPass() {
        candidates = 20;
        cost = MS;
        ReadyRefresh refresh = refresh();
        refresh.open();
        tick(refresh, 2);
        refresh.open();
        tick(refresh, 10);
        assertEquals(2, begun.size());
        assertEquals(List.of(Set.of("pass1")), published.stream().map(ReadyRefresh.Refreshed::ready).toList());
    }

    @Test
    void aChangeWhileClosedIsForgottenOnceOpened() {
        ReadyRefresh refresh = refresh();
        refresh.inventoryChanged();
        refresh.open();
        refresh.tick();
        assertEquals(1, begun.size());
        tick(refresh, ReadyRefresh.QUIET_TICKS);
        assertEquals(1, begun.size(), "a change from before the screen opened started a second pass");
    }
}
