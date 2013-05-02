(ns salesforce.core-test
  (:use clojure.test
        salesforce.core))

(defmacro with-private-fns [[ns fns] & tests]
  "Refers private fns from ns and runs tests in context."
  `(let ~(reduce #(conj %1 %2 `(ns-resolve '~ns '~%2)) [] fns)
     ~@tests))

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

;; Private functions

(with-private-fns [salesforce.core [gen-query-url]]
  (deftest gen-query-url-test
    (testing "should generate a valid url for salesforce.com"
      (let [url (gen-query-url "20.0" "SELECT name from Account")]
        (is (= url "/services/data/v20.0/query?q=SELECT+name+from+Account"))))))
