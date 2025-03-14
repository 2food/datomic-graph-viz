(ns user
  (:require [shadow.cljs.devtools.api :as shadow]
            [shadow.cljs.devtools.server :as server]))

(defn repl
  [build-id]
  (server/start!)
  (shadow/watch build-id))

(comment
  (repl :app)

  (server/stop!)

  (shadow/nrepl-select :app)
  )