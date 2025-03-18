(ns datomic-viz.server
  (:require [clojure.java.io :as io]
            [cognitect.transit :as transit]
            [org.httpkit.server :as hk-server]
            [mount.core :refer [defstate]])
  (:import (java.io ByteArrayOutputStream)))

(def data
  [["Eve" :person/child "Cain"]
   ["Eve" :person/child "Seth"]
   ["Eve" :person/child "Abel"]
   ["Eve" :person/child "Awan"]
   ["Eve" :person/child "Azura"]
   ["Seth" :person/child "Enos"]
   ["Seth" :person/child "Noam"]
   ["Awan" :person/child "Enoch"]])

(defn datoms->graph-data [datoms]
  (let [entities (map (fn [id] {:id id}) (mapcat (juxt first last) datoms))]
    {:nodes   (set (map (fn [id] {:id id}) (mapcat (juxt first last) datoms)))
     :edges   (mapv (fn [[e a v]] {:id (str [e a v]) :source e :target v :attribute a})
                    data)
     :root-id (:id (first entities))}))

(defn app [req]
  (case (:uri req)
    "/data" {:status  200
             :headers {"Content-Type" "application/transit+json"}
             :body    (let [out    (ByteArrayOutputStream. 4096)
                            writer (transit/writer out :json)]
                        (transit/write writer (datoms->graph-data data))
                        (str out))}
    "/js/main.js" {:status  200
                   :headers {"Content-Type" "text/html"}
                   :body    (io/file (io/resource "public/js/main.js"))}
    {:status  200
     :headers {"Content-Type" "text/html"}
     :body    (io/file (io/resource "public/index.html"))}))

(defstate my-server
  :start (hk-server/run-server app {:port                 8080
                                    :legacy-return-value? false})
  :stop (hk-server/server-stop! my-server))
