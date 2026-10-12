// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.compat.jade;

import java.util.List;

import io.github._5thlayer.craftworks.Craftworks;
import io.github._5thlayer.craftworks.machine.AssemblerBlockEntity;
import io.github._5thlayer.libworks.machine.Tank;
import net.minecraft.resources.Identifier;

/**
 * An Assembler's boxes in Jade, as Jade draws any tank: a bar of each box's fluid out of its volume, empty while it
 * holds none (#25, #40). The boxes the Held recipe binds a fluid to and any that holds some, inputs then outputs,
 * untitled, through libworks-runtime's {@code MachineTanks}. Its own provider, since the boxes' capability is on the
 * Fluid Connections and not on the Origin block entity Jade looks at.
 */
final class AssemblerTanks {

    static final Identifier UID = Identifier.fromNamespaceAndPath(Craftworks.MOD_ID, "assembler_fluids");

    private AssemblerTanks() {
    }

    static List<Tank> of(AssemblerBlockEntity assembler) {
        return assembler.fluidSide().boxesInUse().stream()
                .map(box -> new Tank("box_" + box, assembler.fluidSide().boxes().contents(box),
                        assembler.fluidSide().boxes().displayCapacity(box), null))
                .toList();
    }
}
