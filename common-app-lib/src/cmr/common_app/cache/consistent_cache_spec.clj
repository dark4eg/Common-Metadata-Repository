(ns cmr.common-app.cache.consistent-cache-spec
  "Defines a common set of tests for a consistent cache."
  (:require [clojure.test :refer :all]
            [cmr.common.cache :as c]))

(defn put-values-in-caches
  "Puts the key value pairs in the val map into the caches"
  [caches val-map]
  (doseq [cache caches
          [k v] val-map]
    (c/set-value cache k v)))

(defn assert-values-in-caches
  "Asserts that all of the values in the value map are in the caches."
  [caches val-map]
  (doseq [cache caches
          [k v] val-map]
    (is (= v (c/get-value cache k)))))

(defn assert-cache-keys
  "Asserts that the expected keys are in the cache."
  [expected-keys cache]
  (is (= (sort expected-keys) (sort (c/get-keys cache)))))

(defn basic-consistency-test
  [cache-a cache-b]
  (let [caches [cache-a cache-b]
        initial-values {:foo "foo value" :bar "bar value"}]
    ;; Put the same items in both caches
    (put-values-in-caches caches initial-values)

    (testing "Initial state with items in both caches"
      (assert-values-in-caches caches initial-values)
      (assert-cache-keys [:foo :bar] cache-a)
      (assert-cache-keys [:foo :bar] cache-b))

    (testing "Change a value in one cache"
      (c/set-value cache-a :foo "new foo value")

      (testing "The value is retrievable in the same cache"
        (is (= "new foo value" (c/get-value cache-a :foo))))

      (testing "Other cache should no longer have the old value"
        (is (nil? (c/get-value cache-b :foo))))

      (testing "The other cache keys should not be effected"
        (assert-values-in-caches caches {:bar "bar value"}))

      (testing "Keys after change should be correct"
        (assert-cache-keys [:foo :bar] cache-a)
        (assert-cache-keys [:bar] cache-b)))

    (testing "Change a value in the other cache"
      (c/set-value cache-b :foo "another foo value")

      (testing "The value is retrievable in the same cache"
        (is (= "another foo value" (c/get-value cache-b :foo))))

      (testing "Other cache should no longer have the old value"
        (is (nil? (c/get-value cache-a :foo))))

      (testing "The other cache keys should not be effected"
        (assert-values-in-caches caches {:bar "bar value"}))

      (testing "Keys after change should be correct"
        (assert-cache-keys [:bar] cache-a)
        (assert-cache-keys [:foo :bar] cache-b)))))

(defn clear-cache-test
  [cache-a cache-b]
  (let [caches [cache-a cache-b]
        initial-values {:foo "foo value" :bar "bar value"}]
    (put-values-in-caches caches initial-values)

    (c/reset cache-a)
    (is (empty? (c/get-keys cache-a)))
    (is (empty? (c/get-keys cache-b)))
    (assert-values-in-caches caches {:foo nil :bar nil})))

(defn get-value-with-lookup-test
  "This tests that get value with a lookup function will perform correctly. The names of objects in
  the test were chosen based on the use case for adding the consistent cache. They represent an in
  memory version of the real thing"
  [indexer1-cache indexer2-cache]
  (let [;; Represents ECHO's storage of ACLs. Acls here are just a list of symbols
        echo-acls-atom (atom [:acl1 :acl2])
        ;; The lookup function for "fetching" the latest version of the acls
        lookup-fn #(deref echo-acls-atom)

        ;; Checks that acls retrieved from the cache are the expected values.
        ;; Since we provide a lookup function and they're using a consistent cache the values
        ;; should always be correct.
        assert-acls-from-cache (fn [expected-acls]
                                 (is (= expected-acls (c/get-value indexer1-cache :acls lookup-fn)))
                                 (is (= expected-acls (c/get-value indexer2-cache :acls lookup-fn))))]


    (testing "First lookup with empty caches"
      (assert-acls-from-cache [:acl1 :acl2]))

    (testing "Acls have changed"
      (swap! echo-acls-atom conj :acl3)

      (testing "Old values are still cached"
        (assert-acls-from-cache [:acl1 :acl2]))

      (testing "One cache was cleared"
        (c/reset indexer1-cache)
        (assert-acls-from-cache [:acl1 :acl2 :acl3]))

      (testing "Manually updated one cache"
        (swap! echo-acls-atom conj :acl4)
        (c/set-value indexer2-cache :acls (lookup-fn))
        (assert-acls-from-cache [:acl1 :acl2 :acl3 :acl4])))))

(def ^:private cache-test-fns
  "Defines the set of test functions that check a cache implementation"
  [#'basic-consistency-test
   #'clear-cache-test
   #'get-value-with-lookup-test])

(defn assert-consistent-cache
  "Checks a consistent cache implementation to make sure it behaves as expected. Two caches should
  be passed in that use a common hash cache."
  [cache-a cache-b]
  (doseq [test-fn-var cache-test-fns]
    (c/reset cache-a)
    (c/reset cache-b)
    (testing (:name (meta test-fn-var))
      ((var-get test-fn-var) cache-a cache-b))))