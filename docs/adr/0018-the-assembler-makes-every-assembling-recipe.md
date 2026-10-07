---
status: accepted
---

# The Assembler makes every Assembling recipe; the Chemical Plant and the Oil Refinery are gone

Craftworks began as a faithful simulation of Factorio's machines and is becoming a general tech mod, so a machine
has to earn its place by doing something an **Assembler** can't. The **Chemical Plant** (#26) did nothing an
Assembler couldn't once the Assembler had the fluid boxes for it, and the **Oil Refinery** (#27) existed for two
recipes. Both ran on one general fluid machine (ADR-0017), while the Assembler kept a one-box special case of its
own (ADR-0014).

**Decision.** The Chemical Plant, the Oil Refinery and their categories, `chemistry` and `oil-processing`, are
removed (#40). An Assembler of tier 2 or 3 makes every **Assembling recipe**, whether it makes items, fluids or
both, with two input and three output fluid boxes bound by order and sized by ADR-0017's rules, on six
**Fluid Connections**: all three bottom-layer blocks along the edge it faces and along the opposite one, the two
edge centres it had and the four corners the plant had. Connections stay direction-free and pipes stay passive
(ADR-0014): while the **Held recipe** names a fluid, in or out, each connection pulls the ingredients into their
input boxes and pushes the results out of the output boxes, any connection serving any box, and an input box can
only be filled and an output box only drained (ADR-0015's rule, now the Assembler's). A craft waits while an
output box can't hold what it makes. There is one fluid layout, `FluidLayout.ASSEMBLER`, which the Assembler's
block entity always holds: Fast Replace keeps one block entity across tiers, so the tier only says whether the
boxes exist. Tier 1 has none and refuses a recipe that names a fluid; giving it fluids is #41.

The categories are now `crafting`, `advanced-crafting` and `crafting-with-fluid`, the last for any recipe with a
fluid in or out. A recipe that names a removed category fails to load, with an error that points to
`crafting-with-fluid`.

**Considered: growing the Assembler's own one-box code.** It would have meant a second implementation of what
`FluidMachine` already did for N boxes, pushing included. The general code stays, renamed for what it holds
(`MachineFluids`, `MachineFluidFace`) and described by a `FluidLayout`, and the one-box special case
(`AssemblerFluidBox`, `AssemblerFluidConnection`, `FluidConnections`) is deleted.

**Considered: fewer connections.** The Assembler's two would not do, and neither would four. Connections have no
direction and each pipe network carries one fluid, so a recipe with *k* different fluids needs *k* separate
connections, and a recipe of five, a two-in, three-out oil split, needs five. Six covers it and leaves a spare, and
the sites are symmetric under a half turn, so two blockstate rotations still cover the four facings.

**Considered: aliasing the removed categories** to `crafting-with-fluid`. A recipe that says `chemistry` would then
load silently into a different category and, with the plant's boxes gone, with different sizing: a pack author
should read the error and change the recipe on purpose.

**Considered: migrating placed machines** into Assemblers. A plant's slots, boxes and footprint don't map onto an
Assembler's, and the Pack pins Craftworks' exact version (ADR-0008), so it moves on purpose; a loss the changelog
names costs less than code that runs once.

**Consequences.**

- A Chemical Plant or Oil Refinery already placed disappears when its world loads with this version, and so do its
  items in inventories and chests. There is no migration.
- A tier's `categories` list that names a removed id loses that entry when NeoForge corrects the config, and falls
  back to its default only if nothing else is left in it, so a pack that listed either adds `crafting-with-fluid`.
  The `chemical_plant` and `oil_refinery` config sections are gone.
- The Recipe viewer is back to one tab, the Assembler's (ADR-0016): a category no tier holds still shows in no tab.
- The first fluid result takes the room of every output box the recipe leaves unused unless it is **Pinned**
  (ADR-0017), and the Assembler has three output boxes where the plant had two, so a recipe with one fluid result
  holds at least 300 mB of it where the plant held 200. A Pinned recipe is unaffected.
- A world saved with the old one fluid box loads it into the first input box, over its capacity if need be, and
  saves it under a new key, which an older version can't read.
- Jade's provider is `craftworks:assembler_fluids`, so a player's toggle resets once, and EMI favourites saved
  under the removed tabs are lost once.
- This breaks a pack's data and config (ADR-0008), so the next release is a minor. The FactoryWorks Pack changes
  its `chemistry` and `oil-processing` recipes and tier config to `crafting-with-fluid`, replaces any use of the
  two machines, and sets `pinned_fluid_results` where it relied on the refinery's box layout.

This supersedes ADR-0015, and amends ADR-0014 (an Assembler pushes fluid and has two input and three output
boxes), ADR-0016 (one tab) and ADR-0017 (the one fluid machine is the Assembler).
