(ns reframe-2048-2.mechanics-test
  "Tests for the pure mechanics (§9.1 canonical cases + §9.4 invariants).

   These exercise spec §3 (game rules) and §5.3 (xorshift32 RNG) only.
   No re-frame, Reagent, or DOM is required; the suite runs under either
   `shadow-cljs compile test` (browser-test) or any host that picks up
   `cljs.test/deftest`."
  (:require [cljs.test :refer-macros [deftest is testing]]
            [reframe-2048-2.mechanics :as m]))

;; -- Test helpers ------------------------------------------------------------

(def dims-4 [4 4])

(defn- row->tiles
  "Turn a 1-D row `[2 2 4 _]` into a tiles map-by-id sitting on row `r`.
   Use `nil` (or `_` after quoting) for empty cells. Ids are assigned in
   left-to-right column order starting at 1, so the test can assert on
   them deterministically."
  ([row] (row->tiles row 0))
  ([row r]
   (into {}
         (keep-indexed
           (fn [c v]
             (when (some? v)
               (let [id (inc c)]
                 [id {:id id :value v :pos [r c]}])))
           row))))

(defn- row-of
  "Project tiles back to a 1-D row `[v v _ _]` for a given row index."
  [tiles r cols]
  (let [by-pos (into {} (map (fn [{:keys [pos value]}] [pos value])
                             (vals tiles)))]
    (mapv #(get by-pos [r %]) (range cols))))

(defn- with-next-id [tiles]
  (if (empty? tiles) 1 (inc (apply max (keys tiles)))))

;; -- §9.1 canonical row-slide cases ------------------------------------------

(deftest row-2222-left
  (testing "[2 2 2 2] left → [4 4 _ _], score +8 (two independent merges)"
    (let [tiles  (row->tiles [2 2 2 2])
          result (m/slide tiles :left dims-4 (with-next-id tiles))]
      (is (= [4 4 nil nil] (row-of (:tiles result) 0 4)))
      (is (= 8 (:score-delta result)))
      (is (= 2 (count (:merges result))))
      (is (:moved? result)))))

(deftest row-2240-left
  (testing "[2 2 4 _] left → [4 4 _ _] (single merge; the 4 slides)"
    (let [tiles  (row->tiles [2 2 4 nil])
          result (m/slide tiles :left dims-4 (with-next-id tiles))]
      (is (= [4 4 nil nil] (row-of (:tiles result) 0 4)))
      (is (= 4 (:score-delta result)))
      (is (= 1 (count (:merges result))))
      (is (:moved? result)))))

(deftest row-4422-left
  (testing "[4 4 2 2] left → [8 4 _ _] (two merges, different values)"
    (let [tiles  (row->tiles [4 4 2 2])
          result (m/slide tiles :left dims-4 (with-next-id tiles))]
      (is (= [8 4 nil nil] (row-of (:tiles result) 0 4)))
      (is (= 12 (:score-delta result)))
      (is (= 2 (count (:merges result))))
      (is (:moved? result)))))

(deftest row-2_24-left
  (testing "[2 _ 2 4] left → [4 4 _ _] (gap collapses, single merge)"
    (let [tiles  (row->tiles [2 nil 2 4])
          result (m/slide tiles :left dims-4 (with-next-id tiles))]
      (is (= [4 4 nil nil] (row-of (:tiles result) 0 4)))
      (is (= 4 (:score-delta result)))
      (is (= 1 (count (:merges result))))
      (is (:moved? result)))))

;; -- §9.1 full immobile board ------------------------------------------------

(defn- checker-board
  "Build an immobile 4×4 with no two adjacent cells sharing a value:

     [ 2  4  2  4
       4  2  4  2
       2  4  2  4
       4  2  4  2 ]

   No slide is possible: every cell occupied, every adjacency unequal."
  []
  (into {}
        (for [r (range 4)
              c (range 4)
              :let [id (inc (+ (* r 4) c))
                    v  (if (even? (+ r c)) 2 4)]]
          [id {:id id :value v :pos [r c]}])))

(deftest game-over-on-immobile-board
  (testing "Fully occupied alternating board → game-over"
    (let [tiles (checker-board)]
      (is (= #{} (m/legal-moves tiles dims-4)))
      (is (m/game-over? tiles dims-4))
      (doseq [d [:up :down :left :right]]
        (is (false? (m/legal-move? tiles d dims-4))
            (str d " must not be legal on an immobile board"))))))

(deftest non-game-over-with-empty-cell
  (testing "Any board with an empty cell admits at least one legal move"
    (let [tiles {1 {:id 1 :value 2 :pos [0 0]}}]
      (is (not (m/game-over? tiles dims-4)))
      (is (seq (m/legal-moves tiles dims-4))))))

;; -- §5.3 RNG sanity ---------------------------------------------------------

(deftest next-seed-rejects-zero
  (testing "Seed 0 is upgraded to 1 (xorshift32 cannot leave all-zero state)"
    (let [[s1 _] (m/next-seed 0)
          [s2 _] (m/next-seed 1)]
      (is (= s1 s2)
          "next-seed must treat 0 as if it were 1"))))

(deftest next-seed-deterministic
  (testing "Same seed → same sequence"
    (let [run (fn [seed n]
                (loop [s seed acc [] i n]
                  (if (zero? i)
                    acc
                    (let [[s' r] (m/next-seed s)]
                      (recur s' (conj acc r) (dec i))))))]
      (is (= (run 42 10) (run 42 10)))
      (is (not= (run 42 10) (run 43 10))))))

;; -- §9.4 spawn determinism --------------------------------------------------

(deftest spawn-deterministic-for-fixed-seed
  (testing "Same seed + same board ⇒ same spawn (cell and value)"
    (let [tiles {}
          a (m/spawn tiles 12345 dims-4 1)
          b (m/spawn tiles 12345 dims-4 1)]
      (is (= (:tiles a) (:tiles b)))
      (is (= (:seed a)  (:seed b)))
      (is (= 1 (count (:spawns a))))
      (is (= 2 (:next-id a)))
      (let [t (first (vals (:tiles a)))]
        (is (contains? #{2 4} (:value t)))))))

(deftest spawn-on-full-board-is-noop
  (testing "Spawn on a full board returns tiles unchanged but advances seed"
    (let [tiles (checker-board)
          r     (m/spawn tiles 99 dims-4 17)]
      (is (= tiles (:tiles r)))
      (is (= [] (:spawns r)))
      (is (= 17 (:next-id r)))
      (is (not= 99 (:seed r))))))

;; -- §9.4 P(value=2) statistical test ----------------------------------------

(deftest spawn-value-distribution-approx-0_9
  (testing "P(value=2) over many seeded spawns ≈ 0.9 within tolerance"
    ;; 2000 trials; binomial std-dev = sqrt(0.9 * 0.1 * 2000) ≈ 13.4, so
    ;; ±60 (≈ 4.5σ) is safely inside the tails for a non-flaky CI signal.
    (let [trials   2000
          counts   (loop [i        0
                          seed     1
                          n-twos   0
                          n-fours  0]
                     (if (= i trials)
                       {:twos n-twos :fours n-fours}
                       (let [r (m/spawn {} seed dims-4 1)
                             v (:value (first (vals (:tiles r))))]
                         (recur (inc i)
                                (:seed r)
                                (cond-> n-twos  (= v 2) inc)
                                (cond-> n-fours (= v 4) inc)))))
          twos     (:twos counts)
          expected (* 0.9 trials)
          tol      60]
      (is (= trials (+ twos (:fours counts))) "Every trial must produce 2 or 4")
      (is (<= (- expected tol) twos (+ expected tol))
          (str "Got " twos " twos / " trials " trials; expected ≈ " expected " ± " tol)))))

;; -- §9.4 sum-delta invariant ------------------------------------------------

(deftest sum-delta-on-successful-move
  (testing "After a successful slide, sum of tile values is unchanged
            (merges replace 2v with one 2v; spawn is separate)"
    (let [tiles      (row->tiles [2 2 2 2])
          before-sum (reduce + (map :value (vals tiles)))
          result     (m/slide tiles :left dims-4 (with-next-id tiles))
          after-sum  (reduce + (map :value (vals (:tiles result))))]
      (is (= before-sum after-sum)
          "Slide-only sum invariance: merges conserve total tile-value sum"))))

;; -- §3.5 traversal table sanity --------------------------------------------

(deftest traversals-table-matches-spec
  (is (= {:rows [0 1 2 3] :cols [0 1 2 3]} (m/traversals-for :up)))
  (is (= {:rows [3 2 1 0] :cols [0 1 2 3]} (m/traversals-for :down)))
  (is (= {:rows [0 1 2 3] :cols [0 1 2 3]} (m/traversals-for :left)))
  (is (= {:rows [0 1 2 3] :cols [3 2 1 0]} (m/traversals-for :right))))

;; -- §3.7 won-after-move? ---------------------------------------------------

(deftest won-detection-on-2048-merge
  (testing "A merge that produces 2048 is detected"
    (let [tiles  {1 {:id 1 :value 1024 :pos [0 0]}
                  2 {:id 2 :value 1024 :pos [0 1]}}
          result (m/slide tiles :left dims-4 3)]
      (is (m/won-after-move? (:merges result) (:tiles result)))
      (is (= 2048 (:score-delta result))))))

(deftest won-detection-negative
  (testing "A normal merge does NOT trip won"
    (let [tiles  {1 {:id 1 :value 2 :pos [0 0]}
                  2 {:id 2 :value 2 :pos [0 1]}}
          result (m/slide tiles :left dims-4 3)]
      (is (not (m/won-after-move? (:merges result) (:tiles result)))))))

;; -- direction parity --------------------------------------------------------

(deftest right-slide-symmetric-to-left
  (testing "[2 2 2 2] right → [_ _ 4 4] (mirror of left case)"
    (let [tiles  (row->tiles [2 2 2 2])
          result (m/slide tiles :right dims-4 (with-next-id tiles))]
      (is (= [nil nil 4 4] (row-of (:tiles result) 0 4)))
      (is (= 8 (:score-delta result))))))

(deftest no-move-when-already-resolved
  (testing "[2 _ _ _] left is a no-op (single tile already at the edge)"
    (let [tiles  (row->tiles [2 nil nil nil])
          result (m/slide tiles :left dims-4 (with-next-id tiles))]
      (is (false? (:moved? result)))
      (is (zero? (:score-delta result)))
      (is (= tiles (:tiles result))))))

(deftest adjacent-equal-merges-as-expected
  (testing "[4 4 _ _] left → [8 _ _ _] (adjacent equals merge)"
    (let [tiles  (row->tiles [4 4 nil nil])
          result (m/slide tiles :left dims-4 (with-next-id tiles))]
      (is (= [8 nil nil nil] (row-of (:tiles result) 0 4)))
      (is (= 8 (:score-delta result)))
      (is (:moved? result)))))
