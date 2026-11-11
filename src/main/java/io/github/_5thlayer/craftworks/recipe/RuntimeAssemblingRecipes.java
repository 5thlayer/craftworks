// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.recipe;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.mojang.logging.LogUtils;
import io.github._5thlayer.craftworks.assembler.ItemKeys;
import io.github._5thlayer.craftworks.planner.AssemblingRecipeSet;
import io.github._5thlayer.craftworks.planner.Ingredient;
import io.github._5thlayer.craftworks.planner.ItemAmount;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.crafting.DataComponentIngredient;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import org.slf4j.Logger;

/**
 * The Assembling recipe set, read off what the server actually loaded under {@code craftworks:assembling}.
 *
 * <p>Cached against the {@link RecipeManager}'s identity: a datapack reload builds a fresh manager, so
 * its identity is an exact and free invalidation signal with no reload listener to register. The set is
 * therefore rebuilt on every recipe reload, the first time anything asks after it.
 */
public final class RuntimeAssemblingRecipes {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static volatile RecipeManager builtFrom;
    private static volatile AssemblingRecipeSet recipes = AssemblingRecipeSet.empty();
    /** The recipes in {@link #recipes} with a Tested ingredient, built with it. */
    private static volatile List<Tested> tested = List.of();
    /**
     * Each widened set by what widened it, so a player planning again with the same stock gets the same set, and
     * its Reach with it. Cleared with every rebuild.
     */
    private static final Map<List<io.github._5thlayer.craftworks.planner.AssemblingRecipe>, AssemblingRecipeSet> widened =
            new ConcurrentHashMap<>();

    /**
     * A recipe with an ingredient that decides for itself which stacks it takes, such as Neo Vitae's
     * {@code orb_tier}: any blood orb of a tier, bound to its owner or not. Its listed items are bare, and
     * a bare key matches only a pristine stack (ADR-0002), so a bound orb would never pay for it.
     *
     * @param slots the Minecraft ingredient at each index of {@code recipe}'s ingredients, null where
     *              the listed keys say all there is to say
     */
    private record Tested(io.github._5thlayer.craftworks.planner.AssemblingRecipe recipe,
            List<net.minecraft.world.item.crafting.Ingredient> slots) {
    }

    private RuntimeAssemblingRecipes() {
    }

    /**
     * The set for this player: {@link #recipes(Level)}, with each Tested ingredient also taking the keys of
     * the stacks the player holds that it accepts. The same set when the player holds none.
     */
    public static AssemblingRecipeSet recipesFor(ServerPlayer player) {
        AssemblingRecipeSet base = recipes(player.level());
        List<Tested> found = tested;
        if (found.isEmpty()) return base;
        HolderLookup.Provider registries = player.registryAccess();
        List<ItemStack> stock = player.getInventory().getNonEquipmentItems().stream()
                .filter(stack -> !stack.isEmpty()).toList();
        // Keyed by the stack itself: the same stack is asked about by every recipe that tests it.
        Map<ItemStack, String> keys = new IdentityHashMap<>();
        List<io.github._5thlayer.craftworks.planner.AssemblingRecipe> changed = new ArrayList<>();
        for (Tested recipe : found) {
            List<Ingredient> ingredients = new ArrayList<>(recipe.recipe().ingredients());
            boolean any = false;
            for (int n = 0; n < ingredients.size(); n++) {
                net.minecraft.world.item.crafting.Ingredient slot = recipe.slots().get(n);
                if (slot == null) continue;
                Ingredient listed = ingredients.get(n);
                List<String> items = new ArrayList<>(listed.items());
                Map<String, String> remainders = new LinkedHashMap<>(listed.remainders());
                for (ItemStack stack : stock) {
                    if (!slot.test(stack)) continue;
                    String key = keys.computeIfAbsent(stack, held -> ItemKeys.of(held, registries));
                    if (key == null || items.contains(key)) continue;
                    ItemStackTemplate remainder = stack.getItem().getCraftingRemainder(stack);
                    String left = remainder == null ? null : ItemKeys.of(remainder.create(), registries);
                    if (remainder != null && left == null) continue;
                    // After the listed items: a shortfall is still reported as the first of those.
                    items.add(key);
                    if (left != null) remainders.put(key, left);
                }
                if (items.size() == listed.items().size()) continue;
                ingredients.set(n, new Ingredient(items, listed.count(), remainders));
                any = true;
            }
            if (any) changed.add(recipe.recipe().withIngredients(ingredients));
        }
        if (changed.isEmpty()) return base;
        // A bound orb's key moves with the fluid it holds, so the stocks seen are not few: forget them all now and then.
        if (widened.size() > 64) widened.clear();
        return widened.computeIfAbsent(List.copyOf(changed), recipes -> {
            AssemblingRecipeSet.Builder builder = AssemblingRecipeSet.builder();
            // The widened recipes go in first, and a second recipe under an id already added is ignored.
            recipes.forEach(builder::add);
            for (String id : base.ids()) builder.add(base.byId(id));
            return builder.build();
        });
    }

    /** The set for the level's current datapack state, rebuilt when the recipes reload. */
    public static AssemblingRecipeSet recipes(Level level) {
        RecipeManager manager = level.getServer().getRecipeManager();
        if (manager == builtFrom) return recipes;
        return rebuild(manager, level.registryAccess());
    }

    private static synchronized AssemblingRecipeSet rebuild(RecipeManager manager, HolderLookup.Provider registries) {
        if (manager == builtFrom) return recipes;
        List<Tested> found = new ArrayList<>();
        AssemblingRecipeSet built = build(manager, registries, found);
        recipes = built;
        tested = List.copyOf(found);
        // After the new set is out, so nothing built from the old one outlives the clear.
        widened.clear();
        builtFrom = manager;
        LOGGER.info("Personal Assembler: {} Assembling recipe(s) loaded.", built.size());
        return built;
    }

    private static AssemblingRecipeSet build(RecipeManager manager, HolderLookup.Provider registries, List<Tested> found) {
        AssemblingRecipeSet.Builder builder = AssemblingRecipeSet.builder();
        List<String> refused = new ArrayList<>();
        for (RecipeHolder<AssemblingRecipe> holder : manager.recipeMap().byType(CraftworksRecipes.ASSEMBLING_TYPE.get())) {
            var read = read(holder.id().identifier().toString(), holder.value(), registries, refused);
            if (read == null) continue;
            builder.add(read);
            List<net.minecraft.world.item.crafting.Ingredient> slots = new ArrayList<>();
            boolean any = false;
            for (SizedIngredient sized : holder.value().ingredients()) {
                boolean tests = sized.ingredient().isCustom()
                        && !(sized.ingredient().getCustomIngredient() instanceof DataComponentIngredient);
                slots.add(tests ? sized.ingredient() : null);
                any |= tests;
            }
            // Only a Hand-craftable recipe is in the set the Personal Assembler plans with.
            if (any && read.handCraftable()) found.add(new Tested(read, slots));
        }
        if (!refused.isEmpty()) {
            // Named, not counted. A recipe the Assembler will not plan reaches the player as a Crafting
            // Plan with nothing in it, and the only way to tell which recipe and why is to say so here.
            LOGGER.warn("Personal Assembler: {} Assembling recipe(s) refused: {}", refused.size(), refused);
        }
        return builder.build();
    }

    /** The recipe as the Resolver sees it, or null with the reason added to {@code refused}. */
    private static io.github._5thlayer.craftworks.planner.AssemblingRecipe read(
            String id, AssemblingRecipe recipe, HolderLookup.Provider registries, List<String> refused) {
        // The Crafting Plan plans one product per recipe: any other number of item results is never
        // Hand-craftable, and the set holds nothing else, so it is left out rather than refused.
        if (recipe.results().size() != 1) return null;
        List<Ingredient> ingredients = new ArrayList<>();
        for (SizedIngredient sized : recipe.ingredients()) {
            List<String> items = new ArrayList<>();
            Map<String, String> remainders = new LinkedHashMap<>();
            for (ItemStack match : matches(sized)) {
                // A key that names nothing is refused here and nowhere else: the Resolver stays free of
                // any notion of resolvability, and the failure arrives as a named line rather than as a
                // queue that pauses forever with nothing in the log.
                String key = ItemKeys.of(match, registries);
                if (key == null) {
                    refused.add(id + " (an ingredient with a component nothing can name on " + match.getItem() + ")");
                    return null;
                }
                if (items.contains(key)) continue;
                items.add(key);
                ItemStackTemplate remainder = match.getItem().getCraftingRemainder(match);
                if (remainder != null) {
                    String left = ItemKeys.of(remainder.create(), registries);
                    if (left == null) {
                        refused.add(id + " (a remainder nothing can name, left by " + key + ")");
                        return null;
                    }
                    remainders.put(key, left);
                }
            }
            if (items.isEmpty()) {
                refused.add(id + " (an ingredient no item satisfies)");
                return null;
            }
            ingredients.add(new Ingredient(items, sized.count(), remainders));
        }
        ItemStackTemplate product = recipe.productTemplate().orElseThrow();
        String result = ItemKeys.of(product.create(), registries);
        if (result == null) {
            refused.add(id + " (a result with a component nothing can name)");
            return null;
        }
        if (recipe.time() < 0) {
            refused.add(id + " (a time of " + recipe.time() + " ticks)");
            return null;
        }
        // A recipe with a fluid is never Hand-craftable, whatever its flag says (5thlayer/factoryworks#578).
        boolean handCraftable = recipe.handCraftable()
                && recipe.fluidIngredients().isEmpty() && recipe.fluidResults().isEmpty();
        return new io.github._5thlayer.craftworks.planner.AssemblingRecipe(
                id, ingredients, new ItemAmount(result, product.count()), recipe.time(),
                recipe.priority(), handCraftable, DataCarryingRecipes.isDataCarrying(Identifier.parse(id)));
    }

    /**
     * One stack per item the ingredient accepts.
     *
     * <p>A component ingredient is read as its items carrying its components (ADR-0002): one item can be
     * several keys. Any other custom ingredient contributes its bare items.
     */
    private static List<ItemStack> matches(SizedIngredient sized) {
        DataComponentPatch components = sized.ingredient().getCustomIngredient() instanceof DataComponentIngredient data
                ? data.components()
                : DataComponentPatch.EMPTY;
        List<ItemStack> stacks = new ArrayList<>();
        for (Holder<Item> item : sized.ingredient().items().toList()) {
            stacks.add(new ItemStack(item, 1, components));
        }
        return stacks;
    }
}
