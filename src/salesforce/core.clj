(ns salesforce.core
  (:require
    [cheshire.core :as json]
    [clj-http.client :as http]))

(def ^:dynamic +token+ nil)

(defmacro with-token
  [token & body]
  `(binding [+token+ ~token]
     (do ~@body)))

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

(defn token [] (:access_token (auth! @conf)))

(defn ^:private request
  "Make a HTTP request to the Salesforce.com REST API
   Token is the full map returned from (auth! @conf)"
  [method url token]
  (let [base-url (:instance_url token)
        full-url (str base-url url)]
    (->
      (http/request
        {:method method
         :url full-url
         :headers {"Authorization" (str "Bearer " (:access_token token))}})
      :body
      (json/decode true))))

;; API
;; ******************************************************************************

(defn instance-url [token]
  (:instance_url token))

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

(defn object
  "Fetch a single SObject"
  [sobject token]
  (with-version token
    (request :get
      (format "/services/data/v%s/sobjects/%s" +version+ sobject) token)))

(defn object-describe
  "Describe an SObject"
  [sobject token]
  (with-version token
    (request :get
      (format "/services/data/v%s/sobjects/%s/describe" +version+ sobject) token)))

(comment
  (object-describe "Account" token))

(defn recent-items
  "Returns recently created items for an SObject"
  [sobject token]
  (let [response (object sobject token)]
    (:recentItems response)))

;; Salesforce Object Query Language

(defn gen-query-url
  "Given an SOQL string, i.e \"SELECT name from Account\"
   generate a Salesforce SOQL query url in the form:
   /services/data/v20.0/query/?q=SELECT+name+from+Account"
  [query]
  (let [url  (format "/services/data/v%s/query/" +version+)
        soql (->> (clojure.string/split query #"\s+")
                   (interpose "+")
                   clojure.string/join)]
    (apply str [url "?q=" soql])))

(defn execute-soql
  "Executes an arbitrary SOQL query
   i.e SELECT name from Account"
  [query token]
  (with-version token
    (request :get (gen-query-url query) token)))

