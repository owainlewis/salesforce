(ns salesforce.client-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [salesforce.client :as client])
  (:import
   [java.io ByteArrayInputStream]))

(def test-client
  (client/client {:instance-url "https://example.my.salesforce.com/"
                  :access-token "secret"
                  :headers {"Sforce-Call-Options" "client=test"}}))

(defn utf8-bytes [^String value]
  (.getBytes value "UTF-8"))

(deftest client-configuration-test
  (is (= "https://example.my.salesforce.com" (:instance-url test-client)))
  (is (= "67.0" (:api-version test-client)))
  (is (= "dynamic" (client/access-token
                    (client/client {:instance-url "https://example.com"
                                    :token-provider #(identity {:access_token "dynamic"})}))))
  (is (= "a%20b%2Fc" (client/path-segment "a b/c")))
  (is (= "/services/data/v67.0/limits" (client/api-path test-client "/limits")))
  (is (thrown? clojure.lang.ExceptionInfo
               (client/client {:instance-url "http://example.com"
                               :access-token "unsafe"})))
  (is (thrown? clojure.lang.ExceptionInfo
               (client/client {:instance-url "http://example.com"
                               :access-token "unsafe"
                               :allow-insecure? true})))
  (is (:salesforce/client
       (client/client {:instance-url "http://127.0.0.1"
                       :access-token "test"
                       :allow-insecure? true}))))

(deftest json-request-and-observability-test
  (let [seen (atom nil)
        response (binding [client/*http-request*
                           (fn [request]
                             (reset! seen request)
                             {:status 200
                              :headers {"Content-Type" "application/json;charset=UTF-8"
                                        "Sforce-Limit-Info"
                                        "api-usage=11/15000; per-app-api-usage=3/250"
                                        "Sforce-Request-Id" "req-1"}
                              :body (utf8-bytes "{\"ok\":true}")})]
                   (client/request-response! test-client :post "/services/data/v67.0/example"
                                             {:json-body {:name "Acme"}
                                              :headers {"If-Match" "etag"
                                                        "Authorization" "Bearer override"}}))]
    (is (= 200 (:status response)))
    (is (= {:ok true} (:body response)))
    (is (= "req-1" (:request-id response)))
    (is (= {:api-usage {:used 11 :available 15000}
            :per-app-api-usage {:used 3 :available 250}}
           (:limit-info response)))
    (is (= :post (:method @seen)))
    (is (= "https://example.my.salesforce.com/services/data/v67.0/example"
           (:url @seen)))
    (is (= "Bearer secret" (get-in @seen [:headers "Authorization"])))
    (is (= "client=test" (get-in @seen [:headers "Sforce-Call-Options"])))
    (is (= "etag" (get-in @seen [:headers "If-Match"])))
    (is (= "application/json" (get-in @seen [:headers "Content-Type"])))
    (is (false? (:follow-redirects @seen)))
    (is (= "{\"name\":\"Acme\"}" (:body @seen)))))

(deftest response-content-test
  (testing "204 responses return nil"
    (is (nil? (binding [client/*http-request* (constantly {:status 204 :headers {} :body (byte-array 0)})]
                (client/request! test-client :delete "/record")))))
  (testing "text and bytes remain available"
    (is (= "a,b\n1,2"
           (binding [client/*http-request* (constantly {:status 200
                                                        :headers {"Content-Type" "text/csv"}
                                                        :body (utf8-bytes "a,b\n1,2")})]
             (client/request! test-client :get "/csv" {:as :text}))))
    (let [body (binding [client/*http-request* (constantly {:status 200
                                                            :headers {"Content-Type" "application/octet-stream"}
                                                            :body (byte-array [1 2 3])})]
                 (client/request! test-client :get "/blob" {:as :bytes}))]
      (is (= [1 2 3] (vec body))))))

(deftest structured-error-test
  (let [error (try
                (binding [client/*http-request*
                          (constantly {:status 400
                                       :headers {"Content-Type" "application/json"
                                                 "Sforce-Request-Id" "req-error"}
                                       :body (utf8-bytes "[{\"errorCode\":\"INVALID_FIELD\",\"message\":\"No field\",\"fields\":[\"Bad__c\"]}]")})]
                  (client/request! test-client :get "/bad"))
                (catch clojure.lang.ExceptionInfo exception exception))]
    (is (= :salesforce.client/api-error (:type (ex-data error))))
    (is (= 400 (:status (ex-data error))))
    (is (= "INVALID_FIELD" (:error-code (ex-data error))))
    (is (= "No field" (.getMessage ^Exception error)))
    (is (= ["Bad__c"] (:fields (ex-data error))))
    (is (= "req-error" (:request-id (ex-data error))))
    (is (not (re-find #"secret" (pr-str (ex-data error)))))))

(deftest malformed-response-test
  (let [decode-error (try
                       (binding [client/*http-request*
                                 (constantly {:status 200
                                              :headers {"Content-Type" "application/json"}
                                              :body (utf8-bytes "not-json")})]
                         (client/request! test-client :get "/malformed"))
                       (catch clojure.lang.ExceptionInfo exception exception))]
    (is (= :salesforce.client/decode-error (:type (ex-data decode-error))))
    (is (= "not-json" (:body (ex-data decode-error)))))
  (let [api-error (try
                    (binding [client/*http-request*
                              (constantly {:status 502
                                           :headers {"Content-Type" "application/json"}
                                           :body (utf8-bytes "upstream failure")})]
                      (client/request! test-client :get "/malformed-error"))
                    (catch clojure.lang.ExceptionInfo exception exception))]
    (is (= :salesforce.client/api-error (:type (ex-data api-error))))
    (is (= "upstream failure" (:body (ex-data api-error))))))

(deftest structured-stream-and-bytes-error-test
  (let [stream (ByteArrayInputStream.
                (utf8-bytes "[{\"errorCode\":\"BULK_ERROR\",\"message\":\"Bad bulk request\"}]"))
        stream-error (try
                       (binding [client/*http-request*
                                 (constantly {:status 400
                                              :headers {"Content-Type" "application/json"}
                                              :body stream})]
                         (client/request! test-client :get "/bulk" {:as :stream}))
                       (catch clojure.lang.ExceptionInfo exception exception))]
    (is (= "BULK_ERROR" (:error-code (ex-data stream-error))))
    (is (= 0 (.available stream))))
  (let [bytes-error (try
                      (binding [client/*http-request*
                                (constantly {:status 400
                                             :headers {"Content-Type" "application/json"}
                                             :body (utf8-bytes "[{\"errorCode\":\"BYTE_ERROR\",\"message\":\"Bad bytes\"}]")})]
                        (client/request! test-client :get "/bytes" {:as :bytes}))
                      (catch clojure.lang.ExceptionInfo exception exception))]
    (is (= "BYTE_ERROR" (:error-code (ex-data bytes-error))))))

(deftest transport-and-origin-safety-test
  (let [transport-error (try
                          (binding [client/*http-request* (fn [_] (throw (java.net.SocketTimeoutException. "late")))]
                            (client/request! test-client :get "/slow"))
                          (catch clojure.lang.ExceptionInfo exception exception))]
    (is (= :salesforce.client/transport-error (:type (ex-data transport-error))))
    (is (instance? java.net.SocketTimeoutException (.getCause ^Exception transport-error))))
  (let [origin-error (try
                       (client/request! test-client :get "https://attacker.example/path")
                       (catch clojure.lang.ExceptionInfo exception exception))]
    (is (= :salesforce.client/cross-origin-request (:type (ex-data origin-error))))
    (is (not (re-find #"secret" (pr-str (ex-data origin-error)))))))
