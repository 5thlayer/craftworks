// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.GZIPOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The Mod plays alone on Groundworks (FactoryWorks ADR-0127): nothing it ships names another Module's id or
 * holds data or assets in its namespace. The tags it consumes are checked by {@code PlaysAloneTests} in game,
 * where they resolve.
 */
class PlaysAloneTest {

    private static final Path CODE = Path.of("src/main/java");
    private static final Path RESOURCES = Path.of("src/main/resources");
    // Generated before the tests run, as the main resources are, and shipped in the jar with them.
    private static final Path VANILLA_PACK = Path.of("build/generated/vanillaPack");
    private static final Path TAG_CHECK = CODE.resolve("io/github/_5thlayer/craftworks/gametest/PlaysAloneTests.java");

    @Test
    void theShippedCodeAndResourcesNameNoOtherModulesId() throws IOException {
        assertEquals(List.of(), PlaysAlone.otherModulesIds(CODE));
        assertEquals(List.of(), PlaysAlone.otherModulesIds(RESOURCES));
        assertEquals(List.of(), PlaysAlone.otherModulesIds(VANILLA_PACK));
    }

    @Test
    void theShippedCodeNamesNoOtherModulesNamespace() throws IOException {
        assertEquals(List.of(), PlaysAlone.otherModulesNamespaceStrings(CODE));
    }

    @Test
    void theShippedModsTomlRequiresNoOtherModule() throws IOException {
        assertEquals(List.of(), PlaysAlone.otherModulesRequired(RESOURCES));
    }

    @Test
    void theShippedResourcesHoldNothingInAnotherModulesNamespace() throws IOException {
        assertEquals(List.of(), PlaysAlone.otherModulesNamespaces(RESOURCES));
    }

    /** A tag the code starts reading fails here until {@code PlaysAloneTests} checks it in game. */
    @Test
    void everyTagTheCodeReadsIsCheckedInGame() throws IOException {
        assertEquals(List.of(), PlaysAlone.tagsReadButUnchecked(CODE, TAG_CHECK));
    }

    @Test
    void aTagTheCodeReadsOrDeclaresIsReportedUntilTheCheckNamesIt(@TempDir Path code) throws IOException {
        write(code.resolve("a/Fuel.java"), "class Fuel { static final TagKey<Item> FUEL = ItemTags.create(id(\"fuel\")); }");
        write(code.resolve("a/Burn.java"), "class Burn { boolean b(ItemStack s) { return s.is(ItemTags.LOGS) || s.is(Fuel.FUEL); } }");
        write(code.resolve("a/Mine.java"), "class Mine { boolean m(BlockState s) { return s.is(Tags.Blocks.ORES) && !s.is(BlockTags.LOGS); } }");
        write(code.resolve("a/Flow.java"), "class Flow { boolean f(FluidState s) { return s.is(FluidTags.LAVA); } }");
        write(code.resolve("a/Lazy.java"), "class Lazy { boolean l(ItemStack s) { return s.is(TagKey.create(Registries.ITEM, id(\"x\"))); } }");
        write(code.resolve("a/gametest/Check.java"), "class Check { List<TagKey<?>> checked = List.of(FluidTags.LAVA, BlockTags.LOGS); }");
        assertEquals(List.of("a/Burn.java reads ItemTags.LOGS", "a/Fuel.java declares Fuel.FUEL", "a/Lazy.java creates a TagKey outside a constant",
                "a/Mine.java reads Tags.Blocks.ORES"), PlaysAlone.tagsReadButUnchecked(code, code.resolve("a/gametest/Check.java")));
    }

    @Test
    void aDataOrAssetFolderInAnotherModulesNamespaceIsReported(@TempDir Path resources) throws IOException {
        write(resources.resolve("data/fieldworks/recipe/x.json"), "{}");
        write(resources.resolve("assets/voidworks/lang/en_us.json"), "{}");
        write(resources.resolve("data/craftworks/recipe/x.json"), "{}");
        write(resources.resolve("data/groundworks/tags/item/dismantles.json"), "{}");
        assertEquals(List.of("assets/voidworks", "data/fieldworks"), PlaysAlone.otherModulesNamespaces(resources));
    }

    @Test
    void anIdInDataIsReported(@TempDir Path resources) throws IOException {
        write(resources.resolve("data/craftworks/recipe/x.json"), "{\"key\":{\"a\":\"gearworks:gear\"}}");
        assertEquals(List.of("data/craftworks/recipe/x.json names gearworks:gear"), PlaysAlone.otherModulesIds(resources));
    }

    @Test
    void aTagOfTheRetiredPackInARecipeIsReported(@TempDir Path resources) throws IOException {
        write(resources.resolve("data/craftworks/recipe/x.json"), "{\"key\":{\"a\":\"#factoryworks:belts\"}}");
        assertEquals(List.of("data/craftworks/recipe/x.json names factoryworks:belts"), PlaysAlone.otherModulesIds(resources));
    }

    @Test
    void anIdInJavaSourceIsReported(@TempDir Path code) throws IOException {
        write(code.resolve("x/Pipes.java"), "class Pipes { String id = \"pipeworks:pipe\"; }");
        assertEquals(List.of("x/Pipes.java names pipeworks:pipe"), PlaysAlone.otherModulesIds(code));
    }

    @Test
    void anIdInTheModsTomlIsReported(@TempDir Path resources) throws IOException {
        write(resources.resolve("META-INF/neoforge.mods.toml"), "description = \"fills wireworks:cable\"");
        assertEquals(List.of("META-INF/neoforge.mods.toml names wireworks:cable"), PlaysAlone.otherModulesIds(resources));
    }

    @Test
    void anIdInAStructureIsReportedWhenItsLengthByteIsALetter(@TempDir Path resources) throws IOException {
        String id = "fieldworks:" + "o".repeat('A' - "fieldworks:".length());
        Path nbt = resources.resolve("data/craftworks/structure/x.nbt");
        Files.createDirectories(nbt.getParent());
        try (OutputStream out = new GZIPOutputStream(Files.newOutputStream(nbt))) {
            out.write(("\u0008\u0000\u0004Name\u0000" + (char) id.length() + id).getBytes(StandardCharsets.ISO_8859_1));
        }
        assertEquals(List.of("data/craftworks/structure/x.nbt names " + id), PlaysAlone.otherModulesIds(resources));
    }

    @Test
    void anotherModulesNamespaceInJavaSourceIsReported(@TempDir Path code) throws IOException {
        write(code.resolve("x/Pipes.java"), "class Pipes { Identifier id = Identifier.fromNamespaceAndPath(\"pipeworks\", \"pipe\"); }");
        write(code.resolve("x/Own.java"), "class Own { String ns = \"craftworks\"; String gw = \"groundworks\"; }");
        assertEquals(List.of("x/Pipes.java names \"pipeworks\""), PlaysAlone.otherModulesNamespaceStrings(code));
    }

    @Test
    void aRequiredDependencyOnAnotherModuleIsReported(@TempDir Path resources) throws IOException {
        write(resources.resolve("META-INF/neoforge.mods.toml"), """
                [[mods]]
                modId = "craftworks"

                [[dependencies.craftworks]]
                modId = "groundworks"
                type = "required"

                [[dependencies.craftworks]]
                modId = "beltworks"
                type = "required"

                [[dependencies.craftworks]]
                modId="wireworks"
                """);
        assertEquals(List.of("META-INF/neoforge.mods.toml requires beltworks", "META-INF/neoforge.mods.toml requires wireworks"),
                PlaysAlone.otherModulesRequired(resources));
    }

    @Test
    void anOptionalDependencyOnAnotherModuleIsAllowed(@TempDir Path resources) throws IOException {
        write(resources.resolve("META-INF/neoforge.mods.toml"), """
                [[dependencies.craftworks]]
                modId = "pipeworks"
                type = "optional"
                """);
        assertEquals(List.of(), PlaysAlone.otherModulesRequired(resources));
    }

    @Test
    void theModsOwnIdsGroundworksAndTheSharedNamespacesAreAllowed(@TempDir Path resources) throws IOException {
        write(resources.resolve("assets/craftworks/models/x.json"),
                "{\"a\":\"craftworks:block/x\",\"b\":\"minecraft:glass\",\"c\":\"#c:glass_blocks\",\"d\":\"groundworks:x\",\"e\":\"neoforge:y\"}");
        assertEquals(List.of(), PlaysAlone.otherModulesIds(resources));
    }

    private static void write(Path file, String text) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, text);
    }
}
