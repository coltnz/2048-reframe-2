(ns reframe-2048-2.events-test
  "Event-handler tests per spec §9.2 ('Each `reg-event-*` MUST have ≥ 1
   test that dispatches the event into a synthetic db').

   We drive the handlers directly as fns rather than dispatching through
   the router so the tests are deterministic and don't depend on the
   schemas / machines / Reagent adapter being initialised. Each test
   looks up the handler's `:handler-fn` via `re-frame.core/handler-meta`
   (which the events ns registers under `:event/handler-fn`)."
  (:require [cljs.test :refer-macros [deftest is testing use-fixtures]]
            [re-frame.core    :as rf]
            [re-frame.registrar :as registrar]
            ;; Side-effecting require to ensure the events / fxs are
            ;; registered before the suite runs.
            [reframe-2048-2.events]
            [reframe-2048-2.db        :as db]
            [reframe-2048-2.mechanics :as m]
            [reframe-2048-2.schemas   :as s]))

;; -- Test helpers ------------------------------------------------------------

(defn- handler
  "Resolve a registered event handler's `:handler-fn`. Works for both
   `reg-event-db` and `reg-event-fx`; the test caller distinguishes
   by calling shape."
  [event-id]
  (or (:handler-fn (registrar/lookup :event event-id))
      (throw (ex-info "No handler registered" {:event-id event-id}))))

(defn- with-tiles
  "Build a synthetic app-db starting from the spec §5.1 default and
   override the tiles / next-id / rng-seed / phase. Useful for
   exercising specific cases."
  [overrides]
  (-> db/default-db
      (update :game merge overrides)
      ;; Move out of :fresh so :game/move isn't ignored.
      (update-in [:game :phase] #(or (:phase overrides) %))))

;; -- :game/new ---------------------------------------------------------------

(deftest game-new-from-fresh
  (testing ":game/new from :fresh advances to :playing with two spawns (§3.3 / §4.8 D-05)"
    (let [h     (handler :game/new)
          {:keys [db]} (h {:db db/default-db} [:game/new])]
      (is (= :playing (get-in db [:game :phase]))
          "Single-step transition from :fresh → :playing per D-05.")
      (is (= 2 (count (get-in db [:game :tiles])))
          "Exactly two starting tiles per spec §3.3.")
      (is (= 0 (get-in db [:game :score])))
      (is (zero? (count (get-in db [:ui :overlay])))
          ":ui.overlay cleared on :game/new.")
      (is (= 2 (count (get-in db [:ui :animation :spawns])))
          "Both new tiles populate the spawn-animation queue.")
      (is (every? #(contains? #{2 4} (:value %))
                  (vals (get-in db [:game :tiles])))
          "Spawn values are 2 or 4 per spec §3.4."))))

(deftest game-new-from-over
  (testing ":game/new from :over single-steps to :playing (§4.8 D-05)"
    (let [h     (handler :game/new)
          start (-> db/default-db
                    (assoc-in [:game :phase] :over)
                    (assoc-in [:game :score] 999)
                    (assoc-in [:game :best-score] 42)
                    (assoc-in [:ui :overlay] #{:over}))
          {:keys [db]} (h {:db start} [:game/new])]
      (is (= :playing (get-in db [:game :phase]))
          ":over → :playing in one step.")
      (is (= 0 (get-in db [:game :score]))
          "Score resets to 0.")
      (is (= 42 (get-in db [:game :best-score]))
          "Best score preserved (§7.1 — best is per-device).")
      (is (= #{} (get-in db [:ui :overlay]))
          ":over overlay cleared."))))

;; -- :game/move --------------------------------------------------------------

(deftest game-move-successful-on-2222-left
  (testing "Successful :left move on [2 2 2 2] increments score by 8 and spawns one tile"
    (let [h     (handler :game/move)
          tiles {1 {:id 1 :value 2 :pos [0 0]}
                 2 {:id 2 :value 2 :pos [0 1]}
                 3 {:id 3 :value 2 :pos [0 2]}
                 4 {:id 4 :value 2 :pos [0 3]}}
          start (-> db/default-db
                    (assoc-in [:game :phase]    :playing)
                    (assoc-in [:game :tiles]    tiles)
                    (assoc-in [:game :next-id]  5)
                    (assoc-in [:game :rng-seed] 12345))
          {:keys [db]} (h {:db start} [:game/move {:dir :left}])]
      (is (= 8 (get-in db [:game :score]))
          "Score += 8 (two 2+2 merges).")
      (is (= :playing (get-in db [:game :phase]))
          "Still playing — no 2048, board not full.")
      ;; Post-spawn: 2 merge-produced 4s + 1 spawn = 3 tiles
      (is (= 3 (count (get-in db [:game :tiles])))
          "Post-slide 2 tiles + 1 spawn = 3 tiles.")
      (is (pos? (count (get-in db [:ui :animation :slides])))
          "Slide animation queue populated.")
      (is (= 2 (count (get-in db [:ui :animation :merges])))
          "Two merge animations queued.")
      (is (= 1 (count (get-in db [:ui :animation :spawns])))
          "One spawn animation queued."))))

(deftest game-move-unsuccessful-is-noop
  (testing "An unsuccessful move makes no app-db changes (§3.5)"
    (let [h     (handler :game/move)
          tiles {1 {:id 1 :value 2 :pos [0 0]}}
          start (-> db/default-db
                    (assoc-in [:game :phase]    :playing)
                    (assoc-in [:game :tiles]    tiles)
                    (assoc-in [:game :next-id]  2)
                    (assoc-in [:game :rng-seed] 12345))
          {:keys [db]} (h {:db start} [:game/move {:dir :left}])]
      (is (= start db)
          "Unsuccessful move: db unchanged (no spawn, no score, no phase change)."))))

(deftest game-move-ignored-from-fresh
  (testing ":fresh + :game/move is on the Ignored list — no-op (§4.8)"
    (let [h     (handler :game/move)
          {:keys [db]} (h {:db db/default-db} [:game/move {:dir :up}])]
      (is (= db/default-db db)
          ":fresh + :game/move is a silent no-op."))))

(deftest game-move-wins-on-2048
  (testing "Merging to 2048 from :playing transitions to :won (§3.7 / §4.8)"
    (let [h     (handler :game/move)
          tiles {1 {:id 1 :value 1024 :pos [0 0]}
                 2 {:id 2 :value 1024 :pos [0 1]}}
          start (-> db/default-db
                    (assoc-in [:game :phase]    :playing)
                    (assoc-in [:game :tiles]    tiles)
                    (assoc-in [:game :next-id]  3)
                    (assoc-in [:game :rng-seed] 12345))
          {:keys [db]} (h {:db start} [:game/move {:dir :left}])]
      (is (= :won (get-in db [:game :phase])) "Phase → :won.")
      (is (contains? (get-in db [:ui :overlay]) :won)
          ":won added to overlay."))))

(deftest game-move-no-won-from-continuing
  (testing "Merging to 2048 from :continuing does NOT re-fire :won (§3.9)"
    (let [h     (handler :game/move)
          tiles {1 {:id 1 :value 1024 :pos [0 0]}
                 2 {:id 2 :value 1024 :pos [0 1]}}
          start (-> db/default-db
                    (assoc-in [:game :phase]    :continuing)
                    (assoc-in [:game :tiles]    tiles)
                    (assoc-in [:game :next-id]  3)
                    (assoc-in [:game :rng-seed] 12345))
          {:keys [db]} (h {:db start} [:game/move {:dir :left}])]
      (is (= :continuing (get-in db [:game :phase])) "Phase stays :continuing.")
      (is (not (contains? (get-in db [:ui :overlay]) :won))
          ":won banner is NOT re-surfaced from :continuing."))))

;; -- :game/continue ----------------------------------------------------------

(deftest game-continue-from-won
  (testing ":game/continue advances :won → :continuing and clears :won overlay"
    (let [h     (handler :game/continue)
          start (-> db/default-db
                    (assoc-in [:game :phase] :won)
                    (assoc-in [:ui :overlay] #{:won}))
          {:keys [db]} (h {:db start} [:game/continue])]
      (is (= :continuing (get-in db [:game :phase])))
      (is (not (contains? (get-in db [:ui :overlay]) :won))))))

(deftest game-continue-ignored-from-playing
  (testing ":playing + :game/continue is on the Ignored list — no-op (§4.8)"
    (let [h     (handler :game/continue)
          start (assoc-in db/default-db [:game :phase] :playing)
          {:keys [db]} (h {:db start} [:game/continue])]
      (is (= start db) "Ignored silently."))))

;; -- :game/dismiss-over ------------------------------------------------------

(deftest game-dismiss-over-from-over
  (testing ":game/dismiss-over clears :over overlay; phase unchanged (§4.8)"
    (let [h     (handler :game/dismiss-over)
          start (-> db/default-db
                    (assoc-in [:game :phase] :over)
                    (assoc-in [:ui :overlay] #{:over}))
          {:keys [db]} (h {:db start} [:game/dismiss-over])]
      (is (= :over (get-in db [:game :phase])) "Phase still :over.")
      (is (not (contains? (get-in db [:ui :overlay]) :over))
          ":over banner cleared."))))

(deftest game-dismiss-over-ignored-from-playing
  (testing ":playing + :game/dismiss-over is on the Ignored list — no-op"
    (let [h     (handler :game/dismiss-over)
          start (assoc-in db/default-db [:game :phase] :playing)
          {:keys [db]} (h {:db start} [:game/dismiss-over])]
      (is (= start db)))))

;; -- :input/key-down ---------------------------------------------------------

(deftest input-key-down-arrow-translates
  (testing "ArrowUp dispatches :game/move {:dir :up} via :fx [[:dispatch ...]]"
    (let [h (handler :input/key-down)
          start (assoc-in db/default-db [:game :phase] :playing)
          {:keys [fx]} (h {:db start} [:input/key-down {:key "ArrowUp"}])]
      (is (= [[:dispatch [:game/move {:dir :up}]]] fx)))))

(deftest input-key-down-unknown-is-noop
  (testing "Unrecognised key is a silent no-op (§6.1)"
    (let [h (handler :input/key-down)
          start (assoc-in db/default-db [:game :phase] :playing)
          result (h {:db start} [:input/key-down {:key "F12"}])]
      (is (or (nil? (:fx result)) (empty? (:fx result)))
          "No dispatch fired for unknown key.")
      (is (= start (:db result)) "db unchanged."))))

(deftest input-key-down-dropped-during-animation
  (testing "Keypresses arriving mid-animation are dropped (§6.3)"
    (let [h     (handler :input/key-down)
          start (-> db/default-db
                    (assoc-in [:game :phase] :playing)
                    (assoc-in [:ui :animation :slides]
                              [{:tile-id 1 :from [0 0] :to [0 3]}]))
          result (h {:db start} [:input/key-down {:key "ArrowUp"}])]
      (is (or (nil? (:fx result)) (empty? (:fx result)))
          "No dispatch fired while :sub/animation-busy? would be true.")
      (is (= start (:db result))))))

(deftest input-key-down-continue-gated-on-overlay
  (testing "Enter only dispatches :game/continue when :won is in overlay (§6.1)"
    (let [h     (handler :input/key-down)
          no-overlay (assoc-in db/default-db [:game :phase] :playing)
          with-won   (-> db/default-db
                         (assoc-in [:game :phase] :won)
                         (assoc-in [:ui :overlay] #{:won}))]
      (is (or (nil? (:fx (h {:db no-overlay} [:input/key-down {:key "Enter"}])))
              (empty? (:fx (h {:db no-overlay} [:input/key-down {:key "Enter"}]))))
          "No dispatch when :won not in overlay.")
      (is (= [[:dispatch [:game/continue]]]
             (:fx (h {:db with-won} [:input/key-down {:key "Enter"}])))
          "Dispatches :game/continue when :won is in overlay."))))

;; -- :storage/loaded ---------------------------------------------------------

(deftest storage-loaded-with-value
  (testing ":storage/loaded sets :best-score from the positional payload (§7.1)"
    (let [h (handler :storage/loaded)
          db' (h db/default-db [:storage/loaded 1234])]
      (is (= 1234 (get-in db' [:game :best-score]))))))

(deftest storage-loaded-with-nil
  (testing ":storage/loaded with nil payload defaults to 0 (§7.1)"
    (let [h (handler :storage/loaded)
          db' (h db/default-db [:storage/loaded nil])]
      (is (= 0 (get-in db' [:game :best-score]))))))

;; -- :storage/save -----------------------------------------------------------

(deftest storage-save-emits-storage-write
  (testing ":storage/save fires :fx/storage-write for the current best-score"
    (let [h     (handler :storage/save)
          start (assoc-in db/default-db [:game :best-score] 999)
          {:keys [fx]} (h {:db start} [:storage/save])]
      (is (= 1 (count fx)))
      (is (= :fx/storage-write (ffirst fx)))
      (is (= 999 (:value (second (first fx))))))))

;; -- :ui/animation-finished --------------------------------------------------

(deftest animation-finished-removes-slide
  (testing ":ui/animation-finished {:phase :slide :tile-id N} removes the entry"
    (let [h     (handler :ui/animation-finished)
          start (-> db/default-db
                    (assoc-in [:ui :animation :slides]
                              [{:tile-id 1 :from [0 0] :to [0 3]}
                               {:tile-id 2 :from [0 1] :to [0 2]}]))
          db' (h start [:ui/animation-finished {:phase :slide :tile-id 1}])]
      (is (= 1 (count (get-in db' [:ui :animation :slides]))))
      (is (= 2 (:tile-id (first (get-in db' [:ui :animation :slides]))))))))

(deftest animation-finished-removes-spawn
  (testing ":ui/animation-finished with phase :spawn drains the spawn queue"
    (let [h     (handler :ui/animation-finished)
          start (-> db/default-db
                    (assoc-in [:ui :animation :spawns]
                              [{:tile-id 1} {:tile-id 2}]))
          db' (h start [:ui/animation-finished {:phase :spawn :tile-id 1}])]
      (is (= [{:tile-id 2}] (get-in db' [:ui :animation :spawns]))))))

(deftest animation-finished-idempotent
  (testing "Removing a non-existent entry is a no-op (idempotency for hot-reload)"
    (let [h (handler :ui/animation-finished)
          db' (h db/default-db [:ui/animation-finished {:phase :slide :tile-id 99}])]
      (is (= db/default-db db')))))
