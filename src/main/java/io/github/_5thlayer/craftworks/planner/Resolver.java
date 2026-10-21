// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.planner;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    private long applications;
    private int cutShort;

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
     * Recipe applications this Resolver has tried across all its resolves, abandoned routes included.
     *
     * <p>A measure of work that does not depend on the machine, for tests: the time a resolve takes is
     * the number of applications it tries times what each costs, and only the first is something a test
     * can assert on. Nothing in the running mod reads it.
     */
    public long applicationsTried() {
        return applications;
    }

    /** How many searches this Resolver has abandoned at {@link #SEARCH_LIMIT} rather than finished. */
    public int searchesCutShort() {
        return cutShort;
    }

    /**
     * Resolves {@code crafts} runs of {@code recipeId} against what the player has.
     *
     * <p>{@code available} is read, never spent: the queue takes the cost when the plan is queued, and
     * a Resolver that emptied the bag it was handed could not be asked twice, which
     * {@link #largestAffordable} does a dozen times.
     */
    public Resolution resolve(String recipeId, int crafts, ItemBag available) {
        return resolve(recipeId, crafts, new Walk(new LinkedHashMap<>(available.asMap()), null));
    }

    /** A memo for a Ready pass over {@code snapshot}, against this Resolver's recipe set. */
    public Memo memo(ItemBag snapshot) {
        return new Memo(snapshot, recipes);
    }

    /**
     * One craft of {@code recipeId} against the inventory the {@link Memo} was made with, for the Ready
     * pass, which reads only {@code complete()}. Fill never calls this: its Missing and Locked breakdown
     * has to name everything, which the memo's skipping would not.
     */
    public Resolution resolveReady(String recipeId, Memo memo) {
        return resolve(recipeId, 1, new Walk(new LinkedHashMap<>(memo.stock), memo));
    }

    private Resolution resolve(String recipeId, int crafts, Walk walk) {
        AssemblingRecipe root = recipes.byId(recipeId);
        if (root == null || crafts <= 0 || crafts > MAX_CRAFTS) {
            return walk.finish();
        }
        ItemAmount result = root.result();
        if (locked.test(root.id())) {
            walk.add(LOCKED, result.item(), scaled(result.count(), crafts));
            return walk.finish();
        }
        if (!walk.craft(root, crafts, List.of())) {
            walk.add(MISSING, result.item(), scaled(result.count(), crafts));
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
     * What a Ready pass knows and remembers across its resolves (#67, #70): the inventory it was made with,
     * and the items it has found no way to craft from that inventory, by two means.
     *
     * <p>Only "none can be made" is remembered, never "this much can be made". A walk shares its stock and
     * its surplus between branches, so a count that held in one place says nothing about another; but an
     * item that cannot be crafted even once, from the whole inventory, with no ancestors to forbid a
     * recipe, cannot be crafted in a narrower place either.
     *
     * <p><b>Reachability</b>, worked out once when the memo is made, ignores quantities, remainders and the
     * Lock source, every recipe counting as open. It starts from the inventory's keys and runs the recipes
     * forward: a recipe fires once each ingredient lists an item that is reached, or that a held key matches
     * by item id, which over-approximates the Data-carrying draws that match by item id (ADR-0022), and its
     * result is then reached. An item it leaves out cannot be made in any amount, so it is unmakeable. It
     * is a worklist over an index from each item to the recipes that list it, with a count per recipe of
     * ingredients not yet met, so it costs the size of the set once. It catches what no search can afford
     * to: a recolouring cycle that nothing held leads into, whose failing search walks every simple path.
     *
     * <p><b>A probe</b> catches the rest, an item that is reached but short on quantity. It is a search of
     * its own (see {@code Walk.learn}), run once per item after a failure in context and never from that
     * failure, which may have been the amount asked, the stock an earlier ingredient took or a cycle. A
     * search the {@link #SEARCH_LIMIT} cut short never counts, because it proved nothing.
     *
     * <p>The one case a probe's verdict is not exact in is a contrived one: the walk's choices are greedy
     * and never revisited, so a surplus of one acceptable item can steer a later ingredient to succeed where
     * the probe, which has no surplus, drew the other and failed. Ready is a list for a player to pick
     * from and a click still resolves live, so the cost of that is a recipe missing from the list.
     *
     * <p>Valid for one pass: the Lock source is asked through the pass's own cache, and a memo carried to
     * a pass with another inventory would be wrong. Fill never has one.
     */
    public static final class Memo {

        private final Map<String, Integer> stock;
        private final Set<String> unmakeable = new HashSet<>();
        private final Set<String> probed = new HashSet<>();

        public Memo(ItemBag snapshot, AssemblingRecipeSet recipes) {
            this.stock = new LinkedHashMap<>(snapshot.asMap());
            unmakeable.addAll(unreachable(stock.keySet(), recipes));
        }

        /** The results of {@code recipes} that no chain of them reaches from {@code held}. */
        private static Set<String> unreachable(Set<String> held, AssemblingRecipeSet recipes) {
            Set<String> heldIds = new HashSet<>();
            for (String key : held) heldIds.add(ItemKey.itemId(key));
            List<AssemblingRecipe> all = new ArrayList<>(recipes.size());
            for (String id : recipes.ids()) all.add(recipes.byId(id));
            // Every ingredient of every recipe is numbered; per recipe, how many are not yet met, and
            // per item, the ingredients listing it (a number, then its slots filled so far).
            int ingredients = 0;
            for (AssemblingRecipe recipe : all) ingredients += recipe.ingredients().size();
            int[] recipeOf = new int[ingredients];
            int[] unmet = new int[all.size()];
            boolean[] met = new boolean[ingredients];
            Map<String, int[]> listedBy = new HashMap<>();
            int number = 0;
            for (int r = 0; r < all.size(); r++) {
                unmet[r] = all.get(r).ingredients().size();
                for (Ingredient ingredient : all.get(r).ingredients()) {
                    recipeOf[number] = r;
                    for (String item : ingredient.items()) {
                        int[] list = listedBy.get(item);
                        if (list == null) listedBy.put(item, list = new int[3]);
                        else if (list[0] + 1 == list.length) listedBy.put(item, list = Arrays.copyOf(list, list.length * 2));
                        list[++list[0]] = number;
                    }
                    number++;
                }
            }
            Set<String> reached = new HashSet<>();
            Deque<String> pending = new ArrayDeque<>();
            for (String key : held) if (reached.add(key)) pending.add(key);
            // A recipe that wants nothing needs nothing reached.
            for (int r = 0; r < all.size(); r++) {
                String made = all.get(r).result().item();
                if (unmet[r] == 0 && reached.add(made)) pending.add(made);
            }
            // An item a held key matches by id is met without being reached, as a Data-carrying draw would be.
            for (Map.Entry<String, int[]> entry : listedBy.entrySet()) {
                if (heldIds.contains(ItemKey.itemId(entry.getKey()))) pending.add(entry.getKey());
            }
            while (!pending.isEmpty()) {
                int[] list = listedBy.get(pending.poll());
                if (list == null) continue;
                for (int k = 1; k <= list[0]; k++) {
                    int ingredient = list[k];
                    if (met[ingredient]) continue;
                    met[ingredient] = true;
                    if (--unmet[recipeOf[ingredient]] == 0) {
                        String made = all.get(recipeOf[ingredient]).result().item();
                        if (reached.add(made)) pending.add(made);
                    }
                }
            }
            Set<String> out = new HashSet<>();
            for (AssemblingRecipe recipe : all) if (!reached.contains(recipe.result().item())) out.add(recipe.result().item());
            return out;
        }

    }

    private static final int SURPLUS = 0;
    private static final int RAW_COST = 1;
    private static final int MISSING = 2;
    private static final int LOCKED = 3;
    private static final int TO_CRAFT = 4;
    /** Held items live in a map of their own: see {@link Walk#available}. */
    private static final int AVAILABLE = 5;

    /** One change to a bag, kept so a route abandoned can be put back: positive added, negative removed. */
    private record Undo(int bag, String item, int amount) {
    }

    /**
     * One resolution in progress: what has been taken, what has been planned, and what could not be.
     *
     * <p>{@code surplus} is what makes a recipe that produces two at a time behave. Outputs land there
     * and later demands draw from it first, so three sticks asked for twice is four crafts and not
     * six, and the leftover is simply part of the plan, delivered when no remaining step wants it.
     *
     * <p>Every change to a bag goes through {@link #add} and {@link #draw}, which journal it while a
     * {@link Mark} is open: putting a route back is undoing its journal, not restoring copies of every
     * bag, the whole inventory included (#67).
     */
    private final class Walk {

        /**
         * The held items. Drawing leaves a key at zero rather than removing it and nothing is ever added, so
         * putting a draw back restores the order the keys are walked in, which {@link #drawWithData} reads.
         */
        private final Map<String, Integer> available;
        private final ItemBag[] bags = {new ItemBag(), new ItemBag(), new ItemBag(), new ItemBag(), new ItemBag()};
        private final List<CraftStep> steps = new ArrayList<>();
        private final List<Undo> journal = new ArrayList<>();
        private int openMarks;
        /** Net Missing and Locked adds so far: what {@link Mark#clean} compares. */
        private int failures;
        /** The Ready pass's memo, or null for a walk that must report everything, as Fill's does. */
        private final Memo memo;
        /** Recipe applications tried so far, abandoned routes included: see {@link #SEARCH_LIMIT}. */
        private int tried;

        private Walk(Map<String, Integer> available, Memo memo) {
            this.available = available;
            this.memo = memo;
        }

        private void add(int bag, String item, int count) {
            if (count <= 0) return;
            bags[bag].add(item, count);
            if (bag == MISSING || bag == LOCKED) failures++;
            if (openMarks > 0) journal.add(new Undo(bag, item, count));
        }

        private int count(int bag, String item) {
            return bag == AVAILABLE ? available.getOrDefault(item, 0) : bags[bag].count(item);
        }

        /** Spends up to {@code wanted} of {@code item} out of a bag, and says how much. */
        private int draw(int bag, String item, int wanted) {
            if (wanted <= 0) return 0;
            int have = count(bag, item);
            int taken = Math.min(wanted, have);
            if (taken <= 0) return 0;
            if (bag == AVAILABLE) {
                available.put(item, have - taken);
            } else {
                bags[bag].remove(item, taken);
            }
            if (openMarks > 0) journal.add(new Undo(bag, item, -taken));
            return taken;
        }

        private void undo(Undo change) {
            if (change.amount() > 0) {
                bags[change.bag()].remove(change.item(), change.amount());
                if (change.bag() == MISSING || change.bag() == LOCKED) failures--;
            } else if (change.bag() == AVAILABLE) {
                available.put(change.item(), available.get(change.item()) - change.amount());
            } else {
                bags[change.bag()].add(change.item(), -change.amount());
            }
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
            applications++;
            if (++tried > SEARCH_LIMIT) {
                if (tried == SEARCH_LIMIT + 1) cutShort++;
                return false;
            }
            if (overflows(recipe, n)) return false;
            List<String> path = new ArrayList<>(ancestors);
            path.add(recipe.id());
            // Merged rather than one entry per ingredient. An ingredient satisfied from two items
            // yields two draws, and two entries naming the same item would each pass the queue's
            // per-entry check and then together over-consume the buffer.
            ItemBag spent = new ItemBag();
            // What the spent items leave behind goes to the player, never into the surplus: a plan
            // that counted on it would be paying part of its cost after it was queued.
            ItemBag left = new ItemBag();
            // A Data-carrying step also keeps the draws ingredient by ingredient, for each craft's own stacks.
            List<ItemAmount> inOrder = new ArrayList<>();
            long drawnTotal = 0;
            for (Ingredient ingredient : recipe.ingredients()) {
                for (ItemAmount drawn : take(ingredient, ingredient.count() * n, path, recipe.dataCarrying())) {
                    spent.add(drawn.item(), drawn.count());
                    inOrder.add(drawn);
                    drawnTotal += drawn.count();
                    String remainder = ingredient.remainder(drawn.item());
                    if (remainder != null) left.add(remainder, drawn.count());
                }
            }
            ItemAmount made = new ItemAmount(recipe.result().item(), recipe.result().count() * n);
            add(SURPLUS, made.item(), made.count());
            add(TO_CRAFT, made.item(), made.count());
            // A step that came up short is only ever shown as Missing, and cannot be laid out per craft.
            long wanted = 0;
            for (Ingredient ingredient : recipe.ingredients()) wanted += (long) ingredient.count() * n;
            boolean laidOut = recipe.dataCarrying() && wanted == drawnTotal;
            steps.add(new CraftStep(recipe.id(), laidOut ? inOrder : spent.amounts(), List.of(made), recipe.time(), n,
                    left.amounts(), laidOut ? recipe.ingredients().stream().map(Ingredient::count).toList() : List.of()));
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
        private List<ItemAmount> take(Ingredient ingredient, int quantity, List<String> ancestors,
                                      boolean dataCarrying) {
            List<ItemAmount> drawn = new ArrayList<>();
            int owed = quantity;
            owed -= drawAcross(SURPLUS, ingredient, owed, drawn);
            owed -= drawAcross(AVAILABLE, ingredient, owed, drawn);
            if (dataCarrying) owed -= drawWithData(ingredient, owed, drawn);
            if (owed <= 0) return drawn;

            // Route priority: the first route, in the set's order, whose whole subtree resolves. A
            // route is tried against what remains and, if anything under it comes up Missing or
            // Locked, rolled back whole, so an abandoned route never spends what a later step needs.
            for (String item : ingredient.items()) {
                if (memo != null && memo.unmakeable.contains(item)) continue;
                if (tryRoutes(item, owed, ancestors)) {
                    drawn.add(new ItemAmount(item, owed));
                    return drawn;
                }
                if (memo != null) learn(item);
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
                    add(LOCKED, blocked, owed);
                } else {
                    add(MISSING, ingredient.preferred(), owed);
                }
                return drawn;
            }

            if (memo != null) {
                // The walk again would fail as the one above did, from the same state: a Ready pass reads
                // only whether the plan is complete, and walking it twice at every level is what made a
                // failing search cost the square of its depth.
                add(MISSING, maker.result().item(), owed);
                return drawn;
            }
            if (make(maker, owed, ancestors)) drawn.add(new ItemAmount(maker.result().item(), owed));
            return drawn;
        }

        /** Whether some route of {@code item} resolves for {@code owed} of it, leaving the walk as that route left it. */
        private boolean tryRoutes(String item, int owed, List<String> ancestors) {
            for (AssemblingRecipe route : recipes.routes(item)) {
                if (Resolver.this.locked.test(route.id()) || ancestors.contains(route.id())) continue;
                Mark mark = new Mark();
                if (make(route, owed, ancestors) && mark.clean()) {
                    mark.release();
                    return true;
                }
                mark.restore();
            }
            return false;
        }

        /**
         * Asks, once per item per pass, whether {@code item} can be crafted at all: one of it, by its routes,
         * from the whole inventory, with no ancestors. If not, and the search was not cut short, it joins
         * {@link Memo}'s unmakeable items.
         *
         * <p>Run after a failure in context, so an item that is made pays for the probe only when
         * something else went wrong around it, and once.
         */
        private void learn(String item) {
            if (tried > SEARCH_LIMIT || !memo.probed.add(item)) return;
            Walk probe = new Walk(new LinkedHashMap<>(memo.stock), memo);
            if (!probe.tryRoutes(item, 1, List.of()) && probe.tried <= SEARCH_LIMIT) memo.unmakeable.add(item);
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
                add(MISSING, makeable, owed);
                return false;
            }
            draw(SURPLUS, makeable, owed);
            return true;
        }

        /**
         * The walk as it stood before a route was tried: a position in the journal, enough to put it back
         * exactly if the route turns out to leave something Missing or Locked. Ends with {@link #release}
         * or {@link #restore}, one of the two.
         */
        private final class Mark {

            private final int journalSize = journal.size();
            private final int stepCount = steps.size();
            private final int failuresAtMark = failures;

            private Mark() {
                openMarks++;
            }

            /** Whether nothing has come up Missing or Locked since the mark. */
            private boolean clean() {
                return failures == failuresAtMark;
            }

            /** Keeps what the route did. */
            private void release() {
                if (--openMarks == 0) journal.clear();
            }

            /** Puts back what the route did. */
            private void restore() {
                for (int i = journal.size() - 1; i >= journalSize; i--) undo(journal.get(i));
                journal.subList(journalSize, journal.size()).clear();
                steps.subList(stepCount, steps.size()).clear();
                release();
            }
        }

        /** The route reported when none resolves: the item's top route, highest priority first. */
        private AssemblingRecipe topRoute(String item) {
            List<AssemblingRecipe> routes = recipes.routes(item);
            return routes.isEmpty() ? null : routes.get(0);
        }

        /** Spends up to {@code owed} out of a bag, taking whatever the ingredient accepts. */
        private int drawAcross(int bag, Ingredient ingredient, int owed, List<ItemAmount> drawn) {
            int total = 0;
            for (String item : ingredient.items()) {
                if (total >= owed) break;
                int taken = draw(bag, item, owed - total);
                if (taken <= 0) continue;
                if (bag == AVAILABLE) add(RAW_COST, item, taken);
                drawn.add(new ItemAmount(item, taken));
                total += taken;
            }
            return total;
        }

        /**
         * A Data-carrying ingredient's second choice: the held keys carrying data on an item it accepts, in
         * inventory order, once the Pristine ones are spent (ADR-0022). The draws name the exact keys.
         */
        private int drawWithData(Ingredient ingredient, int owed, List<ItemAmount> drawn) {
            if (owed <= 0) return 0;
            Set<String> accepted = new HashSet<>();
            for (String item : ingredient.items()) accepted.add(ItemKey.itemId(item));
            int total = 0;
            for (String key : available.keySet()) {
                if (total >= owed) break;
                if (!ItemKey.hasPatch(key) || ingredient.items().contains(key)
                        || !accepted.contains(ItemKey.itemId(key))) continue;
                int taken = draw(AVAILABLE, key, owed - total);
                if (taken <= 0) continue;
                add(RAW_COST, key, taken);
                drawn.add(new ItemAmount(key, taken));
                total += taken;
            }
            return total;
        }

        private Resolution finish() {
            return new Resolution(steps, bags[RAW_COST].amounts(), bags[TO_CRAFT].amounts(), bags[MISSING].amounts(),
                    bags[LOCKED].amounts());
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
