(ns reframe-2048-2.events
  "re-frame2 event handlers per spec §4.4 + §3 game-rules integration.

   Scope (bead reframe-2048-z0b): every event in spec §4.4's table —
   `:game/new`, `:game/move`, `:game/continue`, `:game/dismiss-over`,
   `:input/key-down`, `:storage/loaded`, `:storage/save`,
   `:ui/animation-finished` — wired through `reg-event-db` /
   `reg-event-fx` and validated via the `:spec` metadata in dev
   (`re-frame.schemas/validate-event!`).

   Integration with `mechanics.cljs` (PR #2): the `:game/move`
   handler is the substantive integration point. It calls

     mechanics/slide     — produces tiles' new positions + merges + slide events
     mechanics/spawn     — places a new tile (consumes RNG twice)
     mechanics/won-after-move? — detects 2048 produced this slide
     mechanics/game-over? — checks for lose post-spawn

   and folds the resulting `{:tiles :score :rng-seed :next-id}` plus
   the animation queues into `app-db`. The FSM transition table
   (`fsm/next-phase`) decides the target phase given the outcome.

   FSM coupling: every transition runs through `fsm/next-phase` so
   the §4.8 table is the single source of truth for `[:game :phase]`.
   The Ignored (state, event) rule is enforced by `next-phase`
   returning `:transition? false`; we honour that with an unchanged
   `app-db` (no fx) for the relevant handlers."
  (:require [re-frame.core    :as rf]
            [reframe-2048-2.db        :as db]
            [reframe-2048-2.fsm       :as fsm]
            [reframe-2048-2.mechanics :as m]
            [reframe-2048-2.schemas   :as s]))

;; -- Helpers -----------------------------------------------------------------

(defn- now-seed
  "Wall-clock millis as the default `:game/new` seed (§5.3). xorshift32
   rejects 0, so we OR-in 1 to guard against the (vanishingly rare)
   midnight-epoch case."
  []
  (let [t (long (.getTime (js/Date.)))]
    (if (zero? t) 1 t)))

(defn- two-fresh-spawns
  "Place the two starting tiles per spec §3.3 / §3.4. Returns the
   `:game` slice with `:tiles`, `:next-id`, `:rng-seed` advanced and
   the `:ui.animation.spawns` queue populated.

   Returns `{:game game-map' :spawn-queue [{:tile-id ...}{:tile-id ...}]}`."
  [seed]
  (let [s1 (m/spawn {} seed db/board-dims 1)
        ;; `mechanics/spawn` advances the seed twice per call regardless
        ;; of whether a tile was placed; we use the advanced seed for
        ;; the second placement.
        s2 (m/spawn (:tiles s1) (:seed s1) db/board-dims (:next-id s1))
        spawns (into (:spawns s1) (:spawns s2))]
    {:tiles      (:tiles s2)
     :seed       (:seed s2)
     :next-id    (:next-id s2)
     :spawn-queue spawns}))

(defn- fresh-game-db
  "Compute the fresh-game `app-db` shape used on every `:game/new`.
   Preserves `:best-score` (spec §7.1 — best score is per-device, not
   per-game) and clears everything else under `:game` and `:ui`."
  [db seed]
  (let [{:keys [tiles seed next-id spawn-queue]} (two-fresh-spawns seed)
        best-score (get-in db [:game :best-score] 0)]
    (-> db
        (assoc :game {:board-dims db/board-dims
                      :phase      :playing
                      :score      0
                      :best-score best-score
                      :tiles      tiles
                      :next-id    next-id
                      :rng-seed   seed})
        (assoc :ui   {:overlay   #{}
                      :animation {:slides  []
                                  :merges  []
                                  :spawns  (vec spawn-queue)}})
        (assoc :input {}))))

(defn- save-best-score-fx
  "Compose `:fx/storage-write` for the new best score. Returns a `:fx`
   entry vector suitable for `:fx [...]`. Spec §7.1: persist on every
   score-change such that `score > best-score`."
  [best-score]
  [:fx/storage-write {:key   "reframe-2048-2/best-score-v1"
                      :value best-score}])

;; -- :game/new ---------------------------------------------------------------
;;
;; Spec §4.8 D-05: `:game/new` from any state goes DIRECTLY to
;; `:playing` in a single FSM step. The handler resets `:score`,
;; regenerates `:tiles`, clears `:ui.overlay`, then the transition
;; fires. `:fresh` is the boot state only.
;;
;; We seed the RNG from `Date.now()` unless an explicit seed is
;; provided in the payload — that hook is for tests / fixtures
;; (`:game/new {:rng-seed 12345}`). Spec §5.3 leaves the production
;; seed implementation-defined; wall-clock millis is the canonical
;; choice.

(rf/reg-event-fx :game/new
  {:doc  "Reset the game to a fresh `:playing` phase with two spawn
          tiles. Preserves best-score. Spec §4.4 / §4.8."
   :spec s/Event-GameNew}
  (fn handler-game-new [{:keys [db]} _event]
    (let [seed (now-seed)]
      {:db (fresh-game-db db seed)
       :fx [[:fx/announce {:message "New game."}]]})))

;; -- :game/move --------------------------------------------------------------
;;
;; The substantive integration point with mechanics.cljs. Steps:
;;   1. Phase guard: §4.8 Ignored-pair rule. If the current phase
;;      isn't a :game/move source, no-op.
;;   2. Slide: `mechanics/slide` against the current tile map.
;;   3. If unsuccessful (no position change, no merge), spec §3.5
;;      says: no spawn, no score increment, no phase advance. Bail.
;;   4. Score: increment by the slide's `:score-delta` (§3.6).
;;   5. Spawn: `mechanics/spawn` (consumes RNG twice).
;;   6. Won? Use `won-after-move?` against the merge log (§3.7),
;;      gated on phase (§3.9 — `:won` does NOT re-fire from
;;      `:continuing`).
;;   7. Lost? `game-over?` against the post-spawn tile map (§3.8).
;;   8. Phase: `fsm/next-phase` with the outcome flags.
;;   9. Best score: persist if score > best-score (§7.1).
;;  10. Overlay: add `:won` / `:over` to `:ui.overlay` on the
;;      relevant transitions.
;;  11. Animation queues: append slides/merges/spawns from this turn.

(rf/reg-event-fx :game/move
  {:doc  "Apply a player move. Slide → spawn → lose-detection. Spec
          §3.5, §3.7, §3.8, §4.4, §4.8."
   :spec s/Event-GameMove}
  (fn handler-game-move [{:keys [db]} [_ {:keys [dir]}]]
    (let [{:keys [phase tiles next-id rng-seed score]} (:game db)]
      (cond
        ;; §4.8 Ignored (state, event) — short-circuit; no-op silently.
        (not (or (= phase :playing) (= phase :continuing)))
        {:db db}

        :else
        (let [slide-result (m/slide tiles dir db/board-dims next-id)]
          (cond
            ;; §3.5: unsuccessful move — no spawn, no score, no phase advance.
            (not (:moved? slide-result))
            {:db db}

            :else
            (let [{slid-tiles :tiles
                   slide-delta :score-delta
                   slide-events :slides
                   merge-events :merges
                   slide-next-id :next-id} slide-result

                  ;; §3.7: detect 2048 in this slide, but ONLY from :playing.
                  ;; From :continuing the win-banner is already dismissed
                  ;; (§3.9) and re-firing would resurface it.
                  won? (and (= phase :playing)
                            (m/won-after-move? merge-events slid-tiles))

                  ;; §3.4: spawn exactly one new tile after a successful move.
                  spawn-result (m/spawn slid-tiles rng-seed db/board-dims slide-next-id)
                  {post-spawn-tiles :tiles
                   post-spawn-seed  :seed
                   post-spawn-next  :next-id
                   spawn-queue      :spawns} spawn-result

                  ;; §3.8: lose iff no legal move on the post-spawn board.
                  lost? (m/game-over? post-spawn-tiles db/board-dims)

                  new-score (+ score slide-delta)
                  new-best  (max (get-in db [:game :best-score] 0) new-score)
                  best-changed? (> new-best (get-in db [:game :best-score] 0))

                  {next-phase :phase} (fsm/next-phase phase :game/move
                                                     {:moved? true
                                                      :won?   won?
                                                      :lost?  lost?})

                  new-overlay (cond-> (get-in db [:ui :overlay])
                                won? (conj :won)
                                lost? (conj :over))

                  new-db (-> db
                             (assoc-in [:game :tiles]      post-spawn-tiles)
                             (assoc-in [:game :score]      new-score)
                             (assoc-in [:game :best-score] new-best)
                             (assoc-in [:game :next-id]    post-spawn-next)
                             (assoc-in [:game :rng-seed]   post-spawn-seed)
                             (assoc-in [:game :phase]      next-phase)
                             (assoc-in [:ui :overlay]      new-overlay)
                             (update-in [:ui :animation :slides]
                                        into slide-events)
                             (update-in [:ui :animation :merges]
                                        into merge-events)
                             (update-in [:ui :animation :spawns]
                                        into spawn-queue))

                  base-fx (cond-> []
                            best-changed?
                            (conj (save-best-score-fx new-best))

                            won?
                            (conj [:fx/announce
                                   {:message "You reached 2048! Press C to continue."}])

                            lost?
                            (conj [:fx/announce
                                   {:message (str "Game over. Final score "
                                                  new-score ".")}]))]
              {:db new-db
               :fx base-fx})))))))

;; -- :game/continue ----------------------------------------------------------

(rf/reg-event-fx :game/continue
  {:doc  "Dismiss the win banner and advance `:won → :continuing`.
          §4.4 / §4.8 / §3.9. No-op silently from any other phase
          (§4.8 Ignored-pair rule)."
   :spec s/Event-GameContinue}
  (fn handler-game-continue [{:keys [db]} _event]
    (let [phase (get-in db [:game :phase])
          {next :phase :keys [transition?]}
          (fsm/next-phase phase :game/continue)]
      (if-not transition?
        {:db db}
        {:db (-> db
                 (assoc-in [:game :phase] next)
                 (update-in [:ui :overlay] disj :won))
         :fx [[:fx/announce {:message "Continuing past 2048."}]]}))))

;; -- :game/dismiss-over ------------------------------------------------------

(rf/reg-event-fx :game/dismiss-over
  {:doc  "UI-only — clear `:over` from `:ui.overlay`. Phase is
          unchanged per spec §4.8 (the `:over → :over` self-transition
          row). No-op silently from any non-`:over` phase."
   :spec s/Event-GameDismissOver}
  (fn handler-game-dismiss-over [{:keys [db]} _event]
    (let [phase (get-in db [:game :phase])
          {:keys [transition?]} (fsm/next-phase phase :game/dismiss-over)]
      (if-not transition?
        {:db db}
        {:db (update-in db [:ui :overlay] disj :over)}))))

;; -- :input/key-down ---------------------------------------------------------
;;
;; Translates browser key strings to game events per spec §6.1. Three
;; gates:
;;   - §6.3: drop the event if `:sub/animation-busy?` is true (still
;;           animating). Implemented inline against `:ui.animation`
;;           so we don't have to subscribe inside an event handler.
;;   - §6.1: unrecognised keys are silent no-ops.
;;   - §6.1: phase-aware — `Enter`/`c`/`C` only triggers `:game/continue`
;;           when `:won` is in `:ui.overlay`; `Escape` only triggers
;;           `:game/dismiss-over` when `:over` is in `:ui.overlay`.
;;
;; The adapter lives here rather than in `input.cljs` because its
;; full logic depends on app-db (overlay + animation queues); an
;; input.cljs ns would either be a one-liner or a separate
;; `reg-event-fx` body. We co-locate it with the other events for
;; cohesion; `input.cljs` carries the key-mapping table used here.

(defn- animation-busy? [db]
  (let [{:keys [slides merges spawns]} (get-in db [:ui :animation])]
    (boolean (or (seq slides) (seq merges) (seq spawns)))))

(defn- key->event
  "Map a DOM key string to a re-frame event vector OR `nil` for keys
   that should be silently no-op'd. Phase + overlay aware. Spec §6.1."
  [k {:keys [overlay phase]}]
  (case k
    ("ArrowUp"    "w" "W") [:game/move {:dir :up}]
    ("ArrowDown"  "s" "S") [:game/move {:dir :down}]
    ("ArrowLeft"  "a" "A") [:game/move {:dir :left}]
    ("ArrowRight" "d" "D") [:game/move {:dir :right}]
    ("n" "N")              [:game/new]
    ("Enter" "c" "C")      (when (contains? overlay :won)
                             [:game/continue])
    "Escape"               (when (contains? overlay :over)
                             [:game/dismiss-over])
    ;; Unrecognised key — spec §6.1 mandates a silent no-op.
    nil))

(rf/reg-event-fx :input/key-down
  {:doc  "Translate a DOM key string into a game event. Silent no-op
          if animation is in flight (§6.3) or the key is unmapped
          (§6.1)."
   :spec s/Event-InputKeyDown}
  (fn handler-input-key-down [{:keys [db]} [_ {:keys [key]}]]
    (cond
      ;; §6.3: drop the key if any animation is still in flight.
      (animation-busy? db)
      {:db db}

      :else
      (let [evt (key->event key
                            {:overlay (get-in db [:ui :overlay])
                             :phase   (get-in db [:game :phase])})]
        (if (some? evt)
          {:db db
           :fx [[:dispatch evt]]}
          {:db db})))))

;; -- :storage/loaded ---------------------------------------------------------

(rf/reg-event-db :storage/loaded
  {:doc  "Set `:best-score` from the persisted value (§4.4 / §7.1).
          A nil payload (parse failure, missing key) MUST default to 0
          per spec §7.1. The payload is positional — the generic
          `:fx/storage-read` effect dispatches the parsed value as
          the last arg of the event vector (see §4.5)."
   :spec s/Event-StorageLoaded}
  (fn handler-storage-loaded [db [_ best-score]]
    (assoc-in db [:game :best-score] (or best-score 0))))

;; -- :storage/save -----------------------------------------------------------

(rf/reg-event-fx :storage/save
  {:doc  "Trigger `:fx/storage-write` for the current best score
          (§4.4 / §7.1)."
   :spec s/Event-StorageSave}
  (fn handler-storage-save [{:keys [db]} _event]
    {:fx [(save-best-score-fx (get-in db [:game :best-score]))]}))

;; -- :ui/animation-finished --------------------------------------------------
;;
;; Each animation queue (`:slides`, `:merges`, `:spawns`) is a vector;
;; `:ui/animation-finished` removes the entry whose `:tile-id` matches.
;; Idempotent: removing a non-existent entry is a no-op.

(rf/reg-event-db :ui/animation-finished
  {:doc  "Remove the corresponding entry from `:ui.animation` (§4.4 /
          §5.1)."
   :spec s/Event-UIAnimationFinished}
  (fn handler-animation-finished [db [_ {:keys [phase tile-id]}]]
    (let [path  (case phase
                  :slide  [:ui :animation :slides]
                  :merge  [:ui :animation :merges]
                  :spawn  [:ui :animation :spawns])]
      (update-in db path
                 (fn [entries]
                   (vec (remove #(= tile-id (:tile-id %)) entries)))))))
