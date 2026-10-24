---
status: accepted
---

# The brewing stand's chains fold into Assembling recipes from water

An **Assembler** makes only **Assembling recipes**, and the brewing stand's mixes are none: vanilla builds its
brewing table in code (`PotionBrewing`'s potion and container mixes), and NeoForge adds `IBrewingRecipe`s through
`RegisterBrewingRecipesEvent`, as Wireworks does for **Bioresin** from a water bottle and sugar cane. Brewing could not
be automated, nor the sugar-cane route to Plastic mass-produced (#76).

**Decision.** At the end of every recipe load, beside the Converted recipes (ADR-0011), Craftworks turns the brewing
table into **Brewed recipes**, in a fourth **Category**, `brewing`, that every tier holds by default. Each is one
whole chain of brews from a water bottle folded into one craft: three glass bottles, 750 mB of `minecraft:water` and
one of each ingredient along the chain make three of what the chain ends in, a bottled potion or a mod's item. No
potion goes in and none is a fluid. Splash adds one gunpowder at the end of the chain, lingering one dragon's breath
after it. A chain never visits a potion twice; a potion two chains reach has two recipes. A brew takes 400 ticks for
each step it folds, pays in FE and burns no blaze powder, and is never Hand-craftable. Its id names the chain:
`craftworks:brewing/<bottle>/<potion ns>/<potion path>/<ingredient ns>.<ingredient path>/...` for a potion
chain, and `craftworks:brewing/<item ns>/<item path>/<ingredient ns>.<ingredient path>/...` for a chain that ends in
an item (no bottle segment, the item in place of the potion). Vanilla's potion and
container mixes and NeoForge `BrewingRecipe`s convert; any other `IBrewingRecipe`, which can't be listed, is skipped
with a log line, as is a mod brew no chain from water reaches. A `brewingRecipes` server flag, on by default, and the
existing exclude list turn them off.

**Considered: potions as fluids.** Each step would be a recipe from one potion fluid to the next, and bottles would
fill from a tank. One fluid carrying its potion as data is what Create does, but Pipeworks' segments hold a bare
`Fluid` and would mix every potion; a fluid per potion means registering a fluid for every potion of every mod, and
glass bottles and potions would need a fluid item capability. Folding the chain needs none of that: water is the
only fluid.

**Considered: one recipe per step, potion bottles in and out**, the stand's mixes one for one. Faithful, but every
chain then needs a machine per step or a player moving bottles between them, and every step still starts from a
water bottle a player fills by hand.

**Considered: only the shortest chain to each potion.** One recipe per potion and simpler ids, but a player who
holds the ingredients of the longer chain could not brew it.

**Considered: a Brewer machine.** ADR-0018 has a machine earn its place by doing what an Assembler can't; brewing is
items and a fluid in, items out over time, which an Assembler does.

**Considered: the Assembler reading the brewing table live**, as the Refiner reads smelting (ADR-0020). Nothing to
convert, but no Assembling recipe either: no Recipe viewer tab, no Fill Recipe, no **Held recipe**, no id a pack can
exclude.

**Consequences.**

- `crafting-with-fluid` is now any recipe with a fluid that is not a Brewed recipe. NeoForge keeps the values a config file
  already holds, so a one-time upgrade (`configVersion` in the server config, 0 to 1) adds `brewing` to a tier whose
  `categories` are exactly the three there were before, which then meant every category. A list that left something
  out is kept as written, so a pack that lists a tier's `categories` must add `brewing` for that tier to brew; and once
  the file is at version 1, a pack can list those three to drop it.
- The ids are part of a pack's data (exclude lists, KubeJS); changing the scheme later breaks a pack (ADR-0008).
- Water bottles, potion bottles as inputs and the stand itself are unchanged; the stand still brews step by step.
- A potion stacks to one, and a result template of three of it is no stack, so a Brewed recipe lists three results of
  one for a potion and one result of three for an item that stacks. The Held recipe's leftover rule, at most a stack
  of one kind (ADR-0021), now asks that what a craft owes, of any kinds, fills at most the three output slots the
  product leaves, a stack counting once per item and components: a lingering recipe owes two more potions and the
  glass bottle of its dragon's breath.
- The brewing table belongs to the server, which doesn't exist for the first recipe load: that load adds no Brewed
  recipes, and the reload `ModRecipes.follow` makes once the server has started brings them in.
- A chain of more than four distinct ingredients, with the bottles, is more than the Assembler's five input slots
  and could never be held, so no Brewed recipe is added for it and one log line counts those skipped, such as a
  lingering long potion (nether wart, a second item, redstone, gunpowder and dragon's breath).
