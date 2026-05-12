(ns reframe-2048-2.views.board
  "Board view — 4×4 cell grid (background) + absolutely-positioned tile
   vnodes (foreground). Spec §4.7 / §8.3.

   Tile identity:
     Each tile vnode carries Reagent key metadata `^{:key id}` so React
     preserves the DOM node across slides — the slide animation is then
     a pure CSS `transform: translate(...)` transition on a persistent
     element. Building the tile list from a positional grid (where keys
     default to array index) MUST NOT happen per spec §8.3; the
     implementation here iterates `(vals @tiles)` and tags each vnode
     with `^{:key id}`.

   Animation hooks (§8.3):
     - Spawn (`tile--spawn` class): added when the tile's id is in
       :ui.animation.spawns. A setTimeout matching --dur-spawn dispatches
       :ui/animation-finished, removing the entry. CSS animations have
       no transitionend for pure scale-in (it's an animation, not a
       transition), and `animationend` ALSO fires for merge — easier to
       use setTimeout (per the bead's mandate).

     - Merge (`tile--merge` class): added when the id is in
       :ui.animation.merges. Native `animationend` listener dispatches
       :ui/animation-finished.

     - Slide: tile's transform is set every render from its current
       :pos. CSS `transition: transform var(--dur-slide) ease-out` on
       `.tile` does the animation. `transitionend` (filtered to the
       `transform` property) dispatches :ui/animation-finished.

   The board container is keyboard-focusable (tabIndex 0) per spec §8.4
   so screen-reader users can land here, but the actual key handler is
   the window-level listener in input.cljs (§6.1)."
  (:require [clojure.string :as str]
            [re-frame.core :as rf]
            [re-frame.views]
            [reframe-2048-2.views.overlay :as overlay])
  (:require-macros [re-frame.views-macros :refer [reg-view]]))

;; -- Constants ---------------------------------------------------------------

(def ^:private board-dims-default [4 4])

;; -- Helpers (pure) ----------------------------------------------------------

(defn- tile-value-class
  "Spec §8.2: per-value background/text classes. ≥4096 falls into the
   'super' bucket per the §8.2 table's last row."
  [v]
  (case v
    2     "tile--v-2"
    4     "tile--v-4"
    8     "tile--v-8"
    16    "tile--v-16"
    32    "tile--v-32"
    64    "tile--v-64"
    128   "tile--v-128"
    256   "tile--v-256"
    512   "tile--v-512"
    1024  "tile--v-1024"
    2048  "tile--v-2048"
    "tile--v-super"))

(defn- digits-class [v]
  (cond
    (>= v 10000) "tile--digits-5"
    (>= v 1000)  "tile--digits-4"
    (>= v 100)   "tile--digits-3"
    :else        nil))

(defn- tile-translate
  "Spec §8.3 slide: a CSS `translate(...)` expressing the tile's
   row/col → pixel offset via the CSS vars. Stored in two places:

   - On the `transform` property — drives the slide transition.
   - In the `--tile-translate` custom property — used by the spawn /
     merge keyframes so they can compose with the translate (otherwise
     the keyframes' `transform` declaration would clobber the slide
     position for the duration of the keyframe run)."
  [[r c]]
  (let [x (str "calc(" c " * (var(--cell-size) + var(--cell-gap)))")
        y (str "calc(" r " * (var(--cell-size) + var(--cell-gap)))")]
    (str "translate(" x ", " y ")")))

(defn- tile-classes [value spawning? merging?]
  (let [parts (cond-> ["tile" (tile-value-class value)]
                (digits-class value) (conj (digits-class value))
                spawning?            (conj "tile--spawn")
                merging?             (conj "tile--merge"))]
    (str/join " " parts)))

;; -- Event-dispatch helpers --------------------------------------------------

(defn- on-merge-end
  "Spec §8.3: dispatch :ui/animation-finished on the keyframe completion
   of the merge pulse. The tile keeps a `--tile-translate` CSS-var in
   its inline style so the pulse keyframes don't reset its position."
  [tile-id _e]
  (rf/dispatch [:ui/animation-finished {:phase :merge :tile-id tile-id}]))

(defn- on-slide-end
  "Spec §8.3: dispatch :ui/animation-finished on `transitionend` of the
   `transform` property only — Reagent / browsers can fire
   transitionend for other properties (e.g. background-color from a
   class flip); we'd otherwise drain the slide queue prematurely."
  [tile-id ^js e]
  (when (= "transform" (.-propertyName e))
    (rf/dispatch [:ui/animation-finished {:phase :slide :tile-id tile-id}])))

;; -- Spawn timeout side-effect ----------------------------------------------
;;
;; Spawn class drives a `tile-spawn` keyframes animation; native
;; `animationend` would also fire for merge, so to keep the two
;; clearly separated we use setTimeout per the bead description
;; ('setTimeout for spawn'). Schedule once per id; suppression atom
;; dedups across re-renders.

(defonce ^:private spawn-timeouts (atom #{}))

(defn- ensure-spawn-timeout! [tile-id]
  (when-not (contains? @spawn-timeouts tile-id)
    (swap! spawn-timeouts conj tile-id)
    (js/setTimeout
      (fn []
        (rf/dispatch [:ui/animation-finished {:phase :spawn :tile-id tile-id}])
        (swap! spawn-timeouts disj tile-id))
      220))) ;; slightly > --dur-spawn (180ms)

;; -- Tile vnode --------------------------------------------------------------

(defn- tile-vnode
  [{:keys [id value pos]} spawning? merging?]
  (let [translate (tile-translate pos)]
    [:div
     {:class       (tile-classes value spawning? merging?)
      ;; Two properties hold the same translate so the keyframes
      ;; (spawn / merge) can compose with the position via
      ;; `var(--tile-translate, none)` and the slide transition can
      ;; animate `transform` directly without the keyframes clobbering
      ;; the position. See style.css §tile-spawn / §tile-merge.
      :style       {:transform translate
                    "--tile-translate" translate}
      :on-animation-end (fn [e] (on-merge-end id e))
      :on-transition-end (fn [e] (on-slide-end id e))
      :data-tile-id id
      :data-value   value
      :aria-label   (str "Tile " value)}
     value]))

;; -- Cell layer --------------------------------------------------------------

(defn- cell-grid
  "16 always-empty backdrop cells. Tiles render OVER this grid as
   absolutely-positioned vnodes."
  [[rows cols]]
  (into [:div.board-grid]
        (for [r (range rows)
              c (range cols)]
          ^{:key [r c]}
          [:div.cell])))

;; -- The exported view ------------------------------------------------------

(defn- anim-id-set
  "Pull the set of tile-ids out of an animation-queue vector."
  [entries]
  (into #{} (map :tile-id) entries))

(reg-view board-view []
  (let [tiles  @(subscribe [:sub/tiles])
        dims   (or @(subscribe [:sub/board-dims]) board-dims-default)
        spawns @(subscribe [:sub/anim-spawns])
        merges @(subscribe [:sub/anim-merges])
        spawn-ids (anim-id-set spawns)
        merge-ids (anim-id-set merges)]
    ;; Side-effect: arm a setTimeout per spawn id so the queue drains.
    ;; Safe under re-render because the suppression atom dedups.
    (doseq [id spawn-ids]
      (ensure-spawn-timeout! id))
    [:div.board-frame
     {:tab-index 0
      :role      "application"
      :aria-label "2048 game board. Use arrow keys, w/a/s/d to move; press n for a new game."}
     [cell-grid dims]
     ;; Absolute-positioned tile layer, OVER the cell grid.
     (into [:div.tile-layer
            {:style {:position       "absolute"
                     :top            "var(--board-padding)"
                     :left           "var(--board-padding)"
                     :right          "var(--board-padding)"
                     :bottom         "var(--board-padding)"
                     :pointer-events "none"}}]
           (for [{:keys [id] :as t} (vals tiles)]
             ^{:key id}
             [tile-vnode t
              (contains? spawn-ids id)
              (contains? merge-ids id)]))
     ;; The overlay shares the board's positioning origin so it masks
     ;; only the play area, per §8.1.
     [overlay/overlay-view]]))
