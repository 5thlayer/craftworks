// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

/**
 * A model naming a Craftworks texture that isn't there draws Minecraft's missing texture, and the headless runs
 * never load a model, so only the dev client would say. The Assemblers' casings, written by
 * {@code scripts/build-machine-models.py}, name many sprites each; this catches one left out.
 */
class ModelTexturesTest {

    private static final Path ASSETS = Path.of("src/main/resources/assets/craftworks");
    private static final String NAMESPACE = "craftworks:";

    @Test
    void everyCraftworksTextureAModelNamesExists() throws IOException {
        List<String> missing = new ArrayList<>();
        int models = 0;
        try (Stream<Path> files = Files.walk(ASSETS.resolve("models"))) {
            for (Path file : files.filter(path -> path.toString().endsWith(".json")).toList()) {
                models++;
                try (Reader reader = Files.newBufferedReader(file)) {
                    check(file.toString(), JsonParser.parseReader(reader), missing);
                }
            }
        }
        assertTrue(models > 0, "no models were found under " + ASSETS.toAbsolutePath());
        assertEquals(List.of(), missing);
    }

    /** Looks at every {@code textures} map anywhere in the model, such as a composite's inline children. */
    private static void check(String where, JsonElement node, List<String> missing) {
        if (node.isJsonObject()) {
            JsonObject object = node.getAsJsonObject();
            if (object.has("textures")) {
                for (var texture : object.getAsJsonObject("textures").entrySet()) {
                    String name = texture.getValue().getAsString();
                    if (name.startsWith(NAMESPACE)) {
                        Path png = ASSETS.resolve("textures").resolve(name.substring(NAMESPACE.length()) + ".png");
                        if (!Files.isRegularFile(png)) {
                            missing.add(where + " " + texture.getKey() + " " + name);
                        }
                    }
                }
            }
            object.entrySet().forEach(entry -> check(where + "/" + entry.getKey(), entry.getValue(), missing));
        }
    }
}
