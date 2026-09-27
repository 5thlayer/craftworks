// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks;

import io.github._5thlayer.craftworks.assembler.client.AssemblerClient;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

/** The Mod's client half, which a dedicated server never loads. */
@Mod(value = Craftworks.MOD_ID, dist = Dist.CLIENT)
public final class CraftworksClient {

    public CraftworksClient(IEventBus modBus) {
        AssemblerClient.register(modBus);
    }
}
