# Project Map

Live summary and categorisation of every open bead. Updated by the mayor (and any session that changes project state) per CLAUDE.md → Mayor Method → rule 1.

## Snapshot

- **Open beads:** 4 (1 in_progress spec; 3 v1+ deferred). Audit bead `reframe-2048-76o` closed.
- **In-flight dispatches:** 0 of 2 (concurrency cap: 2; see CLAUDE.md rule 5).
- **Repo:** `coltnz/2048-reframe-2` (GitHub, private) — official name; local dir is `reframe-2048` (intentional mismatch, do not rename).
- **Beads:** `bd` 1.0.4 (latest, via Homebrew core).
- **Spec:** `/ai/specs/2048-reframe-2.md` at **v0.3 — dispatch-ready**. D-01..D-05 folded from v0.2 audit; D-06..D-09 + 10 NITs deferred per operator no-pedantry redirect.
- **Last updated:** 2026-05-12 — spec v0.3 landed; ready to decompose into implementation beads (cap 2 in-flight per CLAUDE.md rule 5).

## Open beads by category

### Spec authoring
- `reframe-2048-kib` (P1, in_progress) — Draft spec for 2048 in re-frame2. At v0.2; v0.1 audit folded in; v0.2 audit returned 1 blocker + 4 cheap fixes for v0.3.

### v1+ deferred (filed against spec non-goals)
- `reframe-2048-o8f` (P3, open) — Touch / swipe input (NG7). Spec amendment required.
- `reframe-2048-uze` (P3, open) — In-progress game persistence (NG8). Spec amendment required.
- `reframe-2048-cfs` (P4, open) — Vim-key bindings h/j/k/l (NG9). Spec amendment required.

## In-flight dispatches

_None — v0.2 audit returned. Awaiting operator nod before v0.3 pass._

## Recent decisions

- 2026-05-12 — Adopted the mayor method. Standing rules live in `CLAUDE.md`. `/ai/map.md` (this file) is the live index of open work.
- 2026-05-12 — Installed `bd` (beads) v1.0.3 via Homebrew. Hooks wired: SessionStart, PreCompact. `git config beads.role maintainer` set for the mayor.
- 2026-05-12 — Initialised git on `main` branch. Created GitHub repo `coltnz/2048-reframe-2` (private). Local dir name `reframe-2048` retained; official name is `2048-reframe-2`.
- 2026-05-12 — Re-ran `bd init` after `git init` because the original bd init (pre-git) registered the workspace under a path-fallback ID that bd could no longer auto-discover once git was initialised. Workspace now registered under git-aware Repository ID `ef9ffe61`. Persisted as memory key `bd-init-order`.
- 2026-05-12 — Upgraded `bd` 1.0.3 → 1.0.4 via `brew upgrade beads`. Patch release: new `bd init-safety` plus `--reinit-local`/`--discard-remote` flags, `--force` deprecated for `--reinit-local`, plus bug fixes (close routing, dolt-in-git hook recursion, packaging). No breaking changes for this project.
- 2026-05-12 — Spec v0 drafted under bead `reframe-2048-kib`; operator interview round 1 closed 4 high-leverage open questions (Reagent, best-score-only, no touch, baseline+slide animation); 5 mayor-defaulted; v0.1 committed.
- 2026-05-12 — Background audit dispatched under bead `reframe-2048-4el`. Returned 3 BLOCKERS + 14 DEFECTS + 11 NITS + 9 CONFIRMED. Findings folded into spec v0.2. Audit bead closed; full report at `/ai/audits/2026-05-12-spec-2048-reframe-2-v0.1.md`.
- 2026-05-12 — **Operator standing rule added: concurrency cap of 2 background-agent sessions** (CLAUDE.md rule 5). Composes with rule 4: hot-zone beads stay serial; isolated-surface beads parallel up to the ceiling of 2.
- 2026-05-12 — **Operator redirect: pedagogy + playable canonical fidelity > pedantic schema completeness. Stop for review at the "good demo" gate.** Spec stops iterating after D-01..D-05 (v0.3); implementation aims at a playable canonical-looking 2048 and then pauses for operator review. Persisted as memory key `xorshift32-over-splitmix64` for the RNG-specific lesson.
- 2026-05-12 — v0.2 audit returned (bead `reframe-2048-76o`, now closed). 1 BLOCKER (RNG host-precision) + 4 cheap defects folded into v0.3; deferred items left for impl-time follow-up beads per the operator redirect.

## How to update this file

Any session that:
- files a new bead → add it under **Open beads by category**
- dispatches a bead → move it to **In-flight dispatches** with the branch name and the worker's brief
- sees a PR merge → remove the bead, note it under **Recent decisions** if non-trivial
- makes a project-level decision → append it under **Recent decisions** with the date

Keep this file short. Categories are descriptive, not prescriptive — invent new ones as the project grows.
