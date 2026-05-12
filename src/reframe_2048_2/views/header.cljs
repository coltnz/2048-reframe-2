(ns reframe-2048-2.views.header
  "Header view — title, current score, best score, 'New Game' button.

   Spec §4.7 / §8.1.

   Pure subs-driven. `reg-view` auto-injects `subscribe` + `dispatch`
   into lexical scope (see re-frame.views-macros §reg-view); no
   useState/useEffect (§4.2)."
  (:require [re-frame.core :as rf]
            [re-frame.views])
  (:require-macros [re-frame.views-macros :refer [reg-view]]))

(reg-view header-view []
  (let [score @(subscribe [:sub/score])
        best  @(subscribe [:sub/best-score])]
    [:header.header
     [:h1.title "2048"]
     [:div.header__right
      [:div.scores
       [:div.score-box
        [:span.score-box__label "Score"]
        [:span.score-box__value score]]
       [:div.score-box
        [:span.score-box__label "Best"]
        [:span.score-box__value best]]]
      [:button.btn
       {:type     "button"
        :on-click #(dispatch [:game/new])
        :aria-label "Start a new game"}
       "New Game"]]]))
