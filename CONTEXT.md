# Craftworks

A player-held crafting planner, the **Personal Assembler**: the player asks for an item, the mod resolves
every intermediate, takes the whole cost up front, and crafts serially over time.

## Language

**Personal Assembler**:
The player's own crafting planner: their **Assembler queue** and the Crafting Plans it runs, shown on the
inventory screen and the HUD. Every player has one from the start. It belongs to the player, not to a
block or an item, and it replaces the inventory's crafting grid. It crafts nothing directly: every craft is
a **Crafting Plan**.
_Avoid_: hand crafter, portable crafter, auto crafter

**Crafting Plan**:
The resolved, flattened tree of crafts produced when the player asks for an amount; the unit paid for in
full when queued and refunded on cancel. Once queued it is never re-resolved, except by a **Partial
cancel**.
_Avoid_: crafting job, batch, order

**Partial cancel**:
Cancelling fewer crafts of a Crafting Plan's final recipe than it has left. The plan is refunded whole and
what remains is resolved again against the refunded inventory, keeping its place in the queue. A cancel
counts crafts, not items, so it can't leave a recipe making 4 planks half done.
_Avoid_: reduce, trim

**Assembler queue**:
The serial list of Crafting Plans; one per player, held by the player. One runs at a time; a plan that
cannot proceed pauses and stops the plans behind it rather than dropping or cancelling anything. Refunded
into the inventory on death.
_Avoid_: crafting queue, backlog

**Missing**:
A leaf of a Crafting Plan the player does not hold and the Assembler cannot make.
_Avoid_: shortfall, unavailable

**Locked**:
A recipe the Assembler could make but the **Lock source** says this player may not use yet. Distinct from
**Missing**: the two demand different actions.
_Avoid_: blocked, gated

**Assembling recipe**:
A recipe of the mod's own assembling type, the only kind the Assembler plans with: ingredients, one result,
a craft time and a **Route priority**. Items only, never fluids. An ingredient's own remainder (an empty
bucket) returns to the inventory when its step completes; a plan never counts on it.
_Avoid_: hand recipe, admitted recipe

**Route priority**:
The number that decides which of several Assembling recipes makes an item: the highest-priority route the
player can resolve, with nothing Missing or Locked, wins; ties go to the lowest recipe id. Only when no
route resolves does the plan report the top route's Missing and Locked. It applies at every level of a plan and is
the pack author's to set.
_Avoid_: preference, weight

**Lock source**:
What decides whether a recipe is **Locked** for a player: the sources a pack lists (the vanilla recipe
book, Researchd), plus any hook a pack or another mod registers. A recipe is Locked if any of them says so; the Assembler asks, and
never knows why.
_Avoid_: research, unlock provider

**Recipe viewer**:
The item list and recipe screen through which the player asks the Assembler for items: EMI or JEI. A
player cannot use Craftworks without one.
_Avoid_: item interface, recipe browser, JEI (for both)
