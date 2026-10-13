---
status: accepted
---

# Data-carrying recipes keep their inputs' data

ADR-0011 converts other mods' crafting recipes but leaves alone "a subclass that assembles its own result":
one whose result is built from the stacks put in. Mekanism's Atomic Disassembler takes a charged energy
tablet and comes out holding its energy; its tier upgrades keep a machine's contents. #58 asks for them in
the Personal Assembler, and a charged tablet paying for one is not negotiable. ADR-0002 stands in the way
twice: a key is an item and all its data, so a charged tablet is not the tablet the recipe lists and never
pays; and a plan names its result before the craft, which here depends on what the craft takes.

**Decision.** A mod's shaped or shapeless crafting recipe that assembles its own result converts by default,
as a **Data-carrying recipe**; `modRecipesExcluded` still keeps one at the table.

- **What pays.** A Data-carrying recipe's ingredients take an item carrying data as well as a **Pristine**
  one, Pristine first, then in inventory order. Every other recipe keeps ADR-0002's exact match, so a
  charged or enchanted item never pays where its data would vanish.
- **What it makes.** Each craft, in the Personal Assembler and the Assembler machine alike, rebuilds a
  crafting grid from the stacks it took (in pattern positions for a shaped recipe) and asks the original
  recipe what it makes. The result is delivered as made, its item and count included; the plan counts on
  the listed result.
- **Chains.** What a plan made always pays for the step it was made for, whatever data it gained, so data
  flows down a chain of upgrades.
- **The Crafting Plan** shows the predicted final result, a row per distinct one when crafts take different
  stacks; intermediates show as listed.
- **Refunds** return exactly what was taken, data included; a Partial cancel replans against them.
- The recipe viewer is unchanged. A pack's own Assembling recipe is never Data-carrying.

This amends ADR-0002 (matching is exact except in a Data-carrying recipe, and the planner holds the keys of
the stacks a plan actually took) and ADR-0011 (those recipes now convert). It amends ADR-0001 too: the queue
consults a recipe after queueing, to make a Data-carrying recipe's result, though the plan itself is still
resolved and paid once.

**Considered: a forced list.** A config list of recipes or mods converted anyway, to their plain listed
result (prototyped as `modRecipesForced`). A fifth of the code and no change to the planner, but the data
is lost: a charged tablet either never pays, or pays and loses its energy, and the tier upgrades empty the
machine they upgrade.

**Considered: data-tolerant matching everywhere.** Any ingredient naming no data takes an item with data, as
a crafting table does. A damaged pickaxe or an enchanted sword would then be spent by recipes that drop
what it carries.

**Consequences.** The planner holds and saves keys with data that it did not plan on, and a plan's final
result is known only once its inputs are chosen. A mod recipe whose result depends on more than its grid
(the player, the level) is asked with a grid alone.
