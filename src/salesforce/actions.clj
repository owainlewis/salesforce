(ns salesforce.actions
  "Standard, custom, Flow and quick action resources."
  (:require
   [clojure.string :as str]
   [salesforce.api :as api]
   [salesforce.client :as client]))

(defn- action-type-path [action-type]
  (->> (if (sequential? action-type)
         action-type
         (str/split (str action-type) #"/"))
       (map client/path-segment)
       (str/join "/")))

(defn invoke!
  [client-value action-type action-name inputs]
  (api/request! client-value :post
                (str "/actions/" (action-type-path action-type) "/"
                     (client/path-segment action-name))
                {:json-body {:inputs (or inputs [])}}))

(defn invoke-flow!
  ([client-value flow-name]
   (invoke-flow! client-value flow-name []))
  ([client-value flow-name inputs]
   (api/request! client-value :post
                 (str "/actions/custom/flow/" (client/path-segment flow-name))
                 {:json-body {:inputs (or inputs [])}})))

(defn quick-action!
  [client-value action-name context-id record]
  (api/request! client-value :post
                (str "/quickActions/" (client/path-segment action-name))
                {:json-body {:contextId context-id :record record}}))
