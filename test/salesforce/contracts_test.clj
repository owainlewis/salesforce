(ns salesforce.contracts-test
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
   [salesforce.query :as query]
   [salesforce.sobject :as sobject]
   [salesforce.tooling :as tooling]))

(def test-client
  (client/client {:instance-url "https://example.my.salesforce.com"
                  :access-token "token"
                  :api-version "67.0"}))

(defn- json-response [body]
  {:status 200
   :headers {"Content-Type" "application/json"}
   :body (.getBytes ^String (json/generate-string body) "UTF-8")})

(defn- capture
  ([call] (capture call {}))
  ([call response-body]
   (let [seen (atom nil)]
     (binding [client/*http-request* (fn [request]
                                       (reset! seen request)
                                       (json-response response-body))]
       (call))
     @seen)))

(defn- raw-path [request]
  (.getRawPath (java.net.URI. (:url request))))

(defn- decoded-body [request]
  (some-> (:body request) (json/parse-string true)))

(deftest discovery-contract-test
  (is (= "/services/data/" (raw-path (capture #(api/versions test-client)))))
  (is (= "/services/data/v67.0/" (raw-path (capture #(api/resources test-client)))))
  (is (= "/services/data/v67.0/limits" (raw-path (capture #(api/limits test-client)))))
  (is (= "/services/oauth2/userinfo" (raw-path (capture #(api/identity test-client)))))
  (is (= "67.0"
         (binding [client/*http-request*
                   (constantly (json-response [{:version "66.0"} {:version "67.0"}]))]
           (api/latest-version test-client)))))

(deftest complete-sobject-contract-test
  (let [cases [{:call #(sobject/object-info test-client "Account")
                :method :get :path "/services/data/v67.0/sobjects/Account"}
               {:call #(sobject/recent test-client "Account")
                :method :get :path "/services/data/v67.0/sobjects/Account"}
               {:call #(sobject/describe test-client "Account")
                :method :get :path "/services/data/v67.0/sobjects/Account/describe"}
               {:call #(sobject/get-by-external-id test-client "Account" "Key__c" "a/b")
                :method :get :path "/services/data/v67.0/sobjects/Account/Key__c/a%2Fb"}
               {:call #(sobject/delete-by-external-id! test-client "Account" "Key__c" "a/b")
                :method :delete :path "/services/data/v67.0/sobjects/Account/Key__c/a%2Fb"}
               {:call #(sobject/relationship test-client "Account" "001" "Contacts")
                :method :get :path "/services/data/v67.0/sobjects/Account/001/Contacts"}
               {:call #(sobject/blob test-client "Attachment" "00P" "Body")
                :method :get :path "/services/data/v67.0/sobjects/Attachment/00P/Body"}
               {:call #(sobject/named-layout test-client "Account" "Compact")
                :method :get :path "/services/data/v67.0/sobjects/Account/describe/namedLayouts/Compact"}]]
    (doseq [{:keys [call method path]} cases]
      (let [request (capture call)]
        (is (= method (:method request)) path)
        (is (= path (raw-path request)) path))))
  (let [request (capture #(sobject/get-by-external-id
                           test-client "Account" "Key__c" "key" ["Id" "Name"]))]
    (is (= {:fields "Id,Name"} (:query-params request))))
  (let [request (capture #(sobject/updated test-client "Account" "start" "end"))]
    (is (= "/services/data/v67.0/sobjects/Account/updated" (raw-path request)))
    (is (= {:start "start" :end "end"} (:query-params request)))))

(deftest complete-query-and-search-contract-test
  (let [cases [{:call #(query/query-all test-client "SELECT Id FROM Account")
                :path "/services/data/v67.0/queryAll" :params {:q "SELECT Id FROM Account"}}
               {:call #(query/explain test-client "SELECT Id FROM Account")
                :path "/services/data/v67.0/query" :params {:explain "SELECT Id FROM Account"}}
               {:call #(query/search test-client "FIND {Acme}")
                :path "/services/data/v67.0/search" :params {:q "FIND {Acme}"}}
               {:call #(query/parameterized-search test-client {:q "Acme" :sobject "Account"})
                :path "/services/data/v67.0/parameterizedSearch"
                :params {:q "Acme" :sobject "Account"}}
               {:call #(query/search-scope-and-order test-client)
                :path "/services/data/v67.0/search/scopeOrder" :params nil}
               {:call #(query/suggestions test-client "Account" "Acme" {:limit 5})
                :path "/services/data/v67.0/search/suggestions"
                :params {:q "Acme" :sobject "Account" :limit 5}}]]
    (doseq [{:keys [call path params]} cases]
      (let [request (capture call)]
        (is (= path (raw-path request)) path)
        (is (= params (:query-params request)) path)))))

(deftest complete-composite-contract-test
  (let [records [{:attributes {:type "Account" :referenceId "ref1"} :Name "Acme"}]
        cases [{:call #(composite/graph!
                        test-client [{:graphId "g" :compositeRequest []}])
                :method :post :path "/services/data/v67.0/composite/graph"
                :body {:graphs [{:graphId "g" :compositeRequest []}]}}
               {:call #(composite/tree! test-client "Account" records)
                :method :post :path "/services/data/v67.0/composite/tree/Account"
                :body {:records records}}
               {:call #(composite/create-records! test-client records true)
                :method :post :path "/services/data/v67.0/composite/sobjects"
                :body {:allOrNone true :records records}}
               {:call #(composite/update-records! test-client records true)
                :method :patch :path "/services/data/v67.0/composite/sobjects"
                :body {:allOrNone true :records records}}
               {:call #(composite/upsert-records! test-client "Account" "Key__c" records true)
                :method :patch :path "/services/data/v67.0/composite/sobjects/Account/Key__c"
                :body {:allOrNone true :records records}}
               {:call #(composite/retrieve-records! test-client "Account" ["001" "002"] ["Id" "Name"])
                :method :get :path "/services/data/v67.0/composite/sobjects/Account"
                :params {:ids "001,002" :fields "Id,Name"}}
               {:call #(composite/delete-records! test-client ["001" "002"] true)
                :method :delete :path "/services/data/v67.0/composite/sobjects"
                :params {:ids "001,002" :allOrNone true}}]]
    (doseq [{:keys [call method path body params]} cases]
      (let [request (capture call)]
        (is (= method (:method request)) path)
        (is (= path (raw-path request)) path)
        (when body (is (= body (decoded-body request)) path))
        (when params (is (= params (:query-params request)) path))))))

(defn- nested-tree-record [depth]
  (cond-> {"attributes" {"type" "Account"}}
    (> depth 1) (assoc "Children" {"records" [(nested-tree-record (dec depth))]})))

(deftest tree-limit-test
  (is (thrown-with-msg? clojure.lang.ExceptionInfo #"200 total records"
                        (composite/tree! test-client "Account"
                                         (repeat 201 {"attributes" {"type" "Account"}}))))
  (is (thrown-with-msg? clojure.lang.ExceptionInfo #"five sObject types"
                        (composite/tree! test-client "Account"
                                         (mapv #(hash-map "attributes" {"type" (str "Type" %)})
                                               (range 6)))))
  (is (thrown-with-msg? clojure.lang.ExceptionInfo #"five relationship levels"
                        (composite/tree! test-client "Account" [(nested-tree-record 6)]))))

(deftest complete-bulk-contract-test
  (let [cases [{:call #(bulk2/ingest-jobs test-client {:isPkChunkingEnabled true})
                :method :get :path "/services/data/v67.0/jobs/ingest"
                :params {:isPkChunkingEnabled true}}
               {:call #(bulk2/ingest-job test-client "job")
                :method :get :path "/services/data/v67.0/jobs/ingest/job"}
               {:call #(bulk2/abort-ingest-job! test-client "job")
                :method :patch :path "/services/data/v67.0/jobs/ingest/job"
                :body {:state "Aborted"}}
               {:call #(bulk2/delete-ingest-job! test-client "job")
                :method :delete :path "/services/data/v67.0/jobs/ingest/job"}
               {:call #(bulk2/ingest-results test-client "job" :successful {:as :text})
                :method :get :path "/services/data/v67.0/jobs/ingest/job/successfulResults"}
               {:call #(bulk2/ingest-results test-client "job" :failed {:as :text})
                :method :get :path "/services/data/v67.0/jobs/ingest/job/failedResults"}
               {:call #(bulk2/ingest-results test-client "job" :unprocessed {:as :text})
                :method :get :path "/services/data/v67.0/jobs/ingest/job/unprocessedrecords"}
               {:call #(bulk2/create-query-job! test-client {:query "SELECT Id FROM Account"})
                :method :post :path "/services/data/v67.0/jobs/query"
                :body {:query "SELECT Id FROM Account"}}
               {:call #(bulk2/query-jobs test-client {:jobType "V2Query"})
                :method :get :path "/services/data/v67.0/jobs/query"
                :params {:jobType "V2Query"}}
               {:call #(bulk2/query-job test-client "job")
                :method :get :path "/services/data/v67.0/jobs/query/job"}
               {:call #(bulk2/abort-query-job! test-client "job")
                :method :patch :path "/services/data/v67.0/jobs/query/job"
                :body {:state "Aborted"}}
               {:call #(bulk2/delete-query-job! test-client "job")
                :method :delete :path "/services/data/v67.0/jobs/query/job"}
               {:call #(bulk2/parallel-result-pages test-client "job")
                :method :get :path "/services/data/v67.0/jobs/query/job/resultPages"}
               {:call #(bulk2/parallel-result-pages-more
                        test-client "/services/data/v67.0/jobs/query/job/resultPages?locator=next")
                :method :get :path "/services/data/v67.0/jobs/query/job/resultPages"}
               {:call #(bulk2/download-result-page
                        test-client "/services/data/v67.0/jobs/query/job/results?locator=page" {:as :text})
                :method :get :path "/services/data/v67.0/jobs/query/job/results"}]]
    (doseq [{:keys [call method path body params]} cases]
      (let [request (capture call)]
        (is (= method (:method request)) path)
        (is (= path (raw-path request)) path)
        (when body (is (= body (decoded-body request)) path))
        (when params (is (= params (:query-params request)) path))))))

(deftest actions-and-tooling-contract-test
  (let [cases [{:call #(actions/invoke! test-client "standard" "emailSimple" [])
                :method :post :path "/services/data/v67.0/actions/standard/emailSimple"}
               {:call #(actions/invoke! test-client "custom/apex" "MyAction" [{:x 1}])
                :method :post :path "/services/data/v67.0/actions/custom/apex/MyAction"}
               {:call #(actions/quick-action! test-client "Account.NewContact" "001" {:LastName "Smith"})
                :method :post :path "/services/data/v67.0/quickActions/Account.NewContact"}
               {:call #(tooling/execute-anonymous! test-client "System.debug('ok');")
                :method :get :path "/services/data/v67.0/tooling/executeAnonymous"}
               {:call #(tooling/get-record test-client "ApexClass" "01p")
                :method :get :path "/services/data/v67.0/tooling/sobjects/ApexClass/01p"}
               {:call #(tooling/query-more test-client "/services/data/v67.0/tooling/query/next")
                :method :get :path "/services/data/v67.0/tooling/query/next"}]]
    (doseq [{:keys [call method path]} cases]
      (let [request (capture call)]
        (is (= method (:method request)) path)
        (is (= path (raw-path request)) path)))))

(deftest oauth-contract-test
  (let [flows [{:call #(auth/password-token!
                        {:client-id "id" :client-secret "secret" :username "user"
                         :password "pass" :security-token "token"})
                :grant "password"}
               {:call #(auth/refresh-token!
                        {:client-id "id" :client-secret "secret" :refresh-token "refresh"})
                :grant "refresh_token"}
               {:call #(auth/jwt-bearer-token! {:assertion "jwt"})
                :grant "urn:ietf:params:oauth:grant-type:jwt-bearer"}
               {:call #(auth/authorization-code-token!
                        {:client-id "id" :code "code" :redirect-uri "https://app/callback"
                         :code-verifier "verifier"})
                :grant "authorization_code"}]]
    (doseq [{:keys [call grant]} flows]
      (let [request (capture call {:access_token "new"})]
        (is (= :post (:method request)) grant)
        (is (= "https://login.salesforce.com/services/oauth2/token" (:url request)) grant)
        (is (= grant (get-in request [:form-params :grant_type])) grant)
        (is (false? (:follow-redirects request)) grant))))
  (let [request (capture #(auth/token! {:grant_type "client_credentials"}
                                       {:http-options {:url "https://attacker.example"
                                                       :method :get
                                                       :form-params {:secret "leak"}}})
                         {:access_token "new"})]
    (is (= "https://login.salesforce.com/services/oauth2/token" (:url request)))
    (is (= :post (:method request)))
    (is (= {:grant_type "client_credentials"} (:form-params request))))
  (let [seen (atom nil)]
    (binding [client/*http-request* (fn [request]
                                      (reset! seen request)
                                      {:status 204 :headers {} :body nil})]
      (auth/revoke! {:token "revoke-me"
                     :http-options {:url "https://attacker.example" :method :get}}))
    (is (= "https://login.salesforce.com/services/oauth2/revoke" (:url @seen)))
    (is (= :post (:method @seen)))
    (is (= {:token "revoke-me"} (:form-params @seen))))
  (let [error (try
                (binding [client/*http-request*
                          (constantly {:status 502 :headers {"Content-Type" "text/html"}
                                       :body (.getBytes "gateway failure" "UTF-8")})]
                  (auth/client-credentials-token! {:client-id "id" :client-secret "secret"}))
                (catch clojure.lang.ExceptionInfo exception exception))]
    (is (= :salesforce.auth/oauth-error (:type (ex-data error))))
    (is (= 502 (:status (ex-data error))))
    (is (true? (:malformed-response (ex-data error)))))
  (is (thrown? clojure.lang.ExceptionInfo
               (auth/client-credentials-token!
                {:client-id "id" :client-secret "secret"
                 :login-host "https://login.salesforce.com@attacker.example"})))
  (let [error (try
                (binding [client/*http-request*
                          (constantly {:status 200 :headers {"Content-Type" "application/json"}
                                       :body (.getBytes "{\"access_token\":\"TOPSECRET\"" "UTF-8")})]
                  (auth/client-credentials-token! {:client-id "id" :client-secret "secret"}))
                (catch clojure.lang.ExceptionInfo exception exception))]
    (is (= :salesforce.auth/oauth-decode-error (:type (ex-data error))))
    (is (not (str/includes? (pr-str (Throwable->map error)) "TOPSECRET")))))
