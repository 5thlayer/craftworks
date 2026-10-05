// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import io.github._5thlayer.craftworks.machine.AssemblerTier;
import io.github._5thlayer.craftworks.recipe.AssemblingCategory;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Craftworks' server config, {@code craftworks-server.toml} in a world's {@code serverconfig}. */
public final class CraftworksConfig {

    /**
     * What can lock a recipe for a player, besides any registered hook (CONTEXT.md, Lock source).
     * Named as a pack author writes them in the TOML.
     */
    public enum LockSource {
        /** Locked until the recipe is in the player's vanilla recipe book. */
        recipeBook,
        /** Locked while Researchd says the recipe is blocked for the player's team (ADR-0010). */
        researchd
    }

    static final ModConfigSpec SPEC;

    public static final ModConfigSpec.ConfigValue<List<? extends String>> LOCK_SOURCES;

    public static final ModConfigSpec.BooleanValue VANILLA_RECIPES;

    public static final ModConfigSpec.BooleanValue MOD_RECIPES;

    public static final ModConfigSpec.ConfigValue<List<? extends String>> MOD_RECIPES_EXCLUDED;

    /** One tier's Assembler figures, in the config's section for that tier. */
    public record AssemblerSettings(ModConfigSpec.DoubleValue speed, ModConfigSpec.DoubleValue power,
            ModConfigSpec.IntValue buffer, ModConfigSpec.ConfigValue<List<? extends String>> categories) {
    }

    private static final Map<AssemblerTier, AssemblerSettings> ASSEMBLERS = new EnumMap<>(AssemblerTier.class);

    static {
        var builder = new ModConfigSpec.Builder();
        LOCK_SOURCES = builder
                .comment("What locks a recipe for a player, on top of any hook a mod registers. A recipe is",
                        "Locked if any listed source says so. Empty (the default) locks nothing.",
                        "  recipeBook: Locked until the recipe is in the player's recipe book.",
                        "  researchd:  Locked while Researchd blocks the recipe for the player's team.",
                        "For example [\"researchd\"].")
                .defineListAllowEmpty("lockSources", List.of(), () -> LockSource.researchd.name(),
                        value -> value instanceof String name
                                && Arrays.stream(LockSource.values()).anyMatch(source -> source.name().equals(name)));
        VANILLA_RECIPES = builder
                .comment("Whether vanilla's crafting recipes are Assembling recipes, from the pack built into the",
                        "mod. Off, they stay at the crafting table and a pack ships its own Assembling recipes.")
                .define("vanillaRecipes", true);
        MOD_RECIPES = builder
                .comment("Whether other mods' shaped and shapeless crafting recipes become Assembling recipes at",
                        "their own ids as recipes load (ADR-0011). Off, they stay at the crafting table.")
                .define("modRecipes", true);
        MOD_RECIPES_EXCLUDED = builder
                .comment("Mods' crafting recipes that modRecipes leaves at the crafting table: a namespace keeps",
                        "all of a mod's, a recipe id just that one. For example [\"create\", \"mekanism:jetpack\"].")
                .defineListAllowEmpty("modRecipesExcluded", List.of(), () -> "modid",
                        value -> value instanceof String entry && (entry.contains(":")
                                ? Identifier.tryParse(entry) != null
                                : Identifier.isValidNamespace(entry)));
        for (AssemblerTier tier : AssemblerTier.values()) {
            builder.comment("The " + tier.blockName() + " block.").push(tier.blockName());
            ModConfigSpec.DoubleValue speed = builder
                    .comment("Crafting speed: a craft takes the recipe's time divided by this, in ticks.")
                    .defineInRange("speed", tier.defaultSpeed(), 0.01, 1000.0);
            ModConfigSpec.DoubleValue power = builder
                    .comment("FE a tick while crafting. A craft costs this times the ticks it takes, spread over them;",
                            "a tick it can't be paid in full makes no progress, and an idle Assembler draws nothing.")
                    .defineInRange("power", tier.defaultPower(), 0.0, 1_000_000.0);
            ModConfigSpec.IntValue buffer = builder
                    .comment("FE the energy buffer holds.")
                    .defineInRange("buffer", tier.defaultBuffer(), 1, Integer.MAX_VALUE);
            var categories = builder
                    .comment("The categories of Assembling recipe this tier can hold, by Factorio's names: "
                            + AssemblingCategory.ids() + ".",
                            "Fill Recipe refuses a recipe whose category is not listed.")
                    .defineListAllowEmpty("categories",
                            tier.defaultCategories().stream().map(AssemblingCategory::id).toList(),
                            () -> AssemblingCategory.CRAFTING.id(),
                            value -> value instanceof String name && AssemblingCategory.byId(name).isPresent());
            builder.pop();
            ASSEMBLERS.put(tier, new AssemblerSettings(speed, power, buffer, categories));
        }
        SPEC = builder.build();
    }

    private CraftworksConfig() {
    }

    /** Whether the built-in vanilla pack is on; its default, on, until a world's config is loaded. */
    public static boolean vanillaRecipes() {
        return !SPEC.isLoaded() || VANILLA_RECIPES.get();
    }

    /** Whether mods' crafting recipes convert; its default, on, until a world's config is loaded. */
    public static boolean modRecipes() {
        return !SPEC.isLoaded() || MOD_RECIPES.get();
    }

    /** The namespaces and recipe ids kept from conversion; none until a world's config is loaded. */
    public static List<String> modRecipesExcluded() {
        return SPEC.isLoaded() ? List.copyOf(MOD_RECIPES_EXCLUDED.get()) : List.of();
    }

    /** The tier's crafting speed; its default until a world's config is loaded. */
    public static double assemblerSpeed(AssemblerTier tier) {
        return SPEC.isLoaded() ? ASSEMBLERS.get(tier).speed().get() : tier.defaultSpeed();
    }

    /** The tier's FE a tick while crafting; its default until a world's config is loaded. */
    public static double assemblerPower(AssemblerTier tier) {
        return SPEC.isLoaded() ? ASSEMBLERS.get(tier).power().get() : tier.defaultPower();
    }

    /** The tier's energy buffer in FE; its default until a world's config is loaded. */
    public static int assemblerBuffer(AssemblerTier tier) {
        return SPEC.isLoaded() ? ASSEMBLERS.get(tier).buffer().get() : tier.defaultBuffer();
    }

    /** The recipe categories the tier holds; its defaults until a world's config is loaded. */
    public static List<AssemblingCategory> assemblerCategories(AssemblerTier tier) {
        if (!SPEC.isLoaded()) return tier.defaultCategories();
        return ASSEMBLERS.get(tier).categories().get().stream()
                .flatMap(name -> AssemblingCategory.byId(name).stream()).toList();
    }

    /** The configured sources, each once; none until a world's config is loaded. */
    public static List<LockSource> lockSources() {
        if (!SPEC.isLoaded()) return List.of();
        List<LockSource> sources = new ArrayList<>();
        for (String name : LOCK_SOURCES.get()) {
            LockSource source = LockSource.valueOf(name);
            if (!sources.contains(source)) sources.add(source);
        }
        return sources;
    }
}
