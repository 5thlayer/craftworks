# Changelog

Written for players and pack authors: what the Personal Assembler does, and what a pack can set.

## Unreleased

- Craftworks is playable out of the box: vanilla's crafting recipes are Assembling recipes at their own ids, so the crafting table no longer makes them instantly. Special recipes (fireworks, dyeing, map cloning, repair and the like) stay at the crafting table. A pack turns this off with `vanillaRecipes = false` in `craftworks-server.toml`; a pack that relied on vanilla crafting staying at the table must set it. Its own recipes at vanilla's ids still win over the built-in ones. (#9)
- Several Assembling recipes can make the same item. The Assembler takes the highest `priority` route that resolves with nothing Missing or Locked, ties going to the lowest recipe id, and falls back down the list: sticks come from bamboo when there are no planks. (#8)

## 0.1.3

- The jar carries its MIT licence.
- The inventory no longer shows a white sliver of the removed crafting grid's result slot beside the queue. (#19)

## 0.1.2

- A Locked recipe in the Crafting Plan can say why, for example "Research: Steel Axe", beside the entry and in the "Can't start" line. With `lockSources = ["researchd"]` it names the research that unlocks the recipe. Mods and packs can give reasons through `LockHooks.registerReasoned`. (#18)

## 0.1.1

- The Crafting Plan: a middle click on Fill Recipe opens it, and so does a request your inventory can't cover, naming why (Missing, Locked or not enough). It lists what a craft consumes, the intermediates it makes, and what is Missing and Locked. +1, +5 and All queue straight away and grey out when your inventory can't cover them; the plan stays open and updates as you queue, showing the first queue rows. Close returns to the inventory. (#7)
- Fill Recipe on a Locked recipe now opens the Crafting Plan showing it Locked, rather than queueing nothing silently. (#10)

## 0.1.0

- Assembling recipes: a datapack can add `craftworks:assembling` recipes (ingredients with counts and tags, one result, `time` in ticks defaulting to 10, `priority` defaulting to 0). EMI shows them in an Assembling category. (#4)
- The Personal Assembler queues and crafts: on the inventory screen, EMI's Fill Recipe on an Assembling recipe queues 1 (left click), 5 (right click) or as many as your inventory covers (Shift). The whole cost is taken up front, each step takes its craft time, and results and ingredient remainders arrive in your inventory. A full inventory pauses the queue until there is room. The queue survives logging out, is shown beside the hotbar while the inventory is closed, and is refunded into your inventory when you die, before your items drop. (#5)
- The inventory screen is the Assembler: the 2x2 crafting grid and the recipe book button are gone, and the queue is drawn where the grid was, two rows and a "+N more" line. Click either icon of a row to cancel crafts of its final recipe: left 1, right 5, Shift all; what is left of a row is planned again in place. The grid's slots stay in the inventory menu, so slot indices other mods use are unchanged. A client now needs EMI or JEI installed, and refuses to start without one; a dedicated server needs neither. (#6)
- Lock source: the server config `lockSources` lists what locks a recipe for a player: `recipeBook` (Locked until the recipe is in their recipe book) and `researchd` (Locked while Researchd blocks it for their team). Empty, the default, locks nothing. Mods add their own locks through `io.github._5thlayer.craftworks.api.LockHooks`. A recipe is Locked if any listed source or any hook says so, and Fill Recipe on a Locked recipe queues nothing. (#10)
