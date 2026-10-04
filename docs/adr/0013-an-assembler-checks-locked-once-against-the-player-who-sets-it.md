---
status: accepted
---

# An Assembler checks Locked once, against the player who sets its Held recipe

An **Assembler** makes its **Held recipe** with no player at hand, but a Lock source answers for a
`ServerPlayer` (`LockHooks`), online. The Pack's Oritech machines locked by the Researchd team in a
placed-by attachment each one carried, asking on every craft (the Pack's `ResearchdMachineLocks`), and
5thlayer/craftworks#21 first asked for the same: locked "by the team that placed them".

**Decision.** Fill Recipe on an open Assembler asks the Lock sources for the player who presses it, who is
online then, and refuses a recipe Locked for them. Once held, the recipe is never asked about again: the
Assembler crafts it whoever is online, and breaking and placing it again keeps it.

**Considered: the placer's team, on every craft.** It needs a team, and Craftworks has none: Researchd's
is Researchd's, FTB Teams leaves the Showcase (FactoryWorks ADR-0115), and a Library naming either breaks
ADR-0006. Asking every craft also needs the placer online, or a remembered answer that goes stale in the
same way the one-time check does.

**Considered: a machine lock hook**, `LockHooks` asked with the Assembler and its owner, a pack's Binding
answering from whatever teams it has. It keeps team locking live, at the cost of a second hook API every
Lock source must learn, for a case that barely happens: a recipe is rarely Locked again once unlocked.

**Consequences.** A player can set a recipe they have unlocked on an Assembler that a teammate, or anyone,
then runs. A recipe Locked after it is held keeps being made until someone changes it. A pack that wants
team-wide locking for Assemblers needs a new decision and the hook above.
