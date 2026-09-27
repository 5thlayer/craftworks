// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.assembler;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import io.github._5thlayer.craftworks.Craftworks;
import io.github._5thlayer.craftworks.network.QueueSyncPacket;
import io.github._5thlayer.craftworks.planner.AssemblerCodecs;
import io.github._5thlayer.craftworks.planner.AssemblerQueue;
import io.github._5thlayer.craftworks.planner.ItemAmount;
import io.github._5thlayer.craftworks.planner.Resolver;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * The server's side of the Personal Assembler: what each request actually does.
 *
 * <p>Everything here runs on the server, which is the point. A plan is server truth, so the client
 * never holds one: it holds a view of the queue, and every decision that spends or refunds an item is
 * made on this side of the wire.
 */
public final class PersonalAssembler {

    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, Craftworks.MOD_ID);

    /**
     * The Assembler queue, one per player and saved with them.
     *
     * <p>Not copied on death: death refunds the queue into the inventory before the items drop
     * ({@link AssemblerTicker}), so the queue it would copy is always empty.
     */
    public static final Supplier<AttachmentType<AssemblerQueue>> QUEUE = ATTACHMENTS.register(
            "assembler_queue",
            () -> AttachmentType.builder(AssemblerQueue::new)
                    .serialize(AssemblerCodecs.QUEUE.fieldOf("queue"))
                    .build());

    private PersonalAssembler() {
    }

    public static void register(IEventBus modBus) {
        ATTACHMENTS.register(modBus);
        AssemblerTicker.register();
    }

    public static AssemblerQueue queueOf(Player player) {
        return player.getData(QUEUE);
    }

    /**
     * EMI's {@code + Fill Recipe} on the inventory screen: queue what was asked (ADR-0004).
     *
     * <p>The count is decided here from the Resolver's ceiling, never taken from the client. A request
     * the inventory does not cover, a Missing leaf and a Locked recipe all afford nothing, and queue
     * nothing. Opening the Crafting Plan for them instead is the Crafting Plan screen's (#7).
     */
    public static void fill(ServerPlayer player, Identifier recipe, FillRequest request) {
        int count = request.queueCount(PlanSource.ACTIVE.largestAffordable(player, recipe));
        if (count > 0) craft(player, recipe, count);
    }

    /**
     * Resolves that many crafts and queues them in the same step.
     *
     * <p>Resolved here rather than trusted from the client, because a packet is not a button: the
     * count may be stale or invented. An incomplete plan queues nothing, and so does one whose cost the
     * inventory can no longer cover.
     */
    public static boolean craft(ServerPlayer player, Identifier recipe, int amount) {
        int wanted = Math.min(Math.max(1, amount), Resolver.MAX_CRAFTS);
        PlanSource.ResolvedPlan resolved = PlanSource.ACTIVE.resolve(player, recipe, wanted);
        boolean queued = false;
        if (resolved.complete()) {
            AssemblerQueue queue = queueOf(player);
            queued = queue.enqueue(resolved.plan(), new InventoryPlayerItems(player.getInventory()));
            if (queued) player.setData(QUEUE, queue);
        }
        sync(player);
        return queued;
    }

    /**
     * Cancels {@code crafts} of a row's final recipe and refunds their share (ADR-0005). What is left is
     * re-resolved against the refunded inventory, so the intermediates already made are used again.
     * Anything that will not fit goes to the player the way a closed container's contents do -- a
     * cancel is their own action, unlike a finished craft, which pauses rather than drops.
     */
    public static boolean cancel(ServerPlayer player, UUID planId, int crafts) {
        AssemblerQueue queue = queueOf(player);
        AssemblerQueue.CancelResult result = queue.cancel(planId, crafts,
                new InventoryPlayerItems(player.getInventory()),
                (recipe, left, id) -> {
                    Identifier parsed = Identifier.tryParse(recipe);
                    if (parsed == null) return Optional.empty();
                    return Optional.ofNullable(PlanSource.ACTIVE.resolve(player, parsed, left).plan())
                            .map(plan -> plan.withId(id));
                });
        if (result.cancelled()) {
            for (ItemAmount leftover : result.notReturned()) {
                player.getInventory().placeItemBackInInventory(ItemKeys.toStack(leftover, player.registryAccess()));
            }
            player.setData(QUEUE, queue);
        }
        sync(player);
        return result.cancelled();
    }

    /** One tick of one player's queue, run whether or not any screen is open. */
    public static void tick(ServerPlayer player) {
        AssemblerQueue queue = queueOf(player);
        if (queue.isEmpty()) return;
        queue.tick(new InventoryPlayerItems(player.getInventory()));
        player.setData(QUEUE, queue);
    }

    /**
     * Cancels every row into the inventory, and drops what does not fit where the player stands.
     *
     * <p>Death's, before the inventory drops: what the plans held then follows the normal death rules
     * rather than vanishing with the queue.
     */
    public static void refundAll(ServerPlayer player) {
        AssemblerQueue queue = queueOf(player);
        if (queue.isEmpty()) return;
        List<ItemAmount> notReturned = queue.refundAll(new InventoryPlayerItems(player.getInventory()));
        for (ItemAmount leftover : notReturned) {
            player.drop(ItemKeys.toStack(leftover, player.registryAccess()), true, false);
        }
        player.setData(QUEUE, queue);
        sync(player);
    }

    /** Sends the queue's display view. The plan itself never crosses. */
    public static void sync(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, QueueSyncPacket.of(queueOf(player)));
    }
}
