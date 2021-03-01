(ns salesforce.core-test
  (:use clojure.test
        salesforce.core)
  (:import [java.time LocalDate])
  )

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

(deftest with-version-test
  (testing "should set +version+ to a given string"
    (is (= "26.0" (with-version "26.0" @+version+)))))

(deftest soql-test
  (testing "Testing soql protocol"
    (let [gen-query-url-spy (atom nil)]
      (with-redefs [gen-query-url (fn [_ query]
                                    (reset! gen-query-url-spy query))
                    request (fn [& _] nil)]
        (soql ["select * from fruits where name = ? and price >= ? and created = ?" "apple" 9/5 (LocalDate/of 2020 10 10)] nil)
        (is (= "select * from fruits where name = 'apple' and price >= 1.8 and created = '2020-10-10'"
               @gen-query-url-spy) "Using the soql sequential protocol")))
    (let [gen-query-url-spy (atom nil)]
      (with-redefs [gen-query-url (fn [_ query]
                                    (reset! gen-query-url-spy query))
                    request (fn [& _] nil)]
        (soql "select * from fruits where name = 'apple' and price >= 1.8 and created = '2020-10-10'" nil)
        (is (= "select * from fruits where name = 'apple' and price >= 1.8 and created = '2020-10-10'"
               @gen-query-url-spy) "Using the soql String protocol")))
    )
  )

;; Private functions

(with-private-fns [salesforce.core [gen-query-url]]
  (deftest gen-query-url-test
    (testing "should generate a valid url for salesforce.com"
      (let [url (gen-query-url "20.0" "SELECT name from Account")]
        (is (= url "/services/data/v20.0/query?q=SELECT+name+from+Account"))))))

