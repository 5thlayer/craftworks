---
status: accepted, schedule and budget superseded by ADR-0024
---

# Ready is worked out on the server, only while watched, across ticks

The recipe viewer's craftables list shows the Assembling recipes that are **Ready** (#17): one craft
resolves with nothing Missing or Locked, intermediates included. Whether a recipe is Locked is known only
on the server, and the Lock sources (the recipe book, Researchd, hooks, KubeJS) are polled: none says when
its answer changes.

**Decision.** The server resolves Ready for a player and syncs the Ready recipe ids, and only while that
player's client reports the inventory screen open. It recomputes when the screen opens, after the inventory
has been quiet for 10 ticks, and on a 40-tick heartbeat that catches a lock lifting. A refresh is spread
over ticks within a fixed budget of about 2 ms per tick, with no config key, and is synced once complete,
its total time logged. Every Assembling recipe is a candidate, not only those whose direct inputs are held.

**Considered: the client resolves Missing, the server syncs Locked ids.** It reacts faster to the
inventory, but the client would need its own recipe set, and knowing every recipe's Locked answer still
means asking the Lock sources for each recipe on the server.

**Considered: resolving in one tick.** A refresh is one resolve per Assembling recipe, a thousand from the
vanilla pack alone and every mod's Converted recipe on top, so a server tick shared with other players
would pay for one player's open inventory.

**Consequences.** The list lags a lock lifting by up to two seconds, and a large pack by however many ticks
a refresh takes. A player with the inventory closed costs nothing.

**Amended (#66): Ready decides what the list shows, never whether a click reaches the server.** A click
on a Craftables entry or a favorite, or EMI's craft hotkey on a hovered stack, sends its Fill whatever
the last synced set says. In a large pack the set lags the inventory by most of a minute, and a click
gated on it did nothing at all. The server already resolves every Fill and opens the Crafting Plan
naming what is Missing or Locked (ADR-0004), so the lagging set is only a hint about what to list. A
favorite with several Assembling recipes still prefers a Ready one; only when none is Ready does EMI's
own choice stand.

**Amended (#77): a plain right click on a stack queues five, after every EMI bind.** On the Personal
Assembler's screen, a plain right click on a Craftables entry, a favorite or an index stack queues five
the way a left click queues one, and a five the inventory can't cover opens the Crafting Plan, as on
`+` (ADR-0004). EMI has no craft bind that carries a button, so Craftworks answers right click itself,
but only on a press that none of EMI's binds took: with EMI's defaults view-uses keeps right click, and a
player who wants five unbinds it. Taking right click ahead of EMI was rejected, since it would silently
cost every Assembling item its view-uses on the inventory screen. The press crafts only through the
Personal Assembler's handler; a recipe that would go to any other handler, EMI's 2x2 one included, gets
nothing, so no ingredients move into a grid. Shift + right click adds nothing, since Shift + left click
already queues all.
