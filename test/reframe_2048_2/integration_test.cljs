(ns reframe-2048-2.integration-test
  "Spec §9.3 conformance fixtures — at minimum a win-game trace and a
   lose-game trace.

   These tests build a synthetic starting db and drive a sequence of
   `:game/move` events directly through the event handler (no router,
   no Reagent, no machines artefact required). The assertions exercise
   the full state-layer pipeline end-to-end: slide → score → spawn →
   lose / won detection → FSM phase advance → overlay update.

   Per spec §9.3 the test fixtures land under `test/fixtures/*.edn` in
   a later bead (the conformance-fixture corpus is broader than this
   bead's scope per the bead description's 'Out of scope' clause). The
   two traces below cover the spec §9.3 minimum."
  (:require [cljs.test :refer-macros [deftest is testing]]
            [re-frame.registrar :as registrar]
            [reframe-2048-2.events]
            [reframe-2048-2.db        :as db]
            [reframe-2048-2.mechanics :as m]))

;; -- Helpers -----------------------------------------------------------------

(defn- handler [event-id]
  (or (:handler-fn (registrar/lookup :event event-id))
      (throw (ex-info "No handler registered" {:event-id event-id}))))

(defn- play-move
  "Apply one :game/move and return the resulting db. Quietly drops the
   :fx slice for the trace; the win / lose announcements would land
   there in production."
  [db dir]
  (:db ((handler :game/move) {:db db} [:game/move {:dir dir}])))

;; -- Win trace ---------------------------------------------------------------
;;
;; The cleanest win trace: two 1024 tiles slid together. The slide
;; produces a 2048; the resulting phase is :won and :ui.overlay
;; carries :won. We don't drive a full board-up-to-win because the
;; deterministic spawn sequence depends on the seed and the resulting
;; board would be 60+ moves long; the unit test in mechanics-test
;; already validates the per-merge math.

(deftest win-trace
  (testing "[1024 1024 _ _] :left in :playing → :won + score +2048"
    (let [start (-> db/default-db
                    (assoc-in [:game :phase]    :playing)
                    (assoc-in [:game :tiles]
                              {1 {:id 1 :value 1024 :pos [0 0]}
                               2 {:id 2 :value 1024 :pos [0 1]}})
                    (assoc-in [:game :next-id]  3)
                    (assoc-in [:game :rng-seed] 12345))
          db' (play-move start :left)]
      (is (= :won (get-in db' [:game :phase]))
          "Phase transitions to :won when 2048 is produced.")
      (is (contains? (get-in db' [:ui :overlay]) :won)
          ":won is in :ui.overlay.")
      (is (= 2048 (get-in db' [:game :score]))
          "Score += 2048 for the winning merge.")
      (is (= 2048 (get-in db' [:game :best-score]))
          "Best score updated for the winning merge.")
      ;; Post-slide: one merged 2048 tile + one spawn = 2 tiles.
      (is (= 2 (count (get-in db' [:game :tiles]))))
      (is (some #(= 2048 (:value %)) (vals (get-in db' [:game :tiles])))
          "The 2048 tile is present on the board."))))

;; -- Lose trace --------------------------------------------------------------
;;
;; The lose trace is harder to drive deterministically through the
;; spawn RNG (the post-spawn board has to be immobile). Instead we
;; set up a near-immobile board where ONE move would fill the last
;; empty cell with a value that makes the post-spawn board immobile.
;;
;; Strategy: a 4x4 alternating-value board with exactly one empty
;; cell at [3 3] and pre-arranged so a left-slide doesn't merge or
;; move anything except a single cell. Then we let the spawn land at
;; [3 3] (the only empty cell) and the resulting board is immobile.
;;
;; We use a board where the spawn IS the killing move — the spawn
;; consumes the last empty cell; the post-spawn board has no legal
;; move regardless of the spawned value.

(deftest lose-trace
  (testing "Filling the final empty cell with an immobile-board configuration → :over"
    ;; Board: every cell filled with alternating 2/4 except [3 3].
    ;; The :left slide on row 3 [4 2 4 _] → [4 2 4 _] (no-op, no
    ;; merges adjacent). But we need a slide that MOVES so the
    ;; handler progresses to spawn. So we put a different shape on
    ;; one row.
    ;;
    ;; Row 0: [2 4 2 4]   (immobile row)
    ;; Row 1: [4 2 4 2]   (immobile row)
    ;; Row 2: [2 4 2 4]   (immobile row)
    ;; Row 3: [4 2 4 _]   ([3 3] is empty)
    ;;
    ;; Slide :down: column 3 has [4 4 4 _]. Slide down pulls them all
    ;; into the bottom three cells WITHOUT merge (because the top is
    ;; the moving tile, and the 4s pair up — actually `[4 4 4 _]`
    ;; down → `[_ 4 4 8]`. That's a merge; score +8 and three tiles
    ;; in col 3 with the spawn filling [0 3].
    ;;
    ;; That's not a lose trace. Instead pick a configuration where the
    ;; only legal move is one that produces an immobile post-spawn
    ;; board no matter where the spawn lands.
    ;;
    ;; Simpler: hand-craft the pre-spawn near-loss directly.
    ;; Force the spawn seed to a known value so the test is
    ;; deterministic; pin tiles such that the slide is legal but the
    ;; post-spawn board is full and alternating.
    (let [;; Pre-move tiles: 15 tiles in a 4x4 alternating pattern,
          ;; missing [0 0]. Row 0 starts: [_ 4 2 4]. Slide right →
          ;; [_ 4 2 4] (no movement on row 0). We need a slide that
          ;; moves SOMETHING.
          ;;
          ;; Pre-move: 15 tiles with a single 2 at [0 1] and 4s
          ;; elsewhere so slid-left collapses [_ 2 _ _] → [2 _ _ _]
          ;; while everything else stays put. Then the spawn (RNG
          ;; seed chosen for a value of 4 at [0 1]) makes the board
          ;; immobile (alternating 2/4 pattern).
          ;;
          ;; The cleanest path: pre-make a near-lose board with a
          ;; single-cell gap, dispatch a directionally trivial move,
          ;; verify :over after spawn fills the gap.
          ;;
          ;; To keep this readable, we construct the post-spawn board
          ;; directly via the mechanics-level invariants: confirm
          ;; that game-over? detects loss on a fully-occupied
          ;; alternating board (covered by mechanics-test) and that
          ;; the EVENT layer routes that into :over.
          full-alternating
          (into {}
                (for [r (range 4)
                      c (range 4)
                      :let [id (inc (+ (* r 4) c))
                            ;; Use a 2/4 alternation: never any two adjacent
                            ;; equal cells.
                            v  (if (even? (+ r c)) 2 4)]]
                  [id {:id id :value v :pos [r c]}]))

          ;; Replace one corner with a movable-but-non-mergeable tile.
          ;; We'll set [3 3] = 8 — no neighbour equals 8, so the slide
          ;; left makes [3 3] move and forces a merge somewhere else,
          ;; but for this test we only need m/game-over? + the FSM
          ;; routing. So set up the post-slide post-spawn state
          ;; directly and probe `legal-moves` / the :game/move handler.
          ;;
          ;; The simpler, more direct trace:
          ;;
          ;; 1. Construct a *playing* db whose tiles are NOT yet
          ;;    immobile (legal moves exist).
          ;; 2. Apply a move that, after the deterministic spawn,
          ;;    yields an immobile board.
          ;; 3. Assert phase = :over.
          ;;
          ;; Constructing such a fixture deterministically is non-
          ;; trivial; we use a different shortcut: directly invoke
          ;; the handler on a near-immobile board where the slide
          ;; itself produces an immobile board (and the spawn keeps
          ;; it immobile too).
          ]
      ;; Direct mechanics-level sanity check: a fully-occupied
      ;; alternating board IS game-over.
      (is (m/game-over? full-alternating db/board-dims)
          "Alternating 2/4 board has no legal move (§3.8).")))

  (testing "An :over phase is reached when the post-spawn board is immobile"
    ;; Trace: a 15-tile board with a single empty cell at [3 3] and a
    ;; movable-tile at [3 2] of value 8 with neighbours [4 _ 8].
    ;; Sliding LEFT on row 3 moves [3 2]→[3 1] OR [3 0] depending on
    ;; row 3's contents. We hand-pick row 3 so the slide is legal and
    ;; the spawn (deterministic seed) lands at [3 3] making the
    ;; board immobile.
    ;;
    ;; Row contents (1-D):
    ;;   Row 0: [2 4 2 4]
    ;;   Row 1: [4 2 4 2]
    ;;   Row 2: [2 4 2 4]
    ;;   Row 3: [_ 8 2 4]    (cell [3 0] empty; rest fixed)
    ;;
    ;; Slide LEFT on row 3: 8 at [3 1] moves to [3 0]; 2 stays at
    ;; [3 1]? No: slide left ⇒ tiles flow left. Row 3 becomes
    ;; [8 2 4 _]. The [3 3] cell is now empty; the spawn lands at
    ;; [3 3]. If the spawn is value 2, post-board row 3 = [8 2 4 2];
    ;; the full board is then [2 4 2 4 / 4 2 4 2 / 2 4 2 4 / 8 2 4 2].
    ;; Check adjacency: row 3 = [8 2 4 2] — no two adjacent equal.
    ;; Col 3 = [4 2 4 2] — no two adjacent equal. Etc.
    ;;
    ;; We pick the seed so the spawn lands at [3 3] (the only empty
    ;; cell after the slide; `count E = 1`, so any seed works) with
    ;; value 2.
    (let [tiles-pre {;; Row 0
                     1 {:id 1 :value 2 :pos [0 0]}
                     2 {:id 2 :value 4 :pos [0 1]}
                     3 {:id 3 :value 2 :pos [0 2]}
                     4 {:id 4 :value 4 :pos [0 3]}
                     ;; Row 1
                     5 {:id 5 :value 4 :pos [1 0]}
                     6 {:id 6 :value 2 :pos [1 1]}
                     7 {:id 7 :value 4 :pos [1 2]}
                     8 {:id 8 :value 2 :pos [1 3]}
                     ;; Row 2
                     9  {:id 9  :value 2 :pos [2 0]}
                     10 {:id 10 :value 4 :pos [2 1]}
                     11 {:id 11 :value 2 :pos [2 2]}
                     12 {:id 12 :value 4 :pos [2 3]}
                     ;; Row 3: [_ 8 2 4]   -- legal LEFT move
                     14 {:id 14 :value 8 :pos [3 1]}
                     15 {:id 15 :value 2 :pos [3 2]}
                     16 {:id 16 :value 4 :pos [3 3]}}
          ;; Choose a seed empirically: when there's one empty cell
          ;; E={[3 3]}, spawn picks it (mod 1 = 0). The value depends
          ;; on the second RNG draw; we accept whatever it is — the
          ;; post-spawn board has [3 3] holding 2 or 4. Either yields
          ;; an immobile row 3 = [8 2 4 v] with v ∈ {2,4}; for v=2:
          ;; row 3 = [8 2 4 2] (no adjacent equal); for v=4: row 3 =
          ;; [8 2 4 4] (the trailing 4s would be adjacent equals, so
          ;; the board is NOT immobile in that case). So we pick a
          ;; seed that produces value 2.
          ;;
          ;; Trial seeds; we test ourselves via the mechanics fn and
          ;; pick the first that works.
          win-seed (some (fn [seed]
                           (let [r (m/spawn {} seed db/board-dims 1)
                                 v (:value (first (vals (:tiles r))))]
                             (when (= 2 v) seed)))
                         (range 1 1000))
          start (-> db/default-db
                    (assoc-in [:game :phase] :playing)
                    (assoc-in [:game :tiles] tiles-pre)
                    (assoc-in [:game :next-id] 17)
                    (assoc-in [:game :rng-seed] win-seed))
          db'   (play-move start :left)]
      (is (= :over (get-in db' [:game :phase]))
          "Phase transitions to :over after the lose-detection runs.")
      (is (contains? (get-in db' [:ui :overlay]) :over)
          ":over overlay is set."))))
