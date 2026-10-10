---
status: accepted
---

# The Refiner smelts vanilla's smelting and blasting recipes in one block

FactoryWorks ADR-0127 gives Craftworks the electric furnace as the **Refiner**: vanilla smelting and blasting, blasting
winning where an input has both, and "Craftworks' own m:n smelting recipes". Craftworks had no smelting of its own and
no definition of an m:n smelting recipe, and FactoryWorks Core's count-carrying smelting type is dropped (#46).

**Decision.** The Refiner is a single block with an input slot and an output slot, no fuel and no **Held recipe**. It
reads vanilla's `minecraft:smelting` and `minecraft:blasting` recipes from the recipe manager and takes the blasting
one where an input has both. A smelt takes the recipe's cooking time over `speed` (default 2, Factorio's electric
furnace) and costs `power` FE a tick (default 90, its 180 kW at 1 FE = 100 J), spread over the smelt's ticks by the
Assembler's own `CraftRates`, so raw iron blasts in 50 ticks for 4,500 FE and cobblestone smelts in 100 for 9,000. The
buffer defaults to 20,000 FE. All three are in the `refiner` section of `craftworks-server.toml`.

It stands on the Assemblers' machine code: `MachineItemFace` for its item capability, `MachineInventory`,
`EnergyBuffer`, `CraftRates` and `OverloadLimit`. Its one item face is the same on every side. The input takes what a
recipe smelts, up to the Overload Limit of the smelt that takes it (one item a craft, so two at the defaults); only the
output extracts. A smelt is one transaction a tick, as an Assembler's craft is: a result the output cannot take, or a
tick the buffer cannot pay, makes no progress and draws nothing, and progress is thrown away only when the input leaves
or changes to an item another recipe smelts.

**Considered: a copy of FactoryWorks' `GuardedResourceHandler`.** Core wrapped a vanilla container, whose slot-less
insert and extract skipped the per-slot refusals, and needed the guard to put them back. `MachineItemFace` implements
`ResourceHandler` itself, so the interface's slot-less insert and extract already loop through its per-slot rules; a
game test holds that a slot-less insert and extract cannot take the input or fill the output.

**Considered: m:n smelting recipes as Assembling recipes in a smelting category.** It would let a pack write a Refiner
recipe with several inputs or results. Nothing defines what the recipe would say or how the one-slot screen would show
it, so it is left for a ticket that does; a pack that wants more than one-to-one has the Assembler.

**Considered: the Assemblers' 3x3 footprint.** Factorio's furnace is 3x3, but the art that came with it is a cube's
and a footprint needs a model of its own (#22). One block it is; a footprint can replace it with a Fast Replace later.

**Considered: Core's stone and steel furnaces and its fuel table.** Dropped by ADR-0127: vanilla's furnace burns vanilla's
fuels.

**Consequences.**

- The Refiner is crafted by an Assembling recipe, like the Assemblers: a blast furnace, 4 iron ingots and 5 redstone.
  A pack overrides `craftworks:refiner`.
- It is the workstation of vanilla's smelting and blasting tabs in EMI and JEI, and has no tab of its own.
- Its textures are stand-ins drawn flat: Core's came from a CC BY-NC-SA pack, which this repo's MIT licence cannot carry.
