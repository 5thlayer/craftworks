// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.craftworks.assembler;

/**
 * What a press of EMI's {@code + Fill Recipe} on the Assembler's screen asks for (ADR-0004).
 *
 * <p>Left queues one, right queues five, middle opens the Crafting Plan, and Shift over any button
 * queues as many as the inventory covers. The button is whatever Minecraft delivered to the screen,
 * so an OS's own conversions -- Ctrl + left-click is a right-click on macOS -- need no code here.
 *
 * <p>A request the inventory does not fully cover queues nothing: the plan opens instead and names
 * why. Five never quietly becomes three; only {@link #ALL} means "as many as possible".
 */
public enum FillRequest {
    ONE,
    FIVE,
    ALL,
    PLAN;

    /** EMI's craft hotkeys reach the handler with no click behind them. */
    public static final int NO_BUTTON = -1;

    public static FillRequest of(int button, boolean shift) {
        if (shift) return ALL;
        return switch (button) {
            case NO_BUTTON, 0 -> ONE;
            case 1 -> FIVE;
            default -> PLAN;
        };
    }

    /**
     * How many to queue given the resolver's {@code largestAffordable}, or 0 to open the plan.
     *
     * <p>{@code largestAffordable} already folds in Missing and Locked -- a plan that cannot complete
     * affords nothing -- so one number decides every case.
     */
    public int queueCount(int largestAffordable) {
        int ceiling = Math.max(0, largestAffordable);
        return switch (this) {
            case ONE -> ceiling >= 1 ? 1 : 0;
            case FIVE -> ceiling >= 5 ? 5 : 0;
            case ALL -> ceiling;
            case PLAN -> 0;
        };
    }

    /**
     * How many crafts this request asked for, given what {@link #queueCount} decided: what the Crafting
     * Plan names as not covered when nothing was queued. 0 for the plan itself, which refuses nothing.
     */
    public int asked(int queueCount) {
        return switch (this) {
            case ONE -> 1;
            case FIVE -> 5;
            case ALL -> Math.max(1, queueCount);
            case PLAN -> 0;
        };
    }
}
