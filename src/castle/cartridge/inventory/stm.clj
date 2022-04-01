(ns castle.cartridge.inventory.stm
  (:require [castle.cartridge.inventory :refer [Inventory]]
            [clojure.java.io :as io]))

(def EMPTY-GAME {:checked-out 0, :on-shelf 0})

(def inventory
  (ref {}))

(defn- get-game
  ([guid]
   (get-game guid nil))
  ([guid opts]
   (let [{:keys [create?]
          :or {create? false}} opts
         game (get @inventory guid)]
     (when (and (nil? game) (not create?))
       (throw (ex-info "Game not in inventory" {:guid guid})))

     (or game
         (let [new-game (ref EMPTY-GAME)]
           (dosync
             (commute inventory assoc guid new-game))
           new-game)))))

(def EMPTY-CUSTOMER {:checked-out []})

(def customer
  (ref {}))

(defn- get-customer
  ([customer-id]
   (get-customer customer-id nil))
  ([customer-id opts]
   (let [{:keys [create?]
          :or {create? false}} opts
         cust (get @customer customer-id)]
     (when (and (nil? cust) (not create?))
       (throw (ex-info "Unknown customer" {:customer-id customer-id})))

     (or cust
         (let [new-customer (ref EMPTY-CUSTOMER)]
           (dosync
             (commute customer assoc customer-id new-customer))
           new-customer)))))

(defn check-out
  [inventory-row]
  (let [{:keys [checked-out on-shelf]} inventory-row]
    (if (> on-shelf 0)
     {:checked-out (inc checked-out)
      :on-shelf (dec on-shelf)}
     (throw (ex-info "No copies on shelf" {})))))

(defn check-in
  [inventory-row]
  (let [{:keys [checked-out on-shelf]} inventory-row]
    (if (> checked-out 0)
      {:checked-out (dec checked-out)
       :on-shelf (inc on-shelf)}
      (throw (ex-info "No copies checked out")))))

(defn add-game
  [customer-row guid]
  (update customer-row :checked-out conj guid))

(defn return-game
  [customer-row guid]
  (let [{:keys [checked-out]} customer-row
        checked-out' (remove #(= % guid) checked-out)]
    (when (= checked-out' checked-out)
      (throw (ex-info "Customer doesn't have game" {:guid guid})))

    {:checked-out checked-out'}))

(defn -rent
  "Decrement on-shelf quantity, increment checked-out, add game to customer's checked-out"
  [customer-id guid]
  (let [game (get-game guid)
        cust (get-customer customer-id {:create? true})]
    (dosync
      (alter game check-out)
      (alter cust add-game guid))))

(defn -return
  "Increment on-shelf quantity, decrement checked-out, remove game from customer's checked-out list"
  [customer-id guid]
  (let [cust (get-customer customer-id)
        game (get-game guid)]
    (dosync
      (alter cust return-game guid)
      (alter game check-in))))

(defn -set-stock
  "Change a game's on-shelf inventory to a given quantity"
  [guid n]
  (let [game (get-game guid {:create? true})]
    (dosync
      (alter game assoc :on-shelf n))))

(defn -mark-lost
  "Decrement a game's checked-out quantity, remove from customer's checked-out list"
  [customer-id guid]
  (let [cust (get-customer customer-id)
        game (get-game guid)]
    (dosync
      (alter cust return-game guid)
      (alter game update :checked-out dec))))

(defn -customer-checked-out
  [customer-id]
  (-> customer-id
      (get-customer {:create? true})
      (deref)
      :checked-out))

(defn -game-on-shelf
  [guid]
  (-> guid
      (get-game)
      (deref)
      :on-shelf))

(defn -game-checked-out
  [guid]
  (-> guid
      (get-game)
      (deref)
      :checked-out))

(defrecord STMInventory []
  Inventory
  (rent [this customer-id guid]
    (-rent customer-id guid))
  (return [this customer-id guid]
    (-return customer-id guid))
  (set-stock [this guid n]
    (-set-stock guid n))
  (mark-lost [this customer-id guid]
    (-mark-lost customer-id guid))
  (customer-checked-out [this customer-id]
    (-customer-checked-out customer-id))
  (game-on-shelf [this guid]
    (-game-on-shelf guid))
  (game-checked-out [this guid]
    (-game-checked-out guid)))

(defn unsafe-reset!
  "Completely wipe all data, resetting tables to empty"
  []
  (dosync
    (ref-set inventory {})
    (ref-set customer {})))

(defn- table-rowize
  "Return a map in which the values are refs"
  [table]
  (update-vals table ref))

(defn unsafe-initialize!
  "Wipe all existing data, replacing it with inventory and customer tables read
  from edn file on disk"
  [file]
  (let [snapshot (read (java.io.PushbackReader. (io/reader file)))]
    (dosync
      (ref-set inventory (table-rowize (:inventory snapshot)))
      (ref-set customer (table-rowize (:customer snapshot))))))

(defn create
  "Return an Inventory backed by in-memory STM"
  []
  (->STMInventory))

(defn- table-snapshot
  "Return a map in which the values have been deref'd"
  [table]
  (update-vals table deref))

(defn persist!
  "Write current state of inventory and customer tables to edn files on disk"
  [file]
  (with-open [out (io/writer file)]
    (binding [*out* out]
      (prn {:inventory (table-snapshot @inventory)
            :customer (table-snapshot @customer)}))))
