(ns reframe-2048-2.core
  "Entry point for 2048-reframe-2.

   Scope evolution:
   - bead reframe-2048-aja (PR #1, scaffold): mounted a placeholder
     Reagent view inside the single `:game` frame; no events / subs.
   - bead reframe-2048-z0b (PR #3, impl-state): wired every
     registration namespace, registered the AppDb schema and the
     `:game/fsm` machine, attached the window keydown listener, and
     dispatched `:storage/loaded` + the initial `:game/new` so the
     placeholder view boots into `:playing`.
   - bead reframe-2048-4ix (this bead, impl-views): replaces the
     placeholder with the full app-view tree per spec §4.7 — header /
     board / overlay / footer — backed by the canonical §8.2 palette
     (public/css/style.css) and the §8.3 animation hooks. The ARIA
     live region rendered by `views/app/app-view` is the target of
     the `:fx/announce` effect."
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
            [reframe-2048-2.input   :as input]
            [reframe-2048-2.views.app :as views-app])
  (:require-macros [re-frame.views-macros :refer [reg-view]]))

;; -- Constants ---------------------------------------------------------------

(def best-score-storage-key
  "Spec §7.1 — the localStorage key the boot sequence reads from."
  "reframe-2048-2/best-score-v1")

;; -- Views -------------------------------------------------------------------

(reg-view app-root []
  ;; Spec §4.3: single `:game` frame. The frame-provider sets the
  ;; React context so every `subscribe` / `dispatch` inside resolves
  ;; to `:game` rather than `:rf/default`.
  [rf/frame-provider {:frame :game}
   [views-app/app-view]])

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
