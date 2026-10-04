// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.planner;

import com.google.gson.JsonElement;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The round trip of the queue and its plans, which is what persistence and sync both rest on.
 *
 * <p>A unit test and not a world load because the codec is DataFixerUpper's rather than Minecraft's.
 * That matters for what the check can catch: a queue whose codec drops a field does not crash, it comes
 * back short, a plan that was paid for and is now simply gone.
 *
 * <p>{@code JsonOps} stands in for NBT. The queue's values are strings, ints and lists, which both
 * dynamic ops carry identically, so what this asserts about one holds for the other.
 */
class AssemblerCodecsTest {

    private static final String SWIFTNESS = "minecraft:potion[minecraft:potion_contents={potion:\"minecraft:swiftness\"}]";
    private static final String HEALING = "minecraft:potion[minecraft:potion_contents={potion:\"minecraft:healing\"}]";

    private static PlanQueue midPlanQueue() {
        CraftStep gear = new CraftStep(
                "gear", List.of(new ItemAmount("iron_plate", 2)), List.of(new ItemAmount("iron_gear", 1)), 40);
        CraftStep belt = new CraftStep(
                "belt",
                List.of(new ItemAmount("iron_gear", 1), new ItemAmount("iron_plate", 1)),
                List.of(new ItemAmount("transport_belt", 2)),
                10);
        CraftingPlan plan = new CraftingPlan(
                UUID.fromString("6f1b1e5e-0000-4000-8000-00000000abcd"),
                "transport_belt",
                2,
                List.of(new ItemAmount("iron_plate", 3)),
                List.of(gear, belt));
        TestPlayerItems items = new TestPlayerItems().with("iron_plate", 3);
        PlanQueue queue = new PlanQueue();
        queue.enqueue(plan, items);
        for (int i = 0; i < 41; i++) queue.tick(items); // the gear is made; the belt is part-way in
        return queue;
    }

    private static <T> T roundTrip(Codec<T> codec, T value) {
        JsonElement written = codec.encodeStart(JsonOps.INSTANCE, value).result().orElseThrow();
        DataResult<Pair<T, JsonElement>> result = codec.decode(JsonOps.INSTANCE, written);
        assertTrue(result.result().isPresent(), () -> "decode failed: " + result.error().orElseThrow().message());
        return result.result().orElseThrow().getFirst();
    }

    @Test
    void aQueueMidPlanSurvivesTheRoundTrip() {
        PlanQueue queue = midPlanQueue();

        assertEquals(queue.entries(), roundTrip(AssemblerCodecs.QUEUE, queue).entries());
    }

    @Test
    void aBatchPartWayThroughKeepsItsCraftCountAcrossTheRoundTrip() {
        CraftStep gears = new CraftStep(
                "gear", List.of(new ItemAmount("iron_plate", 6)), List.of(new ItemAmount("iron_gear", 3)), 10, 3);
        CraftingPlan plan = new CraftingPlan(UUID.fromString("6f1b1e5e-0000-4000-8000-00000000beef"),
                "iron_gear", 3, List.of(new ItemAmount("iron_plate", 6)), List.of(gears));
        TestPlayerItems items = new TestPlayerItems().with("iron_plate", 6);
        PlanQueue queue = new PlanQueue();
        queue.enqueue(plan, items);
        for (int i = 0; i < 15; i++) queue.tick(items); // one gear made, the second half-way

        PlanQueue restored = roundTrip(AssemblerCodecs.QUEUE, queue);

        assertEquals(queue.entries(), restored.entries());
        assertEquals(1, restored.entries().get(0).craftsDone());
    }

    @Test
    void theBufferSurvivesWithIt() {
        PlanQueue restored = roundTrip(AssemblerCodecs.QUEUE, midPlanQueue());

        assertEquals(
                Map.of("iron_gear", 1, "iron_plate", 1),
                restored.entries().get(0).buffer(),
                "the intermediate already made and the unspent cost are both the player's");
        assertEquals(1, restored.entries().get(0).stepIndex());
    }

    @Test
    void aRestoredQueueGoesOnFromWhereItStopped() {
        PlanQueue restored = roundTrip(AssemblerCodecs.QUEUE, midPlanQueue());
        TestPlayerItems items = new TestPlayerItems();

        for (int i = 0; i < 11; i++) restored.tick(items);

        assertEquals(2, items.count("transport_belt"), "the plan finishes on the other side of a logout");
        assertTrue(restored.isEmpty());
    }

    @Test
    void anEmptyQueueRoundTripsToAnEmptyQueue() {
        assertTrue(roundTrip(AssemblerCodecs.QUEUE, new PlanQueue()).isEmpty());
    }

    @Test
    void aCraftingPlanRoundTripsOnItsOwn() {
        // The plan crosses to the client without a queue around it, so its codec is asserted alone.
        CraftingPlan plan = new CraftingPlan(
                UUID.fromString("6f1b1e5e-0000-4000-8000-000000000123"),
                "torch", 8,
                List.of(new ItemAmount("coal", 2), new ItemAmount("oak_planks", 1)),
                List.of(
                        new CraftStep("stick", List.of(new ItemAmount("oak_planks", 1)),
                                List.of(new ItemAmount("stick", 4)), 10),
                        new CraftStep("torch", List.of(new ItemAmount("coal", 2), new ItemAmount("stick", 2)),
                                List.of(new ItemAmount("torch", 8)), 10, 2)));

        assertEquals(plan, roundTrip(AssemblerCodecs.CRAFTING_PLAN, plan));
    }

    @Test
    void aStepsRemaindersSurviveTheRoundTrip() {
        // Dropping them would not fail anything: a cake queued before a logout would simply keep its
        // buckets.
        CraftStep cake = new CraftStep("cake",
                List.of(new ItemAmount("milk_bucket", 3), new ItemAmount("sugar", 2)),
                List.of(new ItemAmount("cake", 1)), 10, 1, List.of(new ItemAmount("bucket", 3)));

        assertEquals(cake, roundTrip(AssemblerCodecs.CRAFT_STEP, cake));
    }

    @Test
    void aQueuedPotionComesBackAsTheSamePotion() {
        // ADR-0002 asks for this one by name. The key carries a data component patch in vanilla's
        // item-argument syntax, quotes, braces and all, and a codec that mangled it would not crash:
        // it would hand back a different potion over a logout.
        CraftStep step = new CraftStep("brew", List.of(new ItemAmount(SWIFTNESS, 2)),
                List.of(new ItemAmount(HEALING, 1)), 40);
        CraftingPlan plan = new CraftingPlan(
                UUID.fromString("6f1b1e5e-0000-4000-8000-0000000fedcb"),
                HEALING, 1, List.of(new ItemAmount(SWIFTNESS, 2)), List.of(step));
        TestPlayerItems items = new TestPlayerItems().with(SWIFTNESS, 2);
        PlanQueue queue = new PlanQueue();
        queue.enqueue(plan, items);

        PlanQueue restored = roundTrip(AssemblerCodecs.QUEUE, queue);

        assertEquals(queue.entries(), restored.entries());
        assertEquals(Map.of(SWIFTNESS, 2), restored.entries().get(0).buffer());
        for (int i = 0; i < 41; i++) restored.tick(items);
        assertEquals(1, items.count(HEALING), "the plan delivers the potion it was queued for");
        assertEquals(0, items.count("minecraft:potion"), "and not a blank one");
    }
}
