# Changelog

Written for players and pack authors: what the Personal Assembler does, and what a pack can set.

## Unreleased

- Assembling recipes can name fluids: `fluid_ingredients` (a list of `{ingredient: "minecraft:water", amount}`) and `fluid_results` (a list of `{id, amount}`), both optional, and `hand_craftable`, default true. The Personal Assembler plans only through a Hand-craftable recipe: `hand_craftable` true and no fluid in or out. Any other never appears in a Crafting Plan, and EMI and JEI still list it. A recipe written before these fields reads unchanged. KubeJS sets them with `.fluidIngredients([Fluid.of('minecraft:water', 250)])`, `.fluidResults([...])` and `.handCraftable(false)`. (5thlayer/factoryworks#578)

## 0.2.0

- A request whose plan the Assembler would have to search endlessly for, such as a banner when you hold nothing towards any of its dyes, stops after 5,000 tries and shows as Missing, where it used to freeze the server. (#17)
- EMI: the Craftables list shows an Assembling recipe exactly when it is Ready: one craft resolves with nothing Missing or Locked, intermediates included, so sticks are listed when you hold only logs, and a Locked recipe is not. Both routes to an item are listed when both are Ready. Fill Recipe stays lit whatever you hold. The server works it out only while your inventory screen is open, a few ticks after your inventory settles and again every two seconds (which catches a lock lifting), within about 2 ms of a tick, so a large pack takes several ticks to update. Written for EMI 1.1.24 on 26.1.2: a different EMI logs an error and lists as before. (#17)
- EMI: `@craftworks` in the search finds every item an Assembling recipe makes, Locked or not. A row of tabs above EMI's item index holds saved searches: clicking one writes its query into the search bar, and it lights while its query is in the search. Craftworks ships an Assembling tab (`@craftworks`). A resource pack adds, replaces or removes tabs with `assets/<namespace>/craftworks/tabs/<id>.json` (`icon`, `name`, `query`, `order`, or `"enabled": false`), and mods register them with `IndexTabs.register`. Written for EMI 1.1.24 on 26.1.2: a different EMI logs an error and behaves as stock. (#15)
- Other mods' shaped and shapeless crafting recipes are Assembling recipes too: they convert at their own ids as recipes load, after datapacks and KubeJS, with the default time and priority, and a pack's own Assembling recipe at the same id wins. `modRecipesExcluded` in `craftworks-server.toml` keeps namespaces or recipe ids at the crafting table, and `modRecipes = false` turns it off; a pack that relied on mods' crafting staying at the table must set one. (#13)
- JEI support, optional: Assembling recipes have their own JEI category, and a recipe button beside each queues it on the Personal Assembler as EMI's Fill Recipe does. Left-click queues 1, right-click 5, Shift-click as many as you can afford, middle-click shows the Crafting Plan. Needs JEI 29.20.0.53 or later. (#12)
- KubeJS support, optional: scripts create and edit Assembling recipes with `event.recipes.craftworks.assembling(result, ingredients)`, plus `.time(ticks)` and `.priority(n)`, and lock a recipe for a player from a server script with `CraftworksEvents.lock(event => { ... event.lock(reason) })`, combined with the other Lock sources. Needs KubeJS 26.1.2-8.0.3 or later. (#11)
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
