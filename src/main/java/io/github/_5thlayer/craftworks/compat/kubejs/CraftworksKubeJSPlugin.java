// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.compat.kubejs;

import dev.latvian.mods.kubejs.event.EventGroupRegistry;
import dev.latvian.mods.kubejs.plugin.KubeJSPlugin;
import io.github._5thlayer.craftworks.api.LockHooks;

/**
 * Craftworks for KubeJS scripts (#11), named in {@code kubejs.plugins.txt}. KubeJS reads that file and
 * loads this class; nothing else does, so without KubeJS no class of it is ever touched.
 *
 * <p>The {@code craftworks:assembling} recipe schema is not here: it is data, in
 * {@code data/craftworks/kubejs/recipe_schema/assembling.json}, which KubeJS finds on its own.
 */
public final class CraftworksKubeJSPlugin implements KubeJSPlugin {

    @Override
    public void init() {
        LockHooks.registerReasoned(KubeJSLocks::lock);
    }

    @Override
    public void registerEvents(EventGroupRegistry registry) {
        registry.register(KubeJSLocks.GROUP);
    }
}
