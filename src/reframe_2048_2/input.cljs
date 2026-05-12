(ns reframe-2048-2.input
  "Browser keyboard adapter per spec §6.1.

   Scope (bead reframe-2048-z0b): the `window`-level `keydown`
   listener that converts DOM events to `:input/key-down` events. The
   substantive key-to-event mapping lives in `events.cljs` because it
   needs phase + overlay state to gate Enter/c/C and Escape; this
   namespace is the thin DOM-side wrapper.

   `preventDefault` per spec §6.1 — arrow keys MUST NOT scroll the
   page. We also call it for w/a/s/d/n in case the host page binds
   them; the cost is zero on keys we own.

   Wired from `core.cljs` at boot. Idempotent: `attach!` installs the
   listener exactly once (we guard with a `defonce` atom)."
  (:require [re-frame.core :as rf]))

;; -- The key set we own ------------------------------------------------------
;;
;; Mirrors the §6.1 table. We use this twice:
;;   (a) to decide whether to preventDefault on a keydown (so we don't
;;       eat unrelated keypresses like Tab, F5, ⌘+R);
;;   (b) as a sanity reference for tests.
;;
;; Per spec §6.1 the value-set of the `:input/key-down` payload is
;; \"exactly the keys named in §6.1\". We let unrecognised keys
;; through to the event handler anyway (which silent-no-ops on them
;; per §6.1) so the trace bus sees every keypress that reached the
;; listener — useful for testing the §6.3 drop-during-animation
;; behaviour.

(def owned-keys
  "Keys the listener intercepts (preventDefaults + dispatches).
   Spec §6.1, including vim-key exclusion per NG9."
  #{"ArrowUp" "ArrowDown" "ArrowLeft" "ArrowRight"
    "w" "W" "a" "A" "s" "S" "d" "D"
    "n" "N"
    "Enter" "c" "C"
    "Escape"})

(defn- handle-keydown
  "Build the re-frame event from a raw DOM `KeyboardEvent` and
   dispatch it. Returns `true` iff the key was owned (caller uses
   the return value to decide preventDefault)."
  [^js dom-event]
  (let [k (.-key dom-event)]
    (when (contains? owned-keys k)
      ;; Spec §6.1: arrow keys MUST preventDefault to suppress page
      ;; scroll. We extend the same to the other owned keys for
      ;; consistency.
      (.preventDefault dom-event))
    (rf/dispatch [:input/key-down {:key k}])
    (contains? owned-keys k)))

(defonce ^:private listener-installed?
  (atom false))

(defn attach!
  "Install the window-level `keydown` listener. Idempotent — repeat
   calls are no-ops, so the function is safe to call from
   `:after-load` hot-reload hooks."
  []
  (when (and (exists? js/window) (not @listener-installed?))
    (.addEventListener js/window "keydown" handle-keydown)
    (reset! listener-installed? true)
    nil))
