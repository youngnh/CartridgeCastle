(ns castle.cartridge.inventory)

(defprotocol Inventory
  (rent [this customer-id guid] "Customer rented game")
  (return [this customer-id guid] "Customer returned game")
  (set-stock [this guid n] "Set the on-shelf stock of a game")
  (mark-lost [this customer-id guid] "Customer can't return game")
  (customer-checked-out [this customer-id] "Return a list of the games a customer has currently checked out")
  (game-on-shelf [this guid] "Return the # of copies of a game currently on the shelf")
  (game-checked-out [this guid] "Return the # of copies of a game that are currently checked out"))
