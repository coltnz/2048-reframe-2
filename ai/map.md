# Project Map

Live summary and categorisation of every open bead. Updated by the mayor (and any session that changes project state) per CLAUDE.md → Mayor Method → rule 1.

## Snapshot

- **Open beads:** 5 (1 in_progress spec; 1 impl-state dispatched; 3 v1+ deferred). Scaffold + mechanics merged (PRs #1, #2).
- **In-flight dispatches:** 1 of 2 (sequential mode for this branched session — worktree isolation unavailable; serial keeps the shared tree sane).
- **Repo:** `coltnz/2048-reframe-2` (GitHub, private) — official name; local dir is `reframe-2048` (intentional mismatch, do not rename).
- **Beads:** `bd` 1.0.4 (latest, via Homebrew core).
- **Spec:** `/ai/specs/2048-reframe-2.md` at **v0.3 — dispatch-ready**. D-01..D-05 folded from v0.2 audit; D-06..D-09 + 10 NITs deferred per operator no-pedantry redirect.
- **Last updated:** 2026-05-12 — spec v0.3 landed; ready to decompose into implementation beads (cap 2 in-flight per CLAUDE.md rule 5).

## Open beads by category

### Spec authoring
- `reframe-2048-kib` (P1, in_progress) — Draft spec for 2048 in re-frame2. At **v0.3 (dispatch-ready)**.

### Implementation
- ~~`reframe-2048-aja`~~ — impl-scaffold **merged** as PR #1 (`9fa56e8`). Worker 81,818 tokens / 8.7 min.
- ~~`reframe-2048-w9e`~~ — impl-mechanics **merged** as PR #2 (`5035026`). Worker 69,682 tokens / 8.25 min. 18 tests / 54 assertions green. Surfaced one spec defect (`slide` needed `next-id` as a 4th arg — worker added it correctly).
- `reframe-2048-z0b` (P1, open) — **impl-state**: app-db, ~30 Malli schemas, all `reg-event-*` / `reg-sub` / `reg-fx`, `reg-machine` FSM, `:input/key-down` adapter. **Dispatched.** Hot-zone (touches many central files).

### v1+ deferred (filed against spec non-goals)
- `reframe-2048-o8f` (P3, open) — Touch / swipe input (NG7). Spec amendment required.
- `reframe-2048-uze` (P3, open) — In-progress game persistence (NG8). Spec amendment required.
- `reframe-2048-cfs` (P4, open) — Vim-key bindings h/j/k/l (NG9). Spec amendment required.

## In-flight dispatches

- `reframe-2048-z0b` — impl-state. Background agent on shared tree. Brief: schemas, db, events, subs, effects, FSM, input adapter + tests. Mayor will not touch `src/reframe_2048_2/{schemas,db,events,subs,effects,fsm,input,core}.cljs`, the new `test/reframe_2048_2/{events,fsm,integration}_test.cljs`, or `shadow-cljs.edn` while this runs.

**Usage data so far (operator baseline):**
- v0.2 audit (read-only): 55,511 tokens / 3.5 min.
- impl-scaffold (write + npm install + 66 tool uses): 81,818 tokens / 8.7 min.
- impl-mechanics (pure CLJS, 44 tool uses): 69,682 tokens / 8.25 min.
- **Cumulative background: ~257k tokens.**
- Estimate for state: ~120–180k tokens (largest impl bead; many files, schemas, integration with mechanics).

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
- 2026-05-12 — **impl-scaffold merged** (PR #1, `9fa56e8`). re-frame2 alpha installed via git dep at SHA `399a586`. Worker hit two minor frictions (shadow-cljs JVM compiler missing from npm package; `:source-paths` ignored when `:deps {}` is set) — both documented inline. CI workflow green. Cost: 81,818 tokens / 8.7 min. Worker did not commit its bd-close export; mayor captured it on the branch before merge (orphaned by branch deletion; bd dolt has it).
- 2026-05-12 — **Dispatch mode: sequential.** Worktree isolation refused in the `/btw`-branched session (harness state recorded `is_git=false` at session start, pre-`git init`). Parallel-on-shared-tree would conflict on branch state. Reverting to one-at-a-time dispatch for the remainder of this session; full parallel-up-to-2 will resume once we're back in a session with worktree support.
- 2026-05-12 — **impl-mechanics merged** (PR #2, `5035026`). Pure CLJS game-rule fns + xorshift32 RNG + 18 tests, all green. Surfaced spec defect: `slide [tiles dir dims]` was missing `next-id` arg — worker fixed inline; mayor should fold into a v0.4 spec amendment when next iterating the spec. Cost: 69,682 tokens / 8.25 min.

## How to update this file

Any session that:
- files a new bead → add it under **Open beads by category**
- dispatches a bead → move it to **In-flight dispatches** with the branch name and the worker's brief
- sees a PR merge → remove the bead, note it under **Recent decisions** if non-trivial
- makes a project-level decision → append it under **Recent decisions** with the date

Keep this file short. Categories are descriptive, not prescriptive — invent new ones as the project grows.
