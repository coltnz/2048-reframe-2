(ns reframe-2048-2.schemas
  "Malli schemas for the 2048-reframe-2 wire surface, verbatim from
   spec §5.2 plus the `AppDb` rollup of §5.1.

   Scope (bead reframe-2048-z0b): every shape that crosses a re-frame2
   boundary — event payloads (§4.4), effect payloads (§4.5), sub return
   values (§4.6), the in-`app-db` animation queue entries (§5.1), the
   `app-db` itself (§5.1 / §5.2), and the persisted `localStorage`
   blob (§7.1) — has a named Malli schema in this namespace. Per
   re-frame2 SA-3 + spec §5.2: every shape on the wire MUST have a
   schema.

   Requiring `malli.core` keeps the var resolvable at runtime: per
   re-frame.schemas, the default validator delegates to
   `(resolve 'malli.core/validate)`; without an explicit require the
   ns might not be loaded into the CLJS runtime and validation would
   silently no-op.

   This namespace does NOT register `reg-app-schema` itself; that
   wiring lives in `reframe-2048-2.db` so the registration runs at
   app-boot under the active frame (`:game`)."
  (:require [malli.core :as _malli]))

;; -- Core scalar / tile shapes (spec §5.2 'Core types') ----------------------

(def TileId
  "Positive integer per spec §3.2."
  [:and :int [:fn pos?]])

(def PowerOfTwoGeq2
  "Integer ≥ 2 whose binary representation has a single 1-bit. The
   `(bit-and n (dec n))` trick is the standard power-of-two check;
   `(zero? ...)` accepts only 1, 2, 4, 8, ..., so the additional
   `(>= n 2)` clause excludes 1."
  [:and :int
   [:fn (fn [n] (and (>= n 2) (zero? (bit-and n (dec n)))))]])

(def Cell
  "[row col] with row/col ≥ 0. Spec §5.2 D-02 deliberately omits the
   upper bound so the same shape ports unchanged to a v1.1 5×5 board.
   Bounds against the active `:board-dims` (v1: 4×4) are enforced at
   handler boundaries, not in the schema."
  [:tuple [:int {:min 0}] [:int {:min 0}]])

(def Direction
  "Move direction (§2.2)."
  [:enum :up :down :left :right])

(def Phase
  "Game lifecycle phase (§4.8). `:fresh` is boot-only — once the first
   `:game/new` lands, the FSM does not return to `:fresh`."
  [:enum :fresh :playing :won :continuing :over])

(def AnimationPhase
  "Animation-queue bucket (§5.1)."
  [:enum :slide :merge :spawn])

(def Tile
  "A live tile on the board (§3.2 + §5.2). The optional `:merged-from`
   carries the two consumed ids when this tile was produced by a
   merge in the most recent slide; used by the merge-pulse animation
   (§8.3) and cleared on the next move."
  [:map {:closed true}
   [:id          TileId]
   [:value       PowerOfTwoGeq2]
   [:pos         Cell]
   [:merged-from {:optional true} [:maybe [:tuple TileId TileId]]]])

;; -- Event payloads (§4.4 / §5.2 'Event payloads') ---------------------------
;;
;; Each event payload schema validates the event vector's TAIL — re-frame2
;; routes events as `[event-id payload]`, and the spec.cljc validator
;; runs against the whole vector. We attach these as the `:spec` metadata
;; value to each reg-event-* registration in `events.cljs`; the validator
;; reads them via `re-frame.schemas/validate-event!` (Spec 010 §Validation
;; order step 1) which compares the whole event vector. To keep that
;; surface honest we wrap each payload in a `[:cat]`-shaped event vector
;; below — the keyword head + the payload map.

(def Event-GameNew
  "`:game/new` carries no payload."
  [:cat [:= :game/new] [:? [:map {:closed true}]]])

(def Event-GameMove
  "`:game/move {:dir Direction}`."
  [:cat [:= :game/move] [:map {:closed true} [:dir Direction]]])

(def Event-GameContinue
  "`:game/continue` carries no payload."
  [:cat [:= :game/continue] [:? [:map {:closed true}]]])

(def Event-GameDismissOver
  "`:game/dismiss-over` carries no payload."
  [:cat [:= :game/dismiss-over] [:? [:map {:closed true}]]])

(def Event-InputKeyDown
  "`:input/key-down {:key <string>}`. The value-set is restricted at
   the handler layer (§6.1); the schema only requires `:key` to be a
   string so unknown keys can be silently no-op'd rather than rejected
   at the validator."
  [:cat [:= :input/key-down] [:map {:closed true} [:key :string]]])

(def Event-StorageLoaded
  "`:storage/loaded best-score-or-nil` — nil means \"no prior value\"
   per spec §7.1. The payload is positional (not map-shaped) because
   it is dispatched by the generic `:fx/storage-read` effect, which
   does not know which key it is loading; the effect's contract is
   'dispatch :on-success with the parsed value as the last
   positional arg' (§4.5).

   This is a deliberate carve-out from re-frame2 Principle 'Naming
   Over Position': for storage-loaded payloads the value is a single
   primitive and a single-key map adds no clarity. Logged as a
   spec-vs-impl gotcha in the bead PR."
  [:cat [:= :storage/loaded] [:maybe :int]])

(def Event-StorageSave
  "`:storage/save` carries no payload."
  [:cat [:= :storage/save] [:? [:map {:closed true}]]])

(def Event-UIAnimationFinished
  "`:ui/animation-finished {:phase AnimationPhase :tile-id TileId}`."
  [:cat [:= :ui/animation-finished]
   [:map {:closed true}
    [:phase   AnimationPhase]
    [:tile-id TileId]]])

(def Event-UIToggleInstructions
  "`:ui/toggle-instructions` carries no payload — flips the footer
   instruction-visibility flag. A pure UI affordance (§8.1), persisted
   via localStorage (§7.1); not in the spec §4.4 event table."
  [:cat [:= :ui/toggle-instructions] [:? [:map {:closed true}]]])

(def Event-UIInstructionsLoaded
  "`:ui/instructions-loaded hidden?-or-nil` — positional payload
   dispatched by the generic `:fx/storage-read` on boot. nil means
   \"no persisted preference\" and defaults to shown. Positional (not
   map-shaped) for the same reason as `:storage/loaded` (§7.1): a
   single primitive needs no key."
  [:cat [:= :ui/instructions-loaded] [:maybe :boolean]])

;; -- Effect payloads (§4.5 / §5.2 'Effect payloads') -------------------------

(def Fx-StorageWrite
  "Args to `:fx/storage-write`."
  [:map {:closed true}
   [:key   :string]
   [:value :any]])

(def Fx-StorageRead
  "Args to `:fx/storage-read`. `:on-success` is the event id to
   dispatch with the parsed value appended as the last positional
   payload entry."
  [:map {:closed true}
   [:key        :string]
   [:on-success :keyword]])

(def Fx-Focus
  "Args to `:fx/focus`."
  [:map {:closed true}
   [:selector :string]])

(def Fx-Announce
  "Args to `:fx/announce`."
  [:map {:closed true}
   [:message :string]])

;; -- Subscription return values (§4.6 / §5.2 'Subscription return values') --

(def Sub-Board
  "v1: 4×4 vector-of-vectors of TileId-or-nil. The hard 4×4 is OK here
   because the sub bakes in the active board-dims and the schema is
   only consulted by tooling / tests; if v1.1 widens the board, this
   schema gets revised in lockstep."
  [:vector {:min 4 :max 4}
   [:vector {:min 4 :max 4} [:maybe TileId]]])

(def Sub-Tiles    [:map-of TileId Tile])
(def Sub-Score    :int)
(def Sub-BestScore :int)
(def Sub-Phase    Phase)
(def Sub-LegalMoves [:set Direction])
(def Sub-AnimationBusy :boolean)
(def Sub-InstructionsHidden
  "True when the player has hidden the footer instructions (§8.1)."
  :boolean)

;; -- Animation queue entries (§5.1) -----------------------------------------

(def Anim-Slide
  [:map {:closed true}
   [:tile-id TileId]
   [:from    Cell]
   [:to      Cell]])

(def Anim-Merge
  [:map {:closed true}
   [:tile-id  TileId]
   [:from-ids [:tuple TileId TileId]]])

(def Anim-Spawn
  [:map {:closed true}
   [:tile-id TileId]])

;; -- `app-db` (§5.2 'app-db') -----------------------------------------------

(def AppDb-Game
  [:map {:closed true}
   [:board-dims  [:tuple [:int {:min 1}] [:int {:min 1}]]]
   [:phase       Phase]
   [:score       [:int {:min 0}]]
   [:best-score  [:int {:min 0}]]
   [:tiles       [:map-of TileId Tile]]
   [:next-id     [:int {:min 1}]]
   [:rng-seed    :int]])

(def AppDb-UI
  ;; `:instructions-hidden?` is an impl-level UI affordance (footer
  ;; instructions toggle, §8.1) beyond the §5.1 verbatim shape; it is
  ;; persisted to localStorage (§7.1) so it survives reloads.
  [:map {:closed true}
   [:overlay              [:set [:enum :won :over]]]
   [:instructions-hidden? :boolean]
   [:animation [:map {:closed true}
                [:slides  [:vector Anim-Slide]]
                [:merges  [:vector Anim-Merge]]
                [:spawns  [:vector Anim-Spawn]]]]])

(def AppDb-Input
  "Reserved for future input state (e.g. swipe gesture in flight).
   v1 carries no keys; the closed-map keeps an empty `{}` valid and
   rejects accidental writes."
  [:map {:closed true}])

(def AppDb
  "The root re-frame2 db shape for the `:game` frame. The FSM's
   `[:rf/machines :game/fsm]` slot is added by the machines artefact
   on first dispatch into the machine; we list it as optional so the
   schema validates against the initial-db (which has no
   `:rf/machines` yet) and against the post-boot db (which does).

   Spec §5.2 carries the `AppDb` map as a closed three-key shape; the
   `:rf/machines` slot is framework-managed metadata, NOT app shape,
   so we admit it under `:rf/machines` `:any` rather than baking the
   machine snapshot's schema in here. The machine's snapshot is
   validated separately by the machines artefact's own checks."
  [:map {:closed true}
   [:game        AppDb-Game]
   [:ui          AppDb-UI]
   [:input       AppDb-Input]
   [:rf/machines {:optional true} :any]])

;; -- localStorage (§7.1 / §5.2 'localStorage') -------------------------------

(def StorageBlob-BestScore
  "The persisted best-score blob is the bare integer (serialised via
   JSON.stringify ⇒ a number literal). Key
   `reframe-2048-2/best-score-v1`."
  :int)

(def StorageBlob-InstructionsHidden
  "The persisted footer-instructions preference: a bare boolean
   (JSON.stringify ⇒ `true`/`false`). Key
   `reframe-2048-2/instructions-hidden-v1`."
  :boolean)

;; -- Registration helpers ----------------------------------------------------
;;
;; A tiny lookup table the `db` ns reads at boot to register the AppDb
;; schema against the active frame. Keeping the registration outside
;; this ns means `reframe-2048-2.schemas` stays a pure schema-defs
;; namespace; require it from tests and tooling without booting the
;; frame.

(def app-db-schema AppDb)
