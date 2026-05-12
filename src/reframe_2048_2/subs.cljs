(ns reframe-2048-2.subs
  "re-frame2 subscriptions per spec §4.6.

   Scope (bead reframe-2048-z0b): every sub listed in spec §4.6's
   table — `:sub/board`, `:sub/tiles`, `:sub/score`, `:sub/best-score`,
   `:sub/phase`, `:sub/legal-moves`, `:sub/animation-busy?`. Views
   below the root MUST consume only these (or sub-subs derived from
   these) and MUST NOT read `app-db` directly (Spec 006 §Public Query
   Surfaces).

   `:sub/legal-moves` and `:sub/board` are derived subs (`:<-`)
   chained off `:sub/tiles` so that React-style re-renders happen
   only when the tile map's identity changes — re-frame2's reg-sub
   equality dedup gates downstream renders. The trial-resolve in
   `:sub/legal-moves` is the heaviest per-frame compute; the
   derived-sub layer is the recommended caching point per spec §4.6."
  (:require [re-frame.core :as rf]
            [reframe-2048-2.mechanics :as m]
            [reframe-2048-2.schemas   :as s]))

;; -- Leaf subs (direct app-db reads) -----------------------------------------

(rf/reg-sub :sub/tiles
  {:doc  "Map-by-id of every live tile (§4.6)."
   :spec s/Sub-Tiles}
  (fn [db _] (get-in db [:game :tiles])))

(rf/reg-sub :sub/score
  {:doc  "Current score (§4.6)."
   :spec s/Sub-Score}
  (fn [db _] (get-in db [:game :score])))

(rf/reg-sub :sub/best-score
  {:doc  "Persisted best score (§4.6, §7.1)."
   :spec s/Sub-BestScore}
  (fn [db _] (get-in db [:game :best-score])))

(rf/reg-sub :sub/phase
  {:doc  "Lifecycle phase (§4.8). `:fresh | :playing | :won | :continuing | :over`."
   :spec s/Sub-Phase}
  (fn [db _] (get-in db [:game :phase])))

(rf/reg-sub :sub/board-dims
  {:doc "Active board dims; v1 [4 4]. Not in §4.6's named sub list but
         exposed as a derived helper so views can stay dim-agnostic."}
  (fn [db _] (get-in db [:game :board-dims])))

(rf/reg-sub :sub/overlay
  {:doc "The `:ui.overlay` set (subset of #{:won :over}). Not in the
         §4.6 list — surfaced so the views layer can render the win /
         over banners conditionally without re-reading app-db."}
  (fn [db _] (get-in db [:ui :overlay])))

;; -- Derived subs ------------------------------------------------------------

(rf/reg-sub :sub/board
  {:doc  "v1: a 4×4 vector-of-vectors of TileId-or-nil, derived from
          `:sub/tiles` and `:sub/board-dims` (§4.6). Cells with no
          tile are nil; cells with a tile carry the tile's id so view
          code can key directly off the stable id per spec §8.3
          (Reagent `^{:key (:id tile)}`)."
   :spec s/Sub-Board}
  :<- [:sub/tiles]
  :<- [:sub/board-dims]
  (fn [[tiles [rows cols]] _]
    (let [by-pos (into {}
                       (map (fn [{:keys [id pos]}] [pos id]))
                       (vals tiles))]
      (mapv (fn [r]
              (mapv (fn [c] (get by-pos [r c])) (range cols)))
            (range rows)))))

(rf/reg-sub :sub/legal-moves
  {:doc  "Set of directions yielding a successful move on the current
          tile map (§4.6 / §3.5). Chained off `:sub/tiles` so we
          recompute only when the tile-map identity changes, per
          spec §4.6's caching recommendation."
   :spec s/Sub-LegalMoves}
  :<- [:sub/tiles]
  :<- [:sub/board-dims]
  (fn [[tiles dims] _]
    (m/legal-moves tiles dims)))

(rf/reg-sub :sub/animation-busy?
  {:doc  "True iff any animation queue (slides / merges / spawns) is
          non-empty (§4.6 / §6.3). The `:input/key-down` handler
          reads this to drop keypresses arriving mid-animation per
          §6.3."
   :spec s/Sub-AnimationBusy}
  (fn [db _]
    (let [{:keys [slides merges spawns]} (get-in db [:ui :animation])]
      (boolean (or (seq slides) (seq merges) (seq spawns))))))
