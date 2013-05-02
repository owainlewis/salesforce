;; *****************************************
;;
;; Salesforce API wrapper
;;
;; 2013 Owain Lewis <owain@owainlewis.com>
;;
;; See README.md for documentation
;;
;; *****************************************

(ns salesforce.core
  (:require
    [cheshire.core :as json]
    [clj-http.client :as http]))

(def ^:dynamic +token+ nil)

(defmacro with-token
  [token & body]
  `(binding [+token+ ~token]
     (do ~@body)))

(defn ^:private as-json
  "Takes a Clojure map and returns a JSON string"
  [map]
  (json/generate-string map))

;; Salesforce config variables
;; ******************************************************************************

(def ^:dynamic conf
  (ref (binding [*read-eval* false]
         (with-open [r (clojure.java.io/reader "settings.clj")]
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

(defn token [auth] (:access_token auth))

(defn instance-url [auth] (:instance_url auth))

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

;; Version information

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

(defn version
  "Get the Salesforce API version"
  [token]
  (let [response (request :get "/services/data/" token)]
    (if-let [version ((comp :version first) response)]
      version)))

(defonce ^:dynamic +version+ "")

(defmacro with-version [token & body]
  `(binding [+version+ (version ~token)]
     (do ~@body)))

(defmacro with-explicit-version [v & forms]
  `(binding [+version+ v] (do ~@forms)))

;; Resources

(defn resources [token]
  (with-version token
    (request :get (format "/services/data/v%s/" +version+) token)))

(defn s-objects [token]
  (with-version token
    (request :get (format "/services/data/v%s/sobjects/" +version+) token)))

(defn s-object-names
  "Returns the name of the sobject and the url"
  [token]
  (->> (s-objects token)
       :sobjects
       (map (juxt :name (comp :sobject :urls)))))

;; Core API methods
;; ******************************************************************************

(defn so->get
  "Fetch a single SObject or passing in a vector of attributes
   return a subset of the data"
  ([sobject identifier fields token]
     (let [params (->> (into [] (interpose "," fields))
                       (clojure.string/join)
                       (conj ["?fields="])
                       (apply str))]
  (with-version token
    (let [uri (format "/services/data/v%s/sobjects/%s/%s%s"
                 +version+ sobject identifier params)
          response (request :get uri token)]
      (dissoc response :attributes)))))
  ([sobject identifier token]
  (with-version token
    (request :get
      (format "/services/data/v%s/sobjects/%s/%s" +version+ sobject identifier) token))))

(comment
  ;; Fetch all the info
  (so->get "Account" "001i0000007nAs3" auth-info)
  ;; Fetch only the name and website attribute
  (so->get "Account" "001i0000007nAs3" ["Name" "Website"] auth-info))

(defn so->describe
  "Describe an SObject"
  [sobject token]
  (with-version token
    (request :get
      (format "/services/data/v%s/sobjects/%s/describe" +version+ sobject) token)))

(comment
  (describe "Account" token))

(defn so->create
  "Create a new record"
  [type record token]
  (let [params
    { :form-params record
      :content-type :json }]
    (with-version token
      (request :post
        (format "/services/data/v%s/sobjects/Account/" +version+) token params))))

(comment
  (create "Account" {:Name "My account"} (auth! @conf)))

(defn so->update [])

(defn so->delete
  "Delete a record
   - sojbect the name of the object i.e Account
   - identifier the object id
   - token your api auth info"
  [sobject identifier token]
  (with-version token
    (request :delete
      (format "/services/data/v%s/sobjects/%s/%s" +version+ sobject identifier)
      token)))

(comment
  (delete "Account" "001i0000008Ge2OAAS" (auth! @conf)))

(defn recent-items
  "Returns recently created items for an SObject"
  [sobject token]
  (let [response (so->get sobject token)]
    (:recentItems response)))

;; Salesforce Object Query Language
;; *******************************************************

(defn ^:private gen-query-url
  "Given an SOQL string, i.e \"SELECT name from Account\"
   generate a Salesforce SOQL query url in the form:
   /services/data/v20.0/query/?q=SELECT+name+from+Account"
  [version query]
  (let [url  (format "/services/data/v%s/query" version)
        soql (->> (clojure.string/split query #"\s+")
                   (interpose "+")
                   clojure.string/join)]
    (apply str [url "?q=" soql])))

(defn soql
  "Executes an arbitrary SOQL query
   i.e SELECT name from Account"
  [query token]
  (with-version token
    (request :get (gen-query-url +version+ query) token)))

(comment
  (soql "SELECT name from Account" (auth! @conf)))

