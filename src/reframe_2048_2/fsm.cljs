(ns reframe-2048-2.fsm
  "Game-lifecycle finite state machine per spec §4.8.

   Scope (bead reframe-2048-z0b): the §4.8 transitions table as a
   pure data spec, exposed as both:

   - `transition-table` — a `{state {event-id (fn [outcome] target-state)}}`
     lookup the `:game/move` handler consults to decide whether to
     advance to `:won`, `:over`, or stay in `:playing`/`:continuing`.
   - `:game/fsm` — a `reg-machine` registration whose `:on` slots
     mirror the table. The machine is a documentation / conformance
     artefact: the *substantive* phase advance still happens in the
     `reg-event-fx` handlers (events.cljs) because they have access
     to the mechanics' lose-detection / 2048-detection results that
     pure-data guards cannot see without lifting the whole slide into
     `:data`.

   The 'Ignored (state, event) pairs' rule from §4.8 is the same data
   the table encodes: any `(state, event)` not present in the table
   MUST be a no-op. The `next-phase` fn implements that.

   D-05 audit fix (spec v0.3): `:game/new` from any state goes
   *directly* to `:playing` in a single step. `:fresh` is the boot
   state only; once the first `:game/new` fires, the FSM does not
   return to `:fresh`."
  (:require [re-frame.core   :as rf]
            [re-frame.machines]))

;; -- Transition logic --------------------------------------------------------
;;
;; `:game/move` is the only event whose target depends on a runtime
;; outcome (2048 produced? lose-detected post-spawn?). The `outcome`
;; map carries those flags so the table stays a pure data lookup; the
;; mechanics layer (mechanics.cljs) is the producer.
;;
;; Outcome map shape:
;;   {:moved? boolean         ;; spec §3.5 successful-move flag
;;    :won? boolean           ;; spec §3.7 — 2048 produced this slide
;;    :lost? boolean}         ;; spec §3.8 — no legal move after spawn
;;
;; For events without an outcome (e.g. `:game/new`, `:game/continue`),
;; pass an empty map.

(def transition-table
  "Spec §4.8 transitions table, verbatim. Each cell is either a
   keyword target state or a fn `(outcome -> target-state)`. Missing
   entries are no-ops per the 'Ignored (state, event) pairs' rule."
  {:fresh      {:game/new      :playing}

   :playing    {:game/move
                (fn [{:keys [moved? won? lost?]}]
                  (cond
                    (not moved?)             :playing
                    won?                     :won
                    lost?                    :over
                    :else                    :playing))
                :game/new      :playing}

   :won        {:game/continue :continuing
                :game/new      :playing}

   :continuing {:game/move
                (fn [{:keys [moved? lost?]}]
                  (cond
                    (not moved?) :continuing
                    lost?        :over
                    :else        :continuing))
                :game/new      :playing}

   :over       {:game/new          :playing
                ;; Phase unchanged; the UI overlay clears in the
                ;; handler. We register the row anyway so an
                ;; outside reader sees that the event is NOT
                ;; ignored — it is consumed and the overlay is
                ;; mutated, just not the phase.
                :game/dismiss-over :over}})

(defn next-phase
  "Compute the next phase per the §4.8 transitions table.

   Returns:
     {:phase target-phase :transition? boolean}

   `:transition? false` means the (state, event) pair is on the
   'Ignored' list — caller MUST treat it as a no-op (no db change,
   no fx). `:phase` echoes the current phase in that case so
   callers can `assoc-in [:game :phase]` unconditionally.

   `outcome` carries `:moved? :won? :lost?` for `:game/move`; pass
   `{}` for the no-outcome events."
  ([state event-id] (next-phase state event-id {}))
  ([state event-id outcome]
   (let [row    (get transition-table state)
         target (get row event-id)]
     (cond
       (nil? target)
       {:phase state :transition? false}

       (fn? target)
       {:phase (target outcome) :transition? true}

       :else
       {:phase target :transition? true}))))

(defn legal-event?
  "True iff `event-id` is listed under `state` in the §4.8 table — i.e.
   the event would cause some transition or documented-no-op. Used by
   the input layer for diagnostic-only purposes; the actual gating is
   the `:input/key-down` table per §6.1."
  [state event-id]
  (some? (get-in transition-table [state event-id])))

;; -- reg-machine: the documentation / conformance artefact -------------------
;;
;; Per spec §4.8 the game lifecycle MUST be modelled as a re-frame2
;; reg-machine. The machine below mirrors `transition-table` 1:1; its
;; `:state` slot lives at `[:rf/machines :game/fsm :state]` and is
;; kept in lock-step with `[:game :phase]` by the event handlers.
;;
;; Why both? The handlers in events.cljs need mechanics-aware
;; transition logic that pure guard fns can express but can't easily
;; share with the substantive slide-and-spawn pipeline. Computing the
;; phase from the same lookup the machine uses (via `next-phase`)
;; keeps the two surfaces consistent without duplicating the
;; transition table.
;;
;; The machine accepts the same event-ids the events.cljs handlers
;; do; dispatching `[:game/fsm [:game/move {:dir :up}]]` runs through
;; the machine. The events.cljs `:game/move` handler dispatches into
;; the machine *after* it has computed the slide so the snapshot at
;; `[:rf/machines :game/fsm]` reflects the post-handler phase. (See
;; events.cljs for the wiring.)

(def game-fsm-spec
  {:initial :fresh
   :data    {}
   :states
   {:fresh
    {:on {:game/new  {:target :playing}}}

    :playing
    {:on {:game/won  {:target :won}
          :game/over {:target :over}
          :game/new  {:target :playing}}}

    :won
    {:on {:game/continue {:target :continuing}
          :game/new      {:target :playing}}}

    :continuing
    {:on {:game/over {:target :over}
          :game/new  {:target :playing}}}

    :over
    {:on {:game/new          {:target :playing}
          ;; Self-transition for documentation only; the overlay
          ;; clear is the events.cljs handler's job. Per spec
          ;; §4.8 the phase MUST remain `:over`.
          :game/dismiss-over {:target :over}}}}})

(defn register!
  "Register the `:game/fsm` machine. Called from `core.cljs` at boot
   inside the `:game` frame's frame-provider context. Idempotent:
   re-registration is a surgical update of the machine spec (per
   Spec 005 §reg-machine + re-frame2 hot-reload guarantees)."
  []
  (rf/reg-machine :game/fsm game-fsm-spec))
