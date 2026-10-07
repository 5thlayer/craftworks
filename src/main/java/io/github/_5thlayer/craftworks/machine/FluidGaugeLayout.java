// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.machine;

/**
 * Where a machine's gauges stand across its screen, in one row: the input gauges, then the output gauges, the
 * boxes in order. They fill the panel's width from {@link #LEFT} to {@link #RIGHT}, with {@link #GAP} between the
 * gauges of a group and {@link #GROUP_GAP} between the inputs and the outputs, so a Chemical Plant's four are 36
 * wide at 8, 48, 92 and 132, and an Assembler's five (an Oil Refinery's too) 28 wide at 8, 40, 76, 108 and 140.
 * Pure: no Minecraft types.
 */
public record FluidGaugeLayout(int inputs, int outputs) {

    public static final int LEFT = 8;
    public static final int RIGHT = 168;
    public static final int GAP = 4;
    public static final int GROUP_GAP = 8;

    public static FluidGaugeLayout of(FluidLayout layout) {
        return new FluidGaugeLayout(layout.fluidInputs(), layout.fluidOutputs());
    }

    private int boxes() {
        return inputs + outputs;
    }

    /** How wide each gauge is: what is left of the row after the gaps, shared out. */
    public int width() {
        int gaps = Math.max(inputs - 1, 0) + Math.max(outputs - 1, 0);
        int group = inputs > 0 && outputs > 0 ? GROUP_GAP : 0;
        return (RIGHT - LEFT - group - gaps * GAP) / boxes();
    }

    /** The left edge of {@code box}, the inputs then the outputs, from the panel's own left edge. */
    public int x(int box) {
        boolean output = box >= inputs;
        int x = LEFT + box * (width() + GAP);
        return output && inputs > 0 ? x + GROUP_GAP - GAP : x;
    }
}
