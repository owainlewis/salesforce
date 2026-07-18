(ns salesforce.api-test
  (:require
   [cheshire.core :as json]
   [clojure.string :as str]
   [clojure.test :refer [deftest is]]
   [salesforce.actions :as actions]
   [salesforce.api :as api]
   [salesforce.auth :as auth]
   [salesforce.bulk2 :as bulk2]
   [salesforce.client :as client]
   [salesforce.composite :as composite]
   [salesforce.graphql :as graphql]
   [salesforce.query :as query]
   [salesforce.sobject :as sobject]
   [salesforce.tooling :as tooling]))

(def test-client
  (client/client {:instance-url "https://example.my.salesforce.com"
                  :access-token "token"
                  :api-version "67.0"}))

(defn response
  ([body] (response 200 body))
  ([status body]
   {:status status
    :headers {"Content-Type" "application/json"}
    :body (.getBytes (json/generate-string body) "UTF-8")}))

(defn request-for [call]
  (let [seen (atom nil)]
    (binding [client/*http-request* (fn [request]
                                      (reset! seen request)
                                      (response {}))]
      (call))
    @seen))

(defn decoded-body [request]
  (some-> (:body request) (json/parse-string true)))

(deftest generic-api-test
  (let [request (request-for #(api/request! test-client :get "/future/resource"
                                            {:query-params {:new-feature true}}))]
    (is (= :get (:method request)))
    (is (= "https://example.my.salesforce.com/services/data/v67.0/future/resource"
           (:url request)))
    (is (= {:new-feature true} (:query-params request))))
  (let [request (request-for #(api/raw-request! test-client :get "/services/apexrest/custom"))]
    (is (= "https://example.my.salesforce.com/services/apexrest/custom" (:url request)))))

(deftest sobject-contract-test
  (is (= "/services/data/v67.0/sobjects"
         (.getPath (java.net.URI. (:url (request-for #(sobject/objects test-client)))))))
  (let [request (request-for #(sobject/get-record test-client "Custom Object__c" "a/b" ["Id" "Name"]))]
    (is (str/ends-with? (:url request) "/sobjects/Custom%20Object__c/a%2Fb"))
    (is (= {:fields "Id,Name"} (:query-params request))))
  (let [request (request-for #(sobject/create-record! test-client "Account" {:Name "Acme"}))]
    (is (= :post (:method request)))
    (is (= {:Name "Acme"} (decoded-body request))))
  (let [request (request-for #(sobject/update-record! test-client "Account" "001" {:Name "New"}))]
    (is (= :patch (:method request)))
    (is (= {:Name "New"} (decoded-body request))))
  (is (= :delete (:method (request-for #(sobject/delete-record! test-client "Account" "001")))))
  (let [request (request-for #(sobject/upsert-by-external-id!
                               test-client "Account" "External_Id__c" "value/1" {:Name "Acme"}))]
    (is (str/ends-with? (:url request) "/sobjects/Account/External_Id__c/value%2F1"))
    (is (= :patch (:method request))))
  (let [request (request-for #(sobject/deleted test-client "Account" "start" "end"))]
    (is (= {:start "start" :end "end"} (:query-params request)))))

(deftest query-and-pagination-test
  (let [request (request-for #(query/query test-client "SELECT Id FROM Account"))]
    (is (= "/services/data/v67.0/query" (.getPath (java.net.URI. (:url request)))))
    (is (= {:q "SELECT Id FROM Account"} (:query-params request))))
  (let [calls (atom [])
        first-page {:done false :records [{:Id "1"}]
                    :nextRecordsUrl "/services/data/v67.0/query/next-1"}]
    (binding [client/*http-request*
              (fn [request]
                (swap! calls conj (:url request))
                (response {:done true :records [{:Id "2"}]}))]
      (is (= ["1" "2"] (mapv :Id (query/records test-client first-page))))
      (is (= ["https://example.my.salesforce.com/services/data/v67.0/query/next-1"]
             @calls))))
  (is (= "Name = 'O\\'Brien' AND Note = '?'"
         (query/sqlvec->query ["Name = ? AND Note = '?'" "O'Brien"])))
  (is (= "Active = TRUE AND Missing = NULL"
         (query/sqlvec->query ["Active = ? AND Missing = ?" true nil]))))

(deftest composite-contract-and-limits-test
  (let [requests [{:method "GET" :url "/services/data/v67.0/sobjects/Account/001"
                   :referenceId "account"}]
        request (request-for #(composite/composite! test-client requests true))]
    (is (= :post (:method request)))
    (is (= {:allOrNone true :compositeRequest requests} (decoded-body request))))
  (let [request (request-for #(composite/batch! test-client [{:method "GET" :url "limits"}] true))]
    (is (= {:haltOnError true
            :batchRequests [{:method "GET" :url "limits"}]}
           (decoded-body request))))
  (is (thrown-with-msg? clojure.lang.ExceptionInfo #"at most 25"
                        (composite/composite! test-client (repeat 26 {}))))
  (is (thrown-with-msg? clojure.lang.ExceptionInfo #"at most 5"
                        (composite/composite!
                         test-client
                         (mapv #(hash-map :url (str "/services/data/v67.0/query?q=" %))
                               (range 6)))))
  (is (thrown-with-msg? clojure.lang.ExceptionInfo #"at most 5"
                        (composite/composite!
                         test-client
                         (mapv #(hash-map "url" (str "/services/data/v67.0/query?q=" %))
                               (range 6)))))
  (is (thrown-with-msg? clojure.lang.ExceptionInfo #"at most 500"
                        (composite/graph!
                         test-client
                         [{:graphId "g" :compositeRequest (repeat 501 {})}])))
  (is (thrown-with-msg? clojure.lang.ExceptionInfo #"at most 500"
                        (composite/graph!
                         test-client
                         [{"graphId" "g" "compositeRequest" (repeat 501 {})}]))))

(deftest bulk-api-contract-test
  (let [request (request-for #(bulk2/create-ingest-job!
                               test-client {:object "Account" :operation "insert"
                                            :contentType "CSV"}))]
    (is (= "/services/data/v67.0/jobs/ingest"
           (.getPath (java.net.URI. (:url request)))))
    (is (= "insert" (:operation (decoded-body request)))))
  (let [request (request-for #(bulk2/upload-csv! test-client "job/1" "Name\nAcme"))]
    (is (= :put (:method request)))
    (is (str/ends-with? (:url request) "/jobs/ingest/job%2F1/batches"))
    (is (= "text/csv" (get-in request [:headers "Content-Type"])))
    (is (= "Name\nAcme" (:body request))))
  (let [request (request-for #(bulk2/close-ingest-job! test-client "job"))]
    (is (= {:state "UploadComplete"} (decoded-body request))))
  (let [request (request-for #(bulk2/query-results test-client "job"
                                                   {:locator "abc" :max-records 500 :as :text}))]
    (is (= {:locator "abc" :maxRecords 500} (:query-params request)))
    (is (= "text/csv" (get-in request [:headers "Accept"]))))
  (let [states (atom [{:state "InProgress"} {:state "JobComplete"}])
        sleeps (atom [])
        result (bulk2/wait-for-job! #(let [state (first @states)]
                                       (swap! states rest)
                                       state)
                                    {:sleep-fn #(swap! sleeps conj %)
                                     :poll-ms 5})]
    (is (= "JobComplete" (:state result)))
    (is (= [5] @sleeps))))

(deftest actions-tooling-and-graphql-test
  (let [request (request-for #(actions/invoke-flow! test-client "My Flow" [{:x 1}]))]
    (is (str/ends-with? (:url request) "/actions/custom/flow/My%20Flow"))
    (is (= {:inputs [{:x 1}]} (decoded-body request))))
  (let [request (request-for #(tooling/query test-client "SELECT Id FROM ApexClass"))]
    (is (str/ends-with? (:url request) "/tooling/query"))
    (is (= {:q "SELECT Id FROM ApexClass"} (:query-params request))))
  (let [request (request-for #(graphql/execute! test-client "query Q { uiapi { apiVersion } }"
                                                {:first 1} "Q"))]
    (is (str/ends-with? (:url request) "/graphql"))
    (is (= {:query "query Q { uiapi { apiVersion } }"
            :variables {:first 1}
            :operationName "Q"}
           (decoded-body request))))
  (let [error (try
                (binding [client/*http-request*
                          (constantly (response {:data nil :errors [{:message "bad"}]}))]
                  (graphql/execute! test-client "bad"))
                (catch clojure.lang.ExceptionInfo exception exception))]
    (is (= :salesforce.graphql/graphql-error (:type (ex-data error))))))

(deftest oauth-flow-test
  (let [seen (atom nil)
        token (binding [client/*http-request*
                        (fn [request]
                          (reset! seen request)
                          (response {:access_token "new" :instance_url "https://example.com"}))]
                (auth/client-credentials-token! {:client-id "id" :client-secret "secret"}))]
    (is (= "new" (:access_token token)))
    (is (= "https://login.salesforce.com/services/oauth2/token" (:url @seen)))
    (is (= {:grant_type "client_credentials"
            :client_id "id"
            :client_secret "secret"}
           (:form-params @seen))))
  (is (str/starts-with?
       (auth/authorization-url {:client-id "id" :redirect-uri "https://app/callback"
                                :code-challenge "challenge"})
       "https://login.salesforce.com/services/oauth2/authorize?"))
  (is (thrown? clojure.lang.ExceptionInfo
               (auth/client-credentials-token! {:client-id "id" :client-secret "secret"
                                                :login-host "http://unsafe.example"}))))
