![Craftworks](https://raw.githubusercontent.com/5thlayer/craftworks/main/publish/craftworks-cover.png)

Craftworks replaces the crafting grid with the **Personal Assembler**: ask for an item, and every intermediate is planned, the whole cost is taken up front, and the crafts run one after another over time while you keep playing.

**NeoForge, Minecraft 26.1.2 only.** Craftworks is early work, so please report any issues you find on [GitHub](https://github.com/5thlayer/craftworks/issues).

> **Craftworks is built for modpacks.** It adds no recipes of its own: the Assembler crafts only the `craftworks:assembling` recipes a pack or datapack gives it.

## Features

- **Ask for the item, not the steps.** Find an item in EMI and press Fill Recipe on its Assembling recipe: left click queues 1, right click 5, Shift as many as your inventory covers. Every intermediate is resolved for you, so asking for a machine plans the plates, gears and circuits that go into it.
- **Paid up front, crafted over time.** The whole cost of a request is taken when you queue it, and each step takes its own craft time. Results land in your inventory as they finish; a full inventory pauses the queue until there is room.
- **The inventory is the Assembler.** The 2x2 crafting grid is gone, and the queue sits where it was. With the inventory closed, the queue shows beside the hotbar.
- **The Crafting Plan.** Middle-click Fill Recipe to see what a request would consume, the intermediates it would make, and anything **Missing** (you have none and nothing can make it) or **Locked** (not yours to use yet). Queue +1, +5 or All from there, and watch the plan update as you do.
- **Cancel what you don't need.** Click a queue row to cancel crafts of it: left 1, right 5, Shift all. Everything is refunded, and whatever is left is planned again in its place in the queue.
- **Nothing is lost.** The queue survives logging out, and on death it is refunded into your inventory before your items drop.

## For pack authors

- **Assembling recipes.** A datapack adds `craftworks:assembling` recipes: ingredients with counts and tags, one result, a `time` in ticks (default 10) and a `priority` (default 0). Items only, no fluids. EMI shows them in an Assembling category.
- **Route priority.** When several recipes make the same item, the Assembler uses the highest-priority one the player can complete, at every level of the plan.
- **Locking recipes.** The server config `lockSources` decides what keeps a recipe Locked for a player: `recipeBook` (until the recipe is in their recipe book) and `researchd` (while Researchd blocks it for their team). The Crafting Plan names the research that unlocks it. Other mods add their own locks, with or without a reason, through `io.github._5thlayer.craftworks.api.LockHooks`.

## Dependencies

- **EMI**, required on the client: the Assembler has no item list of its own and takes requests through EMI's Fill Recipe. A dedicated server doesn't need it.
- **Researchd**, optional, only if a pack locks recipes behind research.

## License

MIT. Source is on [GitHub](https://github.com/5thlayer/craftworks).
