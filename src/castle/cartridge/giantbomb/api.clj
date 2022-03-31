(ns castle.cartridge.giantbomb.api
  (:require [aleph.http :as http]
            [clojure.string :as string]
            [environ.core :refer [env]]
            [jsonista.core :as json]
            [manifold.deferred :as d]))

(def SERVER-NAME (env :giantbomb-server-name))
(def API-KEY (env :giantbomb-api-key))
(def USER-AGENT (env :cartridge-castle-user-agent))
(def PROXY (env :http-proxy))

(def CONN-POOL
 (if (some? PROXY)
   (let [url (java.net.URL. PROXY)]
     (http/connection-pool
      {:connection-options
       {:proxy-options {:host (.getHost url)
                        :port (.getPort url)}}}))
   http/default-connection-pool))

(defn- unwrap-result
  [result]
  (let [{:strs [error results status_code]} result]
    (if (= status_code 1)
      results
      (throw (ex-info error {:result result})))))

(defn- make-giantbomb-request
  [req]
  (d/chain
   (http/request
    (->
     {:pool CONN-POOL
      :request-method :GET
      :scheme :https
      :server-name SERVER-NAME
      :headers {"User-Agent" USER-AGENT}}
     (merge req)
     (update :query-params merge {:api_key API-KEY, :format "json"})))
   :body
   json/read-value
   unwrap-result))

(defn entity
  "Fetch data about a single, specific resource, identified by guid"
  ([resource guid]
   (entity resource guid nil))
  ([resource guid filters]
   (make-giantbomb-request
    {:uri (format "/api/%s/%s/" (name resource) guid)
     :query-params filters})))

(defmacro defentity [resource]
  `(def ~resource
     ~(format "Fetch data about a single, specific %s, identified by guid" (name resource))
     (partial entity '~resource)))

(defentity game)

(defn collection
  "Fetch a collection of resources matching given search filters"
  ([resource]
   (collection resource nil))
  ([resource filters]
   (make-giantbomb-request
    {:uri (format "/api/%s/" (name resource))
     :query-params filters})))

(defmacro defcollection [resource]
  `(def ~resource
     ~(format "Fetch a collection of %s matching given search filters" (name resource))
     (partial collection '~resource)))

(defcollection games)
