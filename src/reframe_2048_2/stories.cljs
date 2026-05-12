(ns reframe-2048-2.stories
  "Story registrations for 2048-reframe-2 — Storybook-class catalogue
   of named view states.

   Wired against `day8/re-frame2-story` per
   `docs/guide/14-stories.md` (re-frame2 repo, pinned SHA in
   deps.edn). Three rules from that chapter shape what's here:

     1. Every variant runs in its own frame against a fresh app-db.
        We seed deterministic state via a small `:story/seed` event
        registered in this ns rather than driving `:game/new`
        (which would seed the RNG from wall-clock millis and spawn
        random tiles).

     2. Variant bodies are plain data — no fn-slots. The view at
        the centre of each variant is referenced by view-id keyword
        (the id `reg-view` registered).

     3. Assertions in `:play` sequences record-don't-throw; they
        append to the variant frame's assertion accumulator and
        `run-variant` reports the lot at the end.

   Production elision is the `:closure-defines` flag on the :app
   build's :release map in shadow-cljs.edn; every `reg-*` macro
   expands to `(when re-frame.story.config/enabled? ...)` and DCEs
   under :advanced with the constant false."
  (:require [re-frame.core    :as rf]
            [re-frame.story   :as story]
            ;; Side-effecting requires — each ns registers its
            ;; handlers / subs / views into the global registrar at
            ;; ns-load. Order doesn't matter for stories (no per-frame
            ;; schema / machine wiring runs here; variant frames boot
            ;; with a fresh app-db).
            [reframe-2048-2.schemas]
            [reframe-2048-2.effects]
            [reframe-2048-2.subs]
            [reframe-2048-2.fsm]
            [reframe-2048-2.events]
            [reframe-2048-2.views.app]
            [reframe-2048-2.views.header]
            [reframe-2048-2.views.board]
            [reframe-2048-2.views.footer]
            [reframe-2048-2.views.overlay]
            [reframe-2048-2.db :as db]))

;; -- Story-only seed event --------------------------------------------------
;;
;; Reset the variant frame's db to a deterministic snapshot. Variant
;; `:events` slots call `[:story/seed <patch>]` where <patch> is a
;; map merged on top of `db/default-db` so every variant starts from
;; a spec §5.1-correct shape and overrides only the slots it cares
;; about. Not part of the spec §4.4 event table — strictly a
;; Story-time fixture event, analogous to `:counter/initialise` in
;; the worked example.

(rf/reg-event-db :story/seed
  (fn handler-story-seed [_ [_ patch]]
    (-> db/default-db
        (update :game merge (:game patch))
        (update :ui   merge (:ui patch)))))

;; -- Fixtures ---------------------------------------------------------------

(defn- tile
  "Tile vnode shape per spec §5.1: `{:id :value :pos [row col]}`."
  [id value row col]
  {:id id :value value :pos [row col]})

(defn- tiles->map [ts]
  (into {} (map (juxt :id identity)) ts))

(def ^:private empty-board {})

(def ^:private fresh-board
  (tiles->map [(tile 1 2 0 0)
               (tile 2 2 3 3)]))

(def ^:private mid-board
  ;; Powers-of-two ladder up to 256 so every palette colour shows.
  (tiles->map [(tile 1 2   0 0) (tile 2 4   0 1)
               (tile 3 8   1 0) (tile 4 16  1 1)
               (tile 5 32  2 0) (tile 6 64  2 1)
               (tile 7 128 3 0) (tile 8 256 3 1)]))

(def ^:private near-win-board
  (tiles->map [(tile 1 1024 0 0)
               (tile 2 1024 0 1)
               (tile 3 512  1 0)
               (tile 4 256  1 1)
               (tile 5 128  2 0)]))

(def ^:private won-board
  (tiles->map [(tile 1 2048 0 0)
               (tile 2 1024 0 1)
               (tile 3 512  1 0)
               (tile 4 256  1 1)]))

(def ^:private super-board
  (tiles->map [(tile 1 2048  0 0)
               (tile 2 4096  0 1)
               (tile 3 8192  1 0)
               (tile 4 16384 1 1)
               (tile 5 32768 2 0)]))

(def ^:private deadlock-board
  ;; 4×4 grid; no two orthogonally-adjacent tiles share a value, so
  ;; no legal merge — the :over phase fixture.
  (tiles->map [(tile  1 2 0 0) (tile  2 4 0 1) (tile  3 2 0 2) (tile  4 4 0 3)
               (tile  5 4 1 0) (tile  6 2 1 1) (tile  7 4 1 2) (tile  8 2 1 3)
               (tile  9 2 2 0) (tile 10 4 2 1) (tile 11 2 2 2) (tile 12 4 2 3)
               (tile 13 4 3 0) (tile 14 2 3 1) (tile 15 4 3 2) (tile 16 2 3 3)]))

(defn- game-state
  "Build a `:story/seed` patch from a small map of named slots."
  [{:keys [tiles score best-score phase overlay]
    :or   {score 0 best-score 0 phase :playing overlay #{}}}]
  {:game {:tiles      tiles
          :score      score
          :best-score best-score
          :phase      phase}
   :ui   {:overlay overlay}})

;; -- Decorator: centre the canvas -------------------------------------------

(defn- register-decorators! []
  (story/reg-decorator :app/centered
    {:doc  "Centre the variant horizontally and pad it. Single-view
            variants (header, board, overlay, footer) sit flush left
            without it."
     :kind :hiccup
     :wrap (fn [body _]
             [:div {:style {:display          "flex"
                            :justify-content  "center"
                            :align-items      "flex-start"
                            :padding          "2em"
                            :background       "#faf8ef"
                            :min-height       "100vh"}}
              body])}))

;; -- reg-story / reg-variant trees ------------------------------------------

(defn- register-header! []
  (story/reg-story :story.header
    {:doc        "Header — title + scores + new-game button."
     :component  :reframe-2048-2.views.header/header-view
     :decorators [[:app/centered]]
     :tags       #{:dev :docs}
     :substrates #{:reagent}})

  (story/reg-variant :story.header/fresh
    {:doc        "Zero score, zero best."
     :events     [[:story/seed (game-state {:tiles fresh-board})]]
     :play       [[:rf.assert/sub-equals [:sub/score]      0]
                  [:rf.assert/sub-equals [:sub/best-score] 0]]
     :tags       #{:dev :docs :test}
     :substrates #{:reagent}})

  (story/reg-variant :story.header/in-play
    {:doc        "Mid-game — score below best."
     :events     [[:story/seed (game-state {:tiles      mid-board
                                            :score      1456
                                            :best-score 9800})]]
     :play       [[:rf.assert/sub-equals [:sub/score]      1456]
                  [:rf.assert/sub-equals [:sub/best-score] 9800]]
     :tags       #{:dev :docs :test}
     :substrates #{:reagent}})

  (story/reg-variant :story.header/new-best
    {:doc        "Score has overtaken best — both equal."
     :events     [[:story/seed (game-state {:tiles      mid-board
                                            :score      12048
                                            :best-score 12048})]]
     :tags       #{:dev :docs}
     :substrates #{:reagent}}))

(defn- register-footer! []
  (story/reg-story :story.footer
    {:doc        "Footer — static keyboard instructions."
     :component  :reframe-2048-2.views.footer/footer-view
     :decorators [[:app/centered]]
     :tags       #{:dev :docs}
     :substrates #{:reagent}})

  (story/reg-variant :story.footer/default
    {:doc        "The footer reads no app-db — one variant is enough."
     :events     []
     :tags       #{:dev :docs}
     :substrates #{:reagent}}))

(defn- register-overlay! []
  (story/reg-story :story.overlay
    {:doc        "Won / over banner. Hidden when `:ui.overlay` is empty."
     :component  :reframe-2048-2.views.overlay/overlay-view
     :decorators [[:app/centered]]
     :tags       #{:dev :docs}
     :substrates #{:reagent}})

  (story/reg-variant :story.overlay/hidden
    {:doc        "Empty overlay set — nothing rendered."
     :events     [[:story/seed (game-state {:tiles fresh-board})]]
     :play       [[:rf.assert/sub-equals [:sub/overlay] #{}]]
     :tags       #{:dev :docs :test}
     :substrates #{:reagent}})

  (story/reg-variant :story.overlay/won
    {:doc        "Win banner — :won in the overlay set."
     :events     [[:story/seed (game-state {:tiles    won-board
                                            :score    20480
                                            :phase    :won
                                            :overlay  #{:won}})]]
     :play       [[:rf.assert/sub-equals [:sub/overlay] #{:won}]
                  [:rf.assert/sub-equals [:sub/score]   20480]]
     :tags       #{:dev :docs :test}
     :substrates #{:reagent}})

  (story/reg-variant :story.overlay/over
    {:doc        "Game-over banner — :over in the overlay set."
     :events     [[:story/seed (game-state {:tiles   deadlock-board
                                            :score   1232
                                            :phase   :over
                                            :overlay #{:over}})]]
     :play       [[:rf.assert/sub-equals [:sub/overlay] #{:over}]]
     :tags       #{:dev :docs :test}
     :substrates #{:reagent}}))

(defn- register-board! []
  (story/reg-story :story.board
    {:doc        "Board — 4×4 cell grid plus absolutely-positioned tiles."
     :component  :reframe-2048-2.views.board/board-view
     :decorators [[:app/centered]]
     :tags       #{:dev :docs}
     :substrates #{:reagent}})

  (story/reg-variant :story.board/empty
    {:doc        "Backdrop only — no tiles."
     :events     [[:story/seed (game-state {:tiles empty-board})]]
     :play       [[:rf.assert/sub-equals [:sub/tiles] empty-board]]
     :tags       #{:dev :docs :test}
     :substrates #{:reagent}})

  (story/reg-variant :story.board/fresh
    {:doc        "Two starting tiles in opposite corners."
     :events     [[:story/seed (game-state {:tiles fresh-board})]]
     :tags       #{:dev :docs}
     :substrates #{:reagent}})

  (story/reg-variant :story.board/mid-game
    {:doc        "Powers-of-two ladder 2 → 256 — every palette colour."
     :events     [[:story/seed (game-state {:tiles mid-board :score 1456})]]
     :tags       #{:dev :docs}
     :substrates #{:reagent}})

  (story/reg-variant :story.board/near-win
    {:doc        "Two 1024s side-by-side — one merge from 2048."
     :events     [[:story/seed (game-state {:tiles near-win-board
                                            :score 17800})]]
     :tags       #{:dev :docs}
     :substrates #{:reagent}})

  (story/reg-variant :story.board/super-tiles
    {:doc        "≥4096 tiles — the §8.2 `tile--v-super` bucket."
     :events     [[:story/seed (game-state {:tiles super-board
                                            :score 100000})]]
     :tags       #{:dev :docs}
     :substrates #{:reagent}})

  (story/reg-variant :story.board/deadlock
    {:doc        "Every cell filled, no two adjacent equal — no legal move."
     :events     [[:story/seed (game-state {:tiles   deadlock-board
                                            :score   1232
                                            :phase   :over
                                            :overlay #{:over}})]]
     :tags       #{:dev :docs}
     :substrates #{:reagent}}))

(defn- register-app! []
  (story/reg-story :story.app
    {:doc        "The full app — header + board + overlay + footer."
     :component  :reframe-2048-2.views.app/app-view
     :tags       #{:dev :docs}
     :substrates #{:reagent}})

  (story/reg-variant :story.app/fresh
    {:doc        "Boot state — two spawn tiles, no overlay."
     :events     [[:story/seed (game-state {:tiles      fresh-board
                                            :best-score 9800})]]
     :tags       #{:dev :docs :screenshot}
     :substrates #{:reagent}})

  (story/reg-variant :story.app/in-play
    {:doc        "Mid-game ladder under a non-trivial score."
     :events     [[:story/seed (game-state {:tiles      mid-board
                                            :score      1456
                                            :best-score 9800})]]
     :tags       #{:dev :docs}
     :substrates #{:reagent}})

  (story/reg-variant :story.app/won
    {:doc        "Win banner over the board."
     :events     [[:story/seed (game-state {:tiles      won-board
                                            :score      20480
                                            :best-score 20480
                                            :phase      :won
                                            :overlay    #{:won}})]]
     :tags       #{:dev :docs}
     :substrates #{:reagent}})

  (story/reg-variant :story.app/over
    {:doc        "Game-over banner."
     :events     [[:story/seed (game-state {:tiles      deadlock-board
                                            :score      1232
                                            :best-score 9800
                                            :phase      :over
                                            :overlay    #{:over}})]]
     :tags       #{:dev :docs}
     :substrates #{:reagent}}))

(defn- register-workspaces! []
  (story/reg-workspace :Workspace.board/all-states
    {:doc      "Every board state on one canvas — auto-enumerates."
     :layout   :variants-grid
     :for      :story.board
     :columns  2
     :tags     #{:docs}})

  (story/reg-workspace :Workspace.app/all-phases
    {:doc      "The full app across phases — fresh / in-play / won / over."
     :layout   :variants-grid
     :for      :story.app
     :columns  2
     :tags     #{:docs}}))

;; -- Top-level registration -------------------------------------------------

(defn register-all!
  "Register every Story artefact for 2048-reframe-2. Idempotent —
   called once at ns-load and again by core.cljs after a hot reload
   (clear-all! lives in the worked example's test fixture; we don't
   need it here)."
  []
  (story/install-canonical-vocabulary!)
  (register-decorators!)
  (register-header!)
  (register-footer!)
  (register-overlay!)
  (register-board!)
  (register-app!)
  (register-workspaces!))

(register-all!)
