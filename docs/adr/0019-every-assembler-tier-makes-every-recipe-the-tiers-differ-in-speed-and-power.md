---
status: accepted
---

# Every Assembler tier makes every recipe; the tiers differ in speed and power

After #40 an **Assembler** of tier 2 or 3 made every **Assembling recipe**, while tier 1 had no fluid box and held
only `crafting` and `advanced-crafting`. That split was left over from Factorio, where the assembling machine 1
takes no fluid. For a general tech mod (ADR-0018) it gates fluids behind a tier for no reason of its own, and it
kept a flag, `hasFluidBoxes()`, that every caller had to ask: the block entity, Fast Replace, the menu, the screen
and the Held recipe's check.

**Decision.** Tier 1 has the same fluid layout as tiers 2 and 3, `FluidLayout.ASSEMBLER`: two input boxes, three
output boxes and six **Fluid Connections**, sized by ADR-0017's rules (#41). All three tiers hold `crafting`,
`advanced-crafting` and `crafting-with-fluid` by default, and the `categories` key stays in each tier's section of
`craftworks-server.toml`, so a pack can still restrict a tier. No tier refuses a recipe for naming a fluid: what is
left at Fill Recipe is the category, too many fluids and a fluid too large for a box. Fast Replace keeps the boxes
between any two tiers. The tiers then differ in speed, power, buffer and art, and in nothing else. Their crafting
recipes are unchanged, so tier 1 gains fluid handling at the same cost; a pack that wants it dearer overrides
`craftworks:assembler_1`.

**Considered: keeping the gate as a config default only.** The tier could still hold two categories by default and
a pack lift it. It would leave a rule that the code no longer needs and the player has to learn, for the sake of
a Factorio ladder the mod does not follow. A pack that wants the ladder restricts `assembler_1`'s `categories`,
and Fill Recipe then refuses by category, as it always did.

**Considered: a flag on the tier that a pack sets.** A second knob for what the category list already says.

**Consequences.**

- A config key's default changes, which ADR-0008 counts as breaking, so this ships in a minor, with #40's.
- An existing world's `craftworks-server.toml` keeps the `categories` it already wrote, so on that world tier 1
  still refuses `crafting-with-fluid` until the server adds it to `assembler_1`'s list or deletes the file. The
  changelog says so, and so must the FactoryWorks Pack if it ships a server config.
- A swap to tier 1 no longer voids the boxes, and the Held recipe and the connections stay as they were.
- Every tier's screen draws the gauge row and stands the inventory lower, and Jade shows tier 1's fluid bars.
- ADR-0016's rule stands for a restricted config: a category no tier holds shows in no tab.

This amends ADR-0018 (tier 1 has the boxes and holds the fluid category) and ADR-0014 (tier 1 has no fluid box and
refuses a fluid recipe, and a Fast Replace to it voids the boxes).
