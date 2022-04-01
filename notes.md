# Cartridge Castle

## To Start a REPL

first, put your GiantBomb api key in an environment variable:

> export GIANTBOMB_API_KEY=<your key here>

then start a repl

> clj -M:dev

## To start a standalone server

> clj -M:server

## Rough Plan

- write a ns for fetching data from GiantBomb api
  - read api key from environment variable

- write a simple HTTP server
  - start with /status endpoint
  - liberator for resource handlers
    - application/transit+json
    - text/html
      - hiccup to render pages

  - GET /search?{giantbomb params passthrough}
  - GET /game/:id
  - POST /customer/:cid/rentals
    guid: {game uid}
  - PUT /customer/:cid/rental/:guid
  - DELETE /customer/:cid/rental/:guid

- inventory api
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

# API tests

POST /customer/{cust-1}/rentals
=> 400 Bad Request

POST /customer/{cust-1}/rentals
guid {game-1}
=> 401 Not Authorized

POST /customer/{cust-1}/rentals
Cookie: cid={cust-2}
guid {game-1}
=> 403 Forbidden

POST /customer/{cust-1}/rentals
Cookie: cid={cust-1}
guid {bogus-game}
=> 404 Not Found

POST /customer/{cust-1}/rentals
Cookie: cid={cust-1}
guid {game-1}
=> 201 Created

POST /customer/{cust-1}/rentals
Cookie: cid={cust-1}
guid {game-1}
=> 409 Conflict
;; you can't check the same game out twice

POST /customer/{cust-1}/rentals
Cookie: cid={cust-1}
guid {game-1}
return true
=> 201 Created

POST /customer/{cust-1}/rentals
Cookies: cid={cust-1}
guid {game-1}
return true
=> 404 Not Found
