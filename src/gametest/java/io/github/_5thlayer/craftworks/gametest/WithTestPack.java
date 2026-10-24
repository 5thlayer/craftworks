// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.gametest;

import java.util.ArrayList;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import io.github._5thlayer.craftworks.Craftworks;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.util.Unit;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import net.neoforged.neoforge.resource.JarContentsPackResources;

/**
 * A test environment whose batch runs with one of the tests' datapacks switched on, as a pack author's would
 * be, and switched off again after it. The packs live under {@code gametest_packs/} in
 * {@code src/gametest/resources}, which the jar leaves out (libworks ADR 0004). The game-test server switches
 * on every pack it finds, so a pack is found only while its batch runs: offered, found, then selected by a
 * reload, and the other way round after.
 */
record WithTestPack(String name) implements TestEnvironmentDefinition<Unit> {

    static final MapCodec<WithTestPack> CODEC = Codec.STRING.fieldOf("pack").xmap(WithTestPack::new, WithTestPack::name);

    /** The packs being offered, by name. */
    private static final Set<String> OFFERED = ConcurrentHashMap.newKeySet();

    /** Finds the offered packs. Game tests only. */
    static void addPackFinders(AddPackFindersEvent event) {
        if (event.getPackType() == PackType.SERVER_DATA) {
            event.addRepositorySource(packs -> OFFERED.forEach(name -> packs.accept(pack(name))));
        }
    }

    @Override
    public Unit setup(ServerLevel level) {
        OFFERED.add(name);
        reload(level.getServer(), true);
        return Unit.INSTANCE;
    }

    @Override
    public void teardown(ServerLevel level, Unit saveData) {
        reload(level.getServer(), false);
        OFFERED.remove(name);
    }

    @Override
    public MapCodec<WithTestPack> codec() {
        return CODEC;
    }

    /** Selects the pack, or unselects it, and reloads, which on the server thread waits for it. */
    private void reload(MinecraftServer server, boolean selected) {
        var repository = server.getPackRepository();
        repository.reload();
        var ids = new ArrayList<>(repository.getSelectedIds());
        ids.remove(id(name));
        if (selected) {
            ids.add(id(name));
        }
        server.reloadResources(ids).join();
    }

    private static String id(String name) {
        return Craftworks.MOD_ID + "/gametest_packs/" + name;
    }

    private static Pack pack(String name) {
        var file = ModList.get().getModContainerById(Craftworks.MOD_ID).orElseThrow().getModInfo().getOwningFile().getFile();
        var pack = Pack.readMetaAndCreate(
                new PackLocationInfo(id(name), Component.literal(name), PackSource.BUILT_IN, Optional.empty()),
                new JarContentsPackResources.JarContentsResourcesSupplier(file.getContents(), "gametest_packs/" + name),
                PackType.SERVER_DATA, new PackSelectionConfig(false, Pack.Position.TOP, false));
        if (pack == null) {
            throw new IllegalStateException("the tests' pack " + name + " has no readable pack.mcmeta");
        }
        return pack;
    }
}
