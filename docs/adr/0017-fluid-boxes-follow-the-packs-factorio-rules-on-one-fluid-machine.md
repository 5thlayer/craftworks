---
status: accepted
---

# Fluid boxes follow the Pack's Factorio rules, on one fluid machine each machine describes

The **Chemical Plant** (#26) sized its boxes by a rule of Craftworks' own: an output box held the larger of
1,000 mB and the Overload Limit's crafts' worth of its result (ADR-0015), and an input box, like the **Assembler**'s
(ADR-0014), was pulled into until it held 1,000 mB. The FactoryWorks Pack, whose machines Craftworks' replace
(FactoryWorks ADR-0115, ADR-0118), sizes them by figures measured from Factorio (`overload.json`, `specs.json`), and
the **Oil Refinery** (#27) is the next machine to need them.

**Decision.** Every fluid box in Craftworks follows the Pack's rules, as constants beside `OverloadLimit`'s and not
as config:

- An input box holds 1,000 mB, and a machine pulls into it only until it holds 4 crafts' worth of the ingredient
  bound to it, whatever the crafting speed.
- An output box is 100 mB, and holds the larger of that and 3 crafts' worth of the result bound to it. The first
  result also takes the volume of every output box the recipe leaves unused, unless the recipe is **Pinned**: an
  optional `pinned_fluid_results` field on the Assembling recipe, false when omitted.

A craft still waits while an output box can't hold what it makes.

The machines that hold fluid are one fluid machine, each described by a record of its item slots, its input and
output fluid boxes and where its **Fluid Connections** stand, not one set of classes per machine.

**Considered: keeping the 1,000 mB / Overload Limit rule.** It was simpler, and a bucket's worth reads well on a
gauge, but a pack tunes its recipes to Factorio's figures; a machine that holds ten crafts of a result where
Factorio holds three behaves differently in a chain, and the Pack would have to carry its own rule on top of
Craftworks' machines to get its balance back.

**Considered: the multipliers and base sizes in `craftworks-server.toml`.** They are measured figures, not
preferences, and a server that changes them quietly breaks a pack's balance.

**Considered: Pinned as a list of recipe ids in config**, as the Pack's `overload.json` keeps it. Pinning is a fact
about the recipe, as Factorio's `fluidbox_index` is, so it travels with the recipe a pack supplies.

**Considered: an `OilRefinery*` copy of the Chemical Plant's classes.** About 900 lines of near-duplicate code, and
a third fluid machine would make a third copy.

**Consequences.** The Chemical Plant's and Assembler 2 and 3's boxes change from what 0.4.2 shipped: an output box
starts at 100 mB, and an input box holds 4 crafts' worth, so a tier-2 Assembler on a recipe of 10 mB a craft holds
40 mB, not a bucket. The Pack sets `pinned_fluid_results` on its `basic_oil_processing` when that recipe moves onto
Craftworks. ADR-0015's sizing paragraph is superseded; its connections stand.

The one fluid machine is now the Assembler (ADR-0018, #40): the Chemical Plant and the Oil Refinery are removed, and an Assembler of tier 2 or 3 has the two input and three output boxes, sized by these rules, of `FluidLayout.ASSEMBLER`. The rules themselves stand.

Every tier of Assembler has those boxes since ADR-0019 (#41), tier 1 included; the rules stand.
