(ns reframe-2048-2.views.footer
  "Footer view — one-paragraph instruction including the §6.1 key map.
   Spec §4.7 / §8.1."
  (:require [re-frame.views])
  (:require-macros [re-frame.views-macros :refer [reg-view]]))

(reg-view footer-view []
  [:footer.footer
   [:p
    "Join the tiles, get to "
    [:strong "2048!"]
    " Use "
    [:kbd "←"] " " [:kbd "↑"] " " [:kbd "→"] " " [:kbd "↓"]
    " (or "
    [:kbd "W"] " " [:kbd "A"] " " [:kbd "S"] " " [:kbd "D"]
    ") to slide tiles. Tiles with the same number merge when they touch. "
    "Press " [:kbd "N"] " for a new game, "
    [:kbd "Enter"] " (or " [:kbd "C"] ") to keep playing past a win, "
    "and " [:kbd "Esc"] " to dismiss the game-over banner."]])
