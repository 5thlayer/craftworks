// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.compat.jade;

import java.util.ArrayList;
import java.util.List;

import io.github._5thlayer.craftworks.Craftworks;
import io.github._5thlayer.craftworks.machine.AssemblerBlockEntity;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.fluids.FluidStack;
import snownee.jade.api.Accessor;
import snownee.jade.api.fluid.JadeFluidObject;
import snownee.jade.api.view.ClientViewGroup;
import snownee.jade.api.view.FluidView;
import snownee.jade.api.view.IClientExtensionProvider;
import snownee.jade.api.view.IServerExtensionProvider;
import snownee.jade.api.view.ViewGroup;

/**
 * A tier 2 or 3 Assembler's boxes in Jade, as Jade draws any tank: a bar of each box's fluid out of its volume,
 * empty while it holds none (#25, #40). The boxes the Held recipe binds a fluid to and any that holds some, inputs
 * then outputs. Its own provider, since the boxes' capability is on the Fluid Connections and not on the
 * Origin block entity Jade looks at. Tier 1 has no boxes, so no bars.
 */
final class AssemblerFluidView implements IServerExtensionProvider<FluidView.Data>, IClientExtensionProvider<FluidView.Data, FluidView> {

    static final AssemblerFluidView INSTANCE = new AssemblerFluidView();

    private static final Identifier UID = Identifier.fromNamespaceAndPath(Craftworks.MOD_ID, "assembler_fluids");

    @Override
    public List<ViewGroup<FluidView.Data>> getGroups(Accessor<?> accessor) {
        if (!(accessor.getTarget() instanceof AssemblerBlockEntity machine)) {
            return List.of();
        }
        List<FluidView.Data> bars = new ArrayList<>();
        for (int box : machine.boxesInUse()) {
            FluidStack contents = machine.fluids().contents(box);
            JadeFluidObject fluid = contents.isEmpty()
                    ? JadeFluidObject.empty()
                    : JadeFluidObject.of(contents.getFluid(), contents.getAmount(), contents.getComponentsPatch());
            bars.add(new FluidView.Data(fluid, machine.fluids().displayCapacity(box)));
        }
        return bars.isEmpty() ? List.of() : List.of(new ViewGroup<>(bars));
    }

    @Override
    public List<ClientViewGroup<FluidView>> getClientGroups(Accessor<?> accessor, List<ViewGroup<FluidView.Data>> groups) {
        return ClientViewGroup.map(groups, FluidView::readDefault, null);
    }

    @Override
    public Identifier getUid() {
        return UID;
    }
}
