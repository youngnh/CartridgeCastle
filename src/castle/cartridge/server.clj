(ns castle.cartridge.server
  (:require [aleph.http :as http]
            [aleph.netty]
            [castle.cartridge.giantbomb.api :as api]
            [castle.cartridge.inventory :as inventory]
            [castle.cartridge.inventory.stm :as stm]
            [compojure.core :refer [ANY defroutes]]
            [environ.core :refer [env]]
            [hiccup.core :refer [html]]
            [liberator.core :refer [defresource]]
            [ring.middleware.cookies :refer [wrap-cookies]]
            [ring.middleware.params :refer [wrap-params]]))

(def PORT (parse-long (env :cartridge-castle-server-port)))

(defn- has-game?
  [ctx customer-id guid]
  (let [{:keys [inventory]} ctx
        checked-out (inventory/customer-checked-out inventory customer-id)]
    (some #(= % guid) checked-out)))

(defn not-found
  [ctx]
  (html
   [:html
    [:body
     [:h1 "Cartridge Castle"]
     [:p "Game over, man! That couldn't be found"]]]))

(defn params->filter
  [params]
  (reduce (fn [filter-str [name val]]
            (format "%s,%s:%s" filter-str name val))
          ""
          params))

(defn search-ok
  [ctx]
  (html
   [:html
    [:body
     [:h1 "Cartridge Castle"]
     [:form
      [:input {:type "text"
               :placeholder "search"
               :name "name"}]
      [:button "Submit"]
      (let [params (get-in ctx [:request :params])]
        (when-not (empty? params)
          [:div
           (for [game @(api/games {:limit 10
                                   :filter (params->filter params),
                                   :field_list "guid,image,deck,name"})]
             (let [{:strs [guid name]} game]
               [:div [:a {:href (format "/game/%s" guid)} name]]))]))]]]))

(defn game-ok
  [ctx]
  (let [{:keys [cid game guid inventory]} ctx
        {:strs [deck image name]} game
        {:strs [original_url]} image
        on-shelf (try
                   (inventory/game-on-shelf inventory guid)
                   (catch Exception _
                     nil))]
    (html
     [:html
      [:head [:title (format "Cartridge Castle - %s" name)]]
      [:body
       [:h1 "Cartridge Castle"]
       [:div
        ;; Title
        [:h2 name]
        ;; Inventory
        (cond
          (has-game? ctx cid guid)
          [:div [:span [:i "You are renting this title"]]]

          (and on-shelf (> on-shelf 0))
          [:div [:span [:i (format "Copies available for rent: %d" on-shelf)]]]

          (and on-shelf (= on-shelf 0))
          [:div [:span [:i (format "All copies currently rented out")]]]

          :else
          nil)

        ;; Thumbnail
        [:div [:img {:height 240, :src original_url}]]
        ;; Description
        [:div {:style "width: 720"} [:p deck]]]]])))

(defresource search
  :allowed-methods [:get]
  :available-media-types ["text/html"]
  :handle-ok search-ok)

(defresource game [guid]
  :service-available? {:inventory (stm/create)}
  :allowed-methods [:get]
  :available-media-types ["text/html"]
  :authorized? (fn [ctx]
                 {:cid (get-in ctx [:request :cookies "cid" :value])})
  :exists? (fn [ctx]
             (when-let [game (try
                               @(api/game guid)
                               (catch Exception _
                                 false))]
               {:guid guid
                :game game}))
  :handle-ok game-ok
  :handle-not-found not-found)

(defroutes app
  (ANY "/search" [] #'search)
  (ANY "/game/:guid" [guid] (game guid)))

(def handler
  (-> app
      wrap-params))

(defn start-server
  []
  (stm/unsafe-initialize! "store-inventory.edn.db")
  (http/start-server handler {:port PORT}))

(defn stop-server
  [server]
  (stm/persist! "store-inventory.edn.db")
  (.close server))

(defn -main []
  (let [server (start-server)]
    (println "Server started on port" (aleph.netty/port server))))
