---
status: accepted
---

# A bucket craft also pours as a fluid recipe

A Converted recipe (ADR-0011) that takes or makes a filled bucket works only bucket by bucket: Fieldworks' magma
slurry, a lava bucket and soul soil, can't run on an Assembler fed lava through pipes, and its slurry comes out in
buckets (#80, 5thlayer/fieldworks#51). Fieldworks can't ship an Assembling recipe of its own, since libworks'
`checkPlaysAlone` refuses any shipped file that names another Module (libworks ADR 0005, FactoryWorks ADR-0127), so
the fluid route has to come from Craftworks, as Brewed recipes do (ADR-0026).

**Decision.** At the end of every recipe load, after conversion, every Converted recipe that takes or makes a filled
bucket also gives a **Poured recipe** at `craftworks:fluid/<source ns>/<source path>`, in `crafting-with-fluid`:

- A filled bucket is an item that, by NeoForge's fluid item capability, holds exactly 1000 mB of one fluid and empties
  into `minecraft:bucket`. Bottles and tank items are not. An ingredient pours only when every item it accepts holds
  the same fluid; one that mixes a bucket with anything else leaves the recipe without a Poured recipe. A result is
  read the same way.
- Each filled-bucket ingredient becomes a fluid ingredient of 1000 mB of its fluid (the same fluid's buckets summed), a
  filled-bucket result a fluid result of 1000 mB. No bucket goes in, so none comes back as a remainder; and for each
  filled-bucket result one empty-bucket ingredient is dropped, as no bucket carries the fluid out. Every other
  ingredient and its remainder stays as it is.
- The source's craft time and Route priority carry over. A Poured recipe has a fluid, so it is never Hand-craftable.
- A Data-carrying recipe, a pack's own Assembling recipe and an id that is excluded or already taken get none. Nor does
  a recipe whose fluids no Assembler could hold, more than two input fluids, more than three output fluids or more than 2,000 mB of one; those are
  counted in one log line.
- The exclude list opts out either id: the source's, which keeps the bucket craft at the table and so derives
  nothing, or the Poured recipe's alone. There is no flag of its own.

**Considered: replacing the Converted recipe with the fluid one.** One recipe per craft, but a recipe with a fluid is
never Hand-craftable, so the Personal Assembler would lose the bucket craft it plans through today.

**Considered: Fieldworks shipping the Assembling recipe behind a `mod_loaded` condition.** Refused by
`checkPlaysAlone`, and every other mod with a bucket craft would need the same.

**Considered: the Assembler's Drain and Fill slots alone.** They already let an Assembler run a bucket craft from
buckets, but not from pipes, and the slurry would still leave as buckets.

**Consequences.**

- The ids are part of a pack's data (exclude lists, KubeJS); changing the scheme later breaks a pack (ADR-0008).
- The Recipe viewer's Assembler tab lists both routes for each such craft, and an Assembler can hold either.
- The remainders a source recipe overrides (#79) don't reach a Poured recipe, which has no bucket to return.
