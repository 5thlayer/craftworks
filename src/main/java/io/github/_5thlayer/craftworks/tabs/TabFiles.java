// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.tabs;

import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import io.github._5thlayer.craftworks.Craftworks;
import io.github._5thlayer.craftworks.api.IndexTab;
import io.github._5thlayer.craftworks.api.IndexTabs;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.util.GsonHelper;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import org.slf4j.Logger;

/**
 * The resource packs' index tabs, {@code assets/<namespace>/craftworks/tabs/<id>.json}, read on every
 * resource reload (#15).
 *
 * <p>A file holds {@code icon} (an item id), {@code name} (a translation key), {@code query} and
 * {@code order} (0 when absent), and puts that tab at {@code <namespace>:<id>}, replacing whatever was
 * there. {@code "enabled": false} removes the tab at its id instead. A file that can't be read is
 * logged and skipped, and changes nothing.
 */
public final class TabFiles implements ResourceManagerReloadListener {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String DIRECTORY = "craftworks/tabs";
    private static final String EXTENSION = ".json";

    private static volatile List<TabFile> files = List.of();

    /** Reads the tab files on every resource reload. */
    public static void register(IEventBus modBus) {
        modBus.addListener(AddClientReloadListenersEvent.class, event -> event.addListener(
                Identifier.fromNamespaceAndPath(Craftworks.MOD_ID, "index_tabs"), new TabFiles()));
    }

    /** The tab row: the tabs the Java API registered, with the resource packs' files over them. */
    public static List<IndexTab> row() {
        return TabMerge.merge(IndexTabs.all(), files);
    }

    @Override
    public void onResourceManagerReload(ResourceManager resources) {
        List<TabFile> read = new ArrayList<>();
        for (Map.Entry<Identifier, Resource> entry
                : resources.listResources(DIRECTORY, path -> path.getPath().endsWith(EXTENSION)).entrySet()) {
            Identifier file = entry.getKey();
            String path = file.getPath();
            String id = file.getNamespace() + ":"
                    + path.substring(DIRECTORY.length() + 1, path.length() - EXTENSION.length());
            try (Reader reader = entry.getValue().openAsReader()) {
                read.add(parse(id, GsonHelper.parse(reader)));
            } catch (Exception unreadable) {
                LOGGER.error("Skipping index tab file {}: {}", file, unreadable.getMessage());
            }
        }
        files = List.copyOf(read);
    }

    private static TabFile parse(String id, JsonObject json) {
        if (!GsonHelper.getAsBoolean(json, "enabled", true)) return TabFile.disabled(id);
        return TabFile.of(new IndexTab(id,
                GsonHelper.getAsString(json, "icon"),
                GsonHelper.getAsString(json, "name"),
                GsonHelper.getAsString(json, "query"),
                GsonHelper.getAsInt(json, "order", 0)));
    }
}
