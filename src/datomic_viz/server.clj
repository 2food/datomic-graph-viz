(ns datomic-viz.server
  (:require [bling.core :as bling]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [cognitect.transit :as transit]
            [datomic.api :as d]
            [mount.core :refer [defstate] :as m]
            [org.httpkit.server :as hk-server]
            [ring.middleware.keyword-params :as ring-keyword-params]
            [ring.middleware.params :as ring-params])
  (:import (java.io ByteArrayOutputStream)))

(defstate conn
  :start (let [{:keys [conn-str]} (m/args)]
           (try (d/connect conn-str)
                (catch Exception e
                  (bling/callout {:type :error} "Couldn't connect to transactor. Make sure it's running and that the :conn-str is correct.")
                  (throw e)))))

(defn get-node [db entity]
  (->> (d/touch entity)
       (filter (fn [[k v]] (not= :db.type/ref (:db/valueType (d/entity db k)))))
       (into {})))

(defn get-ancestors [db entity levels]
  (if (>= 0 levels)
    []
    (let [edges (->> (d/q '[:find ?e ?attr ?v
                            :in $ ?v
                            :where
                            [?e ?a ?v]
                            [?a :db/ident ?attr]]
                          db (:db/id entity))
                     (take (:max-edges-per-level (m/args))))]
      (set (apply concat edges
                  (mapv (fn [[e _ _]] (get-ancestors db (d/entity db e) (dec levels))) edges))))))

(defn get-descendants [db entity levels]
  (if (>= 0 levels)
    []
    (let [edges (->> (d/q '[:find ?e ?attr ?v
                            :in $ ?e
                            :where
                            [?e ?a ?v]
                            [?a :db/valueType :db.type/ref]
                            [?a :db/ident ?attr]]
                          db (:db/id entity))
                     (take (:max-edges-per-level (m/args))))]
      (set (apply concat edges
                  (mapv (fn [[_ _ v]] (get-descendants db (d/entity db v) (dec levels))) edges))))))

(defn get-edges [db entity ancestors descendants]
  (set (concat (get-ancestors db entity ancestors)
               (get-descendants db entity descendants))))

(defn random-entity [db]
  (->> (d/q '[:find (sample 1 ?e) .
              :in $
              :where
              [?a :db/valueType :db.type/ref]
              [?a :db/ident ?attr]
              [?e ?a ?v]
              (not [?e :db/valueType])
              (not [?e :db/ident :db.part/db])]
            db)
       (first)
       (d/entity db)
       (d/touch)))

(defn graph-data [db {:keys [eid ancestors descendants]}]
  (let [eid    (or eid
                   (:db/id (random-entity db)))
        entity (d/touch (d/entity db eid))
        edges  (get-edges db entity (or ancestors 1) (or descendants 1))]
    {:root-id             (str (:db/id entity))
     :max-edges-per-level (:max-edges-per-level (m/args))
     :edges               (mapv (fn [[e a v]] {:id (str [e a v]) :source (str e) :target (str v) :attribute a})
                                edges)
     :nodes               (->> (conj (set (mapcat (juxt first last) edges))
                                     (:db/id entity))
                               (mapv (fn [id] (assoc (get-node db (d/entity db id))
                                                :id (str id)))))}))

(comment
 (def db (d/db conn))
 (d/q '[:find (pull ?a [:*]) .
        :where [?a :artist/name "John Lennon"]]
      db)

 ; "John Lennon" 527765581346058
 ; "Knutsen & Ludvigsen" 598134325523864

 (get-node db (d/entity db 527765581346058))
 (get-edges db (d/entity db 527765581346058) 0 1)
 (d/touch (d/entity db :artist/gid))
 )

(defn ensure-long [x]
  (cond-> x (string? x) (parse-long)))

(defn parse-params [params]
  (-> params
      (update :eid #(some-> % edn/read-string))
      (update :ancestors #(some-> % ensure-long))
      (update :descendants #(some-> % ensure-long))))


(defn get-data [req]
  (let [out    (ByteArrayOutputStream. 4096)
        writer (transit/writer out :json)
        db     (d/db conn)]
    (transit/write writer (graph-data db (parse-params (:params req))))
    (str out)))

(defn handler [req]
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

(def app
  (-> handler
      (ring-keyword-params/wrap-keyword-params)
      (ring-params/wrap-params)))

(defstate my-server
  :start (let [{:keys [port]} (m/args)]
           (hk-server/run-server app {:port                 port
                                      :legacy-return-value? false}))
  :stop (hk-server/server-stop! my-server))
