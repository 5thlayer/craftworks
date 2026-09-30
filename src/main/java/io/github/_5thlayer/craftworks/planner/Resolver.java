// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.planner;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Turns "N crafts of this recipe" into a Crafting Plan: the flattened tree, in craft order, with what
 * it costs, what is Missing and what is Locked (ADR-0001).
 *
 * <p>Chain-crafting is the whole job. Requesting a recipe whose ingredients the player lacks plans the
 * sub-crafts, recursively, and what the walk cannot make it reports rather than silently dropping: as
 * Missing when nothing makes it, as Locked when something does and the Lock source says no. The two
 * are separate because they send the player on different errands.
 *
 * <p>No Minecraft type appears here, which is what makes the recursion checkable without a game.
 * {@link AssemblingRecipeSet} is the seam on the recipe side, {@link ItemBag} on the inventory side
 * and a predicate over recipe ids on the lock side.
 *
 * <p><b>Crafts of one recipe share one step.</b> Twenty gears is a single {@link CraftStep} with
 * twenty times the inputs and outputs and a count of twenty, not twenty steps: the plan is persisted
 * on the player and a large plan would otherwise be thousands of near-identical records. The time
 * stays one craft's, and the queue still runs and delivers each of the twenty on its own.
 */
public final class Resolver {

    /**
     * The most crafts one plan may be resolved for.
     *
     * <p>It bounds {@link #largestAffordable}'s search, which would otherwise run forever against a
     * recipe that consumes nothing, and it keeps every quantity in the walk inside an {@code int}.
     */
    public static final int MAX_CRAFTS = 100_000;

    /**
     * The most recipe applications one resolve may try before it gives up and reports the plan Missing.
     *
     * <p>A route that fails is rolled back and the next one tried, at every level, so a recipe whose
     * ingredients each accept several items that each have several routes (a banner: wool in sixteen
     * colours, each dyed from its own dyes) costs the product of them when nothing at the bottom is held.
     * Nothing bounded that, and a resolve cannot be paused partway, so one such recipe stalls the server
     * thread. A plan that resolves tries a handful, far under this; only a search that is failing gets
     * here, and reporting it Missing is what it would have ended as.
     */
    static final int SEARCH_LIMIT = 5_000;

    private final AssemblingRecipeSet recipes;
    private final Predicate<String> locked;

    /**
     * @param recipes the Assembling recipes
     * @param locked whether a recipe id is Locked for the player asking: the Lock source, already
     *     bound to that player, so this class never learns what a player is
     */
    public Resolver(AssemblingRecipeSet recipes, Predicate<String> locked) {
        this.recipes = recipes;
        this.locked = locked;
    }

    /**
     * Resolves {@code crafts} runs of {@code recipeId} against what the player has.
     *
     * <p>{@code available} is read, never spent: the queue takes the cost when the plan is queued, and
     * a Resolver that emptied the bag it was handed could not be asked twice, which
     * {@link #largestAffordable} does a dozen times.
     */
    public Resolution resolve(String recipeId, int crafts, ItemBag available) {
        Walk walk = new Walk(available.copy());
        AssemblingRecipe root = recipes.byId(recipeId);
        if (root == null || crafts <= 0 || crafts > MAX_CRAFTS) {
            return walk.finish();
        }
        ItemAmount result = root.result();
        if (locked.test(root.id())) {
            walk.locked.add(result.item(), scaled(result.count(), crafts));
            return walk.finish();
        }
        if (!walk.craft(root, crafts, List.of())) {
            walk.missing.add(result.item(), scaled(result.count(), crafts));
        }
        return walk.finish();
    }

    /**
     * The Crafting Plan's {@code all}: the largest count whose plan is complete, so {@code all} can
     * never queue a plan the inventory then refuses (ADR-0003).
     *
     * <p>Found by doubling and then bisecting rather than by dividing the inventory through the
     * recipe, because a chain's cost is not linear in the count: a recipe making two at a time leaves
     * a surplus that the next craft uses, so twice the crafts can cost less than twice the
     * ingredients.
     */
    public int largestAffordable(String recipeId, ItemBag available) {
        if (!resolve(recipeId, 1, available).complete()) return 0;
        int affordable = 1;
        int beyond = 2;
        while (beyond <= MAX_CRAFTS && resolve(recipeId, beyond, available).complete()) {
            affordable = beyond;
            beyond *= 2;
        }
        int high = Math.min(beyond, MAX_CRAFTS + 1);
        while (affordable + 1 < high) {
            int middle = affordable + (high - affordable) / 2;
            if (resolve(recipeId, middle, available).complete()) {
                affordable = middle;
            } else {
                high = middle;
            }
        }
        return affordable;
    }

    /**
     * One resolution in progress: what has been taken, what has been planned, and what could not be.
     *
     * <p>{@code surplus} is what makes a recipe that produces two at a time behave. Outputs land there
     * and later demands draw from it first, so three sticks asked for twice is four crafts and not
     * six, and the leftover is simply part of the plan, delivered when no remaining step wants it.
     */
    private final class Walk {

        // Not final: a route tried and abandoned puts every one of them back (see Mark). So never hold
        // one in a local across a recursive take: after a restore it would be a discarded bag.
        private ItemBag available;
        private ItemBag surplus = new ItemBag();
        private ItemBag rawCost = new ItemBag();
        private ItemBag missing = new ItemBag();
        private ItemBag locked = new ItemBag();
        private ItemBag toCraft = new ItemBag();
        private final List<CraftStep> steps = new ArrayList<>();
        /** Recipe applications tried so far, abandoned routes included: see {@link #SEARCH_LIMIT}. */
        private int tried;

        private Walk(ItemBag available) {
            this.available = available;
        }

        /**
         * Plans {@code n} runs of {@code recipe}, its ingredients first, and says whether it could.
         *
         * <p>The step is appended after the recursion, so the list comes out in dependency order and
         * the queue can run it front to back without ever looking a recipe up again.
         *
         * <p>It refuses when scaling the recipe would overflow an {@code int}. {@link #MAX_CRAFTS}
         * bounds the craft count and not the product of a count with an ingredient's amount, and a
         * wrapped negative demand reads as already satisfied: a complete plan that reserves nothing
         * and then cannot feed its own first step. It refuses too once the walk has tried
         * {@link #SEARCH_LIMIT} applications, which unwinds every open route the same way.
         */
        private boolean craft(AssemblingRecipe recipe, int n, List<String> ancestors) {
            if (++tried > SEARCH_LIMIT || overflows(recipe, n)) return false;
            List<String> path = new ArrayList<>(ancestors);
            path.add(recipe.id());
            // Merged rather than one entry per ingredient. An ingredient satisfied from two items
            // yields two draws, and two entries naming the same item would each pass the queue's
            // per-entry check and then together over-consume the buffer.
            ItemBag spent = new ItemBag();
            // What the spent items leave behind goes to the player, never into the surplus: a plan
            // that counted on it would be paying part of its cost after it was queued.
            ItemBag left = new ItemBag();
            for (Ingredient ingredient : recipe.ingredients()) {
                for (ItemAmount drawn : take(ingredient, ingredient.count() * n, path)) {
                    spent.add(drawn.item(), drawn.count());
                    String remainder = ingredient.remainder(drawn.item());
                    if (remainder != null) left.add(remainder, drawn.count());
                }
            }
            ItemAmount made = new ItemAmount(recipe.result().item(), recipe.result().count() * n);
            surplus.add(made.item(), made.count());
            toCraft.add(made.item(), made.count());
            steps.add(new CraftStep(recipe.id(), spent.amounts(), List.of(made), recipe.time(), n, left.amounts()));
            return true;
        }

        /** Whether scaling this recipe by {@code n} would put any quantity past an {@code int}. */
        private boolean overflows(AssemblingRecipe recipe, int n) {
            for (Ingredient ingredient : recipe.ingredients()) {
                if ((long) ingredient.count() * n > Integer.MAX_VALUE) return true;
            }
            return (long) recipe.result().count() * n > Integer.MAX_VALUE;
        }

        /**
         * Finds {@code quantity} of anything {@code ingredient} accepts, crafting it if that is what it
         * takes, and says what it actually took.
         *
         * <p>The concrete draws are the answer and not a detail: the step is what the queue spends, and
         * "two of any planks" is not something a buffer can be checked against. What is spent is
         * decided once, here, and never re-decided (ADR-0001).
         *
         * <p>Everything already crafted is drawn on before anything held, and both before anything is
         * crafted, so a surplus is never left stranded while the plan makes more of it.
         */
        private List<ItemAmount> take(Ingredient ingredient, int quantity, List<String> ancestors) {
            List<ItemAmount> drawn = new ArrayList<>();
            int owed = quantity;
            owed -= drawAcross(surplus, ingredient, owed, drawn, false);
            owed -= drawAcross(available, ingredient, owed, drawn, true);
            if (owed <= 0) return drawn;

            // Route priority: the first route, in the set's order, whose whole subtree resolves. A
            // route is tried against what remains and, if anything under it comes up Missing or
            // Locked, rolled back whole, so an abandoned route never spends what a later step needs.
            for (String item : ingredient.items()) {
                for (AssemblingRecipe route : recipes.routes(item)) {
                    if (Resolver.this.locked.test(route.id()) || ancestors.contains(route.id())) continue;
                    Mark mark = new Mark();
                    if (make(route, owed, ancestors) && mark.clean()) {
                        drawn.add(new ItemAmount(item, owed));
                        return drawn;
                    }
                    mark.restore();
                }
            }

            // No route resolves: the plan reports the top route's Missing and Locked, walked again.
            AssemblingRecipe maker = null;
            String blocked = null;
            for (String item : ingredient.items()) {
                AssemblingRecipe route = topRoute(item);
                if (route == null) continue;
                if (Resolver.this.locked.test(route.id())) {
                    if (blocked == null) blocked = item;
                    continue;
                }
                // A cycle. The set is whatever a pack author loaded, so the Resolver must be the
                // thing that stops rather than the thing that hangs.
                if (ancestors.contains(route.id())) continue;
                maker = route;
                break;
            }
            if (maker == null) {
                // Locked beats Missing when anything could have made it: telling a player to go
                // gathering for an item that only needs unlocking is the worse of the two mistakes.
                if (blocked != null) {
                    locked.add(blocked, owed);
                } else {
                    missing.add(ingredient.preferred(), owed);
                }
                return drawn;
            }

            if (make(maker, owed, ancestors)) drawn.add(new ItemAmount(maker.result().item(), owed));
            return drawn;
        }

        /** Crafts {@code owed} of what {@code maker} makes and takes it out of the surplus, or says it could not. */
        private boolean make(AssemblingRecipe maker, int owed, List<String> ancestors) {
            String makeable = maker.result().item();
            int runs = ceilDiv(owed, maker.result().count());
            // Refused, not clamped. Clamping would make fewer intermediates than the parent step's
            // inputs name and nothing downstream would notice: the plan would report complete, the
            // queue would take the cost, and then throw on a step it cannot feed. Reporting it as
            // Missing puts the refusal in front of the player, where every other shortfall goes.
            if (runs > MAX_CRAFTS || !craft(maker, runs, ancestors)) {
                missing.add(makeable, owed);
                return false;
            }
            surplus.remove(makeable, owed);
            return true;
        }

        /**
         * The walk as it stood before a route was tried: enough to put it back exactly if the route
         * turns out to leave something Missing or Locked.
         */
        private final class Mark {

            private final ItemBag available = Walk.this.available.copy();
            private final ItemBag surplus = Walk.this.surplus.copy();
            private final ItemBag rawCost = Walk.this.rawCost.copy();
            private final ItemBag missing = Walk.this.missing.copy();
            private final ItemBag locked = Walk.this.locked.copy();
            private final ItemBag toCraft = Walk.this.toCraft.copy();
            private final int steps = Walk.this.steps.size();

            /** Whether nothing has come up Missing or Locked since the mark. */
            private boolean clean() {
                return missing.equals(Walk.this.missing) && locked.equals(Walk.this.locked);
            }

            private void restore() {
                Walk.this.available = available;
                Walk.this.surplus = surplus;
                Walk.this.rawCost = rawCost;
                Walk.this.missing = missing;
                Walk.this.locked = locked;
                Walk.this.toCraft = toCraft;
                Walk.this.steps.subList(steps, Walk.this.steps.size()).clear();
            }
        }

        /** The route reported when none resolves: the item's top route, highest priority first. */
        private AssemblingRecipe topRoute(String item) {
            List<AssemblingRecipe> routes = recipes.routes(item);
            return routes.isEmpty() ? null : routes.get(0);
        }

        /** Spends up to {@code owed} out of {@code bag}, taking whatever the ingredient accepts. */
        private int drawAcross(ItemBag bag, Ingredient ingredient, int owed, List<ItemAmount> drawn,
                               boolean fromInventory) {
            int total = 0;
            for (String item : ingredient.items()) {
                if (total >= owed) break;
                int taken = drawDown(bag, item, owed - total);
                if (taken <= 0) continue;
                if (fromInventory) rawCost.add(item, taken);
                drawn.add(new ItemAmount(item, taken));
                total += taken;
            }
            return total;
        }

        /** Spends up to {@code wanted} of {@code item} out of {@code bag}, and says how much. */
        private static int drawDown(ItemBag bag, String item, int wanted) {
            if (wanted <= 0) return 0;
            int taken = Math.min(wanted, bag.count(item));
            if (taken <= 0) return 0;
            bag.remove(item, taken);
            return taken;
        }

        private Resolution finish() {
            return new Resolution(steps, rawCost.amounts(), toCraft.amounts(), missing.amounts(), locked.amounts());
        }
    }

    private static int ceilDiv(int quantity, int per) {
        return per <= 0 ? quantity : (quantity + per - 1) / per;
    }

    /** {@code count x times}, saturated rather than wrapped: this only ever feeds a display list. */
    private static int scaled(int count, int times) {
        return (int) Math.min(Integer.MAX_VALUE, (long) count * times);
    }

    /**
     * A resolved plan, or the reasons it is not one.
     *
     * <p>{@code rawCost} is everything the plan takes from the player's items: leaves and any
     * intermediate the player already had, since both leave the inventory when the plan is queued and
     * both come back on a cancel. {@code toCraft} is every step's output, the root's included.
     */
    public record Resolution(
            List<CraftStep> steps,
            List<ItemAmount> rawCost,
            List<ItemAmount> toCraft,
            List<ItemAmount> missing,
            List<ItemAmount> locked) {

        public Resolution {
            steps = List.copyOf(steps);
            rawCost = List.copyOf(rawCost);
            toCraft = List.copyOf(toCraft);
            missing = List.copyOf(missing);
            locked = List.copyOf(locked);
        }

        /** Whether the plan can be queued: nothing Missing and nothing Locked. */
        public boolean complete() {
            return missing.isEmpty() && locked.isEmpty() && !steps.isEmpty();
        }

        /**
         * The Crafting Plan the queue takes, which only a complete resolution has.
         *
         * <p>Only the parts queueing needs cross: the ordered steps and the cost. The display lists stay
         * behind, because a running plan has nothing left to be Missing.
         */
        public CraftingPlan toPlan(UUID id, String rootItem, int amount) {
            return new CraftingPlan(id, rootItem, amount, rawCost, steps);
        }
    }
}
