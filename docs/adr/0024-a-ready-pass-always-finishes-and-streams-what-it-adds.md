---
status: accepted
---

# A Ready pass always finishes and streams what it adds

ADR-0012 spreads a refresh of the Ready set over ticks and drops a pass in flight on an inventory change, an
open or a close. In the FactoryWorks Showcase (about 106 mods, 2,426 Assembling recipes) a pass took about
10 s of server time over about 1,000 ticks, so a player who kept playing rarely saw one finish: the list
lagged the inventory by most of a minute and stayed empty for about 50 s after joining. It also spent about
10 ms a tick against the 2 ms budget, because the budget is checked between whole resolves and a few
resolves, searches failing deep down, cost tens of ms each (#67).

**Decision.** This supersedes ADR-0012's schedule and budget; what stays is that Ready is worked out on the
server only while the inventory screen is open, and that it never gates a click (#66).

- **A pass always finishes and publishes.** A change during it marks the inventory dirty, and the next pass
  starts once the inventory has been quiet for 10 ticks. A set stale by one pass beats no set, and since #66
  a stale set only shows a wrong list.
- **Closing pauses a pass; opening resumes it.** Nothing runs while closed. A change while closed counts as
  pending, so a fresh pass follows the resumed one.
- **Additions stream; removals wait.** Newly Ready ids are sent every 20 ticks or so while a pass runs;
  removals only when it ends, so a recipe never vanishes and comes back mid-pass. Last pass's Ready recipes
  are resolved first.
- **A resolve stays whole, and is made cheap instead.** Trying a route no longer copies the whole inventory;
  a pass remembers the items it found unmakeable from its snapshot, a search cut short by the Resolver's
  search limit never counting, and skips them for the rest of the pass; it asks the Lock source once per
  recipe. The memo and the cached Lock answers are the Ready pass's own: Fill still asks live and still
  reports every Missing and Locked item.

**Considered: a resolve that can pause partway.** It holds the budget exactly, but means rewriting the
recursive walk, rollbacks included, as an explicit stack in the code Fill queues from.

**Considered: starting a pass at login, or keeping the set on the client across sessions.** The first pays for
players with the inventory closed, which ADR-0012 refused; the second shows a set from another world or another
time. Streaming fills the list from the first open instead.

**Considered: remembering what an item can be made of.** Not sound: a walk shares its stock and surplus between
branches, so "3 of X can be made" in one place says nothing about another. Only "none can be made from the whole
snapshot" holds everywhere in the pass.

**Consequences.** The budget is still checked between resolves, so a single resolve can overshoot it; the cap
on one is the search limit, made cheap. The list lags the inventory by up to one pass plus the quiet ticks, and
a lock lifting mid-pass waits for the next pass.
