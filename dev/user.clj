(ns user
  (:require [castle.cartridge.giantbomb.api :as api]
            [clojure.tools.namespace.repl :refer [refresh]]))

(comment

  @(api/game "3030-4725")

  @(api/games {:limit 2}))
