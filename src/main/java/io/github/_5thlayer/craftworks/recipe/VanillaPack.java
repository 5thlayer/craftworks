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
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;

/**
 * The built-in vanilla pack: vanilla's crafting recipes as Assembling recipes at their own ids, so they
 * replace the originals and recipe unlocks still match (#9). The build generates it from the game jar
 * into {@code vanilla_pack/}; nothing in it is written by hand.
 *
 * <p>The server config's {@code vanillaRecipes} flag decides whether it is selected. The flag is read
 * after the world's datapacks have loaded, since NeoForge loads a server config only as the server
 * starts, so the pack is offered like any built-in pack and put in line with the flag once the server
 * has started: when the two disagree the recipes reload once, before the first tick. A config edit while
 * running does the same. The flag is the authority: a pack disabled by hand comes back at the next
 * start while the flag is on.
 */
public final class VanillaPack {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** The pack's id in the pack repository, as {@link AddPackFindersEvent} names a mod's pack. */
    public static final String ID = "mod/" + Craftworks.MOD_ID + ":vanilla_pack";

    /**
     * Every mod jar's data in one pack. NeoForge's own is in it and replaces some of vanilla's crafting
     * recipes (flint and steel, with common tags), so the pack goes above it or those stay instant.
     */
    private static final String MOD_DATA = "mod_data";

    private VanillaPack() {
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(VanillaPack::addPack);
        modBus.addListener(VanillaPack::onConfigReloaded);
        NeoForge.EVENT_BUS.addListener(VanillaPack::onServerStarted);
    }

    private static void addPack(AddPackFindersEvent event) {
        event.addPackFinders(Identifier.fromNamespaceAndPath(Craftworks.MOD_ID, "vanilla_pack"), PackType.SERVER_DATA,
                Component.literal("Craftworks vanilla recipes"), PackSource.BUILT_IN, false, Pack.Position.TOP);
    }

    /**
     * Once the levels exist, which a reload's datapack sync needs. The server waits for the reload
     * itself, on its own thread, before the tick loop that would let a player in.
     */
    private static void onServerStarted(ServerStartedEvent event) {
        followConfig(event.getServer());
    }

    private static void onConfigReloaded(ModConfigEvent.Reloading event) {
        if (event.getConfig().getType() != ModConfig.Type.SERVER) return;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) server.execute(() -> followConfig(server));
    }

    /**
     * Puts the recipes in line with the server config: this pack, then the mods' Converted recipes
     * ({@link ModRecipes}), which a reload for the pack has already brought in line.
     */
    private static void followConfig(MinecraftServer server) {
        follow(server).thenCompose(ignored -> ModRecipes.follow(server));
    }

    /**
     * Selects the pack or drops it to match the flag, reloading the recipes if that changed anything;
     * done at once when it already matches.
     *
     * <p>Selected, it sits directly above the mods' data: above every crafting recipe it replaces, and
     * below any datapack a pack author adds, so theirs at the same ids win. A pack the game added
     * automatically lands on top, so it is moved down here too.
     */
    public static CompletableFuture<Void> follow(MinecraftServer server) {
        boolean wanted = CraftworksConfig.vanillaRecipes();
        List<String> selected = List.copyOf(server.getPackRepository().getSelectedIds());
        List<String> placed = new ArrayList<>(selected);
        placed.remove(ID);
        if (wanted) placed.add(placed.indexOf(MOD_DATA) + 1, ID);
        if (placed.equals(selected)) return CompletableFuture.completedFuture(null);
        LOGGER.info("Personal Assembler: vanillaRecipes is {}, reloading with the built-in vanilla pack {}",
                wanted, wanted ? "above the mods' data" : "off");
        return server.reloadResources(placed);
    }
}
