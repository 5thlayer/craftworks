// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.assembler;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;

import io.github._5thlayer.craftworks.CraftworksConfig;
import io.github._5thlayer.craftworks.api.LockHooks;
import io.github._5thlayer.craftworks.compat.researchd.ResearchdLocks;
import io.github._5thlayer.craftworks.planner.AssemblingRecipe;
import io.github._5thlayer.craftworks.planner.AssemblingRecipeSet;
import io.github._5thlayer.craftworks.planner.CraftingPlan;
import io.github._5thlayer.craftworks.planner.ItemAmount;
import io.github._5thlayer.craftworks.planner.ItemBag;
import io.github._5thlayer.craftworks.planner.Locks;
import io.github._5thlayer.craftworks.planner.PlanPreview;
import io.github._5thlayer.craftworks.planner.Planning;
import io.github._5thlayer.craftworks.planner.Resolver;
import io.github._5thlayer.craftworks.recipe.RuntimeAssemblingRecipes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Planning against one player's stock and Lock source as they are right now, on a running server.
 *
 * <p>Resolving is server-side because a plan is server truth: it reads the player's inventory and the
 * Lock source, and queueing takes the cost off the back of it. A client-held plan would have to be
 * re-validated when queued, which is exactly the re-validation paying up front was meant to remove.
 *
 * <p>It holds no rules. Every decision (what to chain-craft, what is Missing, what "as many as
 * covered" comes to) is the {@link Planning}'s and is unit-tested without Minecraft. This class supplies
 * what that cannot get for itself: the Assembling recipe set, the Lock source and the player's inventory
 * as a multiset, each read once, in {@link #of}.
 *
 * <p>One request makes one, so the Crafting Plan's view, the ceiling and the plan Fill queues share their
 * resolves, and it answers for the inventory as it was then. Anything that changes the inventory ends its
 * use: the next question makes another. The Lock source is asked once per recipe for its lifetime, so a
 * lock that lifts is seen by the next one.
 */
public final class PlayerPlanning {

    /**
     * What the Crafting Plan shows: one craft's {@code display}, and {@code all}, the largest count the
     * inventory covers. Resolved one way for the open and every update so the two cannot drift.
     */
    public record PlanView(PlanDisplay display, int all) {
    }

    private final ServerPlayer player;
    private final AssemblingRecipeSet recipes;
    private final Locks.Reasoned<Component> locks;
    private final Planning planning;

    private PlayerPlanning(ServerPlayer player) {
        this.player = player;
        this.recipes = RuntimeAssemblingRecipes.recipes(player.level());
        this.locks = locksFor(player);
        this.planning = new Resolver(recipes, guarded(locks)).planning(inventoryOf(player));
    }

    /** Reads the player's recipes, Lock source and inventory, now. */
    public static PlayerPlanning of(ServerPlayer player) {
        return new PlayerPlanning(player);
    }

    /**
     * The Crafting Plan of {@code crafts} runs of a recipe, or empty when it is incomplete, which is
     * what queueing refuses on. No display is built.
     */
    public Optional<CraftingPlan> plan(Identifier recipe, int crafts) {
        Resolver.Resolution resolution = planning.plan(recipe.toString(), crafts);
        return resolution.complete()
                ? Optional.of(resolution.toPlan(UUID.randomUUID(), rootItemOf(recipe), crafts)) : Optional.empty();
    }

    /**
     * The largest count whose complete plan the inventory covers: Shift's "as many as covered", so it
     * can never queue a plan the inventory then refuses (ADR-0003).
     */
    public int largestAffordable(Identifier recipe) {
        return planning.largestAffordable(recipe.toString());
    }

    /** One craft as the Crafting Plan shows it, with the ceiling beside it. */
    public PlanView view(Identifier recipe) {
        return view(recipe, 1);
    }

    /** As {@link #view(Identifier)}, showing {@code crafts} runs: what a plan for that count would show. */
    public PlanView view(Identifier recipe, int crafts) {
        Resolver.Resolution resolution = planning.plan(recipe.toString(), crafts);
        // A Data-carrying recipe's result is shown as the original would make it from the stacks the plan takes.
        List<ItemAmount> toCraft = resolution.toCraft();
        if (resolution.complete()) {
            CraftingPlan plan = resolution.toPlan(UUID.randomUUID(), rootItemOf(recipe), crafts);
            toCraft = PlanPreview.toCraft(plan, toCraft, PersonalAssembler.madeBy(player));
        }
        PlanDisplay display = new PlanDisplay(recipe, resolution.rawCost(), toCraft,
                resolution.missing(), resolution.locked(),
                resolution.lockReasons(locks.reasons()), resolution.complete());
        return new PlanView(display, largestAffordable(recipe));
    }

    /** The planning itself, for the Ready pass, which asks it about every recipe and keeps it across ticks. */
    Planning planning() {
        return planning;
    }

    /** Every Assembling recipe id: the Ready pass's candidates. */
    Collection<String> recipeIds() {
        return recipes.ids();
    }

    /** The Lock source for one player, as the Resolver asks it: by recipe id. An id that does not parse is Locked. */
    public static Predicate<String> lockedFor(ServerPlayer player) {
        return guarded(locksFor(player));
    }

    private static Predicate<String> guarded(Predicate<String> locked) {
        return recipe -> Identifier.tryParse(recipe) == null || locked.test(recipe);
    }

    /**
     * The Lock source for one player with the reasons it gives, by recipe id: the configured
     * {@code lockSources}, every {@link LockHooks.LockHook} and every {@link LockHooks.ReasonedLockHook}.
     */
    public static Locks.Reasoned<Component> locksFor(ServerPlayer player) {
        List<Predicate<String>> sources = new ArrayList<>();
        List<Function<String, Optional<Locks.Lock<Component>>>> reasoning = new ArrayList<>();
        for (CraftworksConfig.LockSource source : CraftworksConfig.lockSources()) {
            switch (source) {
                case recipeBook -> sources.add(recipe -> !player.getRecipeBook()
                        .contains(ResourceKey.create(Registries.RECIPE, Identifier.parse(recipe))));
                case researchd -> reasoning.add(reasoned(recipe -> ResearchdLocks.lock(player, recipe)));
            }
        }
        List<Predicate<String>> hooks = new ArrayList<>();
        for (LockHooks.LockHook hook : LockHooks.all()) {
            hooks.add(recipe -> hook.isLocked(player, Identifier.parse(recipe)));
        }
        for (LockHooks.ReasonedLockHook hook : LockHooks.allReasoned()) {
            reasoning.add(reasoned(recipe -> hook.lock(player, recipe)));
        }
        return Locks.reasoned(sources, hooks, reasoning);
    }

    private static Function<String, Optional<Locks.Lock<Component>>> reasoned(
            Function<Identifier, Optional<LockHooks.Lock>> lock) {
        return recipe -> lock.apply(Identifier.parse(recipe)).map(answer -> new Locks.Lock<>(answer.reason()));
    }

    /**
     * What the plan is for, named for the queue's row.
     *
     * <p>Read back off the recipe set rather than carried through the Planning: it plans by
     * recipe and a recipe knows its own result, so passing the item alongside would be two facts that
     * can disagree.
     */
    private String rootItemOf(Identifier recipe) {
        AssemblingRecipe root = recipes.byId(recipe.toString());
        return root == null ? recipe.toString() : root.result().item();
    }

    /**
     * The player's stock as the Resolver counts it: the same thirty-six slots
     * {@link InventoryPlayerItems} will take the cost from.
     *
     * <p>They have to be the same slots. A plan resolved against armour the queue then cannot spend
     * would resolve complete and then be refused when queued.
     */
    private static ItemBag inventoryOf(ServerPlayer player) {
        ItemBag bag = new ItemBag();
        for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
            if (stack.isEmpty()) continue;
            // A stack the key format cannot name is stock nothing can plan against, so it is not stock.
            String key = ItemKeys.of(stack, player.registryAccess());
            if (key != null) bag.add(key, stack.getCount());
        }
        return bag;
    }
}
