(ns datomic-viz.server
  (:require [clojure.java.io :as io]
            [cognitect.transit :as transit]
            [datomic.api :as d]
            [org.httpkit.server :as hk-server]
            [mount.core :refer [defstate]])
  (:import (java.io ByteArrayOutputStream)))

(defstate conn
  :start (d/connect "datomic:dev://localhost:4334/mbrainz-1968-1973"))

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

(defn get-node [db entity]
  (->> (d/touch entity)
       (filter (fn [[k v]] (not= :db.type/ref (:db/valueType (d/entity db k)))))
       (into {})))

(defn get-edges [db entity]
  (->> (d/touch entity)
       (keep (fn [[k v]] (when (= :db.type/ref (:db/valueType (d/entity db k)))
                           [(:db/id entity) k (:db/id (d/entity db v))])))
       (vec)))

(defn graph-data [db eid]
  (let [entity (d/entity db eid)
        edges  (get-edges db entity)]
    {:root-id (str (:db/id entity))
     :edges   (mapv (fn [[e a v]] {:id (str [e a v]) :source (str e) :target (str v) :attribute a})
                    edges)
     :nodes   (mapv (fn [id] (assoc (get-node db (d/entity db id))
                               :id (str id))) (set (mapcat (juxt first last) edges)))}))

(comment
  (def db (d/db conn))
  (d/q '[:find (pull ?a [:*]) .
         :where [?a :artist/name "John Lennon"]]
       db)

  (get-node db (d/entity db 527765581346058))
  (get-edges db (d/entity db 527765581346058))
  (d/touch (d/entity db :artist/gid))
  )


(defn get-data [req]
  (let [out    (ByteArrayOutputStream. 4096)
        writer (transit/writer out :json)
        db     (d/db conn)]
    (transit/write writer (graph-data db 527765581346058))
    (str out)))

(defn app [req]
  (case (:uri req)
    "/data" {:status  200
             :headers {"Content-Type" "application/transit+json"}
             :body    (get-data req)}
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
