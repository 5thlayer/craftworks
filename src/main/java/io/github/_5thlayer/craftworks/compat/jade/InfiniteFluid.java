// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.compat.jade;

import java.util.List;

import io.github._5thlayer.craftworks.Craftworks;
import io.github._5thlayer.craftworks.machine.CreativeFluidSourceBlockEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import snownee.jade.api.Accessor;
import snownee.jade.api.fluid.JadeFluidObject;
import snownee.jade.api.view.ClientViewGroup;
import snownee.jade.api.view.FluidView;
import snownee.jade.api.view.IClientExtensionProvider;
import snownee.jade.api.view.IServerExtensionProvider;
import snownee.jade.api.view.ViewGroup;

/**
 * The Creative Fluid Source's fluid in Jade: its name and "infinite", or "No fluid", in place of its
 * capability's {@code Integer.MAX_VALUE} mB, which Jade's own extension would print as a number.
 */
final class InfiniteFluid implements IServerExtensionProvider<FluidView.Data>, IClientExtensionProvider<FluidView.Data, FluidView> {

    static final InfiniteFluid INSTANCE = new InfiniteFluid();

    private static final Identifier UID = Identifier.fromNamespaceAndPath(Craftworks.MOD_ID, "infinite_fluid");

    @Override
    public List<ViewGroup<FluidView.Data>> getGroups(Accessor<?> accessor) {
        JadeFluidObject fluid = accessor.getTarget() instanceof CreativeFluidSourceBlockEntity source && !source.fluid().isEmpty()
                ? JadeFluidObject.of(source.fluid().getFluid(), 1, source.fluid().getComponentsPatch())
                : JadeFluidObject.empty();
        return List.of(new ViewGroup<>(List.of(new FluidView.Data(fluid, 1))));
    }

    @Override
    public List<ClientViewGroup<FluidView>> getClientGroups(Accessor<?> accessor, List<ViewGroup<FluidView.Data>> groups) {
        FluidView.Data data = groups.getFirst().views.getFirst();
        FluidView view = FluidView.readDefault(data);
        JadeFluidObject fluid = data.fluids().getFirst();
        view.ratio = fluid.isEmpty() ? 0 : 1;
        view.overrideText = fluid.isEmpty()
                ? Component.translatable("craftworks.jade.no_fluid")
                : Component.translatable("craftworks.jade.infinite_fluid", fluid.getDisplayName());
        return List.of(new ClientViewGroup<>(List.of(view)));
    }

    @Override
    public Identifier getUid() {
        return UID;
    }

    /** Ahead of Jade's own fluid extension, which would also hit the source's capability. */
    @Override
    public int getDefaultPriority() {
        return -1000;
    }
}
