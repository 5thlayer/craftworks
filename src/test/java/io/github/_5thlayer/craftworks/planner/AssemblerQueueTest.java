// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.planner;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Assembler queue (ADR-0001).
 *
 * <p>Everything the ADR decided about a running plan is asserted here rather than in a world: the
 * whole cost is taken when the plan is queued, the plan is the unit of cancelling, and a craft that
 * will not fit pauses the queue instead of dropping on the ground.
 */
class AssemblerQueueTest {

    private static final String IRON = "iron_plate";
    private static final String GEAR = "iron_gear";
    private static final String BELT = "transport_belt";

    /** One gear: two plates in, one gear out. */
    private static CraftStep gearStep() {
        return new CraftStep("gear", List.of(new ItemAmount(IRON, 2)), List.of(new ItemAmount(GEAR, 1)), 2);
    }

    /** One belt: a gear and a plate in, two belts out, Factorio's own numbers. */
    private static CraftStep beltStep() {
        return new CraftStep(
                "belt",
                List.of(new ItemAmount(GEAR, 1), new ItemAmount(IRON, 1)),
                List.of(new ItemAmount(BELT, 2)),
                2);
    }

    /** A two-step chain: make the gear, then the belt. Raw cost is three plates. */
    private static CraftingPlan beltPlan() {
        return new CraftingPlan(
                UUID.nameUUIDFromBytes("belt".getBytes()),
                BELT,
                2,
                List.of(new ItemAmount(IRON, 3)),
                List.of(gearStep(), beltStep()));
    }

    private static CraftingPlan gearPlan() {
        return new CraftingPlan(
                UUID.nameUUIDFromBytes("gear".getBytes()),
                GEAR,
                1,
                List.of(new ItemAmount(IRON, 2)),
                List.of(gearStep()));
    }

    private static void tick(AssemblerQueue queue, PlayerItems items, int times) {
        for (int i = 0; i < times; i++) queue.tick(items);
    }

    @Test
    void queueingTakesTheWholeRawCostAtOnce() {
        TestPlayerItems items = new TestPlayerItems().with(IRON, 10);
        AssemblerQueue queue = new AssemblerQueue();

        assertTrue(queue.enqueue(beltPlan(), items));

        assertEquals(7, items.count(IRON), "the plan's whole raw cost leaves the player's items when it is queued");
        assertEquals(1, queue.entries().size());
    }

    @Test
    void queueingIsRefusedWhenTheItemsCannotCoverTheCost() {
        TestPlayerItems items = new TestPlayerItems().with(IRON, 2);
        AssemblerQueue queue = new AssemblerQueue();

        assertFalse(queue.enqueue(beltPlan(), items));

        assertEquals(2, items.count(IRON), "a refused plan takes nothing");
        assertTrue(queue.isEmpty());
    }

    @Test
    void aChainRunsItsStepsInOrderAndDeliversOnlyTheRoot() {
        TestPlayerItems items = new TestPlayerItems().with(IRON, 3);
        AssemblerQueue queue = new AssemblerQueue();
        queue.enqueue(beltPlan(), items);

        tick(queue, items, 2);
        assertEquals(0, items.count(GEAR), "the intermediate is the next step's input, not a delivery");

        tick(queue, items, 2);
        assertEquals(2, items.count(BELT));
        assertEquals(0, items.count(GEAR));

        queue.tick(items);
        assertTrue(queue.isEmpty(), "a plan whose last step delivered leaves the queue");
    }

    @Test
    void surplusFromAnIntermediateIsDelivered() {
        // The gear step makes two, the belt step wants one: the spare is the player's.
        CraftStep pairOfGears =
                new CraftStep("gear", List.of(new ItemAmount(IRON, 2)), List.of(new ItemAmount(GEAR, 2)), 1);
        CraftingPlan plan = new CraftingPlan(
                UUID.randomUUID(), BELT, 2, List.of(new ItemAmount(IRON, 3)), List.of(pairOfGears, beltStep()));
        TestPlayerItems items = new TestPlayerItems().with(IRON, 3);
        AssemblerQueue queue = new AssemblerQueue();
        queue.enqueue(plan, items);

        queue.tick(items);

        assertEquals(1, items.count(GEAR), "what no remaining step needs goes to the player at once");
    }

    /** Three gears as one step: six plates in, three gears out, two ticks a gear. */
    private static CraftingPlan threeGearPlan() {
        return new CraftingPlan(
                UUID.nameUUIDFromBytes("three gears".getBytes()),
                GEAR,
                3,
                List.of(new ItemAmount(IRON, 6)),
                List.of(new CraftStep("gear", List.of(new ItemAmount(IRON, 6)), List.of(new ItemAmount(GEAR, 3)), 2, 3)));
    }

    @Test
    void eachCraftOfABatchDeliversWhenItsOwnDurationElapses() {
        TestPlayerItems items = new TestPlayerItems().with(IRON, 6);
        AssemblerQueue queue = new AssemblerQueue();
        queue.enqueue(threeGearPlan(), items);

        tick(queue, items, 2);
        assertEquals(1, items.count(GEAR), "the first gear arrives after one craft's time, not three");

        tick(queue, items, 2);
        assertEquals(2, items.count(GEAR));

        tick(queue, items, 2);
        assertEquals(3, items.count(GEAR));
        assertTrue(queue.isEmpty());
    }

    @Test
    void theProgressBarIsTheCurrentCraftsNotTheBatchs() {
        TestPlayerItems items = new TestPlayerItems().with(IRON, 6);
        AssemblerQueue queue = new AssemblerQueue();
        queue.enqueue(threeGearPlan(), items);

        tick(queue, items, 3); // one gear done, the second half-way
        assertEquals(0.5f, queue.entries().get(0).progress());
    }

    @Test
    void cancellingMidBatchRefundsTheInputsOfTheCraftsNotYetMade() {
        TestPlayerItems items = new TestPlayerItems().with(IRON, 6);
        AssemblerQueue queue = new AssemblerQueue();
        CraftingPlan plan = threeGearPlan();
        queue.enqueue(plan, items);
        tick(queue, items, 2);

        queue.cancel(plan.id(), items);

        assertEquals(1, items.count(GEAR));
        assertEquals(4, items.count(IRON), "two crafts unmade, two plates each");
    }

    @Test
    void anInputThatDoesNotDivideEvenlyIsStillSpentExactlyOnce() {
        // Two plates of one kind and one of another over two crafts: a tag ingredient drawn from two
        // items. Each craft takes its share, and the batch as a whole takes exactly what it reserved.
        String copper = "copper_plate";
        CraftStep step = new CraftStep("mix",
                List.of(new ItemAmount(IRON, 3), new ItemAmount(copper, 1)), List.of(new ItemAmount(GEAR, 2)), 1, 2);
        CraftingPlan plan = new CraftingPlan(UUID.nameUUIDFromBytes("mix".getBytes()), GEAR, 2,
                List.of(new ItemAmount(IRON, 3), new ItemAmount(copper, 1)), List.of(step));
        TestPlayerItems items = new TestPlayerItems().with(IRON, 3).with(copper, 1);
        AssemblerQueue queue = new AssemblerQueue();
        queue.enqueue(plan, items);

        tick(queue, items, 3);

        assertEquals(2, items.count(GEAR));
        assertEquals(0, items.count(IRON));
        assertEquals(0, items.count(copper));
        assertTrue(queue.isEmpty());
    }

    private static CraftingPlan gearPlan(String name, int gears) {
        return new CraftingPlan(
                UUID.nameUUIDFromBytes(name.getBytes()),
                GEAR,
                gears,
                List.of(new ItemAmount(IRON, 2 * gears)),
                List.of(new CraftStep("gear", List.of(new ItemAmount(IRON, 2 * gears)),
                        List.of(new ItemAmount(GEAR, gears)), 2, gears)));
    }

    @Test
    void queueingTheSameItemAgainGrowsTheLastRowInsteadOfAddingOne() {
        TestPlayerItems items = new TestPlayerItems().with(IRON, 12);
        AssemblerQueue queue = new AssemblerQueue();
        CraftingPlan five = gearPlan("five", 5);
        queue.enqueue(five, items);

        queue.enqueue(gearPlan("one", 1), items);

        assertEquals(1, queue.entries().size());
        assertEquals(6, queue.entries().get(0).plan().amount());
        assertEquals(five.id(), queue.entries().get(0).plan().id(), "the row keeps its id, so its cancel still reaches it");
        assertEquals(0, items.count(IRON), "both reservations are taken");
    }

    @Test
    void aMergedRowCraftsAndDeliversEverythingBothPlansAskedFor() {
        TestPlayerItems items = new TestPlayerItems().with(IRON, 12);
        AssemblerQueue queue = new AssemblerQueue();
        queue.enqueue(gearPlan("five", 5), items);
        tick(queue, items, 3); // part-way into the second gear

        queue.enqueue(gearPlan("one", 1), items);
        tick(queue, items, 20);

        assertEquals(6, items.count(GEAR));
        assertTrue(queue.isEmpty());
    }

    @Test
    void cancellingAMergedRowRefundsWhatIsLeftOfBoth() {
        TestPlayerItems items = new TestPlayerItems().with(IRON, 12);
        AssemblerQueue queue = new AssemblerQueue();
        CraftingPlan five = gearPlan("five", 5);
        queue.enqueue(five, items);
        queue.enqueue(gearPlan("one", 1), items);
        tick(queue, items, 2); // one gear made

        queue.cancel(five.id(), items);

        assertEquals(1, items.count(GEAR));
        assertEquals(10, items.count(IRON));
        assertTrue(queue.isEmpty());
    }

    @Test
    void onlyTheLastRowGrowsSoTheOrderThePlayerQueuedInIsKept() {
        TestPlayerItems items = new TestPlayerItems().with(IRON, 20);
        AssemblerQueue queue = new AssemblerQueue();
        queue.enqueue(gearPlan("gears", 2), items);
        queue.enqueue(beltPlan(), items);

        queue.enqueue(gearPlan("more gears", 1), items);

        assertEquals(3, queue.entries().size(), "a gear behind a belt is a new row, not a jump ahead of it");
    }

    @Test
    void theRowCountsDownAsEachCraftFinishes() {
        TestPlayerItems items = new TestPlayerItems().with(IRON, 24);
        AssemblerQueue queue = new AssemblerQueue();
        queue.enqueue(gearPlan("twelve", 12), items);
        QueuedPlan row = queue.entries().get(0);
        assertEquals(12, row.remainingAmount());
        assertEquals(12, row.remainingStepAmount());

        tick(queue, items, 2);
        row = queue.entries().get(0);
        assertEquals(11, row.remainingAmount(), "x12 -> x11 after one craft");
        assertEquals(11, row.remainingStepAmount());

        tick(queue, items, 4);
        assertEquals(9, queue.entries().get(0).remainingAmount());
    }

    @Test
    void aMergedRowCountsDownAcrossBothPlans() {
        TestPlayerItems items = new TestPlayerItems().with(IRON, 12);
        AssemblerQueue queue = new AssemblerQueue();
        queue.enqueue(gearPlan("five", 5), items);
        queue.enqueue(gearPlan("one", 1), items);

        tick(queue, items, 10); // the five are made; the one is not started
        QueuedPlan row = queue.entries().get(0);
        assertEquals(1, row.remainingAmount());
        assertEquals(1, row.remainingStepAmount());
    }

    @Test
    void anIntermediateStepDoesNotCountDownTheRow() {
        TestPlayerItems items = new TestPlayerItems().with(IRON, 3);
        AssemblerQueue queue = new AssemblerQueue();
        queue.enqueue(beltPlan(), items);

        tick(queue, items, 2); // the gear is made, the belt is not
        assertEquals(2, queue.entries().get(0).remainingAmount(), "no belt exists yet");
    }

    @Test
    void cancellingRefundsTheRemainingReservationAndTheIntermediatesAlreadyMade() {
        TestPlayerItems items = new TestPlayerItems().with(IRON, 3);
        AssemblerQueue queue = new AssemblerQueue();
        CraftingPlan plan = beltPlan();
        queue.enqueue(plan, items);
        tick(queue, items, 2); // the gear is made and held; one plate of the reservation is unspent

        AssemblerQueue.CancelResult result = queue.cancel(plan.id(), items);

        assertTrue(result.cancelled());
        assertTrue(result.notReturned().isEmpty());
        assertEquals(1, items.count(IRON), "the unspent reservation comes back");
        assertEquals(1, items.count(GEAR), "so does the intermediate already produced");
        assertTrue(queue.isEmpty());
    }

    @Test
    void cancellingAPlanThatHasNotStartedRefundsItsWholeCost() {
        TestPlayerItems items = new TestPlayerItems().with(IRON, 5);
        AssemblerQueue queue = new AssemblerQueue();
        CraftingPlan first = beltPlan();
        CraftingPlan second = gearPlan();
        queue.enqueue(first, items);
        queue.enqueue(second, items);
        assertEquals(0, items.count(IRON));

        AssemblerQueue.CancelResult result = queue.cancel(second.id(), items);

        assertTrue(result.cancelled());
        assertEquals(2, items.count(IRON));
        assertEquals(List.of(first.id()), queue.entries().stream().map(e -> e.plan().id()).toList());
    }

    @Test
    void refundingEverythingEmptiesEveryRowIntoThePlayersItems() {
        // What death does: the plans are refunded before the items drop, so a paid-for cost follows
        // the normal death rules rather than vanishing.
        TestPlayerItems items = new TestPlayerItems().with(IRON, 5);
        AssemblerQueue queue = new AssemblerQueue();
        queue.enqueue(beltPlan(), items);
        queue.enqueue(gearPlan(), items);
        tick(queue, items, 2); // the belt's gear is made and held

        List<ItemAmount> notReturned = queue.refundAll(items);

        assertTrue(notReturned.isEmpty());
        assertTrue(queue.isEmpty());
        assertEquals(Map.of(IRON, 3, GEAR, 1), items.contents());
    }

    @Test
    void aRefundThatDoesNotFitSaysWhatWasLeftOver() {
        TestPlayerItems items = new TestPlayerItems(1, 64).with(IRON, 2);
        AssemblerQueue queue = new AssemblerQueue();
        queue.enqueue(gearPlan(), items);
        items.give("stone", 64);

        List<ItemAmount> notReturned = queue.refundAll(items);

        assertEquals(List.of(new ItemAmount(IRON, 2)), notReturned, "the caller drops it; the queue must not lose it");
        assertTrue(queue.isEmpty());
        assertFalse(queue.isBlocked());
    }

    @Test
    void cancellingAnUnknownPlanChangesNothing() {
        TestPlayerItems items = new TestPlayerItems().with(IRON, 3);
        AssemblerQueue queue = new AssemblerQueue();
        queue.enqueue(beltPlan(), items);

        AssemblerQueue.CancelResult result = queue.cancel(UUID.randomUUID(), items);

        assertFalse(result.cancelled());
        assertEquals(1, queue.entries().size());
        assertEquals(0, items.count(IRON));
    }

    @Test
    void aCraftThatWillNotFitPausesTheHeadAndDropsNothing() {
        // One slot, and the reservation vacates it, so the finished gear has nowhere to land.
        TestPlayerItems items = new TestPlayerItems(1, 64).with(IRON, 2);
        AssemblerQueue queue = new AssemblerQueue();
        queue.enqueue(gearPlan(), items);
        items.give("stone", 64); // the freed slot fills up while the craft runs

        tick(queue, items, 5);

        assertTrue(queue.isBlocked(), "the head pauses rather than dropping the craft");
        assertEquals(0, items.count(GEAR));
        assertEquals(1, queue.entries().size(), "and the plan stays on the queue, holding the gear");
    }

    @Test
    void aPausedHeadStopsTheWholeQueue() {
        TestPlayerItems items = new TestPlayerItems(1, 64).with(IRON, 4);
        AssemblerQueue queue = new AssemblerQueue();
        CraftingPlan first = gearPlan();
        // A different item, so it is a second row rather than more of the first.
        CraftingPlan second = new CraftingPlan(
                UUID.randomUUID(), "iron_stick", 1, List.of(new ItemAmount(IRON, 2)),
                List.of(new CraftStep("stick", List.of(new ItemAmount(IRON, 2)), List.of(new ItemAmount("iron_stick", 1)), 2)));
        queue.enqueue(first, items);
        queue.enqueue(second, items);
        items.give("stone", 64);

        tick(queue, items, 20);

        assertEquals(0, items.count(GEAR));
        assertEquals(2, queue.entries().size(), "a blocked head does not step aside");
        assertEquals(0, queue.entries().get(1).stepIndex());
        assertEquals(0, queue.entries().get(1).progressTicks(), "the plan behind it never starts");
    }

    @Test
    void theHeadResumesWhenRoomAppears() {
        TestPlayerItems items = new TestPlayerItems(1, 64).with(IRON, 2);
        AssemblerQueue queue = new AssemblerQueue();
        queue.enqueue(gearPlan(), items);
        items.give("stone", 64);
        tick(queue, items, 5);
        assertTrue(queue.isBlocked());

        items.take("stone", 64);
        queue.tick(items);

        assertEquals(1, items.count(GEAR));
        assertFalse(queue.isBlocked());
    }

    @Test
    void aQueueRoundTripsThroughItsEntries() {
        TestPlayerItems items = new TestPlayerItems().with(IRON, 3);
        AssemblerQueue queue = new AssemblerQueue();
        queue.enqueue(beltPlan(), items);
        tick(queue, items, 3); // mid-plan: one step done, the next part-way through

        AssemblerQueue restored = AssemblerQueue.of(queue.entries());

        assertEquals(queue.entries(), restored.entries());
        assertEquals(1, restored.entries().get(0).stepIndex());
        assertEquals(Map.of(GEAR, 1, IRON, 1), restored.entries().get(0).buffer());

        // And it goes on running from where it stopped, rather than from the start.
        tick(restored, items, 1);
        assertEquals(2, items.count(BELT));
    }

    @Test
    void aStepTheReservationCannotFeedFailsLoudly() {
        // A plan whose raw cost does not cover its own first step: only a resolver can produce this,
        // and the alternative to throwing is crafting a gear out of nothing.
        CraftingPlan underpaid = new CraftingPlan(
                UUID.randomUUID(), GEAR, 1, List.of(new ItemAmount(IRON, 1)), List.of(gearStep()));
        TestPlayerItems items = new TestPlayerItems().with(IRON, 1);
        AssemblerQueue queue = new AssemblerQueue();
        queue.enqueue(underpaid, items);

        assertThrows(IllegalStateException.class, () -> tick(queue, items, 2));
    }

    @Test
    void anEmptyQueueIsNeitherBlockedNorBusy() {
        AssemblerQueue queue = new AssemblerQueue();
        TestPlayerItems items = new TestPlayerItems();

        queue.tick(items);

        assertTrue(queue.isEmpty());
        assertFalse(queue.isBlocked());
        assertNotNull(queue.head());
        assertTrue(queue.head().isEmpty());
    }
    @Test
    void aPlanTakesAndDeliversTheExactPotionItNames() {
        // ADR-0002: the queue counts keys, and two potions share a registry id. A cost taken by id
        // would spend the wrong potion, and a delivery by id would hand back a blank one: a plan that
        // resolved and ran correctly and produced the wrong item.
        String red = "minecraft:potion[minecraft:potion_contents={potion:\"minecraft:swiftness\"}]";
        String green = "minecraft:potion[minecraft:potion_contents={potion:\"minecraft:healing\"}]";
        CraftStep step = new CraftStep("lab", List.of(new ItemAmount(red, 2)),
                List.of(new ItemAmount(green, 1)), 2);
        CraftingPlan plan = new CraftingPlan(
                UUID.randomUUID(), green, 1, List.of(new ItemAmount(red, 2)), List.of(step));
        TestPlayerItems items = new TestPlayerItems().with(red, 2).with(green, 5);
        AssemblerQueue queue = new AssemblerQueue();

        queue.enqueue(plan, items);
        assertEquals(Map.of(green, 5), items.contents(), "only the potion the plan names is taken");

        tick(queue, items, 2);

        assertEquals(Map.of(green, 6), items.contents());
        assertTrue(queue.isEmpty());
    }

    /** Re-resolves a gear row the way the server's resolver would, under the row's own id. */
    private static final AssemblerQueue.Replanner GEARS = (recipe, crafts, id) -> Optional.of(
            new CraftingPlan(id, GEAR, crafts, List.of(new ItemAmount(IRON, 2 * crafts)),
                    List.of(new CraftStep(recipe, List.of(new ItemAmount(IRON, 2 * crafts)),
                            List.of(new ItemAmount(GEAR, crafts)), 2, crafts))));

    @Test
    void cancellingOneOfARowRefundsOneCraftAndKeepsTheRest() {
        TestPlayerItems items = new TestPlayerItems().with(IRON, 10);
        AssemblerQueue queue = new AssemblerQueue();
        CraftingPlan five = gearPlan("five", 5);
        queue.enqueue(five, items);

        queue.cancel(five.id(), 1, items, GEARS);

        assertEquals(2, items.count(IRON));
        assertEquals(1, queue.entries().size());
        assertEquals(4, queue.entries().get(0).remainingAmount());
        assertEquals(five.id(), queue.entries().get(0).plan().id(), "the row keeps its id");
    }

    @Test
    void cancellingMoreThanIsLeftCancelsWhatIsThere() {
        TestPlayerItems items = new TestPlayerItems().with(IRON, 6);
        AssemblerQueue queue = new AssemblerQueue();
        CraftingPlan three = gearPlan("three", 3);
        queue.enqueue(three, items);
        tick(queue, items, 2); // one gear made

        queue.cancel(three.id(), 5, items, GEARS);

        assertTrue(queue.isEmpty());
        assertEquals(1, items.count(GEAR));
        assertEquals(4, items.count(IRON));
    }

    @Test
    void aPartlyCancelledRowKeepsItsPlaceInTheQueue() {
        TestPlayerItems items = new TestPlayerItems().with(IRON, 20);
        AssemblerQueue queue = new AssemblerQueue();
        queue.enqueue(beltPlan(), items);
        CraftingPlan gears = gearPlan("gears", 3);
        queue.enqueue(gears, items);

        queue.cancel(gears.id(), 1, items, GEARS);

        assertEquals(2, queue.entries().size());
        assertEquals(gears.id(), queue.entries().get(1).plan().id());
        assertEquals(2, queue.entries().get(1).remainingAmount());
    }

    @Test
    void theCraftUnderWayKeepsItsProgressWhenLaterCraftsAreCancelled() {
        TestPlayerItems items = new TestPlayerItems().with(IRON, 10);
        AssemblerQueue queue = new AssemblerQueue();
        CraftingPlan five = gearPlan("five", 5);
        queue.enqueue(five, items);
        tick(queue, items, 1);

        queue.cancel(five.id(), 1, items, GEARS);

        assertEquals(1, queue.entries().get(0).progressTicks());
    }

    @Test
    void aRowThatCannotBeReplannedIsLeftAsItWas() {
        TestPlayerItems items = new TestPlayerItems().with(IRON, 10);
        AssemblerQueue queue = new AssemblerQueue();
        CraftingPlan five = gearPlan("five", 5);
        queue.enqueue(five, items);
        tick(queue, items, 1);
        QueuedPlan before = queue.entries().get(0);

        AssemblerQueue.CancelResult result = queue.cancel(five.id(), 1, items, (recipe, crafts, id) -> Optional.empty());

        assertFalse(result.cancelled());
        assertEquals(List.of(before), queue.entries());
        assertEquals(0, items.count(IRON));
    }

    @Test
    void aCountOfZeroCancelsNothing() {
        TestPlayerItems items = new TestPlayerItems().with(IRON, 10);
        AssemblerQueue queue = new AssemblerQueue();
        CraftingPlan five = gearPlan("five", 5);
        queue.enqueue(five, items);
        tick(queue, items, 1);

        assertFalse(queue.cancel(five.id(), 0, items, GEARS).cancelled());
        assertEquals(1, queue.entries().get(0).progressTicks());
    }

    @Test
    void cancellingPartOfAMergedRowKeepsTheRestAsOneRow() {
        TestPlayerItems items = new TestPlayerItems().with(IRON, 12);
        AssemblerQueue queue = new AssemblerQueue();
        CraftingPlan five = gearPlan("five", 5);
        queue.enqueue(five, items);
        queue.enqueue(gearPlan("one", 1), items);
        tick(queue, items, 2); // one gear made

        queue.cancel(five.id(), 2, items, GEARS);

        assertEquals(1, items.count(GEAR));
        assertEquals(4, items.count(IRON), "two unmade gears, two plates each");
        assertEquals(1, queue.entries().size());
        assertEquals(3, queue.entries().get(0).remainingAmount());
    }

    @Test
    void aPartialCancelReusesTheIntermediatesAlreadyMade() {
        // Two belt crafts: the gear for the first is made, then one belt craft is cancelled. The rest is
        // planned again against the refund, which holds that gear, so no second gear is made.
        CraftStep gears = new CraftStep("gear", List.of(new ItemAmount(IRON, 4)), List.of(new ItemAmount(GEAR, 2)), 2, 2);
        CraftStep belts = new CraftStep("belt", List.of(new ItemAmount(GEAR, 2), new ItemAmount(IRON, 2)),
                List.of(new ItemAmount(BELT, 4)), 2, 2);
        CraftingPlan two = new CraftingPlan(UUID.nameUUIDFromBytes("belts".getBytes()), BELT, 4,
                List.of(new ItemAmount(IRON, 6)), List.of(gears, belts));
        TestPlayerItems items = new TestPlayerItems().with(IRON, 6);
        AssemblerQueue queue = new AssemblerQueue();
        queue.enqueue(two, items);
        tick(queue, items, 2); // one gear made
        AssemblerQueue.Replanner fromHeldGear = (recipe, crafts, id) -> Optional.of(
                new CraftingPlan(id, BELT, 2, List.of(new ItemAmount(GEAR, 1), new ItemAmount(IRON, 1)),
                        List.of(new CraftStep(recipe, List.of(new ItemAmount(GEAR, 1), new ItemAmount(IRON, 1)),
                                List.of(new ItemAmount(BELT, 2)), 2, crafts))));

        queue.cancel(two.id(), 1, items, fromHeldGear);

        assertEquals(0, items.count(GEAR), "the made gear went back into the row");
        assertEquals(3, items.count(IRON), "six reserved, two spent on the gear, one re-taken");
        assertEquals(2, queue.entries().get(0).remainingAmount());
    }
}
