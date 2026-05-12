(ns reframe-2048-2.core
  "Entry point for 2048-reframe-2.

   Scope evolution:
   - bead reframe-2048-aja (PR #1, scaffold): mounted a placeholder
     Reagent view inside the single `:game` frame; no events / subs.
   - bead reframe-2048-z0b (this bead, impl-state): wires every
     registration namespace, registers the AppDb schema and the
     `:game/fsm` machine, attaches the window keydown listener, and
     dispatches `:storage/loaded` + the initial `:game/new` so the
     placeholder view boots into `:playing`.

   The placeholder view itself remains until impl-views (next bead);
   we surface `:sub/score` and `:sub/phase` here so the live wiring is
   visible in the browser without needing devtools."
  (:require [reagent.dom.client       :as rdc]
            [re-frame.core            :as rf]
            [re-frame.views]
            [re-frame.adapter.reagent :as reagent-adapter]
            ;; Side-effecting requires — each ns registers its
            ;; handlers / fxs / subs / machines / schemas at ns-load.
            ;; Order matters only for `db` (which registers the
            ;; AppDb schema; must be after schemas is loaded).
            [reframe-2048-2.schemas]
            [reframe-2048-2.db      :as db]
            [reframe-2048-2.effects]
            [reframe-2048-2.subs]
            [reframe-2048-2.fsm     :as fsm]
            [reframe-2048-2.events]
            [reframe-2048-2.input   :as input])
  (:require-macros [re-frame.views-macros :refer [reg-view]]))

;; -- Constants ---------------------------------------------------------------

(def best-score-storage-key
  "Spec §7.1 — the localStorage key the boot sequence reads from."
  "reframe-2048-2/best-score-v1")

;; -- Views -------------------------------------------------------------------
;;
;; Still a placeholder until impl-views. We surface live :sub/score
;; and :sub/phase so a human can sanity-check the wiring without
;; opening devtools, and we expose a 'New game' button so the
;; placeholder is interactive enough to drive a smoke test.

(reg-view placeholder []
  (let [score @(subscribe [:sub/score])
        best  @(subscribe [:sub/best-score])
        phase @(subscribe [:sub/phase])
        legal @(subscribe [:sub/legal-moves])]
    [:div {:style {:font-family "sans-serif"
                   :padding     "2rem"
                   :color       "#776e65"}}
     [:h1 "2048-reframe-2 — state wired"]
     [:p (str "Phase: "       (name phase))]
     [:p (str "Score: "       score)]
     [:p (str "Best score: "  best)]
     [:p (str "Legal moves: " (pr-str legal))]
     [:p "Use arrow keys (or w/a/s/d) to move; n for a new game."]
     [:button {:on-click #(dispatch [:game/new])} "New game"]]))

(reg-view app-root []
  ;; Spec §4.3: single `:game` frame. The frame-provider sets the
  ;; React context so every `subscribe` / `dispatch` inside resolves
  ;; to `:game` rather than `:rf/default`.
  [rf/frame-provider {:frame :game}
   [placeholder]])

;; -- Mount -------------------------------------------------------------------

(defonce ^:private root
  (rdc/create-root (.getElementById js/document "app")))

;; Per-frame boot is orchestrated by a single re-frame event:
;; `:app/initialise` seeds the default-db and then chains the
;; storage-read + initial `:game/new`. This keeps the boot path
;; inside the regular event grammar so the trace bus sees it; it is
;; NOT part of the spec §4.4 event table, but is a thin one-shot
;; orchestration event analogous to `boot.boot/:boot/initialise` in
;; the re-frame2 examples.

(rf/reg-event-fx :app/initialise
  {:doc "Boot-only event: seeds the spec §5.1 default-db, fires
         `:fx/storage-read` for the best-score blob, then dispatches
         the initial `:game/new`. Not part of the spec §4.4 event
         table; analogous to `:boot/initialise` in the re-frame2
         examples."}
  (fn handler-app-initialise [_ _]
    {:db db/default-db
     :fx [[:fx/storage-read
           {:key        best-score-storage-key
            :on-success :storage/loaded}]
          [:dispatch [:game/new]]]}))

(defn ^:export run
  "Initialise the Reagent adapter, register every state surface, and
   render the root view.

   Called as the :init-fn for the shadow-cljs :app build (see
   shadow-cljs.edn). Also invoked as :devtools :after-load so the
   render survives hot reload."
  []
  (rf/init! reagent-adapter/adapter)
  ;; Per Spec 002: re-frame2's frame is auto-created on first dispatch,
  ;; but registering schemas + the machine wants the frame to exist
  ;; first. `reg-frame` is idempotent (surgical update on re-register)
  ;; so it's safe under hot reload.
  (rf/reg-frame :game
    {:doc "The single re-frame2 frame for 2048-reframe-2 (spec §4.3)."})
  ;; Wrap the rest of boot in `with-frame` so `reg-app-schema` /
  ;; `reg-machine` register against `:game`, not `:rf/default`.
  (rf/with-frame :game
    (db/register-schemas!)
    (fsm/register!)
    ;; Seed the db + run the boot sequence. dispatch-sync so the
    ;; storage-read fires and the :game/new lands before the first
    ;; render.
    (rf/dispatch-sync [:app/initialise] {:frame :game}))
  ;; Install the DOM keyboard listener exactly once.
  (input/attach!)
  ;; Render. The frame-provider scopes the subtree to `:game`.
  (rdc/render root [app-root]))
