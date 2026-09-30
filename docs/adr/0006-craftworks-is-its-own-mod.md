---
status: accepted
amends: [1, 2, 3, 4, 5]
---

# Craftworks is its own mod

ADR-0001 to ADR-0005 were written for PlanetaryFactory's `core/assembler/`, where the Personal Assembler
served one pack: Factorio's recipes, Researchd's locks, EMI only. Craftworks takes it out as a mod any
pack can use (5thlayer/craftworks#1), and PlanetaryFactory pins its jar and deletes its own copy. This ADR
records where Craftworks departs from those five; everything they decide that is not named here stands.

The Pack has since been renamed FactoryWorks, at 5thlayer/factoryworks (its ADR-0101). PlanetaryFactory in
these ADRs names that Pack, and links to adamico/planetary-factory redirect there.

**The mod is Craftworks; the planner is still the Personal Assembler.** The name leaves room for the mod to
grow without committing it to. The glossary keeps every term the imported ADRs use.

**Only Craftworks' own recipe type is planned with.** ADR-0001 made the hand set a predicate over the
pack's assembling type. Here the Assembler plans only with `craftworks:assembling` recipes: ingredients,
one result, a craft time in integer ticks (default 10) and a Route priority, all as recipe fields. A pack
changes them by overriding the recipe, in a datapack or through KubeJS's recipe schema.

- *Considered: admitting foreign types by config* (`minecraft:crafting` by default, KubeJS to admit or
  deny, shaped recipes read as bags). Rejected: it meant special recipes, per-type deny rules and two more
  override sources for priority and time, all to avoid a conversion the pack author can run once.
- *Consequence:* a hand-craftable recipe that a machine also makes is two recipes with two ids, since an id
  has one type. A pack's lock hook maps between them.
- *Consequence:* a fresh install would craft nothing, so Craftworks ships vanilla's crafting recipes
  converted in place, at their own ids, as a built-in datapack that a server config flag turns off. Only
  special recipes (fireworks, dyeing, map cloning, repair) stay at the crafting table.

**Several routes to one item are allowed, and the resolver falls back.** PlanetaryFactory asserted one
hand recipe per item and let the first registered win. Craftworks' vanilla pack has many (sticks from
planks or bamboo), so each item takes the highest-priority route the player can resolve with nothing
Missing or Locked, per item and greedily; ties go to the lowest recipe id. Strict priority was rejected:
it reports planks Missing to a player holding bamboo.

**There is no Access gate.** The Assembler is the only hand-crafting surface (ADR-0007), so gating it
behind an item softlocks a new player who cannot make planks. A pack that wants hand-crafting earned
locks recipes through the Lock source instead, and the plan says why.

**Locks come from a Lock source, not Researchd.** `lockSource` is `none` or `recipeBook`, and any hook a
pack registers, through the Java API or KubeJS, is combined with it: a recipe is Locked if any of them
says so.

**The inventory screen is the only place the Assembler is shown**, with the HUD overlay. ADR-0005 stands,
and no screen of its own is added. Fill Recipe comes from EMI, and from JEI through its own recipe button,
with ADR-0004's clicks. The recipe book button is hidden on the inventory screen.

**Ingredient remainders return when their step completes.** An ingredient's own crafting remainder (an
empty bucket) goes to the inventory, and a plan never counts on it, so the cost stays up front.
