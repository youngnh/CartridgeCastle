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

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Helpers

(defn- url-encode
  [s]
  (java.net.URLEncoder/encode s "utf-8"))

(defn- has-game?
  [ctx customer-id guid]
  (let [{:keys [inventory]} ctx
        checked-out (inventory/customer-checked-out inventory customer-id)]
    (some #(= % guid) checked-out)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Responses
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

        ;; Rental form
        (cond
          (some? cid)
          [:div
           [:form {:method :post
                   :action (format "/customer/%s/rentals" (url-encode cid))}
            [:input {:type :hidden
                     :name "guid"
                     :value guid}]
            (if (has-game? ctx cid guid)
              (list
               [:input {:type :hidden
                        :name "return"
                        :value "true"}]
               [:button "Return"])
              [:button "Rent"])]]

          :else
          nil)

        ;; Thumbnail
        [:div [:img {:height 240, :src original_url}]]
        ;; Description
        [:div {:style "width: 720"} [:p deck]]]]])))

(defn rental-created
  [_]
  (html
   [:html
    [:body
     [:h1 "Cartridge Castle"]
     [:p "Rental Complete! glhf ;)"]]]))

(defn rental-returned
  [_]
  (html
   [:html
    [:body
     [:h1 "Cartridge Castle"]
     [:p "gg. Rental Returned"]]]))

(defn rental-failed
  [ctx]
  (let [{:keys [msg]} ctx]
    (html
     [:html
      [:body
       [:h1 "Cartridge Castle"]
       [:p "Unfortunately, we couldn't complete your order"]
       [:p [:i msg]]]])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Resources

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

(defresource customer-rentals [customer-id]
  :service-available? {:inventory (stm/create)}
  :allowed-methods [:post]
  :available-media-types ["text/html"]
  :malformed? (fn [ctx]
                (let [params (get-in ctx [:request :params])
                      {:strs [guid return]} params]
                  (if (some? guid)
                    [false {:guid guid, :return? (not (empty? return))}]
                    true)))
  :authorized? (fn [ctx]
                 (when-let [cid (get-in ctx [:request :cookies "cid" :value])]
                   {:cid cid}))
  :allowed? #(= (:cid %) customer-id)
  :exists? (fn [ctx]
             (try
               (let [{:keys [guid inventory return?]} ctx]
                 (if return?
                   (has-game? ctx customer-id guid)
                   (number? (inventory/game-on-shelf inventory guid))))
               (catch Exception _
                 false)))
  :can-post-to-missing? false
  :conflict? (fn [ctx]
               (let [{:keys [guid return?]} ctx]
                 (if return?
                   false
                   (has-game? ctx customer-id guid))))
  :post! (fn [ctx]
           (let [{:keys [guid inventory return?]} ctx]
             (try
               (if return?
                 (do
                   (inventory/return inventory customer-id guid)
                   {:result :returned})
                 (do
                   (inventory/rent inventory customer-id guid)
                   {:result :rented}))
               (catch Exception ex
                 {:result :failed
                  :msg (ex-message ex)}))))
  :handle-created (fn [ctx]
                    (case (get ctx :result)
                      :rented (rental-created ctx)
                      :returned (rental-returned ctx)
                      :failed (rental-failed ctx)))
  :handle-no-content (fn [ctx]
                       (case (get ctx :result)
                         :rented (rental-created ctx)
                         :returned (rental-returned ctx)
                         :failed (rental-failed ctx))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Server Internals

(defroutes app
  (ANY "/search" [] #'search)
  (ANY "/game/:guid" [guid] (game guid))
  (ANY "/customer/:cid/rentals" [cid] (customer-rentals cid)))

(def handler
  (-> app
      wrap-cookies
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
