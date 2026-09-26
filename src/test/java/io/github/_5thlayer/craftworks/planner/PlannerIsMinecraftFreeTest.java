// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.planner;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * The planner core imports nothing of Minecraft's or NeoForge's; DataFixerUpper's codecs are the one
 * Mojang library it may use.
 *
 * <p>Minecraft is off the test classpath, but a class no test loads would only fail there at runtime,
 * so the rule is read off the source instead.
 */
class PlannerIsMinecraftFreeTest {

    private static final Path PLANNER = Path.of("src/main/java/io/github/_5thlayer/craftworks/planner");

    private static boolean forbidden(String line) {
        if (!line.startsWith("import ")) return false;
        String name = line.substring("import ".length()).replace("static ", "");
        if (name.startsWith("com.mojang.serialization.") || name.startsWith("com.mojang.datafixers.")) return false;
        return name.startsWith("net.minecraft.") || name.startsWith("net.neoforged.") || name.startsWith("com.mojang.");
    }

    @Test
    void noPlannerSourceImportsMinecraft() throws IOException {
        List<String> offending;
        try (Stream<Path> sources = Files.list(PLANNER)) {
            List<Path> files = sources.filter(path -> path.toString().endsWith(".java")).toList();
            assertFalse(files.isEmpty(), "no planner sources found at " + PLANNER.toAbsolutePath());
            offending = files.stream()
                    .flatMap(path -> lines(path).filter(PlannerIsMinecraftFreeTest::forbidden)
                            .map(line -> path.getFileName() + ": " + line))
                    .toList();
        }
        assertEquals(List.of(), offending);
    }

    private static Stream<String> lines(Path path) {
        try {
            return Files.readAllLines(path).stream().map(String::strip);
        } catch (IOException unreadable) {
            throw new IllegalStateException(unreadable);
        }
    }
}
