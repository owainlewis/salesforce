(ns salesforce.tooling
  "Generic Tooling API access sharing the core Salesforce transport."
  (:require
   [clojure.string :as str]
   [salesforce.api :as api]
   [salesforce.client :as client]))

(defn request!
  ([client-value method endpoint]
   (request! client-value method endpoint nil))
  ([client-value method endpoint options]
   (api/request! client-value method
                 (str "/tooling/" (str/replace endpoint #"^/+" ""))
                 options)))

(defn query [client-value soql]
  (request! client-value :get "query" {:query-params {:q soql}}))

(defn query-more [client-value next-records-url]
  (api/raw-request! client-value :get next-records-url))

(defn execute-anonymous! [client-value apex]
  (request! client-value :get "executeAnonymous"
            {:query-params {:anonymousBody apex}}))

(defn get-record [client-value sobject id]
  (request! client-value :get
            (str "sobjects/" (client/path-segment sobject) "/"
                 (client/path-segment id))))
