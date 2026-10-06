// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.compat.jade;

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
 * A tier 2 or 3 Assembler's fluid box in Jade, as Jade draws any tank: a bar of its fluid out of
 * the box's volume, empty while it holds none (#25). Its own provider, since the box's
 * capability is on the two Fluid Connections and not on the Origin block entity Jade looks at. Tier 1 has no
 * box, so no bar.
 */
final class AssemblerFluid implements IServerExtensionProvider<FluidView.Data>, IClientExtensionProvider<FluidView.Data, FluidView> {

    static final AssemblerFluid INSTANCE = new AssemblerFluid();

    private static final Identifier UID = Identifier.fromNamespaceAndPath(Craftworks.MOD_ID, "assembler_fluid");

    @Override
    public List<ViewGroup<FluidView.Data>> getGroups(Accessor<?> accessor) {
        if (!(accessor.getTarget() instanceof AssemblerBlockEntity machine) || !machine.tier().hasFluidBox()) {
            return List.of();
        }
        FluidStack contents = machine.fluidBox().contents();
        JadeFluidObject fluid = contents.isEmpty()
                ? JadeFluidObject.empty()
                : JadeFluidObject.of(contents.getFluid(), contents.getAmount(), contents.getComponentsPatch());
        return List.of(new ViewGroup<>(List.of(new FluidView.Data(fluid, machine.fluidBox().capacity()))));
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
