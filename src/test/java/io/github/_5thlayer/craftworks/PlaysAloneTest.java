// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The Mod plays alone on Groundworks (ADR-0127): nothing it ships names another Module's id or holds
 * data or assets in its namespace. The tags it consumes are checked in game, where they resolve.
 */
class PlaysAloneTest {

    private static final Path CODE = Path.of("src/main/java");
    private static final Path RESOURCES = Path.of("src/main/resources");

    @Test
    void theShippedCodeAndResourcesNameNoOtherModulesId() throws IOException {
        assertEquals(List.of(), PlaysAlone.otherModulesIds(CODE, Craftworks.MOD_ID));
        assertEquals(List.of(), PlaysAlone.otherModulesIds(RESOURCES, Craftworks.MOD_ID));
    }

    @Test
    void theShippedResourcesHoldNothingInAnotherModulesNamespace() throws IOException {
        assertEquals(List.of(), PlaysAlone.otherModulesNamespaces(RESOURCES, Craftworks.MOD_ID));
    }

    @Test
    void aStringLiteralInTheCodeIsReported(@TempDir Path code) throws IOException {
        write(code.resolve("a/Pipes.java"), "class Pipes { String pipe = \"pipeworks:pipe\"; }");
        assertEquals(List.of("a/Pipes.java names pipeworks:pipe"), PlaysAlone.otherModulesIds(code, Craftworks.MOD_ID));
    }

    @Test
    void anIdInAnAssetIsReported(@TempDir Path resources) throws IOException {
        write(resources.resolve("assets/craftworks/models/block/x.json"), "{\"parent\":\"beltworks:block/belt\"}");
        assertEquals(List.of("assets/craftworks/models/block/x.json names beltworks:block/belt"),
                PlaysAlone.otherModulesIds(resources, Craftworks.MOD_ID));
    }

    @Test
    void anIdInTheModsTomlIsReported(@TempDir Path resources) throws IOException {
        write(resources.resolve("META-INF/neoforge.mods.toml"), "description = \"fills wireworks:pole\"");
        assertEquals(List.of("META-INF/neoforge.mods.toml names wireworks:pole"), PlaysAlone.otherModulesIds(resources, Craftworks.MOD_ID));
    }

    @Test
    void aTagOfAnotherModuleInARecipeIsReported(@TempDir Path resources) throws IOException {
        write(resources.resolve("data/craftworks/recipe/x.json"), "{\"key\":{\"a\":\"#factoryworks:belts\"}}");
        assertEquals(List.of("data/craftworks/recipe/x.json names factoryworks:belts"), PlaysAlone.otherModulesIds(resources, Craftworks.MOD_ID));
    }

    @Test
    void anIdInAStructureIsReported(@TempDir Path resources) throws IOException {
        Path nbt = resources.resolve("data/craftworks/structure/x.nbt");
        Files.createDirectories(nbt.getParent());
        try (var out = new java.util.zip.GZIPOutputStream(Files.newOutputStream(nbt))) {
            out.write("\u0008\u0000\u0004Name\u0000\u000efieldworks:ore".getBytes(java.nio.charset.StandardCharsets.ISO_8859_1));
        }
        assertEquals(List.of("data/craftworks/structure/x.nbt names fieldworks:ore"), PlaysAlone.otherModulesIds(resources, Craftworks.MOD_ID));
    }

    @Test
    void theModsOwnIdsGroundworksAndTheSharedNamespacesAreAllowed(@TempDir Path resources) throws IOException {
        write(resources.resolve("data/craftworks/recipe/x.json"),
                "{\"a\":\"minecraft:glass\",\"b\":\"#c:glass_blocks\",\"c\":\"groundworks:x\",\"d\":\"neoforge:y\",\"e\":\"craftworks:assembler_1\"}");
        assertEquals(List.of(), PlaysAlone.otherModulesIds(resources, Craftworks.MOD_ID));
    }

    @Test
    void aDataOrAssetFolderInAnotherModulesNamespaceIsReported(@TempDir Path resources) throws IOException {
        write(resources.resolve("data/fieldworks/recipe/x.json"), "{}");
        write(resources.resolve("assets/pipeworks/lang/en_us.json"), "{}");
        write(resources.resolve("data/groundworks/tags/item/dismantles.json"), "{}");
        assertEquals(List.of("assets/pipeworks", "data/fieldworks"), PlaysAlone.otherModulesNamespaces(resources, Craftworks.MOD_ID));
    }

    private static void write(Path file, String text) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, text);
    }
}
