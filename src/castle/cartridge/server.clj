(ns castle.cartridge.server
  (:require [aleph.http :as http]
            [aleph.netty]
            [castle.cartridge.giantbomb.api :as api]
            [compojure.core :refer [ANY defroutes]]
            [environ.core :refer [env]]
            [hiccup.core :refer [html]]
            [liberator.core :refer [defresource]]
            [ring.middleware.params :refer [wrap-params]]))

(def PORT (parse-long (env :cartridge-castle-server-port)))

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

(defresource search
  :allowed-methods [:get]
  :available-media-types ["text/html"]
  :handle-ok search-ok)

(defroutes app
  (ANY "/search" [] #'search))

(def handler
  (-> app
      wrap-params))

(defn start-server
  []
  (http/start-server handler {:port PORT}))

(defn -main []
  (let [server (start-server)]
    (println "Server started on port" (aleph.netty/port server))))
