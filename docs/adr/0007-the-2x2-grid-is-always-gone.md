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

**Amended: Craftworks teaches the first craft.** Leaving a new player's first planks to the pack left them
to nobody: a pack that ships no content of its own, such as the FactoryWorks Showcase, had no place to say
it, and a player who opened the inventory found no grid and nothing pointing to Fill Recipe. While the
Personal Assembler's queue is empty, the inventory screen now draws a hint where the queue's rows would be:
find an item in EMI or JEI and press +. It names no one viewer, is cut to the blanked area with a trailing
"…" measured by the font, so a GUI scale or a longer translation still fits, and shows its full text as a
tooltip when cut. It is a standing placeholder, not a toast: it keeps no saved state, shows whenever the
queue is empty and is gone as soon as it has a row. The hotbar overlay has none, since the inventory is
where a craft starts. Pacing stays the pack's: craft time, Route priority, and removing or converting
recipes.
