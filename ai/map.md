# Project Map

Live summary and categorisation of every open bead. Updated by the mayor (and any session that changes project state) per CLAUDE.md → Mayor Method → rule 1.

## Snapshot

- **Open beads:** 0
- **In-flight dispatches:** 0
- **Repo:** `coltnz/2048-reframe-2` (GitHub, private) — official name; local dir is `reframe-2048` (intentional mismatch, do not rename).
- **Beads:** `bd` 1.0.4 (latest, via Homebrew core).
- **Last updated:** 2026-05-12 — project initialised, beads installed, mayor method standing rules adopted, git repo created.

## Open beads by category

_No open beads yet. `bd ready` returns no work._

## In-flight dispatches

_None._

## Recent decisions

- 2026-05-12 — Adopted the mayor method. Standing rules live in `CLAUDE.md`. `/ai/map.md` (this file) is the live index of open work.
- 2026-05-12 — Installed `bd` (beads) v1.0.3 via Homebrew. Hooks wired: SessionStart, PreCompact. `git config beads.role maintainer` set for the mayor.
- 2026-05-12 — Initialised git on `main` branch. Created GitHub repo `coltnz/2048-reframe-2` (private). Local dir name `reframe-2048` retained; official name is `2048-reframe-2`.
- 2026-05-12 — Re-ran `bd init` after `git init` because the original bd init (pre-git) registered the workspace under a path-fallback ID that bd could no longer auto-discover once git was initialised. Workspace now registered under git-aware Repository ID `ef9ffe61`. Persisted as memory key `bd-init-order`.
- 2026-05-12 — Upgraded `bd` 1.0.3 → 1.0.4 via `brew upgrade beads`. Patch release: new `bd init-safety` plus `--reinit-local`/`--discard-remote` flags, `--force` deprecated for `--reinit-local`, plus bug fixes (close routing, dolt-in-git hook recursion, packaging). No breaking changes for this project.

## How to update this file

Any session that:
- files a new bead → add it under **Open beads by category**
- dispatches a bead → move it to **In-flight dispatches** with the branch name and the worker's brief
- sees a PR merge → remove the bead, note it under **Recent decisions** if non-trivial
- makes a project-level decision → append it under **Recent decisions** with the date

Keep this file short. Categories are descriptive, not prescriptive — invent new ones as the project grows.
