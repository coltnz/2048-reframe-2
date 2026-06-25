(ns reframe-2048-2.views.footer
  "Footer view — one-paragraph instruction including the §6.1 key map,
   plus a discrete toggle that hides the instructions for a calmer view.
   The hidden/shown preference is persisted (§7.1) so it survives reloads.
   Spec §4.7 / §8.1.

   Pure subs-driven. `reg-view` auto-injects `subscribe` + `dispatch`
   into lexical scope (see re-frame.views-macros §reg-view); no
   useState/useEffect (§4.2)."
  (:require [re-frame.core :as rf]
            [re-frame.views])
  (:require-macros [re-frame.views-macros :refer [reg-view]]))

(reg-view footer-view []
  (let [hidden? @(subscribe [:sub/instructions-hidden?])]
    [:footer.footer {:class (when hidden? "footer--collapsed")}
     ;; Instructions render only when not hidden; the toggle is always
     ;; present so the player can bring them back.
     (when-not hidden?
       [:p#footer-instructions
        "Join the tiles, get to "
        [:strong "2048!"]
        " Use "
        [:kbd "←"] " " [:kbd "↑"] " " [:kbd "→"] " " [:kbd "↓"]
        " (or "
        [:kbd "W"] " " [:kbd "A"] " " [:kbd "S"] " " [:kbd "D"]
        ") to slide tiles. Tiles with the same number merge when they touch. "
        "Press " [:kbd "N"] " for a new game, "
        [:kbd "Enter"] " (or " [:kbd "C"] ") to keep playing past a win, "
        "and " [:kbd "Esc"] " to dismiss the game-over banner."])
     ;; Discrete icon: "×" dismisses the instructions, "?" brings them
     ;; back. aria-expanded/aria-controls make it a proper disclosure.
     [:button.footer__toggle
      {:type          "button"
       :on-click      #(dispatch [:ui/toggle-instructions])
       :aria-expanded (if hidden? "false" "true")
       :aria-controls "footer-instructions"
       :title         (if hidden? "Show instructions" "Hide instructions")
       :aria-label    (if hidden? "Show instructions" "Hide instructions")}
      (if hidden? "?" "×")]]))
