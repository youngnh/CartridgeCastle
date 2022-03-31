(ns castle.cartridge.inventory.test-stm
  (:require [clojure.test :refer [are deftest is testing use-fixtures]]
            [castle.cartridge.inventory :as inventory]
            [castle.cartridge.inventory.stm :as stm]
            [clojure.java.io :as io])
  (:import (clojure.lang ExceptionInfo)))

(defn reset-stm!
  [test]
  (test)
  (stm/unsafe-reset!))

(def cust-1 "tommy")
(def cust-2 "jimmy")
(def game-1 "5415")
(def game-2 "52647")
(def game-3 "37152")

(use-fixtures :each reset-stm!)

(def inventory (stm/create))

(deftest empty-inventory-exceptions
  (is (thrown-with-msg? ExceptionInfo #"Game not in inventory"
        (inventory/rent inventory cust-1 game-1))))

(deftest customer-rentals
  (is (= [] (inventory/customer-checked-out inventory cust-1))))

(deftest rent-a-game
  (inventory/set-stock inventory game-1 1)
  (is (some? (inventory/rent inventory cust-1 game-1))))

(deftest return-a-game
  (testing "return a game that's been checked out"
    (inventory/set-stock inventory game-1 1)
    (is (some? (inventory/rent inventory cust-1 game-1)))
    (is (thrown-with-msg? ExceptionInfo #"No copies on shelf"
          (inventory/rent inventory cust-2 game-1)))
    (is (some? (inventory/return inventory cust-1 game-1))))

  (testing "can't return a game that hasn't been checked out"
    (is (thrown-with-msg? ExceptionInfo #"Customer doesn't have game"
          (inventory/return inventory cust-1 game-1)))))

(deftest mark-a-game-lost
  (inventory/set-stock inventory game-1 1)
  (is (some? (inventory/rent inventory cust-1 game-1)))
  (is (inventory/mark-lost inventory cust-1 game-1))
  (is (= [] (inventory/customer-checked-out inventory cust-1)))
  (is (= 0 (inventory/game-on-shelf inventory game-1)))
  (is (= 0 (inventory/game-checked-out inventory game-1))))

(deftest perist-to-disk
  (inventory/set-stock inventory game-1 2)
  (inventory/set-stock inventory game-2 3)
  (inventory/set-stock inventory game-3 1)
  (inventory/rent inventory cust-1 game-1)
  (inventory/rent inventory cust-1 game-2)
  (inventory/rent inventory cust-2 game-2)

  (let [tmp-file (java.io.File/createTempFile "inventory_" ".edn.db")]
    (stm/persist! tmp-file)

    (is (= {:customer {cust-1 {:checked-out [game-1 game-2]}
                       cust-2 {:checked-out [game-2]}}
            :inventory {game-1 {:checked-out 1 :on-shelf 1}
                        game-2 {:checked-out 2 :on-shelf 1}
                        game-3 {:checked-out 0 :on-shelf 1}}}
           (read (java.io.PushbackReader. (io/reader tmp-file)))))))

(deftest initialize-from-disk
  (let [snapshot {:customer {cust-1 {:checked-out [game-1 game-2]}
                             cust-2 {:checked-out [game-2]}}
                  :inventory {game-1 {:checked-out 1 :on-shelf 1}
                              game-2 {:checked-out 2 :on-shelf 1}
                              game-3 {:checked-out 0 :on-shelf 1}}}
        tmp-file (java.io.File/createTempFile "inventory_" ".edn.db")]
    (with-open [out (io/writer tmp-file)]
      (binding [*out* out]
        (prn snapshot)))

    (stm/unsafe-initialize! tmp-file)

    (is (= [game-1 game-2] (inventory/customer-checked-out inventory cust-1)))
    (is (= [game-2] (inventory/customer-checked-out inventory cust-2)))
    (are [game] (= (inventory/game-on-shelf inventory game) 1)
      game-1
      game-2
      game-3)
    (are [game checked-out] (= (inventory/game-checked-out inventory game) checked-out)
      game-1 1
      game-2 2
      game-3 0)))
