(ns reframe-2048-2.views.overlay
  "Won / over overlay views per spec §4.7 / §8.1.

   Overlay visibility:
     `:sub/overlay` (in subs.cljs) returns a subset of #{:won :over}.
     The overlay is rendered iff the set is non-empty. When :won is
     present we render the win banner; when :over is present we render
     the game-over banner. The two are mutually exclusive in practice
     (the FSM never produces a state with both flags simultaneously)
     but if they co-exist the won banner wins (it must be dismissed
     first via :game/continue).

   Keyboard input under overlay:
     Per spec §8.1, the overlay traps keyboard input — only :game/new,
     :game/continue (won only), and :game/dismiss-over (over only) are
     accepted. The actual gating is in `events/key->event` (events.cljs),
     which silently drops arrow keys when the overlay is shown; the
     overlay buttons here dispatch the same events for click users."
  (:require [re-frame.core :as rf]
            [re-frame.views])
  (:require-macros [re-frame.views-macros :refer [reg-view]]))

(reg-view overlay-view []
  (let [overlay @(subscribe [:sub/overlay])
        score   @(subscribe [:sub/score])]
    (cond
      (contains? overlay :won)
      [:div.overlay.overlay--won
       {:role             "dialog"
        :aria-modal       "true"
        :aria-labelledby  "overlay-won-title"}
       [:h2#overlay-won-title.overlay__title "You win!"]
       [:p (str "You reached 2048 with a score of " score ".")]
       [:div.overlay__actions
        [:button.btn
         {:type "button"
          :on-click #(dispatch [:game/continue])
          :auto-focus true
          :aria-label "Keep playing past 2048"}
         "Keep playing"]
        [:button.btn
         {:type "button"
          :on-click #(dispatch [:game/new])
          :aria-label "Start a new game"}
         "New Game"]]]

      (contains? overlay :over)
      [:div.overlay.overlay--over
       {:role             "dialog"
        :aria-modal       "true"
        :aria-labelledby  "overlay-over-title"}
       [:h2#overlay-over-title.overlay__title "Game over"]
       [:p (str "Final score: " score ".")]
       [:div.overlay__actions
        [:button.btn
         {:type "button"
          :on-click #(dispatch [:game/new])
          :auto-focus true
          :aria-label "Start a new game"}
         "New Game"]
        [:button.btn
         {:type "button"
          :on-click #(dispatch [:game/dismiss-over])
          :aria-label "Dismiss the game over overlay (game stays over)"}
         "Dismiss"]]]

      :else nil)))
