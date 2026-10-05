// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.compat.jade;

import java.util.List;

import io.github._5thlayer.craftworks.Craftworks;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import snownee.jade.api.Accessor;
import snownee.jade.api.view.ClientViewGroup;
import snownee.jade.api.view.EnergyView;
import snownee.jade.api.view.IClientExtensionProvider;
import snownee.jade.api.view.IServerExtensionProvider;
import snownee.jade.api.view.ViewGroup;

/**
 * The Creative Energy Source's energy in Jade: a full bar reading Infinite FE, in place of its capability's
 * {@code Long.MAX_VALUE} of {@code Long.MAX_VALUE}, which Jade's own extension would print as a number.
 */
final class InfiniteEnergy implements IServerExtensionProvider<EnergyView.Data>, IClientExtensionProvider<EnergyView.Data, EnergyView> {

    static final InfiniteEnergy INSTANCE = new InfiniteEnergy();

    private static final Identifier UID = Identifier.fromNamespaceAndPath(Craftworks.MOD_ID, "infinite_energy");

    @Override
    public List<ViewGroup<EnergyView.Data>> getGroups(Accessor<?> accessor) {
        return List.of(new ViewGroup<>(List.of(new EnergyView.Data(1, 1))));
    }

    @Override
    public List<ClientViewGroup<EnergyView>> getClientGroups(Accessor<?> accessor, List<ViewGroup<EnergyView.Data>> groups) {
        EnergyView view = new EnergyView("", "");
        view.ratio = 1;
        view.overrideText = Component.translatable("craftworks.jade.infinite_energy");
        return List.of(new ClientViewGroup<>(List.of(view)));
    }

    @Override
    public Identifier getUid() {
        return UID;
    }

    /** Ahead of Jade's own energy extension, which would also hit the source's capability. */
    @Override
    public int getDefaultPriority() {
        return -1000;
    }
}
