(ns salesforce.composite
  "Salesforce Composite, Batch, Graph, Tree and sObject Collections APIs."
  (:require
   [clojure.string :as str]
   [salesforce.api :as api]
   [salesforce.client :as client]))

(defn- require-at-most! [label maximum values]
  (when (> (count values) maximum)
    (throw (ex-info (str label " supports at most " maximum " items")
                    {:type ::request-limit
                     :limit maximum
                     :actual (count values)}))))

(defn- value-for [map-value key]
  (or (get map-value key) (get map-value (name key))))

(defn- tree-records-with-depth [records depth]
  (mapcat
   (fn [record]
     (let [child-groups (keep #(when (map? %) (value-for % :records))
                              (vals record))]
       (cons [record depth]
             (mapcat #(tree-records-with-depth % (inc depth)) child-groups))))
   records))

(defn batch!
  ([client-value requests]
   (batch! client-value requests false))
  ([client-value requests halt-on-error]
   (require-at-most! "Composite Batch" 25 requests)
   (api/request! client-value :post "/composite/batch"
                 {:json-body {:haltOnError halt-on-error
                              :batchRequests requests}})))

(defn composite!
  ([client-value requests]
   (composite! client-value requests false))
  ([client-value requests all-or-none]
   (require-at-most! "Composite" 25 requests)
   (let [expensive (filter #(re-find #"/(query|queryAll|composite/sobjects)(?:[/?]|$)"
                                     (or (value-for % :url) ""))
                           requests)]
     (require-at-most! "Composite query and collection subrequests" 5 expensive))
   (api/request! client-value :post "/composite"
                 {:json-body {:allOrNone all-or-none
                              :compositeRequest requests}})))

(defn graph!
  [client-value graphs]
  (require-at-most! "Composite Graph" 75 graphs)
  (let [total (reduce + 0 (map #(count (value-for % :compositeRequest)) graphs))]
    (when (> total 500)
      (throw (ex-info "Composite Graph supports at most 500 total subrequests"
                      {:type ::request-limit :limit 500 :actual total}))))
  (api/request! client-value :post "/composite/graph"
                {:json-body {:graphs graphs}}))

(defn tree!
  [client-value sobject records]
  (let [records-with-depth (tree-records-with-depth records 1)
        record-count (count records-with-depth)
        types (into #{}
                    (keep (fn [[record _]]
                            (some-> (value-for record :attributes)
                                    (value-for :type))))
                    records-with-depth)
        depth (reduce max 0 (map second records-with-depth))]
    (when (> record-count 200)
      (throw (ex-info "sObject Tree supports at most 200 total records"
                      {:type ::request-limit :limit 200 :actual record-count})))
    (when (> (count types) 5)
      (throw (ex-info "sObject Tree supports at most five sObject types"
                      {:type ::request-limit :limit 5 :actual (count types)})))
    (when (> depth 5)
      (throw (ex-info "sObject Tree supports at most five relationship levels"
                      {:type ::request-limit :limit 5 :actual depth}))))
  (api/request! client-value :post
                (str "/composite/tree/" (client/path-segment sobject))
                {:json-body {:records records}}))

(defn create-records!
  ([client-value records]
   (create-records! client-value records false))
  ([client-value records all-or-none]
   (require-at-most! "sObject Collection create" 200 records)
   (api/request! client-value :post "/composite/sobjects"
                 {:json-body {:allOrNone all-or-none :records records}})))

(defn update-records!
  ([client-value records]
   (update-records! client-value records false))
  ([client-value records all-or-none]
   (require-at-most! "sObject Collection update" 200 records)
   (api/request! client-value :patch "/composite/sobjects"
                 {:json-body {:allOrNone all-or-none :records records}})))

(defn upsert-records!
  ([client-value sobject external-id-field records]
   (upsert-records! client-value sobject external-id-field records false))
  ([client-value sobject external-id-field records all-or-none]
   (require-at-most! "sObject Collection upsert" 200 records)
   (api/request! client-value :patch
                 (str "/composite/sobjects/" (client/path-segment sobject) "/"
                      (client/path-segment external-id-field))
                 {:json-body {:allOrNone all-or-none :records records}})))

(defn retrieve-records!
  [client-value sobject ids fields]
  (require-at-most! "sObject Collection retrieve" 2000 ids)
  (api/request! client-value :get
                (str "/composite/sobjects/" (client/path-segment sobject))
                {:query-params {:ids (str/join "," ids)
                                :fields (str/join "," fields)}}))

(defn delete-records!
  ([client-value ids]
   (delete-records! client-value ids false))
  ([client-value ids all-or-none]
   (require-at-most! "sObject Collection delete" 200 ids)
   (api/request! client-value :delete "/composite/sobjects"
                 {:query-params {:ids (str/join "," ids)
                                 :allOrNone all-or-none}})))
