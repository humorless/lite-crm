# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Clojure / Babashka

This is a Clojure project with `deps.edn` and `bb.edn`. Load the `clojure-dev` skill before writing any Clojure code.

## Commands

```bash
bb clj-repl        # Start REPL (then run (reset) to boot the system)
bb test            # Run all tests
bb check           # Run fmt + lint + outdated + tests
bb fmt             # Auto-fix formatting (cljfmt)
bb lint            # Run clj-kondo
bb css-watch       # Watch and rebuild TailwindCSS
bb build           # Build production uberjar
```

To run a single test namespace from the REPL:
```clojure
(run-all-tests)    ; defined in dev/user.clj
```

The server starts at `http://localhost:8000`. TailwindCSS is auto-watched in dev mode (started via `integrant-extras.process/process` in `config.dev.edn`).

## REPL (tmux)

The REPL runs in a persistent tmux session named `drepl`. To send a command to it:

```bash
tmux send-keys -t drepl:0 '(reset)' Enter
# then capture output:
tmux capture-pane -t drepl:0 -p
```

If the session is not running, start it with the `drepl` shell alias (opens `bb clj-repl` in a new tmux session).

## Architecture

This is a server-side-rendered web app built on [clojure-stack-lite](https://github.com/abogoyavlensky/clojure-stack-lite).

**System lifecycle** — Managed by [Integrant](https://github.com/weavejester/integrant). Two components:
- `::db/db` — HikariCP connection pool connecting to XTDB 2.x via Postgres wire protocol (port 5432). XTDB must be running (`cd xtdb && docker compose up -d`) before starting the system or tests.
- `::server/server` — Jetty + Reitit router

Config is in `resources/config.edn` using `#profile` reader literals (`:dev`, `:prod`, `:test`). Dev overrides are in `config.dev.edn` via `#merge`/`#include`. The REPL dev namespace (`dev/user.clj`) exposes `(reset)` / `(stop)` to hot-reload the system.

**Request flow**: `routes.clj` → `handlers.clj` → `views.clj`
- Routes are Reitit data maps with Malli coercion
- Handlers call `reitit-extras/render-html` on Hiccup structures returned by views
- Views use `manifest-edn` for cache-busted asset URLs (`manifest/asset`)

**Database queries** use HoneySQL map syntax via two wrappers in `db.clj`:
- `db/exec!` — returns a vector of kebab-cased maps
- `db/exec-one!` — returns a single map

The `db` datasource is injected into handlers via `reitit-extras/wrap-context`, available as `(:db context)` in the request map.

**Tests** are integration tests — they spin up a full system against the running XTDB instance and make real HTTP calls via `clj-http`. Each test file uses `(ig-extras/with-system)` as a `:once` fixture and `test-utils/with-truncated-tables` as a `:each` fixture (deletes all rows from the `user` table between tests). `test-utils/db` and `test-utils/server` are helpers to access the running test system.

**Frontend** — HTMX and Alpine.js are vendored in `resources/public/js/`. Update versions by editing `fetch-assets` in `bb.edn` and running `bb fetch-assets`. CSS is compiled from `resources/public/css/input.css` via TailwindCSS.
