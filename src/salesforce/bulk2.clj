(ns salesforce.bulk2
  "Bulk API 2.0 ingest and query job lifecycle. CSV results can be streamed."
  (:require
   [salesforce.api :as api]
   [salesforce.client :as client]))

(def ^:private terminal-states
  #{"JobComplete" "Failed" "Aborted"})

(defn create-ingest-job!
  [client-value job]
  (api/request! client-value :post "/jobs/ingest" {:json-body job}))

(defn ingest-jobs
  ([client-value]
   (ingest-jobs client-value nil))
  ([client-value parameters]
   (api/request! client-value :get "/jobs/ingest" {:query-params parameters})))

(defn ingest-job [client-value job-id]
  (api/request! client-value :get
                (str "/jobs/ingest/" (client/path-segment job-id))))

(defn upload-csv!
  "Uploads CSV data to an open ingest job. data may be a String, byte array or stream."
  [client-value job-id data]
  (api/request! client-value :put
                (str "/jobs/ingest/" (client/path-segment job-id) "/batches")
                {:raw-body data
                 :content-type "text/csv"
                 :accept "application/json"}))

(defn set-ingest-state! [client-value job-id state]
  (api/request! client-value :patch
                (str "/jobs/ingest/" (client/path-segment job-id))
                {:json-body {:state state}}))

(defn close-ingest-job! [client-value job-id]
  (set-ingest-state! client-value job-id "UploadComplete"))

(defn abort-ingest-job! [client-value job-id]
  (set-ingest-state! client-value job-id "Aborted"))

(defn delete-ingest-job! [client-value job-id]
  (api/request! client-value :delete
                (str "/jobs/ingest/" (client/path-segment job-id))))

(defn ingest-results
  "Downloads a successful, failed, or unprocessed CSV result."
  ([client-value job-id result-type]
   (ingest-results client-value job-id result-type {:as :stream}))
  ([client-value job-id result-type options]
   (let [resource (case result-type
                    :successful "successfulResults"
                    :failed "failedResults"
                    :unprocessed "unprocessedrecords"
                    (throw (ex-info "Unknown ingest result type"
                                    {:type ::invalid-result-type
                                     :result-type result-type})))]
     (api/request! client-value :get
                   (str "/jobs/ingest/" (client/path-segment job-id) "/" resource)
                   (merge {:accept "text/csv"} options)))))

(defn create-query-job! [client-value job]
  (api/request! client-value :post "/jobs/query" {:json-body job}))

(defn query-jobs
  ([client-value]
   (query-jobs client-value nil))
  ([client-value parameters]
   (api/request! client-value :get "/jobs/query" {:query-params parameters})))

(defn query-job [client-value job-id]
  (api/request! client-value :get
                (str "/jobs/query/" (client/path-segment job-id))))

(defn abort-query-job! [client-value job-id]
  (api/request! client-value :patch
                (str "/jobs/query/" (client/path-segment job-id))
                {:json-body {:state "Aborted"}}))

(defn delete-query-job! [client-value job-id]
  (api/request! client-value :delete
                (str "/jobs/query/" (client/path-segment job-id))))

(defn query-results
  "Returns a response envelope so callers can read Sforce-Locator and stream CSV."
  ([client-value job-id]
   (query-results client-value job-id nil))
  ([client-value job-id {:keys [locator max-records as]
                         :or {as :stream}}]
   (api/request-response! client-value :get
                          (str "/jobs/query/" (client/path-segment job-id) "/results")
                          {:query-params (cond-> {}
                                           locator (assoc :locator locator)
                                           max-records (assoc :maxRecords max-records))
                           :accept "text/csv"
                           :as as})))

(defn parallel-result-pages
  "Lists up to five Bulk query result URLs that can be downloaded in parallel."
  [client-value job-id]
  (api/request! client-value :get
                (str "/jobs/query/" (client/path-segment job-id) "/resultPages")))

(defn parallel-result-pages-more
  "Follows the opaque nextRecordsUrl from parallel-result-pages."
  [client-value next-records-url]
  (api/raw-request! client-value :get next-records-url))

(defn download-result-page
  "Downloads a resultUrl returned by parallel-result-pages."
  ([client-value result-url]
   (download-result-page client-value result-url {:as :stream}))
  ([client-value result-url options]
   (api/raw-request! client-value :get result-url
                     (merge {:accept "text/csv"} options))))

(defn wait-for-job!
  "Polls job-fn until a terminal Bulk state is reached or timeout expires.

  Options: :timeout-ms, :poll-ms, :sleep-fn and :clock-fn."
  ([job-fn]
   (wait-for-job! job-fn nil))
  ([job-fn {:keys [timeout-ms poll-ms sleep-fn clock-fn]
            :or {timeout-ms 600000
                 poll-ms 1000
                 sleep-fn #(Thread/sleep (long %))
                 clock-fn #(System/currentTimeMillis)}}]
   (let [started-at (clock-fn)]
     (loop []
       (let [job (job-fn)]
         (cond
           (terminal-states (:state job)) job
           (>= (- (clock-fn) started-at) timeout-ms)
           (throw (ex-info "Timed out waiting for Salesforce Bulk job"
                           {:type ::timeout :last-job job :timeout-ms timeout-ms}))
           :else
           (do (sleep-fn poll-ms)
               (recur))))))))
