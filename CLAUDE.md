# Craftworks

A player-held crafting planner, the Personal Assembler. See `CONTEXT.md` for the domain glossary.

## Workflow

Doc and plumbing changes go straight to `main`: no feature branch, no `/code-review`. That covers
`CLAUDE.md`, `CONTEXT.md`, ADRs, `docs/`, `.claude/` (skills, settings), submodule bumps, and tooling
or CI config. Anything that changes the mod's behaviour still gets a feature branch and a review.

## Releases

A change a player or pack author notices adds its line under `## Unreleased` in `CHANGELOG.md` as it lands. Before bumping `mod_version`, publishing to `~/.m2` or tagging a release, read `docs/agents/releases.md`: releases go through `scripts/release.sh`, and a published version never changes.

A release that must reach the Pack follows the `release-train` skill: one owning session per checkout, and pushes only on the user's word.

## Testing

`sh ./gradlew build` runs the JUnit tests (the Assembler's planning, with no Minecraft); `sh ./gradlew runGameTestServer` runs the game tests headless (a real player on a server) and names each one it ran. A new game test class is registered by a line in `CraftworksGameTests.registerTests`.

## Agent skills

### Issue tracker

Issues and specs live in GitHub Issues on `5thlayer/craftworks`. See `docs/agents/issue-tracker.md`.

### Triage labels

The five default labels: `needs-triage`, `needs-info`, `ready-for-agent`, `ready-for-human`, `wontfix`. See `docs/agents/triage-labels.md`.

### Domain docs

Single-context: `CONTEXT.md` and `docs/adr/` at the repo root. See `docs/agents/domain.md`.
