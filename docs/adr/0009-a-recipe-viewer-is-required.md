---
status: accepted
amends: [6]
---

# A recipe viewer is required

The #2 spec made EMI, JEI and KubeJS all optional (story 56). EMI and JEI are no longer optional as a
pair: a client refuses to start when neither is installed, with a message naming both. The Personal
Assembler has no item list of its own, so without a **Recipe viewer** a player has no way to ask it for
anything beyond what the inventory screen already shows. KubeJS stays optional.

**The check is on the client only.** Fill Recipe reaches the server as Craftworks' own message (a recipe
id and which click). The server resolves the plan and never talks to the viewer, so a dedicated server
needs neither EMI nor JEI.

**Considered: EMI required in `neoforge.mods.toml`.** Rejected. It turns a JEI-only pack away, and NeoForge
cannot declare "EMI or JEI" as one dependency, so a declared dependency could only ever name one.

**Considered: a warning in the log and chat, with the game still starting.** Rejected. The player then gets a
game with no crafting grid (ADR-0007) and no way to ask for an item, which reads as a bug rather than a
missing dependency.

**Consequence:** EMI and JEI stay compile-only in the build, and Craftworks' viewer code still loads only
when its viewer is present. Only the pair together is required.
