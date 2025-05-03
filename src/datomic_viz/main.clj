(ns datomic-viz.main
  (:require [babashka.cli :as cli]
            [bling.core :as bling]
            [datomic-viz.server]
            [mount.core :as m])
  (:import (java.awt Desktop)
           (java.net URI)))

(defn open-url [url]
  (try (.browse (Desktop/getDesktop) (URI. url))
       (catch Exception _
         (println (str "Could not automatically open URL.")))))

(def cli-opts {:coerce     {:port                :long
                            :max-edges-per-level :long}
               :exec-args  {:port                1234
                            :max-edges-per-level 100}
               :args->opts [:conn-str]
               :require    [:conn-str]
               :restrict   [:conn-str :port :max-edges-per-level]})


(defn -main [& args]
  (let [args (cli/parse-opts args cli-opts)]
    (println "Running with: ")
    (prn args)
    (m/start-with-args args)
    (bling/callout {:type  :positive
                    :label "Started"}
                   (str "Running at http://localhost:" (:port args)))
    (open-url (str "http://localhost:" (:port args)))))
