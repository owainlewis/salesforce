(ns salesforce.auth
  "OAuth helpers for Salesforce External Client Apps and legacy Connected Apps."
  (:require
   [cheshire.core :as json]
   [clojure.string :as str]
   [salesforce.client :as client])
  (:import
   [java.net URI URLEncoder]
   [java.nio.charset StandardCharsets]))

(defn- oauth-base-url [login-host]
  (let [host (or login-host "login.salesforce.com")
        candidate (if (re-find #"(?i)^https?://" host) host (str "https://" host))
        ^URI uri (try (URI. candidate) (catch Exception _ nil))]
    (when-not (and uri
                   (= "https" (some-> (.getScheme uri) str/lower-case))
                   (.getHost uri)
                   (nil? (.getUserInfo uri))
                   (or (str/blank? (.getPath uri)) (= "/" (.getPath uri)))
                   (nil? (.getQuery uri))
                   (nil? (.getFragment uri)))
      (throw (ex-info "Salesforce OAuth host must be an HTTPS origin"
                      {:type ::invalid-oauth-host})))
    (str "https://" (.getAuthority uri))))

(defn- decode-response [response]
  (let [body (:body response)
        body (cond
               (nil? body) nil
               (string? body) body
               :else (String. ^bytes body StandardCharsets/UTF_8))]
    (when-not (str/blank? body)
      (try
        (json/parse-string body true)
        (catch Exception _
          (if (<= 200 (or (:status response) 0) 299)
            (throw (ex-info "Unable to decode Salesforce OAuth response"
                            {:type ::oauth-decode-error
                             :status (:status response)}))
            {:malformed-response true}))))))

(defn token!
  "Requests an OAuth token.

  grant-params must include :grant_type and the fields required by that flow.
  Options accepts :login-host and :http-options."
  ([grant-params]
   (token! grant-params nil))
  ([grant-params {:keys [login-host http-options]}]
   (let [url (str (oauth-base-url login-host) "/services/oauth2/token")
         response (try
                    (client/*http-request*
                     (merge http-options
                            {:method :post
                             :url url
                             :form-params grant-params
                             :accept :json
                             :as :byte-array
                             :follow-redirects false
                             :redirect-strategy :none
                             :throw-exceptions false}))
                    (catch Exception cause
                      (throw (ex-info "Salesforce OAuth request failed"
                                      {:type ::transport-error :url url}
                                      cause))))
         body (decode-response response)]
     (if (<= 200 (or (:status response) 0) 299)
       body
       (throw (ex-info (or (:error_description body) "Salesforce OAuth request failed")
                       {:type ::oauth-error
                        :status (:status response)
                        :error (:error body)
                        :error-description (:error_description body)
                        :malformed-response (:malformed-response body)}))))))

(defn password-token!
  "Uses the legacy username-password flow. Prefer authorization code with PKCE,
  client credentials, or JWT bearer for new integrations."
  [{:keys [client-id client-secret username password security-token login-host]}]
  (token! {:grant_type "password"
           :client_id client-id
           :client_secret client-secret
           :username username
           :password (str password security-token)}
          {:login-host login-host}))

(defn refresh-token!
  [{:keys [client-id client-secret refresh-token login-host]}]
  (token! (cond-> {:grant_type "refresh_token"
                   :client_id client-id
                   :refresh_token refresh-token}
            client-secret (assoc :client_secret client-secret))
          {:login-host login-host}))

(defn client-credentials-token!
  [{:keys [client-id client-secret login-host]}]
  (token! {:grant_type "client_credentials"
           :client_id client-id
           :client_secret client-secret}
          {:login-host login-host}))

(defn jwt-bearer-token!
  [{:keys [assertion login-host]}]
  (token! {:grant_type "urn:ietf:params:oauth:grant-type:jwt-bearer"
           :assertion assertion}
          {:login-host login-host}))

(defn authorization-code-token!
  [{:keys [client-id client-secret code redirect-uri code-verifier login-host]}]
  (token! (cond-> {:grant_type "authorization_code"
                   :client_id client-id
                   :code code
                   :redirect_uri redirect-uri}
            client-secret (assoc :client_secret client-secret)
            code-verifier (assoc :code_verifier code-verifier))
          {:login-host login-host}))

(defn authorization-url
  "Builds an authorization endpoint URL. Supply :code-challenge for PKCE."
  [{:keys [client-id redirect-uri state scope code-challenge login-host]}]
  (let [params (cond-> {:response_type "code"
                        :client_id client-id
                        :redirect_uri redirect-uri}
                 state (assoc :state state)
                 scope (assoc :scope (if (sequential? scope) (str/join " " scope) scope))
                 code-challenge (assoc :code_challenge code-challenge
                                       :code_challenge_method "S256"))
        encode #(URLEncoder/encode (str %) StandardCharsets/UTF_8)]
    (str (oauth-base-url login-host) "/services/oauth2/authorize?"
         (str/join "&" (map (fn [[key value]]
                              (str (encode (name key)) "=" (encode value)))
                            params)))))

(defn revoke!
  [{:keys [token login-host http-options]}]
  (let [response (client/*http-request*
                  (merge http-options
                         {:method :post
                          :url (str (oauth-base-url login-host) "/services/oauth2/revoke")
                          :form-params {:token token}
                          :follow-redirects false
                          :redirect-strategy :none
                          :throw-exceptions false}))]
    (when-not (<= 200 (or (:status response) 0) 299)
      (throw (ex-info "Salesforce token revocation failed"
                      {:type ::oauth-error :status (:status response)})))
    nil))
