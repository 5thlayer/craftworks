![Craftworks](https://raw.githubusercontent.com/5thlayer/craftworks/main/publish/craftworks-cover.png)

Craftworks adds automated crafting in the spirit of Factorio. The **Personal Assembler** replaces the player's 2x2 crafting grid: pick the item you want in EMI or JEI, and every intermediate step is planned for you. The whole cost is taken up front, and the crafts run one after another over time while you keep playing. Two placed machines carry on from there, both powered by FE: the **Assembler**, which crafts one chosen recipe over and over, with fluids too, and the **Refiner**, an electric furnace that smelts without fuel.

**NeoForge, Minecraft 26.1.2 only.** Craftworks is early work, so please report any issues you find on [GitHub](https://github.com/5thlayer/craftworks/issues).

To play Craftworks beside every other FactoryWorks mod, install the [FactoryWorks Showcase](https://www.curseforge.com/minecraft/modpacks/factoryworks-showcase) modpack from CurseForge.

> **Craftworks is built for modpacks.** The Assembler crafts `craftworks:assembling` recipes: vanilla's and other mods' crafting recipes out of the box, and whatever a pack or datapack adds or replaces.

## Features

- **Ask for the item, not the steps.** Find an item in EMI or JEI and press Fill Recipe (JEI: the recipe button) on its Assembling recipe: left click queues 1, right click 5, Shift as many as your inventory covers. Every intermediate is resolved for you, so asking for a machine plans the plates, gears and circuits that go into it.
- **Paid up front, crafted over time.** The whole cost of a request is taken when you queue it, and each step takes its own craft time. Results land in your inventory as they finish; a full inventory pauses the queue until there is room.
- **The inventory is the Assembler.** The 2x2 crafting grid is gone, and the queue sits where it was. With the inventory closed, the queue shows beside the hotbar.
- **The Crafting Plan.** Middle-click Fill Recipe or the JEI button to see what a request would consume, the intermediates it would make, and anything **Missing** (you have none and nothing can make it) or **Locked** (not yours to use yet). Queue +1, +5 or All from there, and watch the plan update as you do.
- **Cancel what you don't need.** Click a queue row to cancel crafts of it: left 1, right 5, Shift all. Everything is refunded, and whatever is left is planned again in its place in the queue.
- **Nothing is lost.** The queue survives logging out, and on death it is refunded into your inventory before your items drop.

## Gallery

Craftworks carries a gallery of its features: a scene each, with a sign naming it, built in rows of three, east and then north of where you stand. The Personal Assembler's features are signs at a stand that say how to use it; the three Assemblers and the Refiner stand working, each powered by a Creative Energy Source. It is a datapack in the jar, **Craftworks Gallery**, off until you turn it on in a world's Data Packs screen or with `/datapack enable "mod/craftworks:gallery_pack"`. Then `/function craftworks:gallery/build` builds every scene, and `/function craftworks:gallery/<scene>` one of them, such as `assembler_1`; clicking a scene's sign builds it again. A function needs cheats in single player (on at world creation, or Open to LAN with cheats) or operator rights on a server. The gallery clears the ground it builds on and changes nothing else; it adds no recipes, loot or worldgen.

## For pack authors

- **Assembling recipes.** A datapack adds `craftworks:assembling` recipes: `ingredients` with counts and tags and a `results` list of item stacks (each may be left out when empty, as in a recipe that only uses and makes fluids), a `category` (`crafting` by default, or `advanced-crafting` or `crafting-with-fluid`, the last for any recipe with a fluid in or out), a `time` in ticks (default 10) and a `priority` (default 0). Optional `fluid_ingredients` and `fluid_results` are for an Assembler to handle (each fluid box holds 2,000 mB, so a recipe needing more of one fluid in a craft is refused; a `pinned_fluid_results` left in an older recipe is ignored), and `hand_craftable` (default true) set false keeps an item-only recipe for one: the Personal Assembler plans only through a recipe that is hand-craftable and names no fluid and has exactly one item result. EMI and JEI show them, and KubeJS scripts can create and edit them.
- **Vanilla recipes built in.** Vanilla's crafting recipes are Assembling recipes out of the box, at their own ids, so they replace the crafting table's; special recipes like fireworks and dyeing stay at the table. Set `vanillaRecipes = false` in the server config to ship your own set instead.
- **Other mods' recipes converted.** Every other mod's shaped and shapeless crafting recipes become Assembling recipes at their own ids as recipes load, after your datapacks and KubeJS scripts; your own Assembling recipe at the same id wins. Keep a mod's or a recipe's at the table with `modRecipesExcluded = ["modid", "modid:recipe"]`, or turn it off with `modRecipes = false`.
- **Route priority.** When several recipes make the same item, the Assembler uses the highest-priority one the player can complete, at every level of the plan.
- **Assemblers.** Three tiers of placed machine make one Assembling recipe each, their Held recipe, which you set with Fill Recipe on the open screen. Any FE source powers them, and each tier's speed, power and buffer are in the server config. Placing a higher or lower tier over one swaps it in place. Every tier also makes recipes with fluids, in or out, with or without items: two input fluid boxes and three output fluid boxes, on six Fluid Connections that pull the ingredients from a tank or pipe and push the results out. They stand at the ends of its front and back edges and the centres of its sides, so no two pipes beside them touch and each fluid of a recipe gets a network of its own. The tiers differ only in speed, power and buffer.
- **The Refiner.** A placed machine that smelts what vanilla's furnace and blast furnace do, on FE and no fuel: any vanilla smelting or blasting recipe, blasting where an input has both. Its speed, power and buffer are in the server config. Pipes and hoppers put in what a recipe smelts, up to two smelts' worth, and take out only the result. It smelts one item into one result; smelting with several inputs or results is an Assembling recipe, made by an Assembler.
- **Locking recipes.** The server config `lockSources` decides what keeps a recipe Locked for a player: `recipeBook` (until the recipe is in their recipe book) and `researchd` (while Researchd blocks it for their team). The Crafting Plan names the research that unlocks it. Other mods add their own locks, with or without a reason, through `io.github._5thlayer.craftworks.api.LockHooks`.

## Dependencies

- **EMI or JEI**, one of them on the client: the Assembler has no item list of its own and takes requests through EMI's Fill Recipe or JEI's recipe button. A pack ships one, not both. A dedicated server needs neither.
- **Groundworks**, required: the Assemblers stand on its footprint and swap tiers by its Fast Replace. Install it beside Craftworks; it is not bundled.
- **Pipeworks**, optional: its pipes fill and drain the Assemblers' Fluid Connections, and any other mod's tanks and pipes do too.
- **Jade**, optional: shows the machines' Held recipe, state, progress and energy in its tooltip.
- **KubeJS**, optional: scripts create and edit Assembling recipes and lock recipes for a player.
- **Researchd**, optional, only if a pack locks recipes behind research.

## License

MIT. The cover art uses Minecraft's item textures, which remain Mojang's. Source is on [GitHub](https://github.com/5thlayer/craftworks).
