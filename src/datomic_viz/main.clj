(ns datomic-viz.main
  (:require [babashka.cli :as cli]
            [bling.core :as bling]
            [datomic-viz.server]
            [mount.core :as m]))

(def cli-opts {:coerce     {:port :long}
               :args->opts [:conn-str]
               :exec-args  {:port 1234}
               :require    [:conn-str]})

(defn -main [& args]
  (let [args (cli/parse-opts args cli-opts)]
    (println "Running with: ")
    (prn args)
    (m/start-with-args args)
    (bling/callout {:type  :positive
                    :label "Started"}
                   (str "Running at http://localhost:" (:port args)))))
