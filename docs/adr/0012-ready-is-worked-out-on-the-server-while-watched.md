---
status: accepted
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
