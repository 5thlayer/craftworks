# Craftworks

A player-held crafting planner, the **Personal Assembler**: the player asks for an item, the mod resolves
every intermediate, takes the whole cost up front, and crafts serially over time.

## Language

**Personal Assembler**:
The player's own crafting planner: their **Plan queue** and the Crafting Plans it runs, shown on the
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

**Plan queue**:
The serial list of Crafting Plans; one per player, held by the player. One runs at a time; a plan that
cannot proceed pauses and stops the plans behind it rather than dropping or cancelling anything. Refunded
into the inventory on death.
_Avoid_: Assembler queue, crafting queue, backlog

**Missing**:
A leaf of a Crafting Plan the player does not hold and the Personal Assembler cannot make.
_Avoid_: shortfall, unavailable

**Locked**:
A recipe the Personal Assembler could make but the **Lock source** says this player may not use yet. Distinct from
**Missing**: the two demand different actions.
_Avoid_: blocked, gated

**Ready**:
An Assembling recipe is Ready for a player when one craft of it resolves as a Crafting Plan with nothing
Missing or Locked, intermediates included: what Fill Recipe would queue right now. What the Recipe viewer's
craftables list shows.
_Avoid_: craftable (a recipe viewer's own sense: its direct inputs are held)

**Assembling recipe**:
A recipe of the mod's own assembling type, the only kind the Personal Assembler plans with: item ingredients, a
list of item results (empty only when it has a fluid result), a **Category**, a craft time and a **Route priority**,
and optionally fluid ingredients and fluid results. The
Personal Assembler plans only through one that is **Hand-craftable**; any other is still listed by the Recipe viewer,
for an **Assembler** to make. An ingredient's own remainder (an empty bucket) returns to the inventory when its
step completes; a plan never counts on it.
_Avoid_: hand recipe, admitted recipe

**Hand-craftable**:
An Assembling recipe whose `hand_craftable` flag is true (the default), that names no fluid in or out, and
that has exactly one item result. A recipe with a fluid is never Hand-craftable, whatever its flag says: the
player has no hands for a fluid. Nor is one with no item result or several: a Crafting Plan plans one product
per recipe. A pack sets the flag false for an item-only recipe it keeps for an Assembler. Only a Hand-craftable
recipe appears in a Crafting Plan.
_Avoid_: plannable, machine-only

**Assembler**:
A placed machine, in tiers 1 to 3, that makes its **Held recipe** over and over from the items and power
it is given, whether the recipe makes items, fluids or both. Every tier also holds fluid: two input fluid
boxes and three output fluid boxes of 2,000 mB each, bound to the recipe's fluids by order. It has five input
slots and four **Shared output**s, where the product, the ingredients' remainders and any container land in any slot
with room, and a **Drain slot** and a **Fill slot**. The tiers differ in speed, power, buffer and art only. Never the
**Personal Assembler**, which always takes its full name.
_Avoid_: assembling machine, crafter, auto crafter, machine (alone), Chemical Plant, Oil Refinery (the two
machines it replaced)

**Refiner**:
A placed machine, one block, that smelts what vanilla's furnace and blast furnace do, on FE and no fuel: an input
slot and an output slot, no **Held recipe**. It takes any vanilla smelting or blasting recipe, blasting where an
input has both, at the speed and power its server config sets. The Factorio electric furnace, taken from
FactoryWorks Core (FactoryWorks ADR-0127); never an **Assembler**, which makes Assembling recipes.
_Avoid_: Electric Furnace, furnace (alone), Oil Refinery (a machine Craftworks removed)

**Fluid Connection**:
A face of an **Assembler** where fluid reaches it, with no direction of its own: its role comes from the **Held
recipe**, not from a fixed input or output. An Assembler of any tier has six on the bottom layer, spaced so no
two of their pipes touch: the two ends of the edge it faces, the two ends of the opposite one and the centre of each
side, turning with it. They exist only while its Held recipe names
a fluid in or out; each then pulls the fluid ingredients into their input boxes and pushes the fluid results out of
the output boxes, and any connection serves any box. A recipe of several fluids needs a connection for each, as
a pipe network carries one fluid. Pipeworks' pipes are passive (Pipeworks ADR-0110), so they never push into one,
but another mod that does push can fill an input box through the connection.
_Avoid_: port, fluid port (Pipeworks' `FluidPort` type), input, output

**Shared output**:
One of an **Assembler**'s four output slots, every tier. The product, the further results, the ingredients'
remainders and every emptied or filled container land in any of them with room, a slot already holding the item
before an empty one, and only they extract. A craft waits while its product and remainders do not all fit. Where the
product slot and the remainders slot once were; an older save's two read back as the first two.
_Avoid_: product slot, remainder slot, output buffer (Fieldworks' word)

**Container slot**:
A **Drain slot** or a **Fill slot**: the two slots of an **Assembler** that take a fluid container,
under the input gauges.
_Avoid_: bucket slot, fluid slot

**Drain slot**:
A slot of an **Assembler** that takes a filled fluid container, any item with NeoForge's fluid item
capability, and pours it into the input fluid box bound to its fluid. The emptied container goes to the **Shared output**s. A container moves whole or not at all, and waits in the slot while the box has no room or the outputs have
none. Its ghosts are the Held recipe's fluids as filled containers. Fieldworks' sense (Fieldworks ADR 0004).
_Avoid_: input tank slot, bucket slot, fluid input slot

**Fill slot**:
A slot of an **Assembler** that takes an empty fluid container and fills it from the first output
fluid box, in order, that holds a whole container's worth. The filled container goes to a **Shared output**. A
container moves whole or not at all, and waits in the slot until one does. Its ghosts are every empty fluid container. Fieldworks' sense.
_Avoid_: output tank slot, bucket slot, fluid output slot

**Category**:
The kind an Assembling recipe is, one of four names and no other: `crafting` (the default),
`advanced-crafting`, `crafting-with-fluid`, for any recipe with a fluid in or out that is not brewed (every **Poured recipe**), and
`brewing`, for every **Brewed recipe**. Each tier of
Assembler holds the categories its server config lists, all four by default, and Fill Recipe refuses a
recipe outside them. The Personal Assembler ignores it: it is no machine, and plans through any Hand-craftable
recipe whatever its category.
_Avoid_: type (the recipe's `type` is its Minecraft recipe type), tag

**Held recipe**:
The one Assembling recipe an Assembler is set to make, chosen by a player through the Recipe viewer's
Fill Recipe on the open Assembler; never matched from the items put in. It is refused if it is Locked for the
player who sets it, and once held it is never checked again. An Assembler with none, or that cannot run
its own, holds it and idles.
_Avoid_: selected recipe, active recipe, machine recipe

**Converted recipe**:
An Assembling recipe Craftworks makes from a shaped or shapeless crafting recipe, or from one that wraps
such a recipe (Sophisticated Backpacks' backpacks), at that recipe's own id, replacing it: its slots become a bag of ingredients, with the default craft time (a **Storage recipe**'s is shorter) and Route priority.
Vanilla's come built in; every other mod's are converted as recipes load, unless the pack excludes them or
already has an Assembling recipe at that id.
_Avoid_: imported recipe, ported recipe

**Storage recipe**:
A Converted recipe that packs an item into its storage block or unpacks it, known by its round trip: nine or four of
one item make one of another, and a Converted recipe makes that one back into as many. Both directions count (iron
ingots and the iron block, nuggets and the ingot); a crafting table, from four planks, does not. It takes one tick.
_Avoid_: compression recipe, packing recipe

**Brewed recipe**:
An Assembling recipe Craftworks makes from the brewing stand's mixes, vanilla's and other mods', at an id of its
own: one for each chain of brews that starts from a water bottle, folded into a single craft. It takes water as a
fluid, three glass bottles and one of each ingredient along the chain, and makes three of what the chain ends in, a
bottled potion or another mod's item (Bioresin); no potion goes in and no potion is ever a fluid. A potion reached by
two chains has two. It is never Hand-craftable, burns no blaze powder, and takes the stand's time for every step it
folds.
_Avoid_: brewing recipe (the stand's own mix), potion recipe

**Poured recipe**:
An Assembling recipe Craftworks makes from a Converted recipe that takes or makes a filled bucket, at an id of its
own, beside the Converted recipe, which stays: each filled bucket becomes 1000 mB of its fluid, and no bucket goes in
or comes out. It is in `crafting-with-fluid`, keeps its source's craft time and Route priority, and is never
Hand-craftable. A recipe whose fluids an Assembler could never hold has none.
_Avoid_: fluid recipe, bucketless recipe

**Data-carrying recipe**:
A Converted recipe whose result is built from the stacks a craft consumed, as at a crafting table: a charged
tablet makes a charged Atomic Disassembler, a full factory upgrades into a full one. Its ingredients take an
item carrying data as well as a Pristine one, and both Assemblers make what a crafting table would.
_Avoid_: self-assembling recipe, special recipe

**Pristine**:
An item carrying no data beyond what a freshly made one has: an empty tablet, an undamaged sword. A plan
pays with a Pristine item already held before one carrying data, and with a held item carrying data before
crafting a fresh Pristine one.
_Avoid_: clean, empty, blank

**Route priority**:
The number that decides which of several Assembling recipes makes an item: the highest-priority route the
player can resolve, with nothing Missing or Locked, wins; ties go to the lowest recipe id. Only when no
route resolves does the plan report the top route's Missing and Locked. It applies at every level of a plan and is
the pack author's to set.
_Avoid_: preference, weight

**Lock source**:
What decides whether a recipe is **Locked** for a player: the sources a pack lists (the vanilla recipe
book), plus any hook a pack or another mod registers. A recipe is Locked if any of them says so; the Personal Assembler asks, and
never knows why in any way it acts on. A source may give a reason with its yes (a reasoned hook gives one, such as the
research that unlocks the recipe), which the Crafting Plan shows the player as text the Personal Assembler never reads.
_Avoid_: research, unlock provider

**Recipe viewer**:
The item list and recipe screen through which the player asks the Personal Assembler for items: EMI or JEI. A
player cannot use Craftworks without one. It shows Assembling recipes in one tab, the Assembler's, holding the
recipes whose **Category** any tier of Assembler holds; a category no tier holds is in no tab (ADR-0016).
_Avoid_: item interface, recipe browser, JEI (for both)

**Works tab**:
The creative tab the suite's mods share, `libworks:works`, registered by libworks-runtime. Every Craftworks
item shows there and in no other tab; Craftworks registers no tab of its own (FactoryWorks ADR-0128).
_Avoid_: Craftworks tab, creative tab (alone)
