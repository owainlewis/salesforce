(ns salesforce.core-test
  (:use clojure.test
        salesforce.core))

(def sample-auth
  {:id "https://login.salesforce.com/id/1234",
   :issued_at "1367488271359",
   :instance_url "https://na15.salesforce.com",
   :signature "SIG",
   :access_token "ACCESS"})

(deftest token-test
  (testing "should extract the auth token"
    (is (= "ACCESS" (token sample-auth)))))

(deftest instance-url-test
  (testing "should extract the instance url"
    (is (= "https://na15.salesforce.com" (instance-url sample-auth)))))

(deftest gen-query-url-test
  (testing "should generate a valid url for salesforce.com"
    (let [url (gen-query-url "20.0" "SELECT name from Account")]
      (is (= url "/services/data/v20.0/query?q=SELECT+name+from+Account")))))
