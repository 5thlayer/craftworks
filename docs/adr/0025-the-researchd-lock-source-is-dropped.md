---
status: accepted
---

# The researchd Lock source is dropped

ADR-0010 made `researchd` a value of `lockSources`, reached by a method handle, for the one pack Craftworks
was built for. Researchd has no release for Minecraft 26.1.2, which Craftworks targets: the only public
build is for 1.21.1, and the dev client ran a jar built locally for 26.1. On a supported version the source
can lock nothing, so it was code, config and docs with nothing behind it (#74).

**Decision.** `researchd` leaves `LockSource`, with `ResearchdLocks`, its game test and the dev client's
`-PwithResearchd`. `lockSources` takes only `recipeBook`. The `-Dcraftworks.devLockReason` hook stays: it
shows a reasoned lock in the Crafting Plan, which `LockHooks.registerReasoned` and KubeJS still give.

**A config that lists `researchd` is corrected, not tolerated.** `lockSources` validates each value against
`LockSource`, so NeoForge drops the old name. There is no alias or warning for one release: no 26.1.2 pack
can have Researchd installed, so none can depend on it. It breaks a pack author's config, so the release
bumps the minor and the changelog marks it **Breaking** (ADR-0008).

**Bringing it back is a new decision, not a revert.** If Researchd ships for a supported Minecraft version,
whether it is a built-in source, a hook or a KubeJS script is decided then, against what its API is by then.

This supersedes ADR-0010. Other ADRs that name Researchd stay as written, as history.
