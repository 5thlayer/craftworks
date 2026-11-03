---
status: accepted
---

# Other mods' crafting recipes convert at load

The 2x2 grid is always gone (ADR-0007), and #9 builds vanilla's crafting recipes in as **Converted
recipes**. Every other mod's shaped and shapeless crafting recipes would still be reachable only at a
crafting table, or not at all, unless each pack author converted them by hand. #13 set out to give pack
authors a converter.

**Decision.** Craftworks converts them itself, in Java, at the end of every recipe reload: after every
datapack and KubeJS script has applied, each recipe whose class extends `ShapedRecipe` or
`ShapelessRecipe` with a fixed result is replaced by a real Assembling recipe at its own id, its slots
counted into a bag, with the default craft time and Route priority. An id that is already an Assembling
recipe (the built-in vanilla pack, or a pack's own) is left alone, as are vanilla's ids while
`vanillaRecipes` is off. (A subclass that assembles its own result was left alone here; ADR-0022 converts it
as a Data-carrying recipe.) The server config's `modRecipes` flag,
on by default, turns it off; an exclude list of namespaces and ids keeps chosen recipes at the table. An
ingredient the Assembling codec cannot encode skips its recipe with a log line.

**Considered: a command that dumps a datapack.** It sees the loaded truth, but the output goes stale the
moment a mod updates, and a pack author has to remember to run it again.

**Considered: a standalone script over mod jars, like #9's build task.** Raw JSON misses load conditions,
KubeJS edits and recipes a mod registers in code.

**Considered: a KubeJS script, automatic or copied in.** It makes KubeJS a requirement for a working
Assembler in any modded pack.

**Considered: feeding crafting recipes to the Assembler's recipe set only, leaving them in place.** No
mixin and nothing replaced, but they would not be Assembling recipes: absent from the recipe viewer's
Assembling category, invisible to KubeJS, and with no id a pack could give a Route priority.

**Consequences.** Smelting and machine recipes are never converted; those stay the pack's to write. The
conversion stays separate from #9's build task: the inputs differ (JSON from the game jar against decoded
recipes) and the rule is small.

**Storage recipes take one tick (#78).** The same pass gives the Converted recipes that undo each other, nine or
four of one item making one of another and back, one tick instead of ten. It decides over every Converted recipe at
load, this load's conversions and the built-in vanilla pack's together, so a round trip between the two is found;
the vanilla pack's JSON carries no `time`. The pack a recipe comes from is read off the resource that wins at its
id, so a pack's own Assembling recipe at a vanilla id is neither timed nor the way back for another.

**Amended: each Storage recipe gets a Storage crafting recipe back (#83).** Replacing the crafting recipes left
other mods that pack and unpack items with nothing to find: Functional Storage's and Storage Drawers' compacting
drawers and Sophisticated Backpacks' compacting upgrade read the loaded recipes themselves and keep only crafting
recipes, a shaped one for the way down. So beside every Storage recipe, the vanilla pack's included, the same pass adds
a real crafting recipe at `craftworks:storage/<namespace>/<path>`, built from the Storage recipe rather than kept from
the original, so vanilla's and every mod's come out alike: packing as a shaped grid of its one ingredient, unpacking
as a shapeless recipe of one item. A crafting table and vanilla's Crafter make them at once, which gives nothing away
when a Storage recipe takes one tick; telling a table from a drawer was not worth it. The recipe viewer and the
recipe book don't show them, so a metal is not listed twice. `modRecipesExcluded` can name one to leave it out, since
they arrive after KubeJS. A pack's own Assembling recipe is not a Converted recipe and gets none.

*Considered: a mixin on the recipe lookup.* Vanilla's `getRecipeFor` is a choke point for the table and the Crafter
only; the drawers read the recipe map directly and check the recipe's class, so no lookup hook reaches them.

*Considered: every Converted recipe, for autocrafting mods.* That reopens an instant way round the Personal
Assembler for everything (ADR-0007). Only the Storage recipes, already one tick, come back.
