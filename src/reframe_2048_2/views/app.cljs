(ns reframe-2048-2.views.app
  "Root app view — composes header / board / overlay / footer per spec §4.7,
   and hosts the ARIA live region the `:fx/announce` effect writes to per
   spec §8.4.

   Pure subs-driven; no useState/useEffect (§4.2). The board.cljs view
   embeds the overlay as a sibling element inside its absolutely-positioned
   board frame so the overlay visually masks the play area only, per §8.1."
  (:require [re-frame.core :as rf]
            [re-frame.views]
            [reframe-2048-2.views.header :as header]
            [reframe-2048-2.views.board  :as board]
            [reframe-2048-2.views.footer :as footer])
  (:require-macros [re-frame.views-macros :refer [reg-view]]))

(reg-view app-view []
  [:div.app-root
   [header/header-view]
   [board/board-view]
   [footer/footer-view]
   ;; Spec §8.4: ARIA live region for phase changes + score updates.
   ;; `:fx/announce` writes into the element via the DOM id; visually
   ;; hidden but read by screen readers. `aria-live polite` (per §8.4),
   ;; `aria-atomic true` so the entire message reads each time.
   [:div#a11y-live.visually-hidden
    {:role "status"
     :aria-live "polite"
     :aria-atomic "true"}]])
