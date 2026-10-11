// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;

/** What a Module ships that stops it playing alone on Groundworks (ADR-0127), one scan per rule. */
final class PlaysAlone {

    private static final Set<String> MODULES = Set.of("beltworks", "pipeworks", "wireworks", "craftworks", "fieldworks", "factoryworks");

    private PlaysAlone() {
    }

    /** Each id in another Module's namespace that a file under {@code root} names, a structure's saved block ids included. */
    static List<String> otherModulesIds(Path root, String own) throws IOException {
        String others = String.join("|", MODULES.stream().filter(ns -> isOtherModule(ns, own)).sorted().toList());
        // No word boundary before the namespace: in a structure's NBT a string's length byte can be a letter.
        Pattern ids = Pattern.compile("(" + others + "):[a-z0-9_./-]+");
        List<String> found = new ArrayList<>();
        for (Path file : files(root)) {
            Matcher id = ids.matcher(text(file));
            while (id.find()) {
                found.add(relative(root, file) + " names " + id.group());
            }
        }
        return found;
    }

    /** Each {@code data/<namespace>} or {@code assets/<namespace>} folder under {@code resources} that is another Module's. */
    static List<String> otherModulesNamespaces(Path resources, String own) throws IOException {
        List<String> found = new ArrayList<>();
        for (String kind : List.of("assets", "data")) {
            Path dir = resources.resolve(kind);
            if (!Files.isDirectory(dir)) {
                continue;
            }
            try (Stream<Path> namespaces = Files.list(dir)) {
                namespaces.map(p -> p.getFileName().toString()).filter(ns -> isOtherModule(ns, own)).sorted()
                        .forEach(ns -> found.add(kind + "/" + ns));
            }
        }
        return found;
    }

    /** Each source file outside the game tests that names a {@code TagKey}. */
    static List<String> tagReads(Path code) throws IOException {
        return files(code).stream().map(file -> relative(code, file)).filter(path -> !path.contains("/gametest/"))
                .filter(path -> {
                    try {
                        return Files.readString(code.resolve(path)).contains("TagKey");
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                }).toList();
    }

    private static boolean isOtherModule(String namespace, String own) {
        return MODULES.contains(namespace) && !namespace.equals(own);
    }

    private static List<Path> files(Path root) throws IOException {
        try (Stream<Path> files = Files.walk(root)) {
            return files.filter(Files::isRegularFile).sorted().toList();
        }
    }

    private static String relative(Path root, Path file) {
        return root.relativize(file).toString().replace('\\', '/');
    }

    private static String text(Path file) throws IOException {
        if (file.toString().endsWith(".nbt")) {
            try (InputStream in = new GZIPInputStream(Files.newInputStream(file))) {
                return new String(in.readAllBytes(), StandardCharsets.ISO_8859_1);
            }
        }
        // Latin-1 maps every byte to a char, so a binary file such as a texture still reads.
        return new String(Files.readAllBytes(file), StandardCharsets.ISO_8859_1);
    }
}
