# 2048-reframe-2 — Specification

| Field           | Value                                              |
|-----------------|----------------------------------------------------|
| Status          | **v0.2 — background audit folded in (3 BLOCKERS + 14 DEFECTS + selected NITS resolved)** |
| Spec ID         | `spec-2048-reframe-2`                              |
| Bead history    | Drafted under `reframe-2048-kib`; audited under `reframe-2048-4el` |
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
- **NG9.** Vim-key bindings (`h`/`j`/`k`/`l`) for movement in v1. The canonical 2048 supports them; this spec omits them for v1 keyboard surface clarity. Filed as follow-up bead.

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
- **Score.** A non-negative integer; the sum of the values of all tiles produced by merge events during the current game. Score resets to 0 on `:game/new`.
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

1. Define traversal vectors `rows` and `cols` such that tiles closest to the destination edge are processed first. The exact vectors per direction MUST be:

   | `:dir`    | `rows`         | `cols`         |
   |-----------|----------------|----------------|
   | `:up`     | `[0 1 2 3]`    | `[0 1 2 3]`    |
   | `:down`   | `[3 2 1 0]`    | `[0 1 2 3]`    |
   | `:left`   | `[0 1 2 3]`    | `[0 1 2 3]`    |
   | `:right`  | `[0 1 2 3]`    | `[3 2 1 0]`    |

   Iterate `(for r in rows, for c in cols ...)`. Per the canonical `buildTraversals` rule: when the direction vector's component is `+1`, reverse the corresponding traversal axis.
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

The phase transitions to `:over` iff **no legal move exists in any of `:up`, `:down`, `:left`, `:right`** (equivalently: every cell is occupied AND no two adjacent cells have equal values). A legal move in direction `d` exists when resolving §3.5 against the current board would be a **successful move**. This MUST be re-evaluated after every spawn (§3.4).

*Informative.* The "every cell occupied" conjunct is implied by "no legal move" — if any cell is empty, then at least one of the four directions would slide a tile into it and be successful. We surface it for reader intuition only.

### 3.9 Continue-after-win

After `:won`, the player MAY choose `:continuing`, in which case play continues under identical rules but the win banner is dismissed. Reaching 2048 a second time MUST NOT re-fire `:won`. The phase remains `:continuing` until `:over`.

*Implementation note.* Detect win by current phase, not by a separate "won-already" flag: `(when (= phase :playing) (transition-to :won))`. After `:won → :continuing`, subsequent merges that produce a 2048 tile are guarded by the phase predicate and silently ignored at the FSM layer.

---

## 4. Architecture (normative)

### 4.1 Framework

The implementation MUST be built on the **re-frame2** pattern [§13.1]. It MUST honour the nine discipline principles (Regularity, Named Registration, Naming Over Position, Data Before Magic, Public Query Surfaces, Schema Requirement, Deterministic Execution, Machine-Readable Errors, Low Hidden Context) from re-frame2's `Principles.md`.

### 4.2 Reactive substrate

The implementation MUST use **Reagent** as the React substrate (operator decision, 2026-05-12). Views MUST be hiccup-style Reagent forms. Form-2 / Form-3 components with `:component-did-mount`-style closure-captured state MUST NOT carry application state — they MAY exist only for DOM-handle wiring (e.g., focus management) that the re-frame2 `:fx/focus` effect (§4.5) does not cover.

### 4.3 Frame

The application MUST run in a single re-frame2 frame named `:game`. Multi-frame embedding is out of scope (NG1).

### 4.4 Event grammar

All state mutations MUST go through `reg-event-db` or `reg-event-fx` registrations. No mutation MAY occur in views, effects without `reg-event-*` provenance, or browser callbacks bypassing the event pipeline. The following events MUST exist and MUST have stable ids exactly as named. Payload schemas are defined in §5.2 under matching `Event-*` names.

| Event id                  | Payload schema (§5.2)        | Effect                                                                              |
|---------------------------|------------------------------|-------------------------------------------------------------------------------------|
| `:game/new`               | `Event-GameNew`              | Reset `:game` to initial state; place two spawn tiles per §3.4; clear `:ui.overlay`. |
| `:game/move`              | `Event-GameMove`             | Resolve a move per §3.5; if successful, dispatch spawn (§3.4) and lose-detection (§3.8). |
| `:game/continue`          | `Event-GameContinue`         | Transition `:won → :continuing`; clear `:won` from `:ui.overlay`.                   |
| `:game/dismiss-over`      | `Event-GameDismissOver`      | UI-only: clears `:over` from `:ui.overlay` (phase unchanged).                       |
| `:input/key-down`         | `Event-InputKeyDown`         | Translate browser key to `:game/move`, `:game/new`, `:game/continue`, or `:game/dismiss-over` per §6.1; no-op for unrecognised keys. |
| `:storage/loaded`         | `Event-StorageLoaded`        | Set `:best-score` from the persisted value (or 0 if nil/parse-failure).             |
| `:storage/save`           | `Event-StorageSave`          | Trigger `:fx/storage-write` for the current best score.                             |
| `:ui/animation-finished`  | `Event-UIAnimationFinished`  | Remove the corresponding entry from `:ui.animation.{slides,merges,spawns}`.         |

Per re-frame2 Principle "Naming Over Position", payloads MUST be maps with explicit keys — never positional vectors beyond the event-id head. `Event-InputKeyDown` carries `{:key <string>}` whose value-set is exactly the keys named in §6.1; any other value MUST be a silent no-op.

### 4.5 Effect grammar

The implementation MUST use `reg-fx` for any side effect. v1 effects:

| Effect id            | Payload schema (§5.2) | Behaviour                                                                                                            |
|----------------------|-----------------------|----------------------------------------------------------------------------------------------------------------------|
| `:fx/storage-write`  | `Fx-StorageWrite`     | `localStorage.setItem(key, JSON.stringify(value))`. On any browser exception (quota, disabled, SecurityError) MUST NOT throw; SHOULD log via `:fx/announce` (info level). |
| `:fx/storage-read`   | `Fx-StorageRead`      | `JSON.parse(localStorage.getItem(key))`; dispatch `:on-success` with the parsed value. On missing key, browser exception, or JSON parse failure: dispatch `:on-success` with `nil`. |
| `:fx/focus`          | `Fx-Focus`            | Move keyboard focus (for accessibility, §8.4).                                                                       |
| `:fx/announce`       | `Fx-Announce`         | Push the message into the ARIA live region (§8.4).                                                                   |

Direct DOM mutation outside `reg-fx` MUST NOT occur.

### 4.6 Subscription graph

Subscriptions MUST be registered with `reg-sub`. The following subs MUST exist; views below the root MUST consume only these (or sub-subs derived from these), and MUST NOT read `app-db` directly. Return-value schemas are in §5.2 under matching `Sub-*` names.

| Sub id                  | Returns (§5.2)     | Derivation                                                                                       |
|-------------------------|--------------------|--------------------------------------------------------------------------------------------------|
| `:sub/board`            | `Sub-Board`        | Build a `dims` × `dims` vector-of-vectors from `:game.tiles[*].pos`; cells with no tile are `nil`. |
| `:sub/tiles`            | `Sub-Tiles`        | `(:tiles (:game db))`.                                                                           |
| `:sub/score`            | `Sub-Score`        | `(:score (:game db))`.                                                                           |
| `:sub/best-score`       | `Sub-BestScore`    | `(:best-score (:game db))`.                                                                      |
| `:sub/phase`            | `Sub-Phase`        | `(:phase (:game db))`.                                                                           |
| `:sub/legal-moves`      | `Sub-LegalMoves`   | For each `d ∈ {:up :down :left :right}`, trial-resolve §3.5 against the current `:tiles`; include `d` iff the trial would be a successful move. Caching by `:tiles` identity is RECOMMENDED. |
| `:sub/animation-busy?`  | `:boolean`         | `(boolean (or (seq slides) (seq merges) (seq spawns)))` against `:ui.animation`. Used by §6.3.   |

### 4.7 View tree

Views MUST be pure functions of subscription values. There MUST be no `useState`/`useEffect` (or equivalent) hooks in views. Top-level view hierarchy:

- `app-view`
  - `header-view` (title, score, best-score, "New Game" button)
  - `board-view` (tiles + cells)
  - `overlay-view` (won / over banners; conditional on phase)
  - `footer-view` (instructions)

### 4.8 Game lifecycle FSM

The game lifecycle MUST be modelled as a re-frame2 state machine (`reg-machine`) with the transitions exactly as enumerated below. Implementations using a non-FSM substitute MUST justify in a spec amendment.

**Transitions (normative, exhaustive):**

| From          | Event              | Guard                                  | To            |
|---------------|--------------------|----------------------------------------|---------------|
| `:fresh`      | `:game/new`        | —                                      | `:playing`    |
| `:playing`    | `:game/move`       | move successful AND merge produces 2048 | `:won`        |
| `:playing`    | `:game/move`       | move successful AND no 2048-producing merge AND no legal move post-spawn | `:over` |
| `:playing`    | `:game/move`       | move successful AND game continues     | `:playing`    |
| `:playing`    | `:game/new`        | —                                      | `:fresh` (then immediate `:fresh → :playing`) |
| `:won`        | `:game/continue`   | —                                      | `:continuing` |
| `:won`        | `:game/new`        | —                                      | `:fresh`      |
| `:continuing` | `:game/move`       | move successful AND no legal move post-spawn | `:over` |
| `:continuing` | `:game/move`       | move successful (any other case)       | `:continuing` |
| `:continuing` | `:game/new`        | —                                      | `:fresh`      |
| `:over`       | `:game/new`        | —                                      | `:fresh`      |
| `:over`       | `:game/dismiss-over` | —                                    | `:over` (UI overlay clears; phase unchanged) |

ASCII summary (informative):

```
                  new                  successful-move
       ┌──────►:fresh ──────► :playing ┬──────────────► :playing
       │                       │ │ │   │                   │
       │ new                   │ │ │   │ produces-2048     │ new
       │                       │ │ │   ▼                   ▼
       │                       │ │ │  :won ─continue─► :continuing
       │                       │ │ │   │                   │
       │ new (from any)        │ │ │   │ new               │ new
       │◄──────────────────────┘ │ │   │                   │
       │                         │ │   ▼                   │
       │                         │ └─►:over◄───────────────┘
       │                         │     │
       │                         │     │ dismiss-over (overlay only)
       │ new                     │     │
       │◄────────────────────────┘     │
       │                               │
       └◄──────────────────────────────┘  new
```

Phase changes MUST be event-driven; views MUST NOT compute phase transitions.

---

## 5. Application state (normative)

### 5.1 `app-db` shape

```clojure
{:game  {:board-dims [4 4]              ; row-count, col-count
         :phase      :fresh             ; §4.8
         :score      0
         :best-score 0
         :tiles      {1 {:id 1 :value 2 :pos [0 0]}
                      2 {:id 2 :value 4 :pos [3 3]}}
         :next-id    3
         :rng-seed   42}                ; §5.3
 :ui    {:overlay   #{}                 ; subset of #{:won :over}
         :animation {:slides  []        ; each: {:tile-id, :from Cell, :to Cell}
                     :merges  []        ; each: {:tile-id, :from-ids [TileId TileId]}
                     :spawns  []}}      ; each: {:tile-id}
 :input {}}                             ; reserved
```

Invariants:

- `:tiles` is a **map by id**, not a positional grid. The board layout is **derived** by `:sub/board` from the tiles' `:pos`. This guarantees stable identity for animations (§8.3).
- A tile is "freshly spawned" iff its `:id` appears in `:ui.animation.spawns`. There is no `:spawned?` field on tiles; the animation list is the single source of truth (resolves audit defect B-03).
- Animation lists are added to by the event that produces them (`:game/move`, `:game/new`), and removed from by `:ui/animation-finished`. The implementation MUST dispatch `:ui/animation-finished` from the CSS `transitionend` event for slides and merges, and from a `setTimeout` matching the spawn duration (§8.3) for spawns.
- `:sub/animation-busy?` (§4.6) reads these lists; an empty triple means idle and input MAY proceed.

### 5.2 Schemas

Per re-frame2 SA-3, **every** shape on the wire MUST have a Malli schema. The CLJS implementation MUST use Malli (`metosin/malli`); a non-CLJS port MUST use the equivalent in its host (TypeScript: Zod; Python: pydantic). All schemas below are expressed in Malli vector syntax.

**Core types**

```clojure
(def TileId            [:and :int [:fn pos?]])
(def PowerOfTwoGeq2    [:and :int [:fn (fn [n] (and (>= n 2) (zero? (bit-and n (dec n)))))]])
(def Cell              [:tuple [:int {:min 0 :max 3}] [:int {:min 0 :max 3}]])  ; v1: bounds match :board-dims [4 4]
(def Direction         [:enum :up :down :left :right])
(def Phase             [:enum :fresh :playing :won :continuing :over])
(def AnimationPhase    [:enum :slide :merge :spawn])
(def Tile
  [:map {:closed true}
   [:id          TileId]
   [:value       PowerOfTwoGeq2]
   [:pos         Cell]
   [:merged-from {:optional true} [:maybe [:tuple TileId TileId]]]])
```

**Event payloads (§4.4 — schemas referenced by name)**

```clojure
(def Event-GameNew              [:map {:closed true}])
(def Event-GameMove             [:map {:closed true} [:dir Direction]])
(def Event-GameContinue         [:map {:closed true}])
(def Event-GameDismissOver      [:map {:closed true}])
(def Event-InputKeyDown         [:map {:closed true} [:key :string]])
(def Event-StorageLoaded        [:map {:closed true} [:best-score [:maybe :int]]])  ; nil ⇒ no prior value
(def Event-StorageSave          [:map {:closed true}])
(def Event-UIAnimationFinished  [:map {:closed true}
                                  [:phase AnimationPhase]
                                  [:tile-id TileId]])
```

**Effect payloads (§4.5)**

```clojure
(def Fx-StorageWrite  [:map {:closed true} [:key :string] [:value :any]])
(def Fx-StorageRead   [:map {:closed true} [:key :string] [:on-success :keyword]])
(def Fx-Focus         [:map {:closed true} [:selector :string]])
(def Fx-Announce      [:map {:closed true} [:message :string]])
```

**Subscription return values (§4.6)**

```clojure
(def Sub-Board         [:vector {:min 4 :max 4} [:vector {:min 4 :max 4} [:maybe TileId]]])
(def Sub-Tiles         [:map-of TileId Tile])
(def Sub-Score         :int)
(def Sub-BestScore     :int)
(def Sub-Phase         Phase)
(def Sub-LegalMoves    [:set Direction])
(def Sub-AnimationBusy :boolean)
```

**Animation queue entries (§5.1)**

```clojure
(def Anim-Slide  [:map {:closed true} [:tile-id TileId] [:from Cell] [:to Cell]])
(def Anim-Merge  [:map {:closed true} [:tile-id TileId] [:from-ids [:tuple TileId TileId]]])
(def Anim-Spawn  [:map {:closed true} [:tile-id TileId]])
```

**`app-db`**

```clojure
(def AppDb-Game
  [:map {:closed true}
   [:board-dims  [:tuple [:int {:min 1}] [:int {:min 1}]]]
   [:phase       Phase]
   [:score       [:int {:min 0}]]
   [:best-score  [:int {:min 0}]]
   [:tiles       [:map-of TileId Tile]]
   [:next-id     [:int {:min 1}]]
   [:rng-seed    :int]])                ; see §5.3

(def AppDb-UI
  [:map {:closed true}
   [:overlay   [:set [:enum :won :over]]]
   [:animation [:map {:closed true}
                [:slides  [:vector Anim-Slide]]
                [:merges  [:vector Anim-Merge]]
                [:spawns  [:vector Anim-Spawn]]]]])

(def AppDb-Input [:map {:closed true}])  ; reserved

(def AppDb
  [:map {:closed true}
   [:game  AppDb-Game]
   [:ui    AppDb-UI]
   [:input AppDb-Input]])
```

**localStorage**

```clojure
(def StorageBlob-BestScore :int)         ; key reframe-2048-2/best-score-v1
;; reframe-2048-2/game-v1 is RESERVED per §7.2; no schema in v1.
```

**Instrumentation**

Implementations MUST validate `AppDb` on every event-handler return in dev builds (Malli `instrument` or equivalent). Event payloads MUST be validated against their named schema on entry to each handler in dev. Production builds MAY elide both checks (see §10 closure-define).

### 5.3 RNG

All non-determinism in the game (spawn cell selection, spawn value choice) MUST flow through a seedable pseudo-random generator threaded in `app-db` under `:game.rng-seed`. The PRNG MUST be **splitmix64** (constants from Vigna's reference implementation):

```clojure
(defn next-seed [^long s]
  (let [s (unchecked-add s 0x9E3779B97F4A7C15)
        z (bit-xor s (unsigned-bit-shift-right s 30))
        z (unchecked-multiply z 0xBF58476D1CE4E5B9)
        z (bit-xor z (unsigned-bit-shift-right z 27))
        z (unchecked-multiply z 0x94D049BB133111EB)]
    [s (bit-xor z (unsigned-bit-shift-right z 31))]))
;; ⇒ [next-seed-value random-u64]
```

The spawn procedure (§3.4) MUST consume the RNG exactly twice per call, in this order:

1. **Cell choice.** Compute `random-u64`; choose the empty cell at index `(mod random-u64 (count E))`.
2. **Value choice.** Compute another `random-u64`; tile value is `2` iff the high bit of the result encodes a fraction `< 0.9` (concretely: `< 0.9 × 2^64`), else `4`.

The handler that invokes spawn MUST thread the updated `:rng-seed` into the returned db. Property tests (§9.4) MUST be able to drive the game to determinism by setting an initial seed.

The initial seed at `:game/new` is implementation-defined but MUST default to the current wall-clock millis (production) or a fixture-injected value (tests).

---

## 6. Inputs (normative)

### 6.1 Keyboard

The implementation MUST handle the following keys with the listed events. The `:dir` payload uses the `Direction` schema from §5.2.

| Key                       | Event                                                          | Active in phase                 |
|---------------------------|----------------------------------------------------------------|---------------------------------|
| `ArrowUp` / `w` / `W`     | `:game/move {:dir :up}`                                        | `:playing`, `:continuing`       |
| `ArrowDown` / `s` / `S`   | `:game/move {:dir :down}`                                      | `:playing`, `:continuing`       |
| `ArrowLeft` / `a` / `A`   | `:game/move {:dir :left}`                                      | `:playing`, `:continuing`       |
| `ArrowRight` / `d` / `D`  | `:game/move {:dir :right}`                                     | `:playing`, `:continuing`       |
| `n` / `N`                 | `:game/new`                                                    | any                             |
| `Enter` / `c` / `C`       | `:game/continue` (only if `:won` is in `:ui.overlay`)          | `:won`                          |
| `Escape`                  | `:game/dismiss-over` (only if `:over` is in `:ui.overlay`)     | `:over`                         |

Implementations MUST call `event.preventDefault()` on arrow keys to suppress page scroll. Keys not in this table MUST be silent no-ops at the `:input/key-down` handler. Vim keys (`h`/`j`/`k`/`l`) are out of scope in v1 (NG9).

### 6.2 Touch

Touch input is **out of scope for v1** (NG7). The implementation MUST NOT register touch handlers in v1. A follow-up bead tracks adding swipe support post-v1.

### 6.3 Input buffering

Concurrent keypresses MUST NOT cause two moves to resolve simultaneously. Events MUST resolve run-to-completion (per re-frame2 Deterministic Execution).

A keypress arriving while a slide, merge, or spawn animation is still in flight MUST be **dropped** (operator decision, 2026-05-12). The `:input/key-down` handler MUST first check `:sub/animation-busy?` (§4.6, derived from `:ui.animation` per §5.1) and return without dispatching if true. Implementations MUST NOT queue moves. Drops MUST be observable in the trace bus (§10) so test fixtures can assert on them.

When `prefers-reduced-motion: reduce` is active and animation durations are 0 ms (§8.3), `:ui.animation` is cleared synchronously at the end of the event handler and `:sub/animation-busy?` returns `false` to the next key event. Implementations MUST still dispatch `:ui/animation-finished` events for trace and test fidelity.

---

## 7. Persistence (normative)

### 7.1 Best score

All v1+ localStorage keys for this project MUST be namespaced under `reframe-2048-2/` (matching the GitHub repository name).

The best score MUST be persisted across reloads in `localStorage` under key `reframe-2048-2/best-score-v1` as the bare integer string. On boot, the implementation MUST read and dispatch `:storage/loaded`. On every score change such that `score > best-score`, the implementation MUST dispatch `:storage/save` with the new best score.

**localStorage unavailable.** If `localStorage` is unavailable (Safari Private mode quota, disabled cookies, SecurityError) or any read/write call throws, the implementation MUST:

- Treat best score as session-only (initialise to 0; do not error).
- MUST NOT raise or surface the failure to the user as an error overlay.
- SHOULD log the failure once at info level via `:fx/announce` (§4.5) or `js/console.info`, with the key and the exception class.

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

The implementation MUST use the canonical 2048 palette [§13.2], verbatim hex codes below. Theming alternatives are out of scope for v1 (NG3). Mayor-defaulted decision (2026-05-12) — operator MAY override before v1 via spec amendment.

**Chrome**

| Role             | Colour                  |
|------------------|-------------------------|
| Page background  | `#faf8ef`               |
| Game container   | `#bbada0`               |
| Empty cell       | `rgba(238, 228, 218, 0.35)` (over the game container background) |

**Tiles**

| Value         | Background  | Text colour |
|---------------|-------------|-------------|
| 2             | `#eee4da`   | `#776e65`   |
| 4             | `#ede0c8`   | `#776e65`   |
| 8             | `#f2b179`   | `#f9f6f2`   |
| 16            | `#f59563`   | `#f9f6f2`   |
| 32            | `#f67c5f`   | `#f9f6f2`   |
| 64            | `#f65e3b`   | `#f9f6f2`   |
| 128           | `#edcf72`   | `#f9f6f2`   |
| 256           | `#edcc61`   | `#f9f6f2`   |
| 512           | `#edc850`   | `#f9f6f2`   |
| 1024          | `#edc53f`   | `#f9f6f2`   |
| 2048          | `#edc22e`   | `#f9f6f2`   |
| Super (≥4096) | `#3c3a32`   | `#f9f6f2`   |

### 8.3 Animation (normative)

Animation level: **baseline + slide SHOULD** (operator decision, 2026-05-12). The implementation MUST visually distinguish three event classes:

- **Spawn (MUST).** A newly placed tile MUST scale 0 → 1 over a single visible frame (or longer, ≤ 200 ms).
- **Merge (MUST).** A tile produced by merge MUST briefly emphasize (pulse to 1.1 × scale and back) within ≤ 250 ms.
- **Slide (SHOULD).** Moving tiles SHOULD translate from origin to destination over ≤ 200 ms with an ease-out curve. An implementation that omits slide animation but ships spawn and merge animations is still conformant; the spec defect, if any, is in the SHOULD clause.

If the user agent reports `prefers-reduced-motion: reduce`, slide and merge-pulse durations MUST be 0 ms; spawn MAY be instant.

Tile identity (`:id` in §3.2) is the animation handle. Each tile vnode in the board view MUST carry Reagent key metadata `^{:key (:id tile)}` so that React's reconciler preserves the DOM node across the slide. Building the tile list from a positional grid (where keys would default to array index) MUST NOT happen.

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

The following invariants MUST hold under randomly generated valid input sequences (RNG-injected per §5.3):

- **Monotone score.** Score is monotone non-decreasing across all events.
- **Sum delta (successful moves only).** After every **successful** `:game/move` (per §3.5), the sum of `:tiles[*].value` increases by exactly the value of the freshly-spawned tile (∈ {2, 4}). After an **unsuccessful** `:game/move`, the sum is unchanged.
- **Stable ids.** A tile's `:id` is never reused while that tile still exists in `:tiles`. Once an id is freed (the tile was consumed by a merge), it MAY be reissued.
- **Phase progress.** Phase transitions match the exact table in §4.8; in particular no `:over → :playing` transition occurs without an intervening `:game/new` (which goes via `:fresh`).
- **Determinism.** Two runs with identical seed and identical event sequence produce identical `app-db` snapshots.

---

## 10. Tooling and build

The implementation:

- MUST use **shadow-cljs** as the build tool [§13.1].
- MUST target both `:browser` (release) and `:browser-test` (Karma or shadow-cljs node tests).
- MUST commit a `package.json` and `deps.edn` (re-frame2 reference uses both).
- MUST ship a CI workflow that runs unit tests and a release build on every push to `main`.
- SHOULD ship a release that deploys to GitHub Pages from the `main` branch.
- MUST enable re-frame2's trace / instrumentation bus in dev builds. Production builds MUST strip it. The release build MUST set `re-frame2.config/TRACE-ENABLED` to `false` via `:closure-defines` in `shadow-cljs.edn`; the test build MUST set it to `true`. CI MUST grep the release bundle to assert no occurrences of the symbol `re_frame2.trace.publish_event` (or equivalent post-DCE name) survive.

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

### 12.1 Revision history

| Version | Date       | Change                                                                                                                                                                                              |
|---------|------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| v0      | 2026-05-12 | Initial draft with 10 explicit open questions.                                                                                                                                                      |
| v0.1    | 2026-05-12 | Operator interview round 1 closed Q1, Q2, Q4, Q5; mayor-defaulted Q3, Q6, Q7, Q8, Q9; Q10 moot. Decisions landed in body; §12 became a decisions log.                                              |
| v0.2    | 2026-05-12 | Background audit (bead `reframe-2048-4el`) returned 3 BLOCKERS + 14 DEFECTS + 11 NITS + 9 CONFIRMED. Folded in: blockers A-01..A-03 (Malli everywhere; all 17+ wire shapes schematised; palette ramp inlined); defects B-01..B-14 (traversal vectors; tuple-shaped merged-from; spawn lifecycle via :ui.animation; :ui/animation-finished event added; dropped :game-state from :storage/loaded; :sub/legal-moves derivation; FSM transitions enumerated; splitmix64 RNG specified in new §5.3; localStorage failure mode; property-test sum invariant restated; Enter/c/C → :game/continue; closure-define name); nits C-01..C-11 mostly absorbed. NG9 added for vim keys. |

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
