(ns cmr.db
  "Entry point for the db related operations. Defines a main method that accepts arguments."
  (:require [cmr.common.log :refer (debug info warn error)]
            [drift.execute :as drift]
            [cmr.oracle.user :as o]
            [cmr.oracle.config :as oracle-config]
            [cmr.bootstrap.config :as bootstrap-config]
            [config.migrate-config :as mc])
  (:gen-class))

(defn create-user
  []
  (let [db (oracle-config/sys-dba-db-spec)]
    (o/create-user db (bootstrap-config/db-username) (bootstrap-config/db-password))))

(defn drop-user
  []
  (let [db (oracle-config/sys-dba-db-spec)]
    (o/drop-user db (bootstrap-config/db-username))))

(defn -main
  "Execute the given database operation specified by input arguments."
  [& args]
  (info "Running " args)
  (let [op (first args)]
    (try
      (cond
        (= "create-user" op)
        (create-user)

        (= "drop-user" op)
        (drop-user)

        (= "migrate" op)
        (drift/run args)

        :else
        (info "Unsupported operation: " op))

      (catch Throwable e
        (error e (.getMessage e))
        (System/exit 1))))

  (System/exit 0))