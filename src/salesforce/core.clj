(ns salesforce.core
  "Backward-compatible facade for salesforce-clj 1.x.

  New applications should create a salesforce.client/client and use the focused
  salesforce.api, salesforce.sobject, salesforce.query, salesforce.composite and
  salesforce.bulk2 namespaces."
  (:require
   [cheshire.core :as json]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [salesforce.actions :as actions]
   [salesforce.api :as api]
   [salesforce.auth :as auth]
   [salesforce.client :as client]
   [salesforce.query :as query]
   [salesforce.sobject :as sobject])
  (:import
   [java.net URLEncoder]
   [java.nio.charset StandardCharsets]
   [java.time Instant LocalDate ZonedDateTime]))

(def ^:dynamic +token+ nil)

(defmacro with-token [token & forms]
  `(binding [+token+ ~token]
     (do ~@forms)))

(defn conf [file]
  (ref (binding [*read-eval* false]
         (with-open [reader (io/reader file)]
           (read (java.io.PushbackReader. reader))))))

(defn auth!
  "Legacy username-password OAuth flow. Prefer functions in salesforce.auth."
  [config]
  (auth/password-token! config))

(def ^:private limit-info (atom {}))

(declare +version+)

(defn read-limit-info
  "Returns legacy API usage as {:used n :available n}."
  []
  (or (:api-usage @limit-info)
      (first (vals @limit-info))
      {}))

(defn- legacy-client [token]
  (client/client {:instance-url (or (:instance_url token) (:instance-url token))
                  :access-token (or (:access_token token) (:access-token token))
                  :api-version @+version+
                  :limit-info limit-info}))

(defn request
  "Makes an HTTP request and returns its decoded body.

  This keeps the 1.x signature. New code should use salesforce.client/request-response!
  when status and headers matter."
  [method url token & [params]]
  (let [params (or params {})
        params (if (and (= :json (:content-type params)) (:form-params params))
                 (-> params
                     (assoc :json-body (:form-params params))
                     (dissoc :form-params))
                 params)]
    (client/request! (legacy-client token) method url params)))

(defn all-versions
  "Lists Salesforce REST API versions over HTTPS."
  []
  (let [response (client/*http-request*
                  {:method :get
                   :url "https://login.salesforce.com/services/data/"
                   :as :text
                   :throw-exceptions false})]
    (if (<= 200 (or (:status response) 0) 299)
      (json/parse-string (:body response))
      (throw (ex-info "Unable to discover Salesforce API versions"
                      {:type ::version-discovery-error
                       :status (:status response)})))))

(defn latest-version []
  (get (last (all-versions)) "version"))

;; The legacy facade deliberately retains the 1.x default. Modern clients default
;; to the current v67.0 through salesforce.client/default-api-version.
(defonce ^:dynamic +version+ (atom "39.0"))

(defn set-version! [version]
  (reset! +version+ version))

(def latest-version* (memoize latest-version))

(defmacro with-latest-version [& forms]
  `(binding [+version+ (atom (latest-version*))]
     (do ~@forms)))

(defmacro with-version [version & forms]
  `(binding [+version+ (atom ~version)]
     (do ~@forms)))

(defn resources [token]
  (api/resources (legacy-client token)))

(defn so->objects [token]
  (sobject/objects (legacy-client token)))

(defn so->all [sobject-name token]
  (sobject/object-info (legacy-client token) sobject-name))

(defn so->recent [sobject-name token]
  (sobject/recent (legacy-client token) sobject-name))

(defn so->get
  ([sobject-name identifier fields token]
   (when (or (seq? fields) (vector? fields))
     (dissoc (sobject/get-record (legacy-client token)
                                 sobject-name identifier fields)
             :attributes)))
  ([sobject-name identifier token]
   (sobject/get-record (legacy-client token) sobject-name identifier)))

(defn so->describe [sobject-name token]
  (sobject/describe (legacy-client token) sobject-name))

(defn so->create [sobject-name record token]
  (sobject/create-record! (legacy-client token) sobject-name record))

(defn so->update [sobject-name identifier record token]
  (sobject/update-record! (legacy-client token) sobject-name identifier record))

(defn so->delete [sobject-name identifier token]
  (sobject/delete-record! (legacy-client token) sobject-name identifier))

(defn so->flow
  ([identifier token]
   (actions/invoke-flow! (legacy-client token) identifier))
  ([identifier token data]
   (api/request! (legacy-client token) :post
                 (str "/actions/custom/flow/" (client/path-segment identifier))
                 {:json-body (or data {:inputs []})})))

(defn gen-query-url
  ([version soql]
   (gen-query-url version "query" soql))
  ([version type soql]
   (str "/services/data/v" version "/" type "?q="
        (URLEncoder/encode (str soql) StandardCharsets/UTF_8))))

(defprotocol SOQLable
  (->soql [value] "Serializes a value as a SOQL literal."))

(extend-protocol SOQLable
  String (->soql [value] (query/->soql value))
  clojure.lang.Ratio (->soql [value] (query/->soql value))
  Number (->soql [value] (query/->soql value))
  Boolean (->soql [value] (str "'" value "'"))
  nil (->soql [_] "'null'")
  LocalDate (->soql [value] (str "'" value "'"))
  Instant (->soql [value] (str "'" value "'"))
  ZonedDateTime (->soql [value] (str "'" (.toInstant value) "'"))
  clojure.lang.IPersistentSet
  (->soql [value] (str "(" (str/join "," (sort (map ->soql value))) ")"))
  clojure.lang.Sequential
  (->soql [value] (str "(" (str/join "," (map ->soql value)) ")")))

(defn sqlvec->query
  [[soql & values]]
  (when (or (not (string? soql)) (str/blank? soql))
    (throw (ex-info "A non-empty SOQL query string is required"
                    {:type ::invalid-query})))
  (loop [characters (seq soql)
         values (map ->soql values)
         quoted? false
         escaped? false
         result (StringBuilder.)]
    (if-let [character (first characters)]
      (if (and (= character \?) (not quoted?))
        (if-let [value (first values)]
          (recur (next characters) (next values) quoted? false (.append result value))
          (throw (ex-info "Not enough values for SOQL placeholders"
                          {:type ::invalid-query})))
        (let [quote? (and (= character \') (not escaped?))
              escaped-next? (and (= character \\) (not escaped?))]
          (.append result character)
          (recur (next characters)
                 values
                 (if quote? (not quoted?) quoted?)
                 escaped-next?
                 result)))
      (do
        (when (seq values)
          (throw (ex-info "Too many values for SOQL placeholders"
                          {:type ::invalid-query})))
        (str result)))))

(defprotocol Soql
  (soql [query token] "Executes an arbitrary SOQL query."))

(extend-protocol Soql
  String
  (soql [soql-string token]
    (request :get (gen-query-url @+version+ soql-string) token))
  clojure.lang.Sequential
  (soql [sqlvec token]
    (soql (sqlvec->query sqlvec) token)))

(defn sosl [search-string token]
  (request :get (gen-query-url @+version+ "search" search-string) token))
