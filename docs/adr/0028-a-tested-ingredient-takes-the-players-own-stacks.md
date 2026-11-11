---
status: accepted
---

# A Tested ingredient takes the player's own stacks

[ADR-0002](0002-an-assembler-item-is-its-id-and-its-component-patch.md) matches item keys exactly: a key
with no patch names the pristine item and nothing else. That breaks one kind of ingredient: a custom
ingredient that decides for itself which stacks it takes, and is not NeoForge's component ingredient.
Neo Vitae's `orb_tier` takes any blood orb of a tier. It lists the bare orbs, so a bound orb, which
carries Neo Vitae's binding and fluid components, never paid for a Blank Rune in the Personal
Assembler.

Such an ingredient is a **Tested ingredient**. The planner keeps exact keys, but when a player plans,
each Tested ingredient also lists the keys of the stacks the player holds that the ingredient's own
test accepts, after its listed items. The Resolver still compares strings and stays free of
Minecraft. The planner reads the recipes for that player instead of the one shared set, and only
when the player holds such a stack.

What the player's stock does not hold, a Tested ingredient still names by its bare items: a
component-bearing stack the planner could craft is not offered for it.
