---
status: accepted
---

# A plain ingredient takes its items whatever components they carry

A vanilla ingredient tests the item and nothing else: a gold-quality cheese is cheese, and a Functional Storage
pusher with its settings changed is a pusher. Craftworks named items by id and component patch and matched them
exactly ([ADR-0002](0002-an-assembler-item-is-its-id-and-its-component-patch.md)), and its placed machines hold one
variant per slot, so in both Assemblers a stack carrying components fell short of the ingredient that accepts it
(#87).

## The Personal Assembler

[ADR-0028](0028-a-tested-ingredient-takes-the-players-own-stacks.md)'s widening covers plain ingredients too. When
a player plans, a plain ingredient also lists the keys of the stacks carrying components that the player holds of
the items it lists, after its listed items. Keys stay exact strings and the Resolver stays free of Minecraft, so
ADR-0002 stands. Only the recipes listing an item the player holds a component-bearing stack of are looked at,
through an index built with the recipe set. A component ingredient keeps its exact match, ADR-0002's deliberate
divergence.

## The placed Assembler: spare slots

An input slot still holds one variant. An input the Held recipe doesn't use is a **Spare input**:

- It takes a variant of an ingredient whose own slot holds another, and holds one variant at a time.
- A craft takes each ingredient from its own slot, then from the spares, any variant the ingredient accepts.
- A result the outputs have no room for goes to a spare, after the outputs, as long as no ingredient of the Held
  recipe takes it, since the next craft would spend it; the item face extracts it from there.

Not a buffer of variants behind each slot: the menu shows one stack per slot, and a hidden variant could be neither
seen nor taken out by hand. The cost is that a recipe using all five inputs has no spare, and a second variant waits
until its input empties, as before.
