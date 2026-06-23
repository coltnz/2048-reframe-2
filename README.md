# 2048-reframe-2

Try it here: https://coltnz.github.io/2048-reframe-2/

A 2048 sliding-tile puzzle built on **[re-frame2](https://github.com/day8/re-frame2)** (alpha) + **Reagent**.

<img width="716" height="757" alt="Screenshot 2026-06-23 at 1 29 46 PM" src="https://github.com/user-attachments/assets/3fe9facc-9b00-41fb-bd61-d30c582efdef" />


## Prerequisites

- **Node** 18+ (we test on 20).
- **Java** 11+ (17 in CI) — required by `shadow-cljs` / Clojure CLI.
- **Clojure CLI** (`brew install clojure/tools/clojure` on macOS).

## Build and run

```bash
# One-time
npm install

# Dev: hot-reloading watch + dev HTTP server on :8080
npx shadow-cljs watch app
# → open http://localhost:8080 — you should see "Hello, 2048-reframe-2"

# One-shot compile (matches what CI runs)
npx shadow-cljs compile app

# Production release bundle
npx shadow-cljs release app
```

The `:app` build emits `public/js/main.js`, which `public/index.html`
loads at the root path.

## re-frame2 dependency

re-frame2 is **alpha** and not yet on Clojars. `deps.edn` pulls both the
core artefact and the Reagent adapter directly from the
`day8/re-frame2` monorepo at a pinned SHA. The current pin is recorded
inline in `deps.edn`; to bump, change both `:git/sha` values to the
same new commit and re-run `clojure -P && npx shadow-cljs compile app`.
