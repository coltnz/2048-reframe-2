(ns reframe-2048-2.mechanics
  "Pure 2048 game-rule functions per spec §3 and the xorshift32 RNG per §5.3.

   Scope (bead reframe-2048-w9e): every fn in this namespace is a pure,
   data-in / data-out function. There are deliberately zero references to
   re-frame, Reagent, the DOM, or any side-effectful host API. The
   integrating layer (later beads) is responsible for threading the values
   returned here back into `app-db`.

   Data shapes (matching spec §5.1):

     Tile         {:id TileId :value PowerOfTwoGeq2 :pos [row col]}
                  When produced by a merge, also carries
                  {:merged-from [TileId TileId]} per §3.5 step 3.

     Tiles        {TileId -> Tile}   ; map-by-id, NOT a positional grid

     Dims         [rows cols]        ; v1: [4 4]

     Direction    :up | :down | :left | :right

   The bead description lists `slide [tiles dir dims]`. The implementation
   here takes an additional `next-id` argument and threads it back out
   because §3.5 step 3 requires a *fresh* id for every merged tile and §9.4
   requires those ids to be stable / non-reused while live. Reusing a
   consumed tile's id would violate the stable-ids invariant, and storing
   a counter inside `tiles` would muddle the per-tile schema. Threading
   `next-id` through `slide` is the cleanest fit with the spawn signature
   and with `:game.next-id` in app-db. This is a clarifying deviation, not
   a rule deviation; the public contract for §3.5 is preserved.")

;; -- Constants ---------------------------------------------------------------

(def ^:private direction-vectors
  "§2.2 / §3.5: direction-component vectors for each move."
  {:up    [-1  0]
   :down  [ 1  0]
   :left  [ 0 -1]
   :right [ 0  1]})

(def directions
  "The four legal move directions."
  #{:up :down :left :right})

(def ^:private win-value
  "§3.7: producing a tile of this value transitions :playing → :won."
  2048)

;; -- RNG (§5.3) --------------------------------------------------------------

(defn next-seed
  "xorshift32 — period 2^32 − 1. Returns `[next-state random-int32]`.

   The all-zero state is fixed under xorshift32, so seed 0 is upgraded to
   1 per spec §5.3. All bitwise ops in CLJS coerce to Int32, so the result
   is exactly representable in JS doubles."
  [s]
  (let [s (if (zero? s) 1 s)
        s (bit-xor s (bit-shift-left s 13))
        s (bit-xor s (unsigned-bit-shift-right s 17))
        s (bit-xor s (bit-shift-left s 5))]
    [s s]))

(defn- nonneg-int
  "Mask the sign bit off an Int32, yielding a value in [0, 2^31). Used to
   pre-condition the RNG output for `mod`-based bucketing (§5.3)."
  [r]
  (bit-and r 0x7FFFFFFF))

;; -- Traversal order (§3.5 step 1) ------------------------------------------

(defn traversals-for
  "§3.5 step 1: the row/col traversal vectors per direction.

   The table is normative — `:down` reverses the row axis, `:right`
   reverses the col axis, the other two leave both forward. Tiles closest
   to the destination edge are processed first."
  [dir]
  (case dir
    :up    {:rows [0 1 2 3] :cols [0 1 2 3]}
    :down  {:rows [3 2 1 0] :cols [0 1 2 3]}
    :left  {:rows [0 1 2 3] :cols [0 1 2 3]}
    :right {:rows [0 1 2 3] :cols [3 2 1 0]}))

;; -- Board helpers -----------------------------------------------------------

(defn- index-by-pos
  "Build a {[row col] -> Tile} map. Convenient for slide bookkeeping."
  [tiles]
  (persistent!
    (reduce-kv (fn [m _id {:keys [pos] :as t}] (assoc! m pos t))
               (transient {})
               tiles)))

(defn empty-cells
  "Vector of [row col] cells with no tile, in row-major order."
  [tiles [rows cols]]
  (let [occ (into #{} (map :pos) (vals tiles))]
    (vec
      (for [r (range rows)
            c (range cols)
            :let [cell [r c]]
            :when (not (occ cell))]
        cell))))

(defn- in-bounds?
  [[r c] [rows cols]]
  (and (>= r 0) (< r rows)
       (>= c 0) (< c cols)))

(defn- find-farthest+next
  "§3.5 step 2: walk from `from` in direction `[dr dc]` and return
   `[farthest next-cell-or-nil]`. `farthest` is the last empty cell
   reachable; `next-cell-or-nil` is the cell immediately beyond `farthest`
   (which MAY be off-board, in which case it is nil)."
  [from dir-vec dims by-pos exclude-pos]
  (let [[dr dc] dir-vec
        empty? (fn [cell]
                 (and (in-bounds? cell dims)
                      (or (= cell exclude-pos)
                          (not (contains? by-pos cell)))))]
    (loop [cur from]
      (let [nxt [(+ (first cur) dr) (+ (second cur) dc)]]
        (if (empty? nxt)
          (recur nxt)
          ;; nxt is either off-board or occupied
          [cur (when (in-bounds? nxt dims) nxt)])))))

(defn- traversal-order
  "Yield tile ids in §3.5 step-1 order. Tiles are looked up positionally."
  [tiles dir dims]
  (let [{:keys [rows cols]} (traversals-for dir)
        by-pos (index-by-pos tiles)]
    (for [r rows
          c cols
          :let [t (get by-pos [r c])]
          :when t]
      (:id t))))

;; -- Slide (§3.5) ------------------------------------------------------------

(defn slide
  "Resolve a §3.5 slide for `tiles` in direction `dir`. Returns:

     {:tiles       new tile map-by-id, post-slide
      :score-delta sum of all produced merge values (§3.6)
      :slides      [{:tile-id id :from [r c] :to [r c]} ...]
                   only tiles whose :pos actually changed (excludes merged
                   tiles, which are reported under :merges instead)
      :merges      [{:tile-id new-id :from-ids [id-a id-b]} ...]
      :next-id     next free id after this slide
      :moved?      true iff the slide is a *successful* move per §3.5}

   The `!mergedFrom` guard is implemented via the `merged-targets` set:
   a target cell that already received a merge during *this* slide is
   excluded from further merge eligibility, so `[2 2 2 2]` left becomes
   `[4 4 _ _]` (two independent merges), not `[8 _ _ _]`."
  [tiles dir dims next-id]
  (let [dir-vec (direction-vectors dir)
        ids     (traversal-order tiles dir dims)]
    (loop [ids               ids
           by-id             tiles
           score-delta       0
           slide-events      []
           merge-events      []
           next-id           next-id
           merged-targets    #{}]
      (if (empty? ids)
        (let [moved?         (or (seq slide-events) (seq merge-events))
              ;; A tile that slid in step N can still be consumed by a
              ;; later merge in step N+1 (e.g. `[2 _ 2 4]` left: the
              ;; first 2 slides to col 1; the second 2 then slides AND
              ;; merges with it, dissoc'ing the first one). Its
              ;; slide-event stays in the events list but its DOM
              ;; element disappears in the same render — so the
              ;; browser never fires `transitionend` for it and the
              ;; :ui.animation.slides queue can never drain through it.
              ;; That stuck entry then keeps :sub/animation-busy? true
              ;; and silently drops every subsequent keypress (§6.3).
              ;;
              ;; Filter slide-events down to tiles that still exist in
              ;; the final by-id; the consumed ones are accounted for
              ;; in merge-events anyway.
              live-slides    (vec (filter #(contains? by-id (:tile-id %))
                                          slide-events))]
          {:tiles       by-id
           :score-delta score-delta
           :slides      live-slides
           :merges      (vec merge-events)
           :next-id     next-id
           :moved?      (boolean moved?)})
        (let [id        (first ids)
              t         (get by-id id)
              from-pos  (:pos t)
              by-pos    (index-by-pos by-id)
              [farthest next-cell] (find-farthest+next from-pos dir-vec dims
                                                       by-pos from-pos)
              next-tile (when next-cell (get by-pos next-cell))]
          (if (and next-tile
                   (= (:value next-tile) (:value t))
                   (not (contains? merged-targets next-cell)))
            ;; Merge case (§3.5 step 3)
            (let [merged-value (* 2 (:value t))
                  new-id       next-id
                  consumed-a   id
                  consumed-b   (:id next-tile)
                  new-tile     {:id          new-id
                                :value       merged-value
                                :pos         next-cell
                                :merged-from [consumed-a consumed-b]}]
              (recur (rest ids)
                     (-> by-id
                         (dissoc consumed-a)
                         (dissoc consumed-b)
                         (assoc new-id new-tile))
                     (+ score-delta merged-value)
                     slide-events
                     (conj merge-events
                           {:tile-id  new-id
                            :from-ids [consumed-a consumed-b]})
                     (inc next-id)
                     (conj merged-targets next-cell)))
            ;; Slide case (§3.5 step 4)
            (if (= farthest from-pos)
              ;; No movement at all.
              (recur (rest ids)
                     by-id
                     score-delta
                     slide-events
                     merge-events
                     next-id
                     merged-targets)
              (recur (rest ids)
                     (assoc-in by-id [id :pos] farthest)
                     score-delta
                     (conj slide-events
                           {:tile-id id
                            :from    from-pos
                            :to      farthest})
                     merge-events
                     next-id
                     merged-targets))))))))

;; -- Spawn (§3.4 / §5.3) ----------------------------------------------------

(defn spawn
  "§3.4 spawn procedure, consuming the RNG exactly twice per §5.3.

   Returns

     {:tiles    tiles', the input map plus the new tile (if any)
      :seed     post-spawn seed (advanced twice — even when no cell was
                free, both reads still happen so callers get a stable
                seed cadence)
      :spawns   [{:tile-id new-id}]   ; empty when E was empty
      :next-id  new-id + 1}

   When the empty-cell set is empty (full board) the procedure is a no-op
   on tiles and next-id, but advances the seed twice so test fixtures see
   a stable RNG cadence regardless of board fullness."
  [tiles seed [rows cols :as dims] next-id]
  (let [E         (empty-cells tiles dims)
        [s1 u1]   (next-seed seed)
        [s2 u2]   (next-seed s1)]
    (if (empty? E)
      {:tiles tiles :seed s2 :spawns [] :next-id next-id}
      (let [cell    (nth E (mod (nonneg-int u1) (count E)))
            value   (if (< (mod (nonneg-int u2) 100) 90) 2 4)
            new-id  next-id
            tile    {:id new-id :value value :pos cell}]
        {:tiles   (assoc tiles new-id tile)
         :seed    s2
         :spawns  [{:tile-id new-id}]
         :next-id (inc new-id)}))))

;; -- Legality / game-over (§3.5 / §3.8) -------------------------------------

(defn legal-move?
  "§3.5 / §3.8: true iff resolving the slide in `dir` against `tiles`
   would be a *successful* move (at least one position change or merge).

   `legal-move?` does not need an honest `next-id`: it only inspects the
   `:moved?` flag and discards the produced tile map. We pass a dummy
   value of 0 to keep the slide signature happy."
  [tiles dir dims]
  (:moved? (slide tiles dir dims 0)))

(defn legal-moves
  "Set of directions for which `legal-move?` returns true."
  [tiles dims]
  (into #{}
        (filter #(legal-move? tiles % dims))
        directions))

(defn game-over?
  "§3.8: true iff no direction yields a legal move."
  [tiles dims]
  (empty? (legal-moves tiles dims)))

;; -- Win detection (§3.7) ---------------------------------------------------

(defn won-after-move?
  "§3.7: true iff any of the merge events would have produced a tile of
   value 2048. Caller is responsible for the §3.9 won-vs-continuing phase
   guard; this fn is purely a predicate over the slide's merge log.

   We work from the merge-event log rather than from the resulting tile
   map so a caller can inspect a slide result without needing to know
   the new-id of the produced tile."
  [merges tiles]
  (boolean
    (some (fn [{:keys [tile-id]}]
            (= win-value (get-in tiles [tile-id :value])))
          merges)))
