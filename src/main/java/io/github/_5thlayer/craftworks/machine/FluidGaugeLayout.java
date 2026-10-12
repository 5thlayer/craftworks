// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.machine;

/**
 * Where a machine's gauges stand on its screen, in two columns as the suite's other machines lay theirs: the input
 * gauges down the left, the output gauges down the right, each in box order from the top row. The columns fill the
 * panel's width from {@link #LEFT} to {@link #RIGHT} with {@link #GROUP_GAP} between them, so an Assembler's are 76
 * wide at 8 and 92; a machine with boxes of one kind only gives that column the whole width.
 * Pure: no Minecraft types.
 */
public record FluidGaugeLayout(int inputs, int outputs) {

    public static final int LEFT = 8;
    public static final int RIGHT = 168;
    public static final int GROUP_GAP = 8;

    public static FluidGaugeLayout of(FluidLayout layout) {
        return new FluidGaugeLayout(layout.fluidInputs(), layout.fluidOutputs());
    }

    private boolean twoColumns() {
        return inputs > 0 && outputs > 0;
    }

    /** How wide each gauge is: half the row less the gap between the columns, or the whole row for one column. */
    public int width() {
        return twoColumns() ? (RIGHT - LEFT - GROUP_GAP) / 2 : RIGHT - LEFT;
    }

    /** The left edge of {@code box}, the inputs then the outputs, from the panel's own left edge. */
    public int x(int box) {
        return box >= inputs && twoColumns() ? LEFT + width() + GROUP_GAP : LEFT;
    }

    /** The row {@code box} stands in, from 0 at the top of its column. */
    public int row(int box) {
        return box >= inputs ? box - inputs : box;
    }

    /** How many rows the taller column takes. */
    public int rows() {
        return Math.max(inputs, outputs);
    }
}
