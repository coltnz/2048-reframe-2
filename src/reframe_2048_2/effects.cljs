(ns reframe-2048-2.effects
  "re-frame2 `reg-fx` effects per spec §4.5.

   Scope (bead reframe-2048-z0b): four effect handlers —
   `:fx/storage-write`, `:fx/storage-read`, `:fx/focus`,
   `:fx/announce` — implementing spec §4.5 and the §7.1
   localStorage-unavailable fallback. All four MUST live behind
   `reg-fx` so the impl never DOM-mutates outside the effect pipeline
   (Spec 002 §Effect grammar).

   Failure mode (§7.1): `localStorage.setItem` / `getItem` can throw
   `SecurityError` (Safari Private mode quota) or `QuotaExceededError`
   (storage full). Both are swallowed, logged once via
   `js/console.info`, and the call returns silently. We MUST NOT raise
   to the user.

   The `:fx/storage-read` handler dispatches `:on-success` with the
   parsed value appended as the last positional arg — `[event-id
   parsed-value]`. On any failure path (missing key, parse error,
   browser exception) the value is `nil`. Callers MUST tolerate a nil
   payload; see `events/storage-loaded` (§7.1) which initialises
   best-score to 0 on nil."
  (:require [re-frame.core :as rf]
            [reframe-2048-2.schemas :as s]))

;; -- Helpers -----------------------------------------------------------------

(defn- safe-local-storage
  "Return `js/window.localStorage` or `nil`. The bare property read
   itself can throw in cookies-disabled / restricted-contexts mode on
   some browsers, so we wrap in try/catch."
  []
  (try
    (when (and (exists? js/window) (.-localStorage js/window))
      (.-localStorage js/window))
    (catch :default _ nil)))

(defn- log-storage-failure!
  "Single-line info-level log per spec §7.1 ('SHOULD log the failure
   once at info level'). We use `console.info` rather than dispatching
   `:fx/announce` here to avoid recursion if the announce path itself
   touches storage in some future revision; spec says either is fine."
  [op key exception]
  (try
    (js/console.info
      (str "[reframe-2048-2] localStorage " op
           " failed for key " (pr-str key)
           " (" (some-> exception .-name) "); proceeding without persistence."))
    (catch :default _ nil)))

;; -- :fx/storage-write -------------------------------------------------------

(rf/reg-fx :fx/storage-write
  {:doc  "Persist `{:key, :value}` to localStorage (§4.5 / §7.1).
          JSON-stringified. Swallows browser exceptions and logs once;
          MUST NOT raise."
   :spec s/Fx-StorageWrite}
  (fn handler-storage-write [_ctx {:keys [key value]}]
    (when-let [ls (safe-local-storage)]
      (try
        (.setItem ls key (js/JSON.stringify (clj->js value)))
        (catch :default e
          (log-storage-failure! "setItem" key e))))))

;; -- :fx/storage-read --------------------------------------------------------

(rf/reg-fx :fx/storage-read
  {:doc  "Read `{:key}` from localStorage (§4.5 / §7.1). Dispatches
          `:on-success` with the JSON-parsed value appended as the
          last positional arg. On missing key / parse error / browser
          exception: dispatches `:on-success` with `nil`."
   :spec s/Fx-StorageRead}
  (fn handler-storage-read [_ctx {:keys [key on-success]}]
    (let [parsed
          (try
            (when-let [ls (safe-local-storage)]
              (when-let [raw (.getItem ls key)]
                (let [v (js/JSON.parse raw)]
                  ;; `JSON.parse` returns a `js/Number`/`js/Boolean`/...
                  ;; for primitives; we want the primitive for our
                  ;; BestScore int. `js->clj` handles both literal
                  ;; primitives and JS objects.
                  (js->clj v :keywordize-keys true))))
            (catch :default e
              (log-storage-failure! "getItem" key e)
              nil))]
      (rf/dispatch [on-success parsed]))))

;; -- :fx/focus ---------------------------------------------------------------

(rf/reg-fx :fx/focus
  {:doc  "Focus the element matching `:selector`. v1 is a stub —
          accessibility wiring is a post-demo bead (§8.4). We still
          register the fx-id so the event grammar is observable in
          the trace bus and so views can fire it without raising."
   :spec s/Fx-Focus}
  (fn handler-focus [_ctx {:keys [selector]}]
    (try
      (when (and (exists? js/document) (some? selector))
        (when-let [el (.querySelector js/document selector)]
          (.focus el)))
      (catch :default _ nil))))

;; -- :fx/announce ------------------------------------------------------------

;; Spec §8.4: the ARIA live region's DOM id, rendered by
;; `reframe-2048-2.views.app/app-view`. The handler writes the message
;; into the element's text content; screen readers announce the new
;; text because the element has `aria-live='polite'`.
;;
;; We DOM-query each call rather than caching the element handle so the
;; handler stays robust across hot-reload (which can re-create the node).
(def ^:private a11y-live-id "a11y-live")

(rf/reg-fx :fx/announce
  {:doc  "Push `:message` into the ARIA live region (§8.4). Writes the
          message into the `#a11y-live` element rendered by `app-view`.
          Falls back to `console.info` if the element isn't mounted
          (e.g. during boot before the first render, or in non-browser
          test contexts). Idempotent: writing the same string twice in
          a row is a screen-reader no-op."
   :spec s/Fx-Announce}
  (fn handler-announce [_ctx {:keys [message]}]
    (try
      ;; Mirror to the console for debugging / tests / trace consumers
      ;; that don't have a DOM at hand.
      (js/console.info (str "[a11y/announce] " message))
      (when (exists? js/document)
        (when-let [el (.getElementById js/document a11y-live-id)]
          ;; Clear then write so the screen-reader announces even when
          ;; the same message repeats. The double-write is debounced
          ;; by the browser; no flicker visible because the element is
          ;; visually-hidden.
          (set! (.-textContent el) "")
          (set! (.-textContent el) message)))
      (catch :default _ nil))))
