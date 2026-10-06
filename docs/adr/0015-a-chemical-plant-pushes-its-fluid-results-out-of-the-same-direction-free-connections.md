---
status: accepted
---

# A Chemical Plant pushes its fluid results out of the same direction-free Fluid Connections

The **Chemical Plant** (#26) runs `chemistry` recipes, which have fluid results as well as fluid ingredients, so
unlike the **Assembler** (ADR-0014) it makes fluid, and the fluid it makes has to leave. The pipes that carry it
are another mod's business: Pipeworks' pipes are passive (Pipeworks ADR-0110), and a pack may use any other mod's
pipe or a tank instead.

**Decision.** A Chemical Plant has four **Fluid Connections**, two at either end of each of two opposite
bottom-layer edges of its 3x3, turning with its facing, and none has a direction of its own. They exist while the
**Held recipe** runs here and names a fluid, in or out. Each tick, every connection pulls each of the recipe's fluid
ingredients from the block it faces into the input box its order names, as an Assembler's pull does, and after the
craft pushes what the output boxes hold into the same block, simulated and then executed in one transaction, so a
neighbour that takes none receives none. The same four faces expose the boxes to NeoForge's fluid capability as
one handler: an input box can only be filled, through the box's own filter, and an output box can only be drained;
nothing is put in a result and nothing is taken out of an ingredient. A connection is not an input or an output, so
a pipe or a tank at any of the four serves any box, and the Pack's boiler-style blue and orange rings, which mark a
direction, are not used: every connection wears the one grey ring.

An output box holds the larger of 1,000 mB and the Overload Limit's crafts' worth of the result bound to it (the
Pack's `OutputTankVolume` rule with every result pinned to its own box, so no output takes an unused box's room).
A craft waits while an output box cannot hold what it makes: nothing is drawn, started or voided.

Craftworks names no pipe mod, only the capability (FactoryWorks ADR-0115).

**Considered: fixed input and output connections.** A recipe with two fluids in and out would need two of each, and
a recipe with one of either would leave a connection with nothing to do. Factorio's chemical plant fixes them, but
its connections are laid out for the recipe in hand; here the Held recipe says what each box is for, as it does for
the Assembler.

**Consequences.** A tank beside a connection that holds the same fluid the plant makes is both pulled from, if the
recipe consumes that fluid, and pushed into: a recipe that makes what it consumes will circulate it, which the
Pack's recipes never do. A pipe that is connected to a plant with no fluid recipe finds nothing there, and finds a
handler when the Held recipe changes to one with a fluid, which tells the level its capabilities changed. Changing
the Held recipe voids all four boxes. A recipe with more than two fluid ingredients or results, more than 1,000 mB
of an ingredient a craft, more than two item ingredients, more than one item result or an ingredient that leaves a
remainder is refused at Fill Recipe, since the plant has two input slots, one product slot and no remainder slot.

The output box sizing above is superseded by ADR-0017.
