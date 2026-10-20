---
status: accepted
---

# A wrapped crafting recipe converts through the recipe it wraps

ADR-0011 converts a crafting recipe whose class extends `ShapedRecipe` or `ShapelessRecipe`. Sophisticated
Backpacks' recipes (the backpack, its tier upgrades, its upgrades' next tiers) extend neither: each implements
`CraftingRecipe` and wraps a vanilla `ShapedRecipe`, which it hands out through its `IWrapperRecipe`
interface's `getCompose()`, and its tier upgrades say `isSpecial()` to stay out of the recipe book. All 28 in
the Showcase stayed at the crafting table, unnamed by any log line (#68).

**Decision.** A crafting recipe that is neither shaped nor shapeless, implements an interface named
`IWrapperRecipe` and has a public no-arg `getCompose()` that returns a shaped or shapeless recipe is a
**wrapper**, matched by name so Craftworks compiles against no other mod. It converts through the recipe it
wraps:

- the inner recipe's slots and result make the Converted recipe, at the wrapper's id;
- the inner recipe's `isSpecial()` decides whether it converts, not the wrapper's;
- it is always a Data-carrying recipe (ADR-0022), the wrapper kept as the original, so a tier upgrade keeps
  the backpack's contents; a craft lays its grid out by the inner recipe's pattern and asks the wrapper what
  it made.

As ADR-0022 accepted, the recipe is asked with a grid alone and its own `matches` never: Sophisticated refuses
to upgrade a linked-storage endpoint that is not the primary, and both Assemblers upgrade it anyway. A pack
that minds keeps the recipe at the table with `modRecipesExcluded`. So does one that minds a renamed or filled
chest paying for the plain backpack, which a Data-carrying recipe allows.

The Assembler machine's every-tick trial of a craft checks for room with the listed result, and asks a
Data-carrying recipe what it made only when a craft finishes, so a mod's `assemble` isn't run each tick on the
stacks it holds.

**Considered: any public no-arg method returning a shaped or shapeless recipe.** It would catch another mod's
wrapper unseen, but it calls unknown getters of every unconverted recipe on the reload thread, and a recipe
that merely names another for display would be converted as that one.

**Considered: a config list of wrapper classes.** Explicit, but every pack would have to find and name
Sophisticated's classes before its backpacks worked.

**Consequences.** A mod that wraps recipes some other way still stays at the table, and needs its own rule.
This amends ADR-0011 (a wrapper converts, though its class extends neither recipe) and ADR-0022 (a wrapper's
grid follows the recipe it wraps; the machine asks a Data-carrying recipe only on a finished craft).
