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
     * What can lock a recipe for a player, besides any registered hook (GLOSSARY.md, Lock source).
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

    /** One tier's figures, in the config's section for its block. */
    public record MachineSettings(ModConfigSpec.DoubleValue speed, ModConfigSpec.DoubleValue power,
            ModConfigSpec.IntValue buffer, ModConfigSpec.ConfigValue<List<? extends String>> categories) {
    }

    /** The Refiner's figures, in the config's {@code refiner} section. */
    public record RefinerSettings(ModConfigSpec.DoubleValue speed, ModConfigSpec.DoubleValue power,
            ModConfigSpec.IntValue buffer) {
    }

    /** The Refiner's default crafting speed, Factorio's electric furnace's (ADR-0020). */
    public static final double REFINER_SPEED = 2.0;

    /** The Refiner's default FE a tick: Factorio's 180 kW at 1 FE = 100 J. */
    public static final double REFINER_POWER = 90.0;

    /** The Refiner's default buffer in FE: a little over two vanilla smelts' worth at the default speed and power. */
    public static final int REFINER_BUFFER = 20_000;

    private static final RefinerSettings REFINER;

    /** The figures of each Assembler tier, in the config's section for its block. */
    private static final Map<AssemblerTier, MachineSettings> MACHINES = new EnumMap<>(AssemblerTier.class);

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
                            "a tick it can't be paid in full makes no progress, and an idle machine draws nothing.")
                    .defineInRange("power", tier.defaultPower(), 0.0, 1_000_000.0);
            ModConfigSpec.IntValue buffer = builder
                    .comment("FE the energy buffer holds.")
                    .defineInRange("buffer", tier.defaultBuffer(), 1, Integer.MAX_VALUE);
            var categories = builder
                    .comment("The categories of Assembling recipe this machine can hold: "
                            + AssemblingCategory.ids() + ".",
                            "Fill Recipe refuses a recipe whose category is not listed.")
                    .defineListAllowEmpty("categories",
                            tier.defaultCategories().stream().map(AssemblingCategory::id).toList(),
                            () -> AssemblingCategory.CRAFTING.id(),
                            AssemblingCategory::isId);
            builder.pop();
            MACHINES.put(tier, new MachineSettings(speed, power, buffer, categories));
        }
        builder.comment("The refiner block.").push("refiner");
        REFINER = new RefinerSettings(
                builder.comment("Crafting speed: a smelt takes the recipe's cooking time divided by this, in ticks.")
                        .defineInRange("speed", REFINER_SPEED, 0.01, 1000.0),
                builder.comment("FE a tick while smelting. A smelt costs this times the ticks it takes, spread over them;",
                                "a tick it can't be paid in full makes no progress, and an idle Refiner draws nothing.")
                        .defineInRange("power", REFINER_POWER, 0.0, 1_000_000.0),
                builder.comment("FE the energy buffer holds.")
                        .defineInRange("buffer", REFINER_BUFFER, 1, Integer.MAX_VALUE));
        builder.pop();
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
    public static double speed(AssemblerTier tier) {
        return SPEC.isLoaded() ? MACHINES.get(tier).speed().get() : tier.defaultSpeed();
    }

    /** The tier's FE a tick while crafting; its default until a world's config is loaded. */
    public static double power(AssemblerTier tier) {
        return SPEC.isLoaded() ? MACHINES.get(tier).power().get() : tier.defaultPower();
    }

    /** The tier's energy buffer in FE; its default until a world's config is loaded. */
    public static int buffer(AssemblerTier tier) {
        return SPEC.isLoaded() ? MACHINES.get(tier).buffer().get() : tier.defaultBuffer();
    }

    /** The Refiner's crafting speed; its default until a world's config is loaded. */
    public static double refinerSpeed() {
        return SPEC.isLoaded() ? REFINER.speed().get() : REFINER_SPEED;
    }

    /** The Refiner's FE a tick while smelting; its default until a world's config is loaded. */
    public static double refinerPower() {
        return SPEC.isLoaded() ? REFINER.power().get() : REFINER_POWER;
    }

    /** The Refiner's energy buffer in FE; its default until a world's config is loaded. */
    public static int refinerBuffer() {
        return SPEC.isLoaded() ? REFINER.buffer().get() : REFINER_BUFFER;
    }

    /** The tier's {@code categories} setting, which a game test sets as a server's config would. */
    public static ModConfigSpec.ConfigValue<List<? extends String>> categoriesSetting(AssemblerTier tier) {
        return MACHINES.get(tier).categories();
    }

    /** The recipe categories the tier holds; its defaults until a world's config is loaded. */
    public static List<AssemblingCategory> categories(AssemblerTier tier) {
        if (!SPEC.isLoaded()) return tier.defaultCategories();
        return MACHINES.get(tier).categories().get().stream()
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
