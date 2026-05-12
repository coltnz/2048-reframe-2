(ns reframe-2048-2.core
  "Entry point for 2048-reframe-2.

   Scope (bead reframe-2048-aja, the scaffold bead): mount a placeholder
   Reagent view inside the single re-frame2 frame named :game per spec
   §4.3. No game logic, no events beyond what is needed to render the
   placeholder, no subs, no persistence. Those land in later beads."
  (:require [reagent.dom.client      :as rdc]
            [re-frame.core           :as rf]
            [re-frame.views]
            [re-frame.adapter.reagent :as reagent-adapter])
  (:require-macros [re-frame.views-macros :refer [reg-view]]))

;; -- Views -------------------------------------------------------------------
;;
;; reg-view (Spec 004) is the multi-frame contract for view definition.
;; The :game frame's `dispatch` and `subscribe` would be injected as
;; lexical bindings inside the body; we don't use them yet, but the
;; macro is the canonical shape and we use it from day one so later
;; beads do not have to rewrite the placeholder.

(reg-view placeholder []
  [:div {:style {:font-family "sans-serif"
                 :padding     "2rem"
                 :color       "#776e65"}}
   "Hello, 2048-reframe-2"])

(reg-view app-root []
  ;; The frame-provider wraps the whole render tree so every nested
  ;; subscribe / dispatch resolves to the :game frame (spec §4.3),
  ;; rather than falling through to re-frame2's :rf/default frame.
  [rf/frame-provider {:frame :game}
   [placeholder]])

;; -- Mount -------------------------------------------------------------------

(defonce ^:private root
  (rdc/create-root (.getElementById js/document "app")))

(defn ^:export run
  "Initialise the Reagent adapter and render the root view.

   Called as the :init-fn for the shadow-cljs :app build (see
   shadow-cljs.edn). Also invoked as :devtools :after-load so the
   render survives a hot reload."
  []
  (rf/init! reagent-adapter/adapter)
  (rdc/render root [app-root]))
