# Cartridge Castle

## To Start a REPL

first, put your GiantBomb api key in an environment variable:

> export GIANTBOMB_API_KEY=<your key here>

then start a repl

> clj -A:dev

## Rough Plan

- write a ns for fetching data from GiantBomb api
  - read api key from environment variable

- write a simple HTTP server
  - start with /status endpoint
  - liberator for resource handlers
    - application/transit+json
    - text/html
      - hiccup to render pages

- /checkout api
  - in-memory Clojure ref
  - STM transactions to check out games
    - poor man's durability by writing edn to a file on server shutdown
  - upgrade implementation to SQLite if there's time
    - should be simple to swap out PostgreSQL at this point

## Tech Stack
- clojure cli / deps.edn
- Aleph for the http server & the GiantBomb api client
- spec + generative tests
- liberator - never used it before!
