---
status: accepted
---

# The 2x2 grid is always gone

Installing Craftworks removes the inventory's 2x2 crafting grid, with no setting to keep it. The Personal
Assembler's queue is drawn in its place (ADR-0005). Craftworks is for packs that want hand-crafting
planned and serial, and a working 2x2 beside it would be an instant, unplanned way round that pace.

**The grid's slots are hidden, not deleted.** `InventoryMenu`'s slot indices (result 0, grid 1–4, armour
5–8, and so on) are read by other mods. The grid slots stay in the menu, disabled and drawn nowhere.
Removing them would shift every index after them. Do not "clean them up".

**Considered: an `inventoryGrid` setting of keep, hide or replace.** Rejected. `hide` left an empty hole
in the screen. `keep` made the inventory screen something other than the Assembler, and it needed a
second screen, a keybind, and a rule for which handler owns Fill Recipe when EMI's own 2x2 handler shares
the inventory's key. Everything that setting existed to allow is a pack's decision to install Craftworks
or not.

**Consequences.** Pacing is the pack author's job: craft time, Route priority, and removing or converting
recipes. That includes making sure a new player can make their first planks. Nothing is stranded in the
old grid, because vanilla returns its contents to the inventory on close and on logout.
