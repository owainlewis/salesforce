(ns salesforce.live-smoke-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [salesforce.api :as api]
   [salesforce.auth :as auth]
   [salesforce.client :as client]
   [salesforce.query :as query]))

(defn- environment [name]
  (System/getenv name))

(deftest live-read-only-smoke-test
  (let [login-host (environment "SALESFORCE_LOGIN_HOST")
        client-id (environment "SALESFORCE_CLIENT_ID")
        client-secret (environment "SALESFORCE_CLIENT_SECRET")]
    (if (every? some? [login-host client-id client-secret])
      (let [token (auth/client-credentials-token!
                   {:login-host login-host
                    :client-id client-id
                    :client-secret client-secret})
            salesforce (client/client (assoc token :api-version "67.0"))]
        (testing "v67 resources and limits are available"
          (is (map? (api/resources salesforce)))
          (is (map? (api/limits salesforce))))
        (testing "a read-only SOQL query succeeds"
          (is (true? (:done (query/query salesforce
                                         "SELECT Id FROM Organization LIMIT 1"))))))
      (is true "Live smoke credentials are not configured"))))
