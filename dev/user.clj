(ns user
  (:require [castle.cartridge.giantbomb.api :as api]
            [castle.cartridge.inventory :as inventory]
            [castle.cartridge.inventory.stm :as stm]
            [castle.cartridge.server :as server]
            [clojure.tools.namespace.repl :refer [refresh]]))

(comment

  @(api/game "3030-4725")

  @(api/games {:limit 2})

  @(api/games {:filter "name:DOOM"})

  (def server (server/start-server))
  (server/stop-server server)

  (def inventory (stm/create)))
