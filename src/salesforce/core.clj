;; *****************************************
;;
;; Salesforce API wrapper
;;
;; See README.md for documentation
;;
;; *****************************************

(ns salesforce.core
  (:require
    [clojure.string :as str]
    [cheshire.core :as json]
    [clj-http.client :as http]))

(def ^:dynamic +token+ nil)

(defmacro with-token
  [token & forms]
  `(binding [+token+ ~token]
     (do ~@forms)))

(defn ^:private as-json
  "Takes a Clojure map and returns a JSON string"
  [map]
  (json/generate-string map))

;; Salesforce config variables
;; ******************************************************************************

(defn conf [f]
  (ref (binding [*read-eval* false]
         (with-open [r (clojure.java.io/reader f)]
           (read (java.io.PushbackReader. r))))))

;; Authentication and request helpers
;; ******************************************************************************

(defn auth!
  "Get security token and auth info from Salesforce
   config is a map in the form
   - client-id ID
   - client-secret SECRET
   - username USERNAME
   - password PASSWORD
   - security-token TOKEN"
  [{:keys [client-id client-secret username password security-token]}]
     (let [auth-url "https://login.salesforce.com/services/oauth2/token"
           params {:grant_type "password"
                   :client_id client-id
                   :client_secret client-secret
                   :username username
                   :password (str password security-token)
                   :format "json"}
           resp (http/post auth-url {:form-params params})]
       (-> (:body resp)
           (json/decode true))))


;; HTTP request functions
;; ******************************************************************************

(defn ^:private request
  "Make a HTTP request to the Salesforce.com REST API
   Token is the full map returned from (auth! @conf)"
  [method url token & params]
  (let [base-url (:instance_url token)
        full-url (str base-url url)]
    (->
      (http/request
        (merge (or (first params) {})
          {:method method
           :url full-url
           :headers {"Authorization" (str "Bearer " (:access_token token))}}))
      :body
      (json/decode true))))

(defn ^:private safe-request
  "Perform a request but catch any exceptions"
  [method url token & params]
  (try
    (with-meta
      (request method url token params)
      {:method method :url url :token token})
  (catch Exception e (.toString e))))

;; API
;; ******************************************************************************

;; Salesforce API version information

(defn all-versions
  "Lists all available versions of the Salesforce REST API"
  []
  (->> (http/get "http://na1.salesforce.com/services/data/")
       :body
       (json/parse-string)))

(defn latest-version
  "What is the latest API version?"
  []
  (->> (last (all-versions))
       (map (fn [[k _]] [(keyword k) _]))
       (into {})
       :version))

(defonce ^:dynamic +version+ (atom "27.0"))

(defn set-version! [v]
  (reset! +version+ (atom v)))

(def latest-version*
  "Memoized latest-version, used by (with-latest-version) macro"
  (memoize latest-version))

(defmacro with-latest-version [& forms]
  `(binding [+version+ (latest-version*)]
     (do ~@forms)))

(defmacro with-version [v & forms]
  `(binding [+version+ (atom ~v)] (do ~@forms)))

;; Resources

(defn resources [token]
  (request :get (format "/services/data/v%s/" @+version+) token))

;; Core API methods
;; ******************************************************************************

(defn so->objects
  "Lists all of the available sobjects"
  [token]
  (request :get (format "/services/data/v%s/sobjects" @+version+) token))

(defn so->all
  "All sobjects i.e (so->all \"Account\" auth-info)"
  [sobject token]
  (request :get (format "/services/data/v%s/sobjects/%s" @+version+ sobject) token))

(defn so->recent
  "The recently created items under an sobject identifier
   e.g (so->recent \"Account\" auth-info)"
  [sobject token]
  (:recentItems (so->all sobject token)))

(defn so->get
  "Fetch a single SObject or passing in a vector of attributes
   return a subset of the data"
  ([sobject identifier fields token]
     (when (or (seq? fields) (vector? fields))
       (let [params (->> (into [] (interpose "," fields))
                         (str/join)
                         (conj ["?fields="])
                         (apply str))
             uri (format "/services/data/v%s/sobjects/%s/%s%s"
                   @+version+ sobject identifier params)
             response (request :get uri token)]
         (dissoc response :attributes))))
  ([sobject identifier token]
    (request :get
     (format "/services/data/v%s/sobjects/%s/%s" @+version+ sobject identifier) token)))

(comment
  ;; Fetch all the info
  (so->get "Account" "001i0000007nAs3" auth)
  ;; Fetch only the name and website attribute
  (so->get "Account" "001i0000007nAs3" ["Name" "Website"] auth))

(defn so->describe
  "Describe an SObject"
  [sobject token]
  (request :get
    (format "/services/data/v%s/sobjects/%s/describe" @+version+ sobject) token))

(comment
  (so->describe "Account" auth))

(defn so->create
  "Create a new record"
  [type record token]
  (let [params
    { :form-params record
      :content-type :json }]
    (request :post
      (format "/services/data/v%s/sobjects/Account/" @+version+) token params)))

(comment
  (so->create "Account" {:Name "My new account"} auth))

(defn so->update [])

(defn so->delete
  "Delete a record
   - sojbect the name of the object i.e Account
   - identifier the object id
   - token your api auth info"
  [sobject identifier token]
  (request :delete
    (format "/services/data/v%s/sobjects/%s/%s" @+version+ sobject identifier)
    token))

(comment
  (so->delete "Account" "001i0000008Ge2OAAS" auth))

;; Salesforce Object Query Language
;; *******************************************************

(defn ^:private gen-query-url
  "Given an SOQL string, i.e \"SELECT name from Account\"
   generate a Salesforce SOQL query url in the form:
   /services/data/v20.0/query/?q=SELECT+name+from+Account"
  [version query]
  (let [url  (format "/services/data/v%s/query" version)
        soql (->> (str/split query #"\s+")
                  (interpose "+")
                  str/join)]
    (apply str [url "?q=" soql])))

(defn soql
  "Executes an arbitrary SOQL query
   i.e SELECT name from Account"
  [query token]
  (request :get (gen-query-url @+version+ query) token))

(comment
  (soql "SELECT name from Account" auth))

