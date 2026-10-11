// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.gametest;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import io.github._5thlayer.craftworks.Craftworks;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.neoforged.fml.ModList;

/** The Mod plays alone on Groundworks (ADR-0127). */
final class PlaysAloneTests {

    /** libworks-runtime is nested in the Mod's jar. */
    private static final Set<String> ALONE = Set.of("minecraft", "neoforge", "groundworks", "libworks", Craftworks.MOD_ID);

    /** A -PwithKubeJS run puts KubeJS on the server for its own tests, with the mods it requires. */
    private static final Set<String> KUBEJS = Set.of("kubejs", "rhino", "betteradvancedtooltips");

    private static final Set<String> FILLS_ALONE = Set.of("minecraft", Craftworks.MOD_ID);

    private static final Pattern HASHED_TAG = Pattern.compile("\"#([a-z0-9_.-]+:[a-z0-9_./-]+)\"");
    private static final Pattern TAG_FIELD = Pattern.compile("\"tag\"\\s*:\\s*\"([a-z0-9_.-]+:[a-z0-9_./-]+)\"");

    private PlaysAloneTests() {
    }

    static void register(CraftworksGameTests.Registrar tests, boolean kubeJS) {
        tests.test("only_groundworks_is_loaded_beside_the_mod", 1, helper -> onlyGroundworks(helper, kubeJS));
        tests.test("every_tag_the_mods_data_names_holds_an_entry_of_minecraft_or_craftworks", 1, PlaysAloneTests::tagsFillAlone);
    }

    private static void onlyGroundworks(GameTestHelper helper, boolean kubeJS) {
        List<String> extra = ModList.get().getMods().stream().map(m -> m.getModId())
                .filter(id -> !ALONE.contains(id) && !(kubeJS && KUBEJS.contains(id))).toList();
        helper.assertTrue(extra.isEmpty(), "the run loads " + extra + " beside Groundworks");
        helper.succeed();
    }

    /**
     * Each tag the Mod's data names, in a recipe, a loot table or a tag of its own, holds an entry of Minecraft or the
     * Mod. A recipe that names a tag no mod fills does not load at all, so the tags are read from the jar's data, not
     * from the recipes that loaded.
     */
    private static void tagsFillAlone(GameTestHelper helper) {
        Set<Identifier> tags = new TreeSet<>();
        try {
            for (Path root : ModList.get().getModFileById(Craftworks.MOD_ID).getFile().getContents().getContentRoots()) {
                Path data = root.resolve("data");
                if (!Files.isDirectory(data)) {
                    continue;
                }
                try (Stream<Path> files = Files.walk(data)) {
                    for (Path file : files.filter(f -> f.toString().endsWith(".json")).toList()) {
                        tags.addAll(tagsNamedIn(Files.readString(file)));
                    }
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        var registries = helper.getLevel().registryAccess();
        List<Identifier> empty = tags.stream()
                .filter(tag -> registries.registries().noneMatch(registry -> fillsAlone(registry.value(), tag))).toList();
        helper.assertTrue(empty.isEmpty(), "these tags hold no entry of " + FILLS_ALONE + ": " + empty);
        helper.succeed();
    }

    private static Set<Identifier> tagsNamedIn(String json) {
        Set<Identifier> tags = new TreeSet<>();
        Matcher hashed = HASHED_TAG.matcher(json);
        while (hashed.find()) tags.add(Identifier.parse(hashed.group(1)));
        Matcher field = TAG_FIELD.matcher(json);
        while (field.find()) tags.add(Identifier.parse(field.group(1)));
        return tags;
    }

    private static <T> boolean fillsAlone(Registry<T> registry, Identifier tag) {
        return registry.get(TagKey.create(registry.key(), tag)).stream().flatMap(HolderSet::stream)
                .anyMatch(entry -> entry.unwrapKey().filter(key -> FILLS_ALONE.contains(key.identifier().getNamespace())).isPresent());
    }
}
