// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.compat.researchd;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.List;
import java.util.Optional;

import com.mojang.logging.LogUtils;
import io.github._5thlayer.craftworks.api.LockHooks;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
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
 *
 * <p>A Locked recipe is also named the research that unlocks it (#18), from
 * {@code researchesUnlocking} and {@code researchName}. That is the research the recipe sits under,
 * not necessarily the next one the team can start: parents are not filtered. A Researchd without
 * those calls, or an empty answer (no team, or team data stale after a datapack edit), is plain
 * Locked with no reason.
 */
public final class ResearchdLocks {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final String MOD_ID = "researchd";
    private static final String API = "com.portingdeadmods.researchd.api.ResearchdApi";

    /** {@code ResearchdApi.isRecipeBlocked(Player, ResourceKey<Recipe<?>>)}, or null when unreachable. */
    private static MethodHandle isRecipeBlocked;
    /** {@code researchesUnlocking(Player, ResourceKey<Recipe<?>>)} and {@code researchName(Level, ResourceKey<Research>)}. */
    private static MethodHandle researchesUnlocking;
    private static MethodHandle researchName;
    private static volatile boolean looked;

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

    /** Whether the recipe is Locked, and by which research, as a reasoned lock. */
    public static Optional<LockHooks.Lock> lock(ServerPlayer player, Identifier recipe) {
        if (!isLocked(player, recipe)) return Optional.empty();
        Component reason = reason(player, recipe);
        return reason == null ? Optional.of(LockHooks.Lock.NO_REASON) : LockHooks.Lock.because(reason);
    }

    /** "Research: A, B", or null when Researchd cannot say. */
    @SuppressWarnings("unchecked")
    private static Component reason(ServerPlayer player, Identifier recipe) {
        if (researchesUnlocking == null || researchName == null) return null;
        try {
            List<ResourceKey<?>> researches = (List<ResourceKey<?>>) (List<?>) researchesUnlocking.invokeExact(
                    (Player) player, ResourceKey.create(Registries.RECIPE, recipe));
            if (researches.isEmpty()) return null;
            MutableComponent names = Component.empty();
            for (int index = 0; index < researches.size(); index++) {
                if (index > 0) names.append(", ");
                names.append((Component) researchName.invokeExact((Level) player.level(), (ResourceKey) researches.get(index)));
            }
            return Component.translatable("craftworks.plan.lock_reason.research", names);
        } catch (Throwable failure) {
            throw new IllegalStateException("Researchd failed naming the research that unlocks " + recipe, failure);
        }
    }

    /** The lookup, once. {@code looked} is written last, so a reader that sees it sees the handle too. */
    private static MethodHandle handle() {
        if (looked) return isRecipeBlocked;
        synchronized (ResearchdLocks.class) {
            if (!looked) {
                isRecipeBlocked = lookUp();
                if (isRecipeBlocked != null) lookUpReasons();
                looked = true;
            }
        }
        return isRecipeBlocked;
    }

    private static MethodHandle lookUp() {
        if (!ModList.get().isLoaded(MOD_ID)) {
            LOGGER.error("lockSources lists researchd, but Researchd is not installed: it locks nothing");
            return null;
        }
        try {
            return MethodHandles.publicLookup().findStatic(Class.forName(API), "isRecipeBlocked",
                    MethodType.methodType(boolean.class, Player.class, ResourceKey.class));
        } catch (ReflectiveOperationException missing) {
            LOGGER.error("This Researchd has no {}.isRecipeBlocked(Player, ResourceKey): the researchd lock source locks nothing",
                    API, missing);
            return null;
        }
    }

    /** The two naming calls came after {@code isRecipeBlocked}, so an older Researchd locks without reasons. */
    private static void lookUpReasons() {
        try {
            Class<?> api = Class.forName(API);
            researchesUnlocking = MethodHandles.publicLookup().findStatic(api, "researchesUnlocking",
                    MethodType.methodType(List.class, Player.class, ResourceKey.class));
            researchName = MethodHandles.publicLookup().findStatic(api, "researchName",
                    MethodType.methodType(Component.class, Level.class, ResourceKey.class));
        } catch (ReflectiveOperationException missing) {
            researchesUnlocking = null;
            researchName = null;
            LOGGER.warn("This Researchd has no {}.researchesUnlocking or researchName: Locked recipes will not say which research unlocks them",
                    API, missing);
        }
    }
}
