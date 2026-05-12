(ns reframe-2048-2.fsm-test
  "Spec §4.8 FSM transitions table verbatim, plus the
   'Ignored (state, event) pairs' enumeration. Driven directly
   against `fsm/next-phase` so the tests are pure-data and don't
   require the re-frame2 machines artefact to be running."
  (:require [cljs.test :refer-macros [deftest is testing]]
            [reframe-2048-2.fsm :as fsm]))

;; -- Convenience -------------------------------------------------------------

(defn- step
  "Sugar over `fsm/next-phase`."
  ([state event] (fsm/next-phase state event {}))
  ([state event outcome] (fsm/next-phase state event outcome)))

;; -- §4.8 transitions table — exhaustive (one test per row) ------------------

(deftest fresh-game-new
  (is (= {:phase :playing :transition? true}
         (step :fresh :game/new))))

(deftest playing-game-move-won
  (testing "Successful move producing 2048 → :won"
    (is (= {:phase :won :transition? true}
           (step :playing :game/move {:moved? true :won? true :lost? false})))))

(deftest playing-game-move-over
  (testing "Successful move with no 2048 and no legal post-spawn → :over"
    (is (= {:phase :over :transition? true}
           (step :playing :game/move {:moved? true :won? false :lost? true})))))

(deftest playing-game-move-continues
  (testing "Successful move that doesn't win or lose → :playing"
    (is (= {:phase :playing :transition? true}
           (step :playing :game/move {:moved? true :won? false :lost? false})))))

(deftest playing-game-move-unsuccessful
  (testing "Unsuccessful move → :playing (no phase change)"
    (is (= {:phase :playing :transition? true}
           (step :playing :game/move {:moved? false :won? false :lost? false})))))

(deftest playing-game-new
  (is (= {:phase :playing :transition? true}
         (step :playing :game/new))))

(deftest won-game-continue
  (is (= {:phase :continuing :transition? true}
         (step :won :game/continue))))

(deftest won-game-new
  (is (= {:phase :playing :transition? true}
         (step :won :game/new))))

(deftest continuing-game-move-lost
  (is (= {:phase :over :transition? true}
         (step :continuing :game/move
               {:moved? true :won? false :lost? true}))))

(deftest continuing-game-move-still-continuing
  (is (= {:phase :continuing :transition? true}
         (step :continuing :game/move
               {:moved? true :won? false :lost? false}))))

(deftest continuing-game-move-unsuccessful
  (is (= {:phase :continuing :transition? true}
         (step :continuing :game/move
               {:moved? false :won? false :lost? false}))))

(deftest continuing-game-new
  (is (= {:phase :playing :transition? true}
         (step :continuing :game/new))))

(deftest over-game-new
  (is (= {:phase :playing :transition? true}
         (step :over :game/new))))

(deftest over-dismiss-stays-over
  (testing "Spec §4.8: :over + :game/dismiss-over → :over (phase unchanged; overlay clears in handler)"
    (is (= {:phase :over :transition? true}
           (step :over :game/dismiss-over)))))

;; -- 'Ignored (state, event) pairs' rule (§4.8) ------------------------------
;;
;; Every (state, event) pair not in the transitions table MUST be a
;; no-op at the FSM layer. We assert :transition? false on each named
;; pair from the spec's enumerative list (§4.8 prose).

(deftest fresh-ignored-pairs
  (testing ":fresh + non-:game/new events are Ignored"
    (doseq [ev [:game/move :game/continue :game/dismiss-over]]
      (let [{:keys [phase transition?]} (step :fresh ev)]
        (is (= :fresh phase) (str ":fresh + " ev " phase echo"))
        (is (false? transition?) (str ":fresh + " ev " ignored"))))))

(deftest won-ignored-pairs
  (testing ":won + :game/move / :game/dismiss-over are Ignored"
    (doseq [ev [:game/move :game/dismiss-over]]
      (let [{:keys [phase transition?]} (step :won ev)]
        (is (= :won phase))
        (is (false? transition?))))))

(deftest over-ignored-pairs
  (testing ":over + :game/move / :game/continue are Ignored"
    (doseq [ev [:game/move :game/continue]]
      (let [{:keys [phase transition?]} (step :over ev)]
        (is (= :over phase))
        (is (false? transition?))))))

(deftest playing-ignored-pairs
  (testing ":playing + :game/continue / :game/dismiss-over are Ignored"
    (doseq [ev [:game/continue :game/dismiss-over]]
      (let [{:keys [phase transition?]} (step :playing ev)]
        (is (= :playing phase))
        (is (false? transition?))))))

(deftest continuing-ignored-pairs
  (testing ":continuing + :game/continue / :game/dismiss-over are Ignored"
    (doseq [ev [:game/continue :game/dismiss-over]]
      (let [{:keys [phase transition?]} (step :continuing ev)]
        (is (= :continuing phase))
        (is (false? transition?))))))

;; -- D-05 audit fix: :game/new from any state is single-step ---------------

(deftest game-new-single-step-from-any-state
  (testing "Spec §4.8 D-05: :game/new goes :playing in one step from every state"
    (doseq [from [:fresh :playing :won :continuing :over]]
      (is (= {:phase :playing :transition? true}
             (step from :game/new))
          (str ":game/new from " from " is single-step")))))

;; -- legal-event? sanity -----------------------------------------------------

(deftest legal-event-recognises-table-rows
  (is (fsm/legal-event? :playing :game/move))
  (is (fsm/legal-event? :won :game/continue))
  (is (fsm/legal-event? :over :game/new))
  (is (not (fsm/legal-event? :fresh :game/move)))
  (is (not (fsm/legal-event? :won :game/move))))
