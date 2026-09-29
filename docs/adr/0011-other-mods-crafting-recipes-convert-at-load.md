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
recipe (the built-in vanilla pack, or a pack's own) is left alone. The server config's `modRecipes` flag,
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
