---
status: accepted
---

# A breaking change for a pack author bumps the minor

Craftworks' versioning is libworks' ADR 0001, which every Library inherits unchanged:
<https://github.com/5thlayer/libworks/blob/main/docs/adr/0001-below-1-0-an-addition-bumps-the-patch.md>.
This ADR held the rule until the template took it over, and now only names what it covers here.

For Craftworks, a breaking change covers:

- a pack's data: an Assembling recipe's fields, the built-in vanilla pack's flag, and the files and keys
  a pack writes to set Route priority
- a pack's config: a key's name, meaning or default
- the code a pack or another mod calls: the Lock source hook and the KubeJS bindings
- a player's save: a queue saved by one version that another can't read back

Nothing nests Craftworks and the Pack pins its exact version, so the minor only tells a pack author to
read the changelog before moving the pin.
