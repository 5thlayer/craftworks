// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.planner;

import java.util.ArrayList;
import java.util.Collections;
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
     * A {@link Planning} against {@code stock}: this Resolver's recipes and Lock source, read for one moment
     * of the inventory. {@code stock} is copied, so the caller may go on changing it.
     */
    public Planning planning(ItemBag stock) {
        return new Planning(this, stock);
    }

    /**
     * Resolves {@code crafts} runs of {@code recipeId} against what the player has, once and uncached: a
     * {@link Planning} of {@code available} asked one question. Whoever asks several keeps the Planning.
     */
    public Resolution resolve(String recipeId, int crafts, ItemBag available) {
        return planning(available).plan(recipeId, crafts);
    }

    /** The largest count of {@code recipeId} that {@code available} covers: see {@link Planning#largestAffordable}. */
    public int largestAffordable(String recipeId, ItemBag available) {
        return planning(available).largestAffordable(recipeId);
    }

    /** What {@code stock} cannot reach (see {@link Reach}), which a walk over it skips: the worklist runs once per stock. */
    Set<String> unreachedFrom(Map<String, Integer> stock) {
        return recipes.reach().unreachableFrom(stock.keySet());
    }

    /**
     * One craft of {@code recipeId} against the stock the {@link Memo} was made with, for the Ready pass,
     * which reads only {@code complete()}. Fill never calls this: its Missing and Locked breakdown has to
     * name everything, which the memo's probes would not.
     */
    Resolution resolveReady(String recipeId, Memo memo) {
        return resolve(recipeId, 1, new Walk(new LinkedHashMap<>(memo.stock), memo, Set.of()));
    }

    private Resolution resolve(String recipeId, int crafts, Walk walk) {
        AssemblingRecipe root = recipes.byId(recipeId);
        if (root == null || crafts <= 0 || crafts > MAX_CRAFTS) {
            return walk.finish();
        }
        ItemAmount result = root.result();
        if (locked.test(root.id())) {
            walk.addLocked(result.item(), scaled(result.count(), crafts), root.id());
            return walk.finish();
        }
        if (!walk.craft(root, crafts, List.of())) {
            walk.add(MISSING, result.item(), scaled(result.count(), crafts));
        }
        return walk.finish();
    }

    /** {@code crafts} runs of {@code recipeId} from {@code stock}, which the walk copies and never spends. */
    Resolution resolveAgainst(String recipeId, int crafts, Map<String, Integer> stock, Set<String> unreached) {
        return resolve(recipeId, crafts, new Walk(new LinkedHashMap<>(stock), null, unreached));
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
     * <p><b>Reachability</b> (see {@link Reach}), worked out once when the {@link Planning} is made, starts the unmakeable
     * items off with everything the inventory cannot lead to. Every {@link Planning#plan} skips the same items without
     * a memo (#73); what the memo adds is the probes below.
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
     * <p>Valid for one {@link Planning}: it holds that Planning's stock, which it never changes, and the Lock
     * answers are the Planning's. Only a Ready pass keeps one; Fill never has one.
     */
    static final class Memo {

        private final Map<String, Integer> stock;
        private final Set<String> unmakeable = new HashSet<>();
        private final Set<String> probed = new HashSet<>();

        Memo(Map<String, Integer> stock, Set<String> unreached) {
            this.stock = stock;
            unmakeable.addAll(unreached);
        }

    }

    private static final int SURPLUS = 0;
    private static final int RAW_COST = 1;
    private static final int MISSING = 2;
    private static final int LOCKED = 3;
    private static final int TO_CRAFT = 4;
    /** Held items live in a map of their own: see {@link Walk#available}. */
    private static final int AVAILABLE = 5;

    /**
     * One change to a bag, kept so a route abandoned can be put back: positive added, negative removed.
     *
     * <p>{@code lockedBy} is set on a Locked add that first named, in {@link Walk#lockedBy}, a recipe that
     * Locked its item, so putting the add back takes that recipe back too; null everywhere else.
     */
    private record Undo(int bag, String item, int amount, String lockedBy) {

        private Undo(int bag, String item, int amount) {
            this(bag, item, amount, null);
        }
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
        /** The recipes that Locked each Locked item, in the order they were met: see {@link Resolution#lockedBy}. */
        private final Map<String, List<String>> lockedBy = new LinkedHashMap<>();
        private int openMarks;
        /** Net Missing and Locked adds so far: what {@link Mark#clean} compares. */
        private int failures;
        /** The Ready pass's memo, or null for a walk that must report everything, as Fill's does. */
        private final Memo memo;
        /**
         * What the inventory cannot reach, skipped like a memo's unmakeable items, for a walk without a memo: a
         * memo's own set starts from the same answer. Empty for a memo's walk.
         */
        private final Set<String> unreached;
        /** Recipe applications tried so far, abandoned routes included: see {@link #SEARCH_LIMIT}. */
        private int tried;

        private Walk(Map<String, Integer> available, Memo memo, Set<String> unreached) {
            this.available = available;
            this.memo = memo;
            this.unreached = unreached;
        }

        private void add(int bag, String item, int count) {
            if (count <= 0) return;
            bags[bag].add(item, count);
            if (bag == MISSING || bag == LOCKED) failures++;
            if (openMarks > 0) journal.add(new Undo(bag, item, count));
        }

        /** Adds to the Locked bag and remembers {@code recipe} as what Locked {@code item}, both put back by an undo. */
        private void addLocked(String item, int count, String recipe) {
            if (count <= 0) return;
            List<String> recipes = lockedBy.computeIfAbsent(item, key -> new ArrayList<>());
            boolean fresh = !recipes.contains(recipe);
            if (fresh) recipes.add(recipe);
            bags[LOCKED].add(item, count);
            failures++;
            if (openMarks > 0) journal.add(new Undo(LOCKED, item, count, fresh ? recipe : null));
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
                if (change.lockedBy() != null) {
                    List<String> recipes = lockedBy.get(change.item());
                    recipes.remove(change.lockedBy());
                    if (recipes.isEmpty()) lockedBy.remove(change.item());
                }
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
                if (unreached.contains(item) || memo != null && memo.unmakeable.contains(item)) continue;
                if (tryRoutes(item, owed, ancestors)) {
                    drawn.add(new ItemAmount(item, owed));
                    return drawn;
                }
                if (memo != null) learn(item);
            }

            // No route resolves: the plan reports the top route's Missing and Locked, walked again. A route
            // that only converts between what this ingredient accepts is not the errand, so it is passed over.
            AssemblingRecipe maker = null;
            String blocked = null;
            AssemblingRecipe lockedRoute = null;
            for (String item : ingredient.items()) {
                AssemblingRecipe route = topRoute(item, ingredient);
                if (route == null) continue;
                if (Resolver.this.locked.test(route.id())) {
                    if (blocked == null) {
                        blocked = item;
                        lockedRoute = route;
                    }
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
                    addLocked(blocked, owed, lockedRoute.id());
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
            Walk probe = new Walk(new LinkedHashMap<>(memo.stock), memo, Set.of());
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

        /**
         * The route reported when none resolves: the item's first route that is not a conversion, highest
         * priority first, or null when it has none.
         *
         * <p>A conversion, for the ingredient being filled, is a route with an ingredient listing another item
         * this ingredient accepts: the recolouring of a colour from any other colour. Descending into one
         * walks the colours, and what the player is short of is "any of these, and the dye", not every dye in
         * the cycle (#73).
         */
        private AssemblingRecipe topRoute(String item, Ingredient filling) {
            for (AssemblingRecipe route : recipes.routes(item)) {
                if (!converts(route, item, filling)) return route;
            }
            return null;
        }

        /** Whether some ingredient of {@code route} lists an item {@code filling} accepts other than {@code item}. */
        private static boolean converts(AssemblingRecipe route, String item, Ingredient filling) {
            for (Ingredient own : route.ingredients()) {
                for (String listed : own.items()) {
                    if (!listed.equals(item) && filling.items().contains(listed)) return true;
                }
            }
            return false;
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
                    bags[LOCKED].amounts(), lockedBy);
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
     *
     * <p>{@code lockedBy} names, for each Locked item, the recipes that Locked it in the order the walk
     * met them: one item can be Locked by different recipes, since the route reported depends on the
     * ingredient being filled. A route the walk abandoned leaves nothing in it.
     */
    public record Resolution(
            List<CraftStep> steps,
            List<ItemAmount> rawCost,
            List<ItemAmount> toCraft,
            List<ItemAmount> missing,
            List<ItemAmount> locked,
            Map<String, List<String>> lockedBy) {

        public Resolution {
            steps = List.copyOf(steps);
            rawCost = List.copyOf(rawCost);
            toCraft = List.copyOf(toCraft);
            missing = List.copyOf(missing);
            locked = List.copyOf(locked);
            Map<String, List<String>> copy = new LinkedHashMap<>();
            lockedBy.forEach((item, recipes) -> copy.put(item, List.copyOf(recipes)));
            lockedBy = Collections.unmodifiableMap(copy);
        }

        /**
         * Each Locked item's reason, from the first of the recipes that Locked it which has one in
         * {@code byRecipe}; an item none of them has a reason for is absent. In the order of {@link #locked}.
         */
        public <R> Map<String, R> lockReasons(Map<String, R> byRecipe) {
            Map<String, R> byItem = new LinkedHashMap<>();
            for (ItemAmount amount : locked) {
                for (String recipe : lockedBy.getOrDefault(amount.item(), List.of())) {
                    R reason = byRecipe.get(recipe);
                    if (reason == null) continue;
                    byItem.put(amount.item(), reason);
                    break;
                }
            }
            return byItem;
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
