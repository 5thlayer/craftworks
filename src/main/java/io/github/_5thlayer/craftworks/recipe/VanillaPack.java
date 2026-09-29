// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.recipe;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import com.mojang.logging.LogUtils;
import io.github._5thlayer.craftworks.Craftworks;
import io.github._5thlayer.craftworks.CraftworksConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;

/**
 * The built-in vanilla pack: vanilla's crafting recipes as Assembling recipes at their own ids, so they
 * replace the originals and recipe unlocks still match (#9). The build generates it from the game jar
 * into {@code vanilla_pack/}; nothing in it is written by hand.
 *
 * <p>The server config's {@code vanillaRecipes} flag decides whether it is selected. The flag is read
 * after the world's datapacks have loaded, since NeoForge loads a server config only as the server
 * starts, so the pack is offered like any built-in pack and put in line with the flag then: when the two
 * disagree the recipes reload once, before anyone joins. A config edit while running does the same.
 */
public final class VanillaPack {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** The pack's id in the pack repository, as {@link AddPackFindersEvent} names a mod's pack. */
    public static final String ID = "mod/" + Craftworks.MOD_ID + ":vanilla_pack";

    private VanillaPack() {
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(VanillaPack::addPack);
        modBus.addListener(VanillaPack::onConfigReloaded);
        NeoForge.EVENT_BUS.addListener(VanillaPack::onServerAboutToStart);
    }

    private static void addPack(AddPackFindersEvent event) {
        event.addPackFinders(Identifier.fromNamespaceAndPath(Craftworks.MOD_ID, "vanilla_pack"), PackType.SERVER_DATA,
                Component.literal("Craftworks vanilla recipes"), PackSource.BUILT_IN, false, Pack.Position.TOP);
    }

    private static void onServerAboutToStart(ServerAboutToStartEvent event) {
        follow(event.getServer());
    }

    private static void onConfigReloaded(ModConfigEvent.Reloading event) {
        if (event.getConfig().getType() != ModConfig.Type.SERVER) return;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) server.execute(() -> follow(server));
    }

    /**
     * Selects the pack or drops it to match the flag, reloading the recipes if that changed anything;
     * done at once when it already matches.
     */
    public static CompletableFuture<Void> follow(MinecraftServer server) {
        boolean wanted = CraftworksConfig.vanillaRecipes();
        List<String> selected = new ArrayList<>(server.getPackRepository().getSelectedIds());
        if (selected.contains(ID) == wanted) return CompletableFuture.completedFuture(null);
        // Last is highest: the pack must sit above vanilla's to replace its recipes.
        if (wanted) selected.add(ID);
        else selected.remove(ID);
        LOGGER.info("Personal Assembler: vanillaRecipes is {}, reloading with the built-in vanilla pack {}",
                wanted, wanted ? "on" : "off");
        return server.reloadResources(selected);
    }
}
