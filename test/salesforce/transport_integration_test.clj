(ns salesforce.transport-integration-test
  (:require
   [clojure.test :refer [deftest is use-fixtures]]
   [salesforce.client :as client])
  (:import
   [com.sun.net.httpserver HttpExchange HttpHandler HttpServer]
   [java.net InetSocketAddress]
   [java.nio.charset StandardCharsets]))

(defonce ^:private server (atom nil))
(defonce ^:private base-url (atom nil))
(defonce ^:private authorization (atom nil))

(defn- handler [status content-type body]
  (reify HttpHandler
    (^void handle [_ ^HttpExchange exchange]
      (reset! authorization (.getFirst (.getRequestHeaders exchange) "Authorization"))
      (when content-type
        (.add (.getResponseHeaders exchange) "Content-Type" content-type))
      (if (nil? body)
        (.sendResponseHeaders exchange status -1)
        (let [payload (.getBytes ^String body StandardCharsets/UTF_8)]
          (.sendResponseHeaders exchange status (alength payload))
          (with-open [output (.getResponseBody exchange)]
            (.write output payload)))))))

(defn- server-fixture [run-tests]
  (let [http-server (HttpServer/create (InetSocketAddress. 0) 0)]
    (.createContext http-server "/json"
                    (handler 200 "application/json" "{\"message\":\"hello\"}"))
    (.createContext http-server "/empty" (handler 204 nil nil))
    (.createContext http-server "/error"
                    (handler 429 "application/json"
                             "[{\"errorCode\":\"REQUEST_LIMIT_EXCEEDED\",\"message\":\"Slow down\"}]"))
    (.createContext http-server "/redirect"
                    (reify HttpHandler
                      (^void handle [_ ^HttpExchange exchange]
                        (.add (.getResponseHeaders exchange) "Location"
                              "http://127.0.0.1:9/stolen")
                        (.sendResponseHeaders exchange 302 -1))))
    (.start http-server)
    (reset! server http-server)
    (reset! base-url (str "http://127.0.0.1:" (.getPort (.getAddress http-server))))
    (try
      (run-tests)
      (finally
        (.stop http-server 0)
        (reset! server nil)))))

(use-fixtures :once server-fixture)

(defn- test-client []
  (client/client {:instance-url @base-url
                  :access-token "integration-token"
                  :allow-insecure? true}))

(deftest actual-http-json-and-empty-response-test
  (is (= {:message "hello"} (client/request! (test-client) :get "/json")))
  (is (= "Bearer integration-token" @authorization))
  (is (nil? (client/request! (test-client) :delete "/empty"))))

(deftest actual-http-error-test
  (let [error (try
                (client/request! (test-client) :get "/error")
                (catch clojure.lang.ExceptionInfo exception exception))]
    (is (= 429 (:status (ex-data error))))
    (is (= "REQUEST_LIMIT_EXCEEDED" (:error-code (ex-data error))))
    (is (= "Slow down" (.getMessage ^Exception error))))
  (let [redirect-error (try
                         (client/request! (test-client) :get "/redirect")
                         (catch clojure.lang.ExceptionInfo exception exception))]
    (is (= :salesforce.client/api-error (:type (ex-data redirect-error))))
    (is (= 302 (:status (ex-data redirect-error))))))
