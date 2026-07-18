(ns salesforce.sobject
  "Salesforce sObject metadata and record operations."
  (:require
   [clojure.string :as str]
   [salesforce.api :as api]
   [salesforce.client :as client]))

(defn- segment [value]
  (client/path-segment value))

(defn objects [client-value]
  (api/request! client-value :get "/sobjects"))

(defn object-info [client-value sobject]
  (api/request! client-value :get (str "/sobjects/" (segment sobject))))

(defn recent [client-value sobject]
  (:recentItems (object-info client-value sobject)))

(defn describe [client-value sobject]
  (api/request! client-value :get
                (str "/sobjects/" (segment sobject) "/describe")))

(defn get-record
  ([client-value sobject id]
   (get-record client-value sobject id nil))
  ([client-value sobject id fields]
   (api/request! client-value :get
                 (str "/sobjects/" (segment sobject) "/" (segment id))
                 (when (seq fields) {:query-params {:fields (str/join "," fields)}}))))

(defn create-record! [client-value sobject record]
  (api/request! client-value :post
                (str "/sobjects/" (segment sobject))
                {:json-body record}))

(defn update-record! [client-value sobject id record]
  (api/request! client-value :patch
                (str "/sobjects/" (segment sobject) "/" (segment id))
                {:json-body record}))

(defn delete-record! [client-value sobject id]
  (api/request! client-value :delete
                (str "/sobjects/" (segment sobject) "/" (segment id))))

(defn get-by-external-id
  ([client-value sobject external-id-field external-id]
   (get-by-external-id client-value sobject external-id-field external-id nil))
  ([client-value sobject external-id-field external-id fields]
   (api/request! client-value :get
                 (str "/sobjects/" (segment sobject) "/"
                      (segment external-id-field) "/" (segment external-id))
                 (when (seq fields) {:query-params {:fields (str/join "," fields)}}))))

(defn upsert-by-external-id!
  [client-value sobject external-id-field external-id record]
  (api/request! client-value :patch
                (str "/sobjects/" (segment sobject) "/"
                     (segment external-id-field) "/" (segment external-id))
                {:json-body record}))

(defn delete-by-external-id!
  [client-value sobject external-id-field external-id]
  (api/request! client-value :delete
                (str "/sobjects/" (segment sobject) "/"
                     (segment external-id-field) "/" (segment external-id))))

(defn deleted [client-value sobject start end]
  (api/request! client-value :get
                (str "/sobjects/" (segment sobject) "/deleted")
                {:query-params {:start start :end end}}))

(defn updated [client-value sobject start end]
  (api/request! client-value :get
                (str "/sobjects/" (segment sobject) "/updated")
                {:query-params {:start start :end end}}))

(defn relationship [client-value sobject id relationship-name]
  (api/request! client-value :get
                (str "/sobjects/" (segment sobject) "/" (segment id) "/"
                     (segment relationship-name))))

(defn blob
  "Downloads a blob field as bytes by default. Pass {:as :stream} to stream it."
  ([client-value sobject id field]
   (blob client-value sobject id field {:as :bytes}))
  ([client-value sobject id field options]
   (api/request! client-value :get
                 (str "/sobjects/" (segment sobject) "/" (segment id) "/"
                      (segment field))
                 options)))

(defn named-layout [client-value sobject layout-name]
  (api/request! client-value :get
                (str "/sobjects/" (segment sobject) "/describe/namedLayouts/"
                     (segment layout-name))))
