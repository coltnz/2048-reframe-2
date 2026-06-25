(ns reframe-2048-2.db
  "Initial `app-db` value and per-frame schema registration.

   Scope (bead reframe-2048-z0b): one canonical `default-db` matching
   spec §5.1's literal shape — `:fresh` phase, empty tiles, empty
   animation queues — plus `register-schemas!` which attaches
   `schemas/AppDb` against the active frame's root path. The
   registration uses `re-frame.schemas/reg-app-schema` so dev builds
   validate every handler's returned `app-db` against the schema;
   production builds elide the check via `goog.DEBUG=false` per
   spec §5.2."
  (:require [re-frame.core    :as rf]
            [re-frame.schemas :as schemas]
            [reframe-2048-2.schemas :as s]))

;; -- Constants ---------------------------------------------------------------

(def board-dims
  "Spec §3.1 / §5.1: the v1 board is 4×4. Centralised here so views,
   subs, and event handlers all read the SAME dims and we never
   hard-code `4` outside this constant per spec §3.1."
  [4 4])

(def instructions-hidden-storage-key
  "localStorage key for the footer-instructions visibility preference
   (§7.1 / §8.1). Read on boot by `:app/initialise`, written on every
   `:ui/toggle-instructions`. Defined here (not core.cljs) so both the
   boot reader and the toggle writer share one literal."
  "reframe-2048-2/instructions-hidden-v1")

;; -- Initial db --------------------------------------------------------------
;;
;; The `:rng-seed` here is the *boot* seed; the first `:game/new` event
;; replaces it with a wall-clock-derived seed (`Date.now()`) — or in
;; tests, the fixture-injected seed. xorshift32 cannot leave the
;; all-zero state, so the initial 1 is the smallest safe constant.

(def default-db
  "Spec §5.1 verbatim:

     {:game  {:board-dims [4 4]
              :phase      :fresh
              :score      0
              :best-score 0
              :tiles      {}
              :next-id    1
              :rng-seed   1}
      :ui    {:overlay   #{}
              :animation {:slides [] :merges [] :spawns []}}
      :input {}}

   `:phase :fresh` is the boot state only; the FSM advances to
   `:playing` on the first `:game/new` per spec §4.8 (D-05 single-step
   audit fix).

   `:ui.instructions-hidden?` is an impl UI affordance beyond the §5.1
   verbatim shape (footer toggle, §8.1); it defaults to shown (false)
   and is overwritten on boot from localStorage (§7.1)."
  {:game  {:board-dims board-dims
           :phase      :fresh
           :score      0
           :best-score 0
           :tiles      {}
           :next-id    1
           :rng-seed   1}
   :ui    {:overlay              #{}
           :instructions-hidden? false
           :animation {:slides  []
                       :merges  []
                       :spawns  []}}
   :input {}})

;; -- Schema registration -----------------------------------------------------

(defn register-schemas!
  "Register the root `AppDb` Malli schema against the active frame.
   Idempotent: the schemas artefact treats a second registration as a
   surgical update of the same path. Called from core.cljs at boot,
   inside the `:game` frame's `frame-provider` context — so the
   registration lands against `:game`, not `:rf/default`."
  []
  (schemas/reg-app-schema [] s/AppDb))
