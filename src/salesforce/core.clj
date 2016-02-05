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

(defn conf [f]
  (ref (binding [*read-eval* false]
         (with-open [r (clojure.java.io/reader f)]
           (read (java.io.PushbackReader. r))))))

(defn auth!
  "Get security token and auth info from Salesforce
   config is a map in the form
   - client-id ID
   - client-secret SECRET
   - username USERNAME
   - password PASSWORD
   - security-token TOKEN
   - sandbox? IS_SANDBOX
   - sandbox-url URL"
  [{:keys [client-id client-secret username password security-token sandbox? sandbox-url]}]
     (let [login-host (if sandbox? (or sandbox-url "test.salesforce.com") "login.salesforce.com")
           auth-url (format "https://%s/services/oauth2/token" login-host)
           params {:grant_type "password"
                   :client_id client-id
                   :client_secret client-secret
                   :username username
                   :password (str password security-token)
                   :format "json"}
           resp (http/post auth-url {:form-params params})]
       (-> (:body resp)
           (json/decode true))))

(def ^:private limit-info (atom {}))

(defn- parse-limit-info [v]
  (let [[used available]
        (->> (-> (str/split v #"=")
                 (second)
                 (str/split #"/"))
             (map #(Integer/parseInt %)))]
    {:used used
     :available available}))

(defn read-limit-info
  "Deref the value of the `limit-info` atom which is
   updated with each call to the API. Returns a map,
   containing the used and available API call counts:
   {:used 11 :available 15000}"
  []
  @limit-info)

(defn ^:private request
  "Make a HTTP request to the Salesforce.com REST API
   Token is the full map returned from (auth! @conf)"
  [method url token & params]
  (let [base-url (:instance_url token)
        full-url (str base-url url)
        resp (http/request
               (merge (or (first params) {})
                      {:method method
                       :url full-url
                       :headers {"Authorization" (str "Bearer " (:access_token token))}}))]
    (-> (get-in resp [:headers "sforce-limit-info"]) ;; Record limit info in atom
        (parse-limit-info)
        ((partial reset! limit-info)))
    (-> resp
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
  (reset! +version+ v))

(def latest-version*
  "Memoized latest-version, used by (with-latest-version) macro"
  (memoize latest-version))

(defmacro with-latest-version [& forms]
  `(binding [+version+ (atom (latest-version*))]
     (do ~@forms)))

(defmacro with-version [v & forms]
  `(binding [+version+ (atom ~v)] (do ~@forms)))

;; Resources

(defn resources [token]
  (request :get (format "/services/data/v%s/" @+version+) token))

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
  [sobject record token]
  (let [params
    { :form-params record
      :content-type :json }]
    (request :post
      (format "/services/data/v%s/sobjects/%s/" @+version+ sobject) token params)))

(comment
  (so->create "Account" {:Name "My new account"} auth))

(defn so->update 
  "Update a record
   - sojbect the name of the object i.e Account
   - identifier the object id
   - record map of data to update object with
   - token your api auth info"
  [sobject identifier record token]
  (let [params
    { :body (json/generate-string record)
      :content-type :json }]
    (request :patch
      (format "/services/data/v%s/sobjects/%s/%s" @+version+ sobject identifier) 
      token params)))

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

(defn so->flow
  "Invoke a flow (see: https://developer.salesforce.com/docs/atlas.en-us.salesforce_vpm_guide.meta/salesforce_vpm_guide/vpm_distribute_system_rest.htm)
  - indentifier of flow (e.g. \"Escalate_to_Case\")
  - inputs map (e.g. {:inputs [{\"CommentCount\" 6
                                \"FeedItemId\" \"0D5D0000000cfMY\"}]})
  - token to your api auth info"
  [identifier token & [data]]
  (let [params {:body (json/generate-string (or data {:inputs []}))
                :content-type :json}]
    (request :post
      (format "/services/data/v%s/actions/custom/flow/%s" @+version+ identifier)
      token params)))

(comment
  (so->flow "Escalate_to_Case" a {:inputs [{"CommentCount" 6
                                            "FeedItemId" "0D5D0000000cfMY"}]}))

;; Salesforce Object Query Language
;; ------------------------------------------------------------------------------------

(defn ^:private gen-query-url
  "Given an SOQL string, i.e \"SELECT name from Account\"
   generate a Salesforce SOQL query url in the form:
   /services/data/v20.0/query/?q=SELECT+name+from+Account"
  [version query]
  (let [url  (format "/services/data/v%s/query" version)
        soql (java.net.URLEncoder/encode query "UTF-8")]
    (apply str [url "?q=" soql])))

(defn soql
  "Executes an arbitrary SOQL query
   i.e SELECT name from Account"
  [query token]
  (request :get (gen-query-url @+version+ query) token))

(comment
  (soql "SELECT name from Account" auth))

