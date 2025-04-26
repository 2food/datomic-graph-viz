(ns user
  (:require [clojure.tools.namespace.repl :as repl]
            [mount.core :as m]
            [shadow.cljs.devtools.api :as shadow]
            [shadow.cljs.devtools.server :as server]))

(defmacro capture-env
  "Capture local bindings. You can also specify which symbols to capture
  Example:
  (defn adder [x y]
    (user/capture-env)
    (+ x y))
  =>
  (defn adder [x y]
    (def x x)
    (def y y)
    (+ x y))"
  ([]
   `(capture-env ~@(keys &env)))
  ([& symbols]
   (cons 'do
         (map (fn [local]
                `(def ~local ~local))
              symbols))))

;; disable refresh reloading this ns
(repl/disable-reload!)
(repl/set-refresh-dirs "src")
(defn reset-system [& states]
  (time (let [stop-res (m/stop)]
          (repl/refresh)
          (merge stop-res
                 (m/start (cond-> (m/with-args {})
                            (not-empty states) (m/only states)))))))

(defn repl
  [build-id]
  (server/start!)
  (shadow/watch build-id))

(comment
  (repl :app)

  (server/stop!)

  (shadow/nrepl-select :app)
  )