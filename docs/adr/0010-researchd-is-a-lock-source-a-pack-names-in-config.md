---
status: accepted
amends: [10]
---

# Researchd is a Lock source a pack names in config

#10 gave the **Lock source** two built-in values, nothing and the vanilla recipe book, and a Java API for
everything else. The one pack Craftworks is built for gates its hand recipes with Researchd, so every
pack author on it would write the same Java (or KubeJS) hook calling `ResearchdApi.isRecipeBlocked`.

**Decision.** `lockSource` becomes `lockSources`, a list, and `researchd` is one of its values beside
`recipeBook`. Empty, the default, locks nothing. A recipe is Locked if any listed source or any hook says
so. A pack gates the Assembler on its research tree with `lockSources = ["researchd"]` and no code.

**Researchd is reached by a method handle, not compiled against.** Its jar is published to no maven,
and a build that needed it could not run in CI. The one call is looked up the first time it is asked.
Listing `researchd` without Researchd installed, or on a Researchd without that call, is an error in the
log and the source locks nothing; it is not a crash, since a server that will not start over a config
line is a worse answer than one that logs it.

**Considered: a KubeJS startup script calling `LockHooks.register`.** It works with no change here, but it
is still code, it depends on KubeJS's class filter admitting both classes, and it is the same few lines in
every pack that uses Researchd.

**Considered: a data file listing Locked recipe ids.** Rejected: Researchd unlocks per team as research
completes, which a static list cannot say.

**Consequences.**

- The signature is checked by nothing at build time. A Researchd that renames the call fails soft, and
  only its log line says so.
- A call that is found but throws is not failed soft: the resolve that asked fails, loudly, since a
  lock that cannot be answered must not quietly read as unlocked.
- No game test runs against Researchd, which needs its own library on the run; the game tests cover the
  source listed with Researchd absent.
- Each further mod that deserves a built-in source is a new value here, not a hook in the pack.
