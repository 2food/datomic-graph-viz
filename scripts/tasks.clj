(ns tasks
  (:require [babashka.fs :as fs]
            [clojure.string :as str]
            [filipesilva.datomic-pro-manager :as dpm]))

(defn clean []
  (dpm/clean nil)
  (dpm/sqlite-delete {:opts {:yes true}})
  (dpm/run "rm -rf ./backups")
  (dpm/run "rm -rf ./resources/public/js")
  nil)

(defn mbrainz-demo []
  (dpm/download nil)
  (if (fs/exists? "backups/mbrainz")
    (dpm/info "Mbrainz backup already downloaded to ./backups")
    (do (fs/create-dir "backups")
        (fs/create-dir "storage")
        (dpm/run "curl -L https://s3.amazonaws.com/mbrainz/datomic-mbrainz-1968-1973-backup-2017-07-20.tar -o backups/mbrainz.tar")
        (dpm/run "tar -xzf backups/mbrainz.tar")
        (fs/move "mbrainz-1968-1973" "backups/mbrainz")
        (dpm/sqlite-create nil)
        (dpm/restore {:opts {:db-name "mbrainz"}})))
  (dpm/up nil))

(defn start [& args]
  (when-not (fs/exists? "resources/public/js/main.js")
    (dpm/run "npm i")
    (dpm/run "npx shadow-cljs compile app"))
  (dpm/run (str/join " " (cons "clj -M:run" args))))

(comment
 (clean)
 (mbrainz-demo))