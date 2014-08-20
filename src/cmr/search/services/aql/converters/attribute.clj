(ns cmr.search.services.aql.converters.attribute
  "Contains functions for parsing, validating and converting additionalAttributes aql element to query conditions"
  (:require [cmr.common.xml :as cx]
            [cmr.search.services.aql.conversion :as a]
            [cmr.search.models.query :as qm]
            [cmr.search.services.parameters.converters.attribute :as p]
            [cmr.common.date-time-parser :as date-time-parser]))

(defn- attrib-value->condition
  [attrib-type attrib-name value]
  (let [condition (qm/map->AttributeValueCondition
                    {:type attrib-type
                     :name attrib-name
                     :value value})]
    (p/parse-component-type condition)))

(defn- attrib-range-elem->condition
  [attrib-type attrib-name range-elem]
  (let [minv (get-in range-elem [:attrs :lower])
        maxv (get-in range-elem [:attrs :upper])
        condition (qm/map->AttributeRangeCondition
                    {:type attrib-type
                     :name attrib-name
                     :min-value minv
                     :max-value maxv})]
    (p/parse-component-type condition)))

(defn- time-from-strings
  "Returns time from strings of hour, minute and second. Returns nil if all strings are nil."
  [hour minute sec]
  (when (or hour minute sec)
    (let [time-str (format "%s:%s:%s" hour minute sec)]
      (date-time-parser/parse-time time-str))))

(defn- parse-time-range-element
  "Returns start-time and stop-time in a vector by parsing the given time range element"
  [element]
  (let [{hour :HH minute :MI sec :SS} (cx/attrs-at-path element [:startTime :time])
        start-time (time-from-strings hour minute sec)
        {hour :HH minute :MI sec :SS} (cx/attrs-at-path element [:stopTime :time])
        stop-time (time-from-strings hour minute sec)]
    [start-time stop-time]))

(defmulti attrib-value-element->condition
  "Returns the query condition of the given additional attribute value element"
  (fn [attrib-name value-elem]
    (:tag value-elem)))

(defmethod attrib-value-element->condition :value
  [attrib-name value-elem]
  (attrib-value->condition :string attrib-name (first (:content value-elem))))

(defmethod attrib-value-element->condition :textPattern
  [attrib-name value-elem]
  (let [value (-> value-elem :content first a/aql-pattern->cmr-pattern)]
    (qm/map->AttributeValueCondition
      {:type :string
       :name attrib-name
       :value value
       :pattern? true})))

(defmethod attrib-value-element->condition :list
  [attrib-name value-elem]
  (let [values (cx/strings-at-path value-elem [:value])
        conditions (map (partial attrib-value->condition :string attrib-name) values)]
    (qm/or-conds conditions)))

(defmethod attrib-value-element->condition :range
  [attrib-name value-elem]
  (attrib-range-elem->condition :string attrib-name value-elem))

(defmethod attrib-value-element->condition :float
  [attrib-name value-elem]
  (attrib-value->condition :float attrib-name (first (:content value-elem))))

(defmethod attrib-value-element->condition :floatRange
  [attrib-name value-elem]
  (attrib-range-elem->condition :float attrib-name value-elem))

(defmethod attrib-value-element->condition :int
  [attrib-name value-elem]
  (attrib-value->condition :int attrib-name (first (:content value-elem))))

(defmethod attrib-value-element->condition :intRange
  [attrib-name value-elem]
  (attrib-range-elem->condition :int attrib-name value-elem))

(defmethod attrib-value-element->condition :Date
  [attrib-name value-elem]
  (let [{year :YYYY month :MM day :DD hour :HH minute :MI sec :SS} (:attrs value-elem)
        value (a/date-time-from-strings year month day hour minute sec)]
    (qm/map->AttributeValueCondition
      {:type :datetime
       :name attrib-name
       :value value})))

(defmethod attrib-value-element->condition :dateRange
  [attrib-name value-elem]
  (let [[start-date stop-date] (a/parse-date-range-element value-elem)]
    (qm/map->AttributeRangeCondition
      {:type :datetime
       :name attrib-name
       :min-value start-date
       :max-value stop-date})))

(defmethod attrib-value-element->condition :time
  [attrib-name value-elem]
  (let [{hour :HH minute :MI sec :SS} (:attrs value-elem)
        value (format "%s:%s:%s" hour minute sec)]
    (attrib-value->condition :time attrib-name value)))

(defmethod attrib-value-element->condition :timeRange
  [attrib-name value-elem]
  (let [[start-time stop-time] (parse-time-range-element value-elem)]
    (qm/map->AttributeRangeCondition
      {:type :time
       :name attrib-name
       :min-value start-time
       :max-value stop-time})))

(defn- additional-attribute-element->conditions
  "Returns the query conditions of the given additionalAttribute element"
  [additional-attribute]
  (let [attrib-name (cx/string-at-path additional-attribute [:additionalAttributeName])
        attrib-value (first (cx/content-at-path additional-attribute [:additionalAttributeValue]))]
    (attrib-value-element->condition attrib-name attrib-value)))

;; Converts additionalAttributes element into query condition, returns the converted condition
(defmethod a/element->condition :attribute
  [concept-type element]
  (let [attributes (cx/elements-at-path element [:additionalAttribute])
        operator (get-in element [:attrs :operator])
        conditions (map additional-attribute-element->conditions attributes)
        attrib-condition (if (= "OR" operator)
                           (qm/or-conds conditions)
                           (qm/and-conds conditions))]
    (if (= :granule concept-type)
      ;; Granule attribute queries will inherit values from their parent collections.
      (qm/or-conds [attrib-condition (qm/->CollectionQueryCondition attrib-condition)])
      attrib-condition)))