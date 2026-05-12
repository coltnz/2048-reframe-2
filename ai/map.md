# Project Map

Live summary and categorisation of every open bead. Updated by the mayor (and any session that changes project state) per CLAUDE.md → Mayor Method → rule 1.

## Snapshot

- **Open beads:** 6 (1 in_progress spec; 2 post-demo follow-ups; 3 v1+ deferred). **DEMO PLAYABLE END-TO-END (operator verified 2026-05-12).** 6 PRs merged: scaffold, mechanics, state, views, input-frame fix, animation-finished frame fix.
- **In-flight dispatches:** 0 of 2.
- **Repo:** `coltnz/2048-reframe-2` (GitHub, private) — official name; local dir is `reframe-2048` (intentional mismatch, do not rename).
- **Beads:** `bd` 1.0.4 (latest, via Homebrew core).
- **Spec:** `/ai/specs/2048-reframe-2.md` at **v0.3 — dispatch-ready**. D-01..D-05 folded from v0.2 audit; D-06..D-09 + 10 NITs deferred per operator no-pedantry redirect.
- **Last updated:** 2026-05-12 — re-frame2-story wired up: `src/reframe_2048_2/stories.cljs` registers stories for every view in `views/`; `core.cljs` hash-routes `#/stories` -> `mount-shell!`; production elision via `:closure-defines {re-frame.story.config/enabled? false}` in `shadow-cljs.edn`. bd tracking was intentionally removed earlier this session (commit `271e08d`); the SessionStart hook still echoes the bd workflow but the `.beads` dir was deleted — track open work in this file rather than via `bd create`.

## Open beads by category

### Spec authoring
- `reframe-2048-kib` (P1, in_progress) — Draft spec for 2048 in re-frame2. At **v0.3 (dispatch-ready)**.

### Implementation
- ~~`reframe-2048-aja`~~ — impl-scaffold **merged** as PR #1. 81,818 tokens.
- ~~`reframe-2048-w9e`~~ — impl-mechanics **merged** as PR #2. 69,682 tokens.
- ~~`reframe-2048-z0b`~~ — impl-state **merged** as PR #3. 209,530 tokens. 62 tests / 153 assertions green.
- ~~`reframe-2048-4ix`~~ — impl-views **merged** as PR #4 (`08e0e2a`). 148,128 tokens / 12.5 min. Canonical palette + animations + a11y wiring. **Cumulative impl spend: ~509k tokens (4 dispatches + 2 audits ≈ 615k total background).**

### Post-demo follow-ups
- `reframe-2048-5hs` (P2, open) — impl-trace-define: §10 closure-define + CI grep (one spec MUST that the state worker noted is functionally covered by `goog.DEBUG`; close the formal gap).
- `reframe-2048-3sl` (P2, open) — spec-v0.4: fold the 6 impl-discovered spec defects + the 4 deferred v0.2-audit defects (D-06..D-09).

### v1+ deferred (filed against spec non-goals)
- `reframe-2048-o8f` (P3, open) — Touch / swipe input (NG7). Spec amendment required.
- `reframe-2048-uze` (P3, open) — In-progress game persistence (NG8). Spec amendment required.
- `reframe-2048-cfs` (P4, open) — Vim-key bindings h/j/k/l (NG9). Spec amendment required.

## In-flight dispatches

_None — demo gate reached. Awaiting operator visual review (open `http://localhost:8080` and play)._

**Final usage data per dispatch:**
- v0.2 audit (read-only): 55,511 tokens / 3.5 min.
- impl-scaffold: 81,818 tokens / 8.7 min.
- impl-mechanics: 69,682 tokens / 8.25 min.
- impl-state: 209,530 tokens / 25 min.
- impl-views: 148,128 tokens / 12.5 min.
- **Cumulative background: ~615k tokens** (≈ matches mayor's pre-demo estimate of ~500–550k; came in slightly higher because impl-state hit the upper end of its range).

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
- 2026-05-12 — **impl-state merged** (PR #3, `a57a332`). app-db + 30+ Malli schemas + all reg-event/sub/fx + FSM + input adapter + 62 tests / 153 assertions. Production release compile clean. Surfaced 5 spec defects (filed as `reframe-2048-3sl` for v0.4 amendment): Event-StorageLoaded shape mismatch, reg-machine substitute needs spec amendment, re-frame2 alpha :exclusions workaround, :app/initialise as reset-frame-db! substitute, §10 closure-define functionally subsumed by goog.DEBUG. Cost: 209,530 tokens / 25 min.
- 2026-05-12 — **impl-views merged** (PR #4, `08e0e2a`). **DEMO GATE REACHED.** Canonical 2048 UI: 5 view namespaces + 344-line CSS with verbatim §8.2 palette + §8.3 animation set (spawn scale, merge pulse, slide translate) + `prefers-reduced-motion: reduce` zeroing + ARIA live region wired to `:fx/announce`. Mayor pre-review build green (dev + test + release). One known a11y gap: score-changed ARIA announcement skipped (worker misread the "no-useState in views" rule as forbidding event-side debounced announcements; phase changes ARE announced; trivial follow-up). Cost: 148,128 tokens / 12.5 min.
- 2026-05-12 — **All 4 implementation PRs merged (#1–#4).** Total background spend: ~615k tokens across 6 agent dispatches (2 audits + 4 impl). Project paused at demo gate per operator standing rule.
- 2026-05-12 — **Demo-gate review surfaced two related bugs** that all workers + CI + tests missed. Both fixed by mayor directly (1-line + 3-line edits): PR #5 (`reframe-2048-89u`, input.cljs window listener dispatch routing) and PR #6 (`reframe-2048-82n`, board.cljs animationend / transitionend / setTimeout dispatch routing). Root cause for both: ANY rf/dispatch from outside a React render context MUST pass `{:frame :game}` explicitly — the Reagent adapter's `:adapter/current-frame` hook only resolves to `:game` inside the render cycle. Without it, dispatch defaults to a nil/`:rf/default` frame and is **silently lost** (no error trace surfaced). Folded into bead `reframe-2048-3sl` (spec-v0.4 amendment) as the highest-priority normative clause. Persisted as bd memory keys `non-react-dispatch-frame` and `mayor-demo-gate-worked`.
- 2026-05-12 — **Demo verified PLAYABLE end-to-end.** Operator confirmed keys move tiles, score updates, animations work. Project closes its first major loop: spec-first → 4 dispatched implementation beads → 2 demo-gate fix PRs → working canonical 2048. Total mayor-method first-run footprint: ~615k tokens of background-agent work to reach a playable demo, plus mayor-direct fixes for two issues that operator review caught.
- 2026-05-12 — **Repo visibility changed from private to public** on operator request. coltnz/2048-reframe-2 is now visible at `https://github.com/coltnz/2048-reframe-2` to anyone with the URL.
- 2026-05-12 — **Third demo-gate fix merged** (PR #7, `7926a6f`). Operator diagnosed stuck-input state from screenshots after PRs #5/#6: a tile that slid then was consumed by a merge in the same slide loop left a stale entry in `:ui.animation.slides`, jamming `:sub/animation-busy?` at true and silently dropping all input. Fix: filter slide-events at end of `mechanics/slide` to keep only entries whose tile-id still exists in final by-id. 63 tests / 156 assertions pass, including a new regression test `slide-queue-no-stale-entries`. The third defect of the same animation-queue family is folded into `reframe-2048-3sl` for the v0.4 spec amendment alongside the non-React-context-dispatch rule from PRs #5 + #6.
- 2026-05-12 — **Storybook (`re-frame2-story`) wired up** in this session, mayor-direct (no worker dispatch — see operator no-stop-for-questions directive). Five stories registered (`:story.header` / `:story.footer` / `:story.overlay` / `:story.board` / `:story.app`) covering 17 variants total; two workspaces auto-grid every variant by parent. Hash-routing in `core.cljs`: `#/` -> live app, `#/stories` -> `story/mount-shell!`. Deps added: `io.github.day8/re-frame2-story` and `io.github.day8/re-frame2-epoch` (epoch is required by `re-frame.story.ui.scrubber` but not transitively pulled by re-frame2 core). Production elision verified by grep on `npx shadow-cljs release app` output: no story IDs or `register_all` symbols in `public/js/main.js`. 63 tests / 156 assertions still green. Two `:undeclared-var` warnings on `ns-resolve` in upstream `re-frame.story.loaders` + `fx_stubs.cljc` — wrapped in try/catch upstream so they no-op in CLJS; harmless.

## How to update this file

Any session that:
- files a new bead → add it under **Open beads by category**
- dispatches a bead → move it to **In-flight dispatches** with the branch name and the worker's brief
- sees a PR merge → remove the bead, note it under **Recent decisions** if non-trivial
- makes a project-level decision → append it under **Recent decisions** with the date

Keep this file short. Categories are descriptive, not prescriptive — invent new ones as the project grows.
