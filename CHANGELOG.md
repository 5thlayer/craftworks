# Changelog

Written for players and pack authors: what the Personal Assembler does, and what a pack can set.

## Unreleased

- Assembling recipes: a datapack can add `craftworks:assembling` recipes (ingredients with counts and tags, one result, `time` in ticks defaulting to 10, `priority` defaulting to 0). EMI shows them in an Assembling category. (#4)
- The Personal Assembler queues and crafts: on the inventory screen, EMI's Fill Recipe on an Assembling recipe queues 1 (left click), 5 (right click) or as many as your inventory covers (Shift). The whole cost is taken up front, each step takes its craft time, and results and ingredient remainders arrive in your inventory. A full inventory pauses the queue until there is room. The queue survives logging out, is shown beside the hotbar while the inventory is closed, and is refunded into your inventory when you die, before your items drop. (#5)
