---
status: accepted
---

# A breaking change for a pack author bumps the minor

Craftworks' version is semver from 0.1.0, and Groundworks' rule (its ADR 0001) is the train's rule too.
Below 1.0, only a breaking change bumps the minor version. An addition or a fix bumps the patch.

A breaking change is one that could make a pack that worked before stop working, or behave differently,
with no change of its own. For Craftworks that covers:

- a pack's data: an Assembling recipe's fields, the built-in vanilla pack's flag, and the files and keys
  a pack writes to set Route priority
- a pack's config: a key's name, meaning or default
- the code a pack or another mod calls: the Lock source hook and the KubeJS bindings
- a player's save: a queue saved by one version that another can't read back

Changes that only move the pace of play, like a default craft time, don't count as breaking. The pack
author's own values still win.

**Considered: Groundworks' version ranges.** Beltworks nests Groundworks under a range up to the next
minor, so a patch reaches the Pack with no Beltworks release. Nothing nests Craftworks, and the Pack pins
the exact version. A range would buy nothing, so the minor here only tells a pack author to read the
changelog before moving the pin.

**Consequences.** A minor release's changelog names what a pack author has to change. A release that
reads an older save's queue in a new form is still a patch, as long as the old saves load.
