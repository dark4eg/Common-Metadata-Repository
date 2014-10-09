(ns cmr.search.services.acls.acl-helper
  "Contains functions for dealing with acls"
  (:require [cmr.acl.acl-cache :as ac]
            [cmr.acl.core :as acl]
            [cmr.common.cache :as cache]))

(def token-sid-cache-name
  :token-sid)

(defn context->sids
  "Wraps the existing context->sids but with caching"
  [context]
  (let [{:keys [token]} context]
    (cache/cache-lookup (cache/context->cache context token-sid-cache-name)
                        token
                        #(acl/context->sids context))))

(defn get-acls-applicable-to-token
  "Retrieves the ACLs that are applicable to the current user."
  [context]
  (let [acls (ac/get-acls context)
        sids (context->sids context)]
    (filter (partial acl/acl-matches-sids-and-permission? sids :read) acls)))


