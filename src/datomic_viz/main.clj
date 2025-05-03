(ns datomic-viz.main
  (:require [babashka.cli :as cli]
            [bling.core :as bling]
            [datomic-viz.server]
            [mount.core :as m]
            [clojure.edn :as edn])
  (:import (java.awt Desktop)
           (java.net URI)))

(defn open-url [url]
  (try (.browse (Desktop/getDesktop) (URI. url))
       (catch Exception _
         (println (str "Could not automatically open URL.")))))

(def cli-opts (edn/read-string (slurp "cli-opts.edn")))

(defn -main [& args]
  (let [args (cli/parse-opts args cli-opts)]
    (println "Running with: ")
    (prn args)
    (m/start-with-args args)
    (bling/callout {:type  :positive
                    :label "Started"}
                   (str "Running at http://localhost:" (:port args)))
    (open-url (str "http://localhost:" (:port args)))))
