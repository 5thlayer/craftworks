// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.compat.researchd;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.fml.ModList;
import org.slf4j.Logger;

/**
 * The {@code researchd} Lock source (ADR-0010): a recipe is Locked while Researchd says it is blocked
 * for the player's team.
 *
 * <p>Researchd is reached by a method handle, not compiled against: its jar is on no maven, and a
 * build that needed it could not run in CI. The one call is looked up once, the first time it is
 * asked, and a Researchd without it is an error in the log rather than a crash: the source then locks
 * nothing, as it does when Researchd is not installed at all.
 */
public final class ResearchdLocks {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final String MOD_ID = "researchd";
    private static final String API = "com.portingdeadmods.researchd.api.ResearchdApi";

    /** {@code ResearchdApi.isRecipeBlocked(Player, ResourceKey<Recipe<?>>)}, or null when unreachable. */
    private static MethodHandle isRecipeBlocked;
    private static boolean looked;

    private ResearchdLocks() {
    }

    public static boolean isLocked(ServerPlayer player, Identifier recipe) {
        MethodHandle blocked = handle();
        if (blocked == null) return false;
        try {
            return (boolean) blocked.invokeExact((Player) player, ResourceKey.create(Registries.RECIPE, recipe));
        } catch (Throwable failure) {
            throw new IllegalStateException("Researchd failed answering whether " + recipe + " is blocked", failure);
        }
    }

    private static synchronized MethodHandle handle() {
        if (looked) return isRecipeBlocked;
        looked = true;
        if (!ModList.get().isLoaded(MOD_ID)) {
            LOGGER.error("lockSources lists researchd, but Researchd is not installed: it locks nothing");
            return null;
        }
        try {
            isRecipeBlocked = MethodHandles.publicLookup()
                    .findStatic(Class.forName(API), "isRecipeBlocked",
                            MethodType.methodType(boolean.class, Player.class, ResourceKey.class))
                    .asType(MethodType.methodType(boolean.class, Player.class, ResourceKey.class));
        } catch (ReflectiveOperationException missing) {
            LOGGER.error("This Researchd has no {}.isRecipeBlocked(Player, ResourceKey): the researchd lock source locks nothing",
                    API, missing);
        }
        return isRecipeBlocked;
    }
}
