(ns cmr.metadata-db.data.oracle.concept-tables
  (require [cmr.common.services.errors :as errors]
           [cmr.common.log :refer (debug info warn error)]
           [cmr.common.util :as cutil]
           [clojure.string :as string]
           [clojure.pprint :refer (pprint pp)]
           [clojure.java.jdbc :as j]
           [inflections.core :as inf]
           [cmr.metadata-db.services.provider-validation :as pv]
           [cmr.metadata-db.data.oracle.collection-table :as ct]
           [cmr.metadata-db.data.oracle.granule-table :as gt]
           [cmr.metadata-db.data.oracle.service-table :as st]))

(def all-provider-concept-types
  "All the concept types that have tables for each (non-small) provider"
  [:collection :granule :service])

(defmulti get-table-name
  "Get the name for the table for a given provider and concept-type"
  (fn [provider-id concept-type]
    concept-type))

(defmethod get-table-name :access-group
  [_ _]
  "cmr_groups")

(defmethod get-table-name :acl
  [_ _]
  "cmr_acls")

(defmethod get-table-name :tag
  [_ _]
  "cmr_tags")

(defmethod get-table-name :tag-association
  [_ _]
  "cmr_tag_associations")

(defmethod get-table-name :humanizer
  [_ _]
  "cmr_humanizers")

(defmethod get-table-name :variable
  [_ _]
  "cmr_variables")

(defmethod get-table-name :default
  [provider concept-type]
  ;; Dont' remove the next line - needed to prevent SQL injection
  (pv/validate-provider provider)
  (let [{:keys [provider-id small]} provider
        db-provider-id (if small pv/small-provider-id provider-id)]
    (format "%s_%s" (string/lower-case db-provider-id) (inf/plural (name concept-type)))))

(defn create-concept-table-id-sequence
  "Create a sequence to populate the ids for a concept table."
  [db provider concept-type]
  (let [sequence-name (str (get-table-name provider concept-type) "_seq")]
    (info "Creating sequence [" sequence-name "]")
    (j/db-do-commands db (format "CREATE SEQUENCE %s" sequence-name))))

(defmulti create-concept-table
  "Create a table to hold concepts of a given type."
  (fn [db provider concept-type]
    concept-type))

(defmethod create-concept-table :collection
  [db provider concept-type]
  (let [table-name (get-table-name provider :collection)]
    (info "Creating table [" table-name "]")
    (j/db-do-commands db (format "CREATE TABLE %s (%s, %s)"
                                 table-name
                                 (ct/collection-column-sql provider)
                                 (ct/collection-constraint-sql provider table-name)))
    (ct/create-collection-indexes db provider table-name)))

(defmethod create-concept-table :granule
  [db provider concept-type]
  (let [table-name (get-table-name provider :granule)]
    (info "Creating table [" table-name "]")
    (j/db-do-commands db (format "CREATE TABLE %s (%s, %s)"
                                 table-name
                                 (gt/granule-column-sql provider)
                                 (gt/granule-constraint-sql provider table-name)))
    (gt/create-granule-indexes db provider table-name)))

(defmethod create-concept-table :service
  [db provider concept-type]
  (let [table-name (get-table-name provider :service)]
    (info "Creating table [" table-name "]")
    (j/db-do-commands db (format "CREATE TABLE %s (%s, %s)"
                                 table-name
                                 (st/service-column-sql provider)
                                 (st/service-constraint-sql provider table-name)))
    (st/create-service-indexes db provider table-name)))

(defn create-provider-concept-tables
  "Create all the concept tables for the given provider."
  [db provider]
  (info "Creating concept tables for provider [" (:provider-id provider) "]")
  (doseq [concept-type all-provider-concept-types]
    (create-concept-table db provider concept-type)
    (create-concept-table-id-sequence db provider concept-type)))

(defn delete-provider-concept-tables
  "Delete the concept tables associated with the given provider."
  [db provider]
  (info "Deleting concept tables for provider [" (:provider-id provider) "]")
  (doseq [concept-type all-provider-concept-types]
    (let [table-name (get-table-name provider concept-type)
          sequence-name (str table-name "_seq")]
      (info "Dropping table" table-name)
      (j/db-do-commands db (str "DROP TABLE " table-name))
      (info "Dropping sequence" sequence-name)
      (j/db-do-commands db (str "DROP SEQUENCE " sequence-name)))))
