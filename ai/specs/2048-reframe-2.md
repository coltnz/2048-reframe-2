# 2048-reframe-2 — Specification

| Field           | Value                                              |
|-----------------|----------------------------------------------------|
| Status          | **v0.1 — operator interview round 1 complete, pending background audit** |
| Spec ID         | `spec-2048-reframe-2`                              |
| Bead            | `reframe-2048-kib`                                 |
| Editor          | Mayor (Claude session)                             |
| Repository      | `coltnz/2048-reframe-2`                            |
| References      | §13                                                |
| Last revised    | 2026-05-12                                         |

> **How to read this spec.** Sections marked **(normative)** are obligations on the implementation; the RFC 2119 keywords (MUST, SHOULD, MAY, MUST NOT, SHOULD NOT) carry their RFC 2119 / RFC 8174 meaning *only when capitalised*. Sections marked **(informative)** are explanation and rationale.
>
> The spec is the artefact; the code is downstream (per re-frame2 [§13.1]). A background-agent implementer should be able to produce working code from this document alone, without consulting external sources.
>
> **Open questions** in §12 are not punted; each MUST resolve to a landed decision or an explicit "host-choice" framing before this spec moves from v0 to v1 (see re-frame2's `SPEC-AUTHORING.md` clause SA-4).

---

## Abstract

This specification defines a single-page web application that implements the canonical 2048 sliding-tile puzzle, built on the **re-frame2** application pattern. The intent is dual: (a) produce a faithful, playable 2048 clone in the browser; (b) exercise re-frame2's spec-first discipline end-to-end on a small but non-trivial domain — game mechanics, persistence, animation, accessibility — so that downstream sessions (background agents, audit agents, the mayor) can rely on this document as the single source of truth.

---

## 1. Introduction

### 1.1 Background

**2048** is a sliding-tile puzzle invented by Gabriele Cirulli in 2014. The player slides numbered tiles on a 4×4 grid; like-valued adjacent tiles merge when slid into each other, doubling. The score equals the sum of all merge results. The titular goal is producing a tile of value 2048; the player MAY continue past that point. Definitive mechanics are recovered from the canonical source [§13.2].

**re-frame2** is the spec-first successor to re-frame v1 — an application pattern for reactive SPAs targeting React-style virtual-DOM substrates. Its central thesis is that the **specification** is the artefact; conforming implementations may be in CLJS, TS, or Python, and may be AI-generated [§13.1]. Status is **alpha** but a reference implementation is shippable today.

### 1.2 Goals

- **G1 — Playable canonical 2048.** Mechanics MUST match the canonical implementation [§13.2] within the tolerance defined in §3.
- **G2 — Spec-conformant.** The implementation MUST observe every MUST clause in this document. Any divergence is a spec defect to be amended, not an implementation defect to be patched (per re-frame2 clause SA-5).
- **G3 — Single-source-of-truth.** A new contributor (human or AI) reading only this spec and the linked re-frame2 docs SHOULD be able to produce a conforming implementation. Where this fails, the spec is incomplete and MUST be revised.
- **G4 — Test-suite-led.** A conformance test corpus MUST exist and MUST pass before any release tag.

### 1.3 Non-goals

- **NG1.** Multiplayer, networked sync, or any server component beyond static hosting.
- **NG2.** Custom board sizes beyond 4×4 in v1 (the *representation* MUST NOT hard-code 4; see §5.1).
- **NG3.** Custom tile palettes or theming beyond the single palette specified in §8.2 in v1.
- **NG4.** Mobile-first or PWA installability in v1 (responsive layout is in scope; offline mode is not).
- **NG5.** Telemetry to any third party.
- **NG6.** Localisation / i18n in v1.
- **NG7.** Touch / swipe input in v1 (operator decision, 2026-05-12). Filed as follow-up bead.
- **NG8.** In-progress game persistence in v1 (operator decision, 2026-05-12). Only best score is persisted (§7). The `reframe-2048-2/game-v1` localStorage key is RESERVED for future use.

---

## 2. Terminology

### 2.1 RFC 2119 keywords

The key words **MUST**, **MUST NOT**, **REQUIRED**, **SHALL**, **SHALL NOT**, **SHOULD**, **SHOULD NOT**, **RECOMMENDED**, **MAY**, and **OPTIONAL** in this document are to be interpreted as described in RFC 2119 / RFC 8174 when, and only when, they appear in all capitals.

### 2.2 Domain terms

- **Board.** The square grid on which play happens. In v1 the board is 4×4 (§3.1).
- **Cell.** A coordinate `[row col]` on the board, 0-indexed, where `[0 0]` is the top-left.
- **Tile.** A numbered object occupying one cell at any instant. A tile has an integer `value` (a power of two ≥ 2), a position, and (for animation purposes only) a stable identity (§5.1).
- **Empty cell.** A cell with no tile.
- **Move.** One of `:up`, `:down`, `:left`, `:right`, triggered by player input.
- **Direction vector.** `:up`→`[-1 0]`, `:down`→`[1 0]`, `:left`→`[0 -1]`, `:right`→`[0 1]`.
- **Slide.** The deterministic transformation of board state induced by one move (§3.5).
- **Merge.** When a moving tile of value `v` enters a cell occupied (during the same slide) by another tile of value `v`, the two tiles combine into a single tile of value `2v` at that cell.
- **Spawn.** Placement of a new tile (§3.4) at an empty cell chosen uniformly at random.
- **Score.** A non-negative integer; the sum of the values of all tiles produced by merge events during the current game.
- **Best score.** The maximum score ever achieved on this device for this game (persisted; §7).
- **Game phases.** `:fresh`, `:playing`, `:won`, `:continuing`, `:over` (§4.8).

---

## 3. Game rules (normative)

### 3.1 Board

The board MUST be a 4-row × 4-column grid in v1. Implementations MUST NOT hard-code `4` outside the single dimension constant; see §5.1.

### 3.2 Tiles

Every tile MUST have:

| Field    | Type             | Notes                                              |
|----------|------------------|----------------------------------------------------|
| `id`     | positive integer | Stable across moves; reused only for new spawns.   |
| `value`  | integer          | A power of two, `≥ 2`.                             |
| `pos`    | `[row col]`      | Current cell.                                      |

Two tiles MUST NOT occupy the same cell at end of any move resolution. During the *internal* steps of one slide a transient merge-collision MAY exist; see §3.5.

### 3.3 Initial state

On `:fresh → :playing` transition, the implementation MUST place exactly **two** tiles. Each is placed by independent invocation of the spawn procedure (§3.4).

### 3.4 Spawn

The spawn procedure MUST:

1. Identify the set `E` of empty cells.
2. If `E` is empty, no spawn occurs and the procedure returns.
3. Otherwise, select a cell `c ∈ E` uniformly at random.
4. Choose a tile value `v` where `P(v = 2) = 0.9` and `P(v = 4) = 0.1`. Implementations MUST use these exact probabilities. The RNG SHOULD be deterministic-seedable for testing (§9.4).
5. Place a new tile at `c` with the chosen `v` and a fresh `id`.

After every **successful move** (§3.5) the implementation MUST invoke the spawn procedure exactly once.

### 3.5 Move resolution

A move is **successful** iff at least one tile changes position OR at least one merge occurs. An unsuccessful move MUST NOT trigger spawn, MUST NOT increment score, and MUST NOT advance any phase.

Given a move direction `d`, the implementation MUST resolve the slide as follows:

1. Define a traversal order over cells such that tiles closest to the destination edge are processed first.
   - `:up`    — rows ascending,   cols any order.
   - `:down`  — rows descending,  cols any order.
   - `:left`  — cols ascending,   rows any order.
   - `:right` — cols descending,  rows any order.
2. For each tile `t` in traversal order, find the **farthest** empty cell reachable in direction `d`, plus the **next** cell beyond that. Call them `farthest` and `next`.
3. **Merge case.** If `next` exists, contains a tile `u` of equal value to `t`, AND `u` has not already been merged-into during this slide, then:
   - Remove `t` and `u`.
   - Place a new tile at `next.pos` with value `2 · t.value`, a fresh `id`, and a `merged-from` flag (used for animation, §8.3) referencing the ids of `t` and `u`.
   - Increment score by `2 · t.value` (i.e., the value of the produced tile).
   - The newly produced tile MUST NOT itself be merged again during the same slide.
4. **Slide case.** Otherwise, move `t` to `farthest` (which MAY equal `t.pos`).

Notes:

- A tile MAY merge AT MOST ONCE per move.
- On a row `[2 2 2 2]` slid left, the result MUST be `[4 4 _ _]` — not `[8 _ _ _]` and not `[4 2 2 _]`. (Two independent merges, not a cascade.)
- On a row `[2 2 4 _]` slid left, the result MUST be `[4 4 _ _]`. (Single merge of the two 2s; the 4 slides but does not merge.)

### 3.6 Scoring

The score MUST be incremented by the **produced tile's value** on each merge event in §3.5 step 3 — that is, by `2 · t.value`. No other event increments score.

### 3.7 Win condition

The first time a merge produces a tile of value **2048**, the phase transitions `:playing → :won`. The win condition MUST be detected within the same move resolution that produced the 2048 tile, and the UI MUST acknowledge the win (§8). Reaching 4096+ MUST NOT itself cause any phase change.

### 3.8 Lose condition

The phase transitions to `:over` iff:

- Every cell is occupied, AND
- No legal move exists in any of `:up`, `:down`, `:left`, `:right`.

A legal move exists when at least one of `:up`, `:down`, `:left`, `:right` would be a successful move (§3.5). This MUST be re-evaluated after every spawn (§3.4).

### 3.9 Continue-after-win

After `:won`, the player MAY choose `:continuing`, in which case play continues under identical rules but the win banner is dismissed. Reaching 2048 a second time MUST NOT re-fire `:won`. The phase remains `:continuing` until `:over`.

---

## 4. Architecture (normative)

### 4.1 Framework

The implementation MUST be built on the **re-frame2** pattern [§13.1]. It MUST honour the nine discipline principles (Regularity, Named Registration, Naming Over Position, Data Before Magic, Public Query Surfaces, Schema Requirement, Deterministic Execution, Machine-Readable Errors, Low Hidden Context) from re-frame2's `Principles.md`.

### 4.2 Reactive substrate

The implementation MUST use **Reagent** as the React substrate (operator decision, 2026-05-12). Views MUST be hiccup-style Reagent forms. Form-2 / Form-3 components with `:component-did-mount`-style closure-captured state MUST NOT carry application state — they MAY exist only for DOM-handle wiring (e.g., focus management) that the re-frame2 `:fx/focus` effect (§4.5) does not cover.

### 4.3 Frame

The application MUST run in a single re-frame2 frame named `:game`. Multi-frame embedding is out of scope (NG1).

### 4.4 Event grammar

All state mutations MUST go through `reg-event-db` or `reg-event-fx` registrations. No mutation MAY occur in views, effects without `reg-event-*` provenance, or browser callbacks bypassing the event pipeline. The following events MUST exist and MUST have stable ids exactly as named:

| Event id                | Payload                       | Effect                                        |
|-------------------------|-------------------------------|-----------------------------------------------|
| `:game/new`             | `{}`                          | Reset db; place two spawn tiles.              |
| `:game/move`            | `{:dir #{:up :down :left :right}}` | Resolve a move per §3.5.                    |
| `:game/continue`        | `{}`                          | `:won → :continuing`.                         |
| `:game/dismiss-over`    | `{}`                          | UI-only: hides the game-over banner without changing phase. |
| `:input/key-down`       | `{:key "ArrowLeft" /* etc. */}` | Translate browser key to `:game/move` or no-op. |
| `:storage/loaded`       | `{:best-score int :game-state map?}` | Populated on app boot from localStorage.    |
| `:storage/save`         | `{}`                          | Effect-fx to write current state. Side-effecting via `:fx/storage-write`. |

Per re-frame2 Principle "Naming Over Position", payloads MUST be maps with explicit keys — never positional vectors beyond the event-id head.

### 4.5 Effect grammar

The implementation MUST use `reg-fx` for any side effect. v1 effects:

| Effect id            | Payload                              | Behaviour                                          |
|----------------------|--------------------------------------|----------------------------------------------------|
| `:fx/storage-write`  | `{:key string :value map}`           | `localStorage.setItem(key, JSON.stringify(value))`. |
| `:fx/storage-read`   | `{:key string :on-success event-id}` | Read; dispatch `:on-success` with the parsed value or `nil`. |
| `:fx/focus`          | `{:selector string}`                 | Move keyboard focus (for accessibility, §8.4).     |
| `:fx/announce`       | `{:message string}`                  | ARIA live-region announcement (§8.4).              |

Direct DOM mutation outside `reg-fx` MUST NOT occur.

### 4.6 Subscription graph

Subscriptions MUST be registered with `reg-sub`. The following subs MUST exist; views below the root MUST consume only these (or sub-subs derived from these), and MUST NOT read `app-db` directly:

| Sub id                  | Returns                                |
|-------------------------|----------------------------------------|
| `:sub/board`            | 4×4 vector-of-vectors of tile-id or `nil`. |
| `:sub/tiles`            | Map of `id → tile`.                    |
| `:sub/score`            | Integer.                               |
| `:sub/best-score`       | Integer.                               |
| `:sub/phase`            | One of `:fresh :playing :won :continuing :over`. |
| `:sub/legal-moves`      | Set of `:up :down :left :right`.       |

### 4.7 View tree

Views MUST be pure functions of subscription values. There MUST be no `useState`/`useEffect` (or equivalent) hooks in views. Top-level view hierarchy:

- `app-view`
  - `header-view` (title, score, best-score, "New Game" button)
  - `board-view` (tiles + cells)
  - `overlay-view` (won / over banners; conditional on phase)
  - `footer-view` (instructions)

### 4.8 Game lifecycle FSM

The game lifecycle MUST be modelled as a re-frame2 state machine (`reg-machine`) with transitions exactly as below. Implementations using a non-FSM substitute MUST justify in a spec amendment.

```
        new
:fresh ─────► :playing ─┬─► :won ──continue──► :continuing
                        │                          │
                        └─────────────────► :over ◄┘
                            (no legal moves)
```

Phase changes MUST be event-driven; views MUST NOT compute phase transitions.

---

## 5. Application state (normative)

### 5.1 `app-db` shape

```clojure
{:game {:board-dims [4 4]                       ;; row-count, col-count
        :phase      :fresh                       ;; §4.8
        :score      0
        :best-score 0
        :tiles      {1 {:id 1 :value 2 :pos [0 0]
                        :spawned? true}
                     2 {:id 2 :value 2 :pos [3 3]
                        :spawned? true}}
        :next-id    3
        :rng-seed   <opaque>}                    ;; testability; §9
 :ui   {:overlay   #{}                           ;; subset of #{:won :over}
        :animation {:moves []  :merges []  :spawns []}}
 :input {:pending-key nil}}
```

`:tiles` is a **map by id**, not a positional 4×4. The board layout is **derived** by `:sub/board` from the tiles' `:pos`. This guarantees stable identity for animations (§8.3).

### 5.2 Schemas

Per re-frame2 SA-3, every shape on the wire MUST have a schema. v1 schemas:

- `Cell`        — `[:tuple :int :int]` with each element in `[0 dim)`.
- `Tile`        — `{:id pos-int :value (s/and int (powerof2 ≥ 2)) :pos Cell :spawned? boolean :merged-from [:maybe [:vector pos-int]]}`.
- `Phase`       — `[:enum :fresh :playing :won :continuing :over]`.
- `AppDb`       — composite of the above.
- `EventVec`    — `[:cat keyword? :map]` — head is event-id, payload is a map.
- `StorageBlob` — `{:version pos-int :best-score :int :game [:maybe AppDb-game]}`.

Implementations MUST validate `AppDb` on every event-handler return in dev builds (Malli `instrument`). Production builds MAY elide.

---

## 6. Inputs (normative)

### 6.1 Keyboard

The implementation MUST handle the following keys with the listed events:

| Key                       | Event                              |
|---------------------------|------------------------------------|
| `ArrowUp` / `w` / `W`     | `:game/move {:dir :up}`            |
| `ArrowDown` / `s` / `S`   | `:game/move {:dir :down}`          |
| `ArrowLeft` / `a` / `A`   | `:game/move {:dir :left}`          |
| `ArrowRight` / `d` / `D`  | `:game/move {:dir :right}`         |
| `n` / `N`                 | `:game/new`                        |
| `Escape`                  | `:game/dismiss-over` (if overlay)  |

Implementations MUST call `event.preventDefault()` on arrow keys to suppress page scroll.

### 6.2 Touch

Touch input is **out of scope for v1** (NG7). The implementation MUST NOT register touch handlers in v1. A follow-up bead tracks adding swipe support post-v1.

### 6.3 Input buffering

Concurrent keypresses MUST NOT cause two moves to resolve simultaneously. Events MUST resolve run-to-completion (per re-frame2 Deterministic Execution).

A keypress arriving while a slide animation is still in flight MUST be **dropped** (operator decision, 2026-05-12). The implementation MUST gate `:input/key-down` on an animation-busy flag in `:ui` (§5.1) such that drops are cheap and observable. Implementations MUST NOT queue moves.

---

## 7. Persistence (normative)

### 7.1 Best score

The best score MUST be persisted across reloads in `localStorage` under key `reframe-2048-2/best-score-v1` as the bare integer string. On boot, the implementation MUST read and dispatch `:storage/loaded`. On every score change such that `score > best-score`, the implementation MUST dispatch `:storage/save` with the new best score.

The implementation MUST NOT provide best-score export, share, or sync features in v1. Best score is strictly per-device.

### 7.2 In-progress game

In-progress games MUST NOT be persisted in v1 (NG8). A page reload MUST start a fresh `:fresh` phase. The localStorage key `reframe-2048-2/game-v1` is RESERVED for a future bead.

---

## 8. Presentation

### 8.1 Layout (informative + normative-where-noted)

- Header MUST display the game title, current score, and best score.
- Board MUST occupy a square area centred horizontally, with cells sized responsively to fit viewport on devices ≥ 320px wide.
- Footer MUST contain a one-paragraph instruction summary including the keyboard map of §6.1.
- The `:won` and `:over` overlays MUST visually block the board area and MUST trap keyboard input such that the only events the overlay accepts are `:game/new`, `:game/continue` (won only), and `:game/dismiss-over` (over only). Arrow keys arriving while an overlay is shown MUST be ignored.

### 8.2 Colour palette (normative for v1)

The implementation MUST use the canonical 2048 palette [§13.2]: background `#faf8ef`, empty-cell `#cdc1b4`, tile colours ramped from `#eee4da` (value 2) through `#edc22e` (value 2048 and above). The exact ramp is specified in `style/tile-colours.edn` (to be created by the implementing agent). Theming alternatives are out of scope for v1 (NG3). Mayor-defaulted decision (2026-05-12) — operator MAY override before v1 via spec amendment.

### 8.3 Animation (normative)

Animation level: **baseline + slide SHOULD** (operator decision, 2026-05-12). The implementation MUST visually distinguish three event classes:

- **Spawn (MUST).** A newly placed tile MUST scale 0 → 1 over a single visible frame (or longer, ≤ 200 ms).
- **Merge (MUST).** A tile produced by merge MUST briefly emphasize (pulse to 1.1 × scale and back) within ≤ 250 ms.
- **Slide (SHOULD).** Moving tiles SHOULD translate from origin to destination over ≤ 200 ms with an ease-out curve. An implementation that omits slide animation but ships spawn and merge animations is still conformant; the spec defect, if any, is in the SHOULD clause.

If the user agent reports `prefers-reduced-motion: reduce`, slide and merge-pulse durations MUST be 0 ms; spawn MAY be instant.

Tile identity (`:id` in §3.2) is the animation handle. Implementations MUST use Reagent's keyed-render contract such that a tile with stable `:id` preserves its DOM node across the slide.

### 8.4 Accessibility (normative)

- The board MUST be reachable by keyboard focus; arrow keys MUST work whenever the document has focus, not only when a particular element does.
- An ARIA live region MUST announce: phase changes (`:won`, `:over`), and `score now N` (debounced ≤ 1 / sec).
- Colour MUST NOT be the sole channel for tile value; the numeric value MUST be visible.
- Contrast ratio between tile text and tile background MUST meet WCAG 2.2 AA (≥ 4.5:1 for body text).

---

## 9. Testing (normative)

### 9.1 Pure-mechanics tests

Pure functions implementing §3 (slide, merge, spawn-given-rng, lose-detection) MUST have ≥ 95 % branch coverage. The following canonical cases MUST be tested:

- `[2 2 2 2]` slid left → `[4 4 _ _]` (two independent merges, score +8).
- `[2 2 4 _]` slid left → `[4 4 _ _]`.
- `[4 4 2 2]` slid left → `[8 4 _ _]`.
- `[2 _ 2 4]` slid left → `[4 4 _ _]`.
- Full immobile board → `:over`.

### 9.2 Event-handler tests

Each `reg-event-*` MUST have ≥ 1 test that dispatches the event into a synthetic db and asserts on the returned db (or fx map). RNG MUST be injected via a fixture.

### 9.3 Conformance fixtures

The implementation MUST ship `test/fixtures/*.edn` whose shape conforms to re-frame2's conformance harness conventions [§13.1]. At minimum: replay traces of one win-game, one lose-game, and three property-based shrunk traces.

### 9.4 Property-based tests

The following invariants MUST hold under randomly generated valid input sequences:

- Score is monotone non-decreasing.
- Total of all tile values increases by exactly the value of any spawned tile (2 or 4) per move.
- A tile's `id` is never reused while that tile still exists.
- Phase transitions form a DAG over §4.8 (no `:over → :playing` transitions, etc.).

---

## 10. Tooling and build

The implementation:

- MUST use **shadow-cljs** as the build tool [§13.1].
- MUST target both `:browser` (release) and `:browser-test` (Karma or shadow-cljs node tests).
- MUST commit a `package.json` and `deps.edn` (re-frame2 reference uses both).
- MUST ship a CI workflow that runs unit tests and a release build on every push to `main`.
- SHOULD ship a release that deploys to GitHub Pages from the `main` branch.
- MUST enable re-frame2's trace / instrumentation bus in dev builds. Production builds MUST strip it (verifiable by `goog-define` or `:closure-defines` substitution at build time).

---

## 11. Out-of-scope (informative)

Items the implementation MUST NOT include in v1 — call out as "future work" if surfaced in code:

- Multiplayer, leaderboards, networking.
- Custom themes, dark mode beyond a CSS-variable swap toggle (§12.6).
- Mobile-first PWA installability.
- Localisation.
- Telemetry to any third party.

---

## 12. Decisions log

> Per re-frame2 SA-4, every open question in this spec is closed — either by operator decision or by mayor-defaulted decision flagged for operator override. The table below is the authoritative record.

| #  | Topic                              | Decision                                                                 | How resolved               | Where landed       |
|----|------------------------------------|---------------------------------------------------------------------------|----------------------------|--------------------|
| 1  | Reactive substrate                 | Reagent                                                                  | Operator, 2026-05-12       | §4.2               |
| 2  | Animation budget                   | Baseline (spawn + merge MUST) + slide SHOULD                             | Operator, 2026-05-12       | §8.3               |
| 3  | Input pacing during animation      | Drop (no queue, no cancel)                                               | Mayor default, 2026-05-12  | §6.3               |
| 4  | Touch input in v1                  | Out of scope (NG7)                                                       | Operator, 2026-05-12       | §1.3 / §6.2        |
| 5  | In-progress game persistence       | Not persisted in v1 (NG8); key reserved                                  | Operator, 2026-05-12       | §1.3 / §7.2        |
| 6  | Visual palette                     | Canonical 2048 palette                                                   | Mayor default, 2026-05-12  | §8.2               |
| 7  | Best-score export / share          | None in v1                                                               | Mayor default, 2026-05-12  | §7.1               |
| 8  | Trace / dev tooling                | Enabled in dev builds; stripped from production                          | Mayor default, 2026-05-12  | §10                |
| 9  | Win-banner UX                      | Overlay blocks input; only `:game/new` and `:game/continue` accepted     | Mayor default, 2026-05-12  | §8.1               |
| 10 | Stored-game versioning             | Moot — no in-progress persistence in v1                                  | Resolved by decision 5     | —                  |

Mayor-defaulted decisions (3, 6, 7, 8, 9) are spec-amendable by operator decree at any time before v1; background agents MUST NOT re-open them without operator direction.

---

## 13. References

### 13.1 Normative — re-frame2

- `day8/re-frame2` repository: https://github.com/day8/re-frame2
- Key spec docs consulted for v0:
  - `spec/SPEC-AUTHORING.md` — meta-spec obligations (clauses SA-1 … SA-7).
  - `spec/Principles.md` — the nine discipline principles.
  - `spec/000-Vision.md` — pattern goals and non-goals.
  - `spec/001-Registration.md` — the `reg-*` grammar.
  - `spec/004-Views.md` — view-as-pure-function discipline.
  - `spec/005-StateMachines.md` — FSM substrate.
  - `spec/006-ReactiveSubstrate.md` — subs and substrate-independence.
  - `spec/008-Testing.md` — fixture corpus expectations.
  - `spec/010-Schemas.md` — Malli boundary checks.

### 13.2 Normative — 2048 canonical

- `gabrielecirulli/2048` repository: https://github.com/gabrielecirulli/2048
- `js/game_manager.js` — definitive mechanics: `startTiles = 2`; `Math.random() < 0.9 ? 2 : 4`; merge guarded by `!next.mergedFrom`; `score += merged.value`; `if (merged.value === 2048) self.won = true`; `keepPlaying` flag; lose at `!movesAvailable()`.

### 13.3 Informative

- "2048 (video game)" — https://en.wikipedia.org/wiki/2048_(video_game)
- 2048.org — game site, https://www.2048.org/
- RFC 2119, RFC 8174 — keyword interpretation.
- WCAG 2.2 — Web Content Accessibility Guidelines.
