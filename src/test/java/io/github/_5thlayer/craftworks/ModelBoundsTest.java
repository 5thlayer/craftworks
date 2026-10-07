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

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

/**
 * Minecraft refuses a model whose element's {@code from} or {@code to} is below -16 or above 32 on any axis
 * (CuboidModelElement), and the headless runs never load a model, so only the dev client would say. A footprint
 * wider than three blocks is drawn by a composite of children placed by {@code transform}, each inside the
 * bounds; see {@code scripts/build-machine-models.py}.
 */
class ModelBoundsTest {

    private static final Path MODELS = Path.of("src/main/resources/assets/craftworks/models");
    private static final double LOW = -16;
    private static final double HIGH = 32;

    @Test
    void everyElementOfEveryModelIsInsideTheBoundsMinecraftAllows() throws IOException {
        List<String> outside = new ArrayList<>();
        int models = 0;
        try (Stream<Path> files = Files.walk(MODELS)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".json")).toList()) {
                models++;
                try (Reader reader = Files.newBufferedReader(file)) {
                    check(file.toString(), JsonParser.parseReader(reader), outside);
                }
            }
        }
        assertTrue(models > 0, "no models were found under " + MODELS.toAbsolutePath());
        assertEquals(List.of(), outside);
    }

    /** Looks at every {@code elements} list anywhere in the model, such as a composite's inline children. */
    private static void check(String where, JsonElement node, List<String> outside) {
        if (node.isJsonObject()) {
            JsonObject object = node.getAsJsonObject();
            if (object.has("elements")) {
                int index = 0;
                for (JsonElement element : object.getAsJsonArray("elements")) {
                    for (String key : new String[] {"from", "to"}) {
                        JsonArray corner = element.getAsJsonObject().getAsJsonArray(key);
                        for (JsonElement value : corner) {
                            if (value.getAsDouble() < LOW || value.getAsDouble() > HIGH) {
                                outside.add(where + " element " + index + " " + key + " " + corner);
                            }
                        }
                    }
                    index++;
                }
            }
            object.entrySet().forEach(entry -> check(where + "/" + entry.getKey(), entry.getValue(), outside));
        }
    }
}
