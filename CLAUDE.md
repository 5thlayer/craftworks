# Personal Assembler

A player-held crafting planner. See `CONTEXT.md` for the domain glossary.

## Workflow

Doc and plumbing changes go straight to `main`: no feature branch, no `/code-review`. That covers
`CLAUDE.md`, `CONTEXT.md`, ADRs, `docs/`, `.claude/` (skills, settings), submodule bumps, and tooling
or CI config. Anything that changes the mod's behaviour still gets a feature branch and a review.

## Agent skills

### Issue tracker

Issues and specs live in GitHub Issues on `5thlayer/personal-assembler`. See `docs/agents/issue-tracker.md`.

### Triage labels

The five default labels: `needs-triage`, `needs-info`, `ready-for-agent`, `ready-for-human`, `wontfix`. See `docs/agents/triage-labels.md`.

### Domain docs

Single-context: `CONTEXT.md` and `docs/adr/` at the repo root. See `docs/agents/domain.md`.
