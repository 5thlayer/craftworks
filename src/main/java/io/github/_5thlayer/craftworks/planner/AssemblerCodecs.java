// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.planner;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * How the Assembler queue and its Crafting Plans are written down, so that they survive a logout and
 * reach the client.
 *
 * <p>Free of Minecraft, exactly like what it serialises. {@code Codec} is DataFixerUpper's, not
 * Minecraft's, so the vanilla conveniences that would drag {@code net.minecraft} in here
 * ({@code UUIDUtil.STRING_CODEC}, {@code ExtraCodecs.POSITIVE_INT}) are written out longhand. What that
 * buys is a round trip checked in an ordinary unit test rather than only in a running game, where a
 * broken codec reaches the player as a queue that silently emptied over a logout.
 */
public final class AssemblerCodecs {

    private static final Codec<UUID> UUID_CODEC = Codec.STRING.comapFlatMap(
            text -> {
                try {
                    return DataResult.success(UUID.fromString(text));
                } catch (IllegalArgumentException malformed) {
                    return DataResult.error(() -> "Not a plan id: " + text);
                }
            },
            UUID::toString);

    public static final Codec<ItemAmount> ITEM_AMOUNT = RecordCodecBuilder.create(instance -> instance.group(
                    Codec.STRING.fieldOf("item").forGetter(ItemAmount::item),
                    Codec.intRange(1, Integer.MAX_VALUE).fieldOf("count").forGetter(ItemAmount::count))
            .apply(instance, ItemAmount::new));

    public static final Codec<CraftStep> CRAFT_STEP = RecordCodecBuilder.create(instance -> instance.group(
                    Codec.STRING.fieldOf("recipe").forGetter(CraftStep::recipe),
                    ITEM_AMOUNT.listOf().fieldOf("inputs").forGetter(CraftStep::inputs),
                    ITEM_AMOUNT.listOf().fieldOf("outputs").forGetter(CraftStep::outputs),
                    Codec.INT.fieldOf("time").forGetter(CraftStep::time),
                    Codec.INT.optionalFieldOf("crafts", 1).forGetter(CraftStep::crafts),
                    ITEM_AMOUNT.listOf().optionalFieldOf("remainders", List.of()).forGetter(CraftStep::remainders))
            .apply(instance, CraftStep::new));

    public static final Codec<CraftingPlan> CRAFTING_PLAN = RecordCodecBuilder.create(instance -> instance.group(
                    UUID_CODEC.fieldOf("id").forGetter(CraftingPlan::id),
                    Codec.STRING.fieldOf("root_item").forGetter(CraftingPlan::rootItem),
                    Codec.INT.fieldOf("amount").forGetter(CraftingPlan::amount),
                    ITEM_AMOUNT.listOf().fieldOf("raw_cost").forGetter(CraftingPlan::rawCost),
                    CRAFT_STEP.listOf().fieldOf("steps").forGetter(CraftingPlan::steps))
            .apply(instance, CraftingPlan::new));

    private static final Codec<Map<String, Integer>> BUFFER = Codec.unboundedMap(Codec.STRING, Codec.INT);

    public static final Codec<QueuedPlan> QUEUED_PLAN = RecordCodecBuilder.create(instance -> instance.group(
                    CRAFTING_PLAN.fieldOf("plan").forGetter(QueuedPlan::plan),
                    BUFFER.fieldOf("buffer").forGetter(QueuedPlan::buffer),
                    Codec.INT.fieldOf("step").forGetter(QueuedPlan::stepIndex),
                    Codec.INT.optionalFieldOf("crafts_done", 0).forGetter(QueuedPlan::craftsDone),
                    Codec.INT.fieldOf("progress").forGetter(QueuedPlan::progressTicks))
            .apply(instance, QueuedPlan::restored));

    /**
     * The queue itself.
     *
     * <p>Whether it is paused is not written: that is derived from the next tick, and a queue that
     * paused because the inventory was full must not come back paused into an inventory with room.
     */
    public static final Codec<AssemblerQueue> QUEUE = QUEUED_PLAN.listOf()
            .xmap(AssemblerQueue::of, AssemblerQueue::entries)
            .fieldOf("plans")
            .codec();

    private AssemblerCodecs() {
    }
}
