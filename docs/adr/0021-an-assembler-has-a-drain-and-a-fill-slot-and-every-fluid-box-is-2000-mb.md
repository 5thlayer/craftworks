---
status: accepted
---

# An Assembler has a drain and a fill slot, shared outputs, and fluid boxes of a flat 2,000 mB

Fluid reached an **Assembler** only through its **Fluid Connections**, so a world with no pipe mod could neither feed
a fluid recipe nor take a fluid result out. Fieldworks solved the same with filled containers (Fieldworks ADR 0004),
and libworks-runtime ships the move (`FluidContainers`, libworks#21). The boxes' sizes, ADR-0017's, did not suit a
bucket: an input box held 4 crafts' worth, 40 mB for a recipe of 10 mB, and an output box 100 mB, so a bucket of 1,000
mB seldom fitted whole. Craftworks is also no longer held to the Pack's Factorio figures: the FactoryWorks Pack is gone.

**Decision.** An Assembler of every tier has two slots beside its inputs (#57), as every tier has the same fluid boxes and
Fluid Connections (ADR-0019):

- A **drain slot** takes any filled fluid container, any item with NeoForge's fluid item capability, and pours it into
  the input box bound to its fluid.
- A **fill slot** takes any empty fluid container and fills it from the first output box, in order, that holds a whole
  container's worth.

The two **container slots** stand under the input gauges, apart from the outputs, which they would otherwise read as
part of. Their ghosts, drawn while the slot is empty and the hover tooltip those give, are the fill slot's every empty
fluid container in the loaded mods (each item whose fluid item handler, on a fresh stack, is empty and takes some
fluid, scanned once for the game) and the drain slot's each fluid of the Held recipe's fluid ingredients as a filled
form of each of those, a fluid with no container skipped. The client learns the fluids from the Held recipe it is
sent.

A container moves whole or not at all and waits in its slot while it can't, because the box has no room, or lacks the
fluid, or the outputs have no room for what it becomes. The move is libworks-runtime's `FluidContainers`, run once a
tick for each slot.

**Shared outputs.** Four output slots replace the product slot and the remainders slot, on every tier. The product, the
ingredients' remainders, the recipe's further results, and the emptied and filled containers all land in any slot with
room, a slot holding something before an empty one. A craft waits while its product and remainders do not all fit.
The Held recipe's leftover rule stays (ADR-0018): its remainders and further results are at most one kind of item
and at most a stack, which four slots only make easier. The outputs stand in the inventory where the product and the
remainders did, as its first two slots, so a world saved earlier loads with nothing lost or moved.

**Fluid boxes** are a flat 2,000 mB, input and output, so a bucket always swaps whole, like the Wireworks Boiler's
tanks (Wireworks ADR 0011's amendment). The machine's pull and a pipe fill an input box to the brim. This supersedes
ADR-0017's sizing and ADR-0015's before it:

- the pull limit of 4 crafts' worth, and the output boxes' 100 mB grown to 3 crafts' worth, are gone;
- the first result no longer takes the room of the output boxes a recipe leaves unused, and so
  `pinned_fluid_results`, and the **Pinned** recipe, are gone. A recipe file that still has the field loads, since
  the recipe codec ignores a field it does not name;
- a recipe is refused (Fill Recipe says the fluid is too large) if one craft needs more of a fluid, ingredient or
  result, than a box holds.

**Automation.** The item face, on any face of any block of the Assembler, routes by what an item is. An item the Held
recipe names as an ingredient goes to its input slot first (cake's milk buckets stay ingredients); only one an input
has no room for, or no input takes, goes on. A filled container whose fluid some input box takes goes to the drain
slot, and an empty one a fluid result of the Held recipe can fill goes to the fill slot. Everything extracts from the
shared outputs, and from nothing else. The item face is picky on purpose: a hopper stuck with a container no slot can use is worse than one that does not
bring it. A hand may put any filled container in the drain slot and any empty one in the
fill slot, whatever the Held recipe, and shift-click routes as the item face does.

**Considered: one container slot, as Fieldworks first thought.** A stack of buckets waiting for room in a box blocks
the empty ones that would make that room (Fieldworks ADR 0004), so two slots.

**Considered: sizing a box by its recipe still, to a bucket at least.** It would keep the Pack's behaviour in a chain,
but a box that changes size with the Held recipe is a box a player cannot predict, and there is no Pack to keep.

**Considered: refusing nothing a hopper brings the container slots.** A lava bucket, or an empty bucket on a recipe that
makes no fluid, would sit in a slot for good and jam it; the item face takes a container only if it can move.

**Consequences.** This breaks a pack author's recipes and a player's screen (ADR-0008, libworks ADR 0001), so the next release is a minor. A pack author's
recipe with fluid results sized by the old rules now has 2,000 mB boxes, and a recipe needing more than 2,000 mB of
one fluid a craft is refused. A save keeps its fluid, though a box above 2,000 mB, which the old output sizing could
make, keeps what it holds. A Fast Replace between any tiers keeps what is in the drain and fill slots, as it keeps the other slots.
