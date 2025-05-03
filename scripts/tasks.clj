(ns tasks
  (:require [babashka.fs :as fs]
            [babashka.process :as p]
            [bling.core :as bling]
            [clojure.string :as str]
            [filipesilva.datomic-pro-manager :as dpm]))

(defn run [& ss]
  (let [s (str/join " " ss)]
    (bling/callout {:colorway :purple} s)
    (p/shell s)))

(defn clean []
  (dpm/clean nil)
  (dpm/sqlite-delete {:opts {:yes true}})
  (dpm/run "rm -rf ./backups")
  (dpm/run "rm -rf ./resources/public/js")
  nil)

(defn mbrainz-demo-transactor []
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

(defn mbrainz-demo []
  (if (fs/exists? "storage")
    (run "clj -M:run \"datomic:sql://mbrainz?jdbc:sqlite:storage/sqlite.db\"")
    (bling/callout {:type :error}
                   "Hello there! Make sure you've started the transactor first with `bb mbrainz-demo-transactor`.")))

(defn start [& args]
  (run (str/join " " (cons "clj -M:run" args))))

(comment
 (clean)
 (mbrainz-demo))