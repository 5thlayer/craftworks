---
status: accepted
---

# A machine moves fluid through its direction-free Fluid Connections; pipes stay passive

Tiers 2 and 3 of the **Assembler** craft recipes with a fluid ingredient (#25), so fluid has to reach them.
The pipes that carry it are another mod's business: Pipeworks' pipes are passive (Pipeworks ADR-0110), and
a pack may use any other mod's pipe or a tank instead.

**Decision.** A tier 2 or 3 Assembler has one fluid box of 1,000 mB and two **Fluid Connections**, at the
centres of two opposite bottom-layer edges of its 3x3, turning with its facing. A connection has no direction
of its own: each tick it pulls the fluid the **Held recipe** consumes from the block it faces, up to the
box's room, simulated and then executed in one transaction, so a neighbour holding another fluid gives none
and nothing mixes. The same face exposes the box to NeoForge's fluid capability, so a mod that pushes can
fill it too, but nothing is ever taken out through it: an Assembler makes no fluid results (the recipe
categories are Factorio's, and none has them), so it never pushes fluid. A connection takes whatever role
the Held recipe gives it, as Factorio's assembling machine 2 does: the connections exist only while the Held
recipe has a fluid ingredient, drawn as a ring on the casing, and with none there is no ring and no
capability on those faces. Tier 1 has none and refuses a fluid recipe.

Craftworks names no pipe mod, only the capability (FactoryWorks ADR-0115), so the pull works against
Pipeworks' pipes, another mod's pipes and a plain tank alike.

**Considered: pipes pushing.** The pipe would need to know the Assembler's box and its Held recipe, which a
pipe cannot do without a type of Craftworks, or without every machine saying what it accepts. Pipes would
stop being passive, and every other mod's pipe would need the same code to feed an Assembler.

**Considered: fixed input and output connections.** A recipe would need an input for each of its fluids and
an output for each of its results, and an Assembler has one box and makes no fluid. Factorio's assembling
machine 2 does not fix them either: its connections take their role from the recipe, and a recipe here has at
most one fluid ingredient and no fluid result, so a connection has only one thing to do.

**Consequences.** A pipe that is connected to an Assembler with no fluid recipe finds nothing there, and
finds a handler when the Held recipe changes to one with a fluid, which tells the level its capabilities
changed. Changing the Held recipe voids the box, and so does a Fast Replace to tier 1 or breaking the
Assembler; a Fast Replace between tiers 2 and 3 keeps it. A recipe needing two fluids, or more than 1,000 mB
of one a craft, is refused at Fill Recipe, and so is any recipe with a fluid result. A pack that wants
Assemblers to make fluid, or to hold two fluids at once, needs a new decision.

The fluid box's size is superseded by ADR-0017.

The Assembler's one fluid box, its two connections and its never pushing are superseded by ADR-0018 (#40): an
Assembler of tier 2 or 3 has two input and three output boxes on six connections and pushes its fluid results out. The
connections stay direction-free and the pipes passive.
