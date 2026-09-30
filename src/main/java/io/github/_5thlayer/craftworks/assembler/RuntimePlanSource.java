// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.assembler;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;

import io.github._5thlayer.craftworks.CraftworksConfig;
import io.github._5thlayer.craftworks.api.LockHooks;
import io.github._5thlayer.craftworks.compat.researchd.ResearchdLocks;
import io.github._5thlayer.craftworks.planner.AssemblingRecipe;
import io.github._5thlayer.craftworks.planner.AssemblingRecipeSet;
import io.github._5thlayer.craftworks.planner.ItemAmount;
import io.github._5thlayer.craftworks.planner.ItemBag;
import io.github._5thlayer.craftworks.planner.Locks;
import io.github._5thlayer.craftworks.planner.Resolver;
import io.github._5thlayer.craftworks.recipe.RuntimeAssemblingRecipes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * The Resolver, wired to a running server.
 *
 * <p>It holds no rules. Every decision (what to chain-craft, what is Missing, what "as many as
 * covered" comes to) is the {@link Resolver}'s and is unit-tested without Minecraft. This class supplies
 * what the Resolver cannot get for itself: the Assembling recipe set and the player's inventory as a
 * multiset.
 *
 * <p>What is Locked is asked of the Lock source afresh on every resolve: the configured
 * {@code lockSources} and every {@link LockHooks} hook, for this player.
 */
public final class RuntimePlanSource implements PlanSource {

    @Override
    public ResolvedPlan resolve(ServerPlayer player, Identifier recipe, int crafts) {
        Locks.Reasoned<Component> locks = locksFor(player);
        Resolver.Resolution resolution = new Resolver(RuntimeAssemblingRecipes.recipes(player.level()), guarded(locks))
                .resolve(recipe.toString(), crafts, inventoryOf(player));
        PlanDisplay display = new PlanDisplay(recipe, resolution.rawCost(), resolution.toCraft(),
                resolution.missing(), resolution.locked(),
                reasonsByItem(player, recipe, resolution.locked(), locks.reasons()), resolution.complete());
        if (!resolution.complete()) return new ResolvedPlan(display, null);
        return new ResolvedPlan(display, resolution.toPlan(UUID.randomUUID(), rootItemOf(player, recipe), crafts));
    }

    @Override
    public int largestAffordable(ServerPlayer player, Identifier recipe) {
        return resolverFor(player).largestAffordable(recipe.toString(), inventoryOf(player));
    }

    private static Resolver resolverFor(ServerPlayer player) {
        return new Resolver(RuntimeAssemblingRecipes.recipes(player.level()), lockedFor(player));
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
     * Each Locked item's reason, by the recipe the Resolver found Locked for it: the root when the root
     * is Locked, otherwise the item's first route, which is the one the Resolver asks.
     */
    private static Map<String, Component> reasonsByItem(ServerPlayer player, Identifier recipe,
            List<ItemAmount> locked, Map<String, Component> reasons) {
        Map<String, Component> byItem = new LinkedHashMap<>();
        if (reasons.isEmpty()) return byItem;
        AssemblingRecipeSet recipes = RuntimeAssemblingRecipes.recipes(player.level());
        AssemblingRecipe root = recipes.byId(recipe.toString());
        for (ItemAmount amount : locked) {
            String item = amount.item();
            Component reason = root != null && root.result().item().equals(item) ? reasons.get(root.id()) : null;
            if (reason == null) {
                List<AssemblingRecipe> routes = recipes.routes(item);
                if (!routes.isEmpty()) reason = reasons.get(routes.get(0).id());
            }
            if (reason != null) byItem.put(item, reason);
        }
        return byItem;
    }

    /**
     * What the plan is for, named for the queue's row.
     *
     * <p>Read back off the recipe set rather than carried through the Resolver: the Resolver plans by
     * recipe and a recipe knows its own result, so passing the item alongside would be two facts that
     * can disagree.
     */
    private static String rootItemOf(ServerPlayer player, Identifier recipe) {
        AssemblingRecipe root = RuntimeAssemblingRecipes.recipes(player.level()).byId(recipe.toString());
        return root == null ? recipe.toString() : root.result().item();
    }

    /**
     * The player's stock as the Resolver counts it: the same thirty-six slots
     * {@link InventoryPlayerItems} will take the cost from.
     *
     * <p>They have to be the same slots. A plan resolved against armour the queue then cannot spend
     * would resolve complete and then be refused when queued.
     */
    static ItemBag inventoryOf(ServerPlayer player) {
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
