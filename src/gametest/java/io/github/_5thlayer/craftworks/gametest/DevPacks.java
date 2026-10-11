// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.gametest;

import io.github._5thlayer.craftworks.Craftworks;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddPackFindersEvent;

/**
 * Switches on the Mod's dev pack, {@code dev_pack/}, in the {@code client} run and its game-test
 * pack, {@code gametest_pack/}, in {@code gameTestServer}, each when the Mod has one: datapacks
 * in {@code src/gametest/resources}, which only the Mod's own runs load and the jar leaves out
 * (libworks ADR 0004). The convention plugin names the run in {@code libworks.run}.
 */
@EventBusSubscriber(modid = Craftworks.MOD_ID)
final class DevPacks {

    private DevPacks() {
    }

    @SubscribeEvent
    static void addPackFinders(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.SERVER_DATA) {
            return;
        }
        String folder = switch (System.getProperty("libworks.run", "")) {
            case "client" -> "dev_pack";
            case "gameTestServer" -> "gametest_pack";
            default -> null;
        };
        if (folder == null || !has(folder)) {
            return;
        }
        event.addPackFinders(Identifier.fromNamespaceAndPath(Craftworks.MOD_ID, folder), PackType.SERVER_DATA,
                Component.literal(Craftworks.MOD_ID + " " + folder), PackSource.BUILT_IN, true, Pack.Position.TOP);
    }

    private static boolean has(String folder) {
        return ModList.get().getModFileById(Craftworks.MOD_ID).getFile().getContents().findFile(folder + "/pack.mcmeta").isPresent();
    }
}
