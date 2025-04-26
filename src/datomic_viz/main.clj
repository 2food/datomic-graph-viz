(ns datomic-viz.main
  (:require [datomic-viz.server]
            [babashka.cli :as cli]
            [mount.core :as m]))

(def default-conn-str "datomic:sql://mbrainz?jdbc:sqlite:storage/sqlite.db")

(def cli-opts {:coerce     {:port :long}
               :args->opts [:conn-str]
               :exec-args  {:port     1234
                            :conn-str default-conn-str}})

(defn -main [& args]
  (let [args (cli/parse-opts args cli-opts)]
    (println "Running with: ")
    (prn args)
    (m/start-with-args (cond-> args
                         (= default-conn-str (:conn-str args)) (assoc :using-default-conn-str true)))
    (println "Started")
    (println (str "Running at http://localhost:" (:port args)))))
