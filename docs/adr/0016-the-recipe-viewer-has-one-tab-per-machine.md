---
status: accepted
---

# The Recipe viewer has one tab per machine, and a category no machine holds shows in none

EMI and JEI showed every Assembling recipe in one tab, `craftworks:assembling`, with every **Assembler** tier and
the **Chemical Plant** as its workstations (#38). `U` on the plant then listed every recipe in the game, and its
`chemistry` recipes were lost among them.

**Decision.** The Recipe viewer has one tab per machine: `craftworks:assembler` (icon Assembler 1, shared by the
three tiers) and `craftworks:chemical_plant`. A recipe is in every tab whose machine holds its **Category** under
the server config (`CraftworksConfig.categories`); the Assembler's tab takes any category any tier holds. By
default that is `crafting`, `advanced-crafting` and `crafting-with-fluid` in the Assembler's tab and `chemistry`
in the plant's; a recipe both hold is in both. Each machine is a workstation of its own tab only, so `U` on any
Assembler tier opens the Assembler's tab and `U` on the plant opens the plant's. The rule is `MachineTabs`, pure
Java with no Minecraft or viewer types.

**A category no machine holds shows in no tab.** `oil-processing` is held by nothing by default, so its recipes
are in no tab: the Recipe viewer lists what a machine can run, and a recipe nothing runs is not one. A pack that
wants them listed gives a machine the category. The log says how many were left out, once each time a viewer
builds its lists, so a pack author whose recipe has gone missing can find why. The Personal Assembler is not a
machine here: it ignores the category (**Hand-craftable** recipes are planned whatever theirs), and Fill Recipe
on the inventory screen works from either tab and gets no workstation.

**When it's read.** The tabs and workstations come from the server config as the client has it, which NeoForge
sends as the player logs in (`ConfigSync`), when EMI and JEI build their lists. A config edit shows after the
next recipe reload, not live.

**Fill Recipe stays on both tabs.** With a machine open, EMI's Fill Recipe and JEI's `+` show on both tabs, as
they did on the one. Whether the open machine holds the recipe is the server's call, and it refuses with its
usual message rather than the button hiding the reason.

**EMI keys a recipe by id.** A recipe in both tabs would be two EMI recipes with one id, so the copy in the
plant's tab takes `craftworks:chemical_plant/<namespace>/<path>`, and every handler reads the recipe's own id
back from the recipe rather than EMI's. JEI keys no recipe by id, so it needs none. A recipe in one tab keeps
its own id.

**Consequences.** An EMI favourite or recipe-tree entry saved under `craftworks:assembling` is lost once. A
recipe that moves between tabs on a config edit changes EMI id in the same way, once.

The Pack's ADR-0096 gives each of its machines a tab of its own too.
