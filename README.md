# Salesforce for Clojure

[![CI](https://github.com/owainlewis/salesforce/actions/workflows/ci.yml/badge.svg)](https://github.com/owainlewis/salesforce/actions/workflows/ci.yml)
[![Clojars Project](https://img.shields.io/clojars/v/com.owainlewis/salesforce.svg)](https://clojars.org/com.owainlewis/salesforce)

A modern Clojure client for Salesforce Platform REST APIs.

Version 2 targets Salesforce API v67.0, Summer '26. It provides first-class clients for core data, SOQL and SOSL, Composite APIs, Bulk API 2.0, Actions, Tooling, and GraphQL. A generic versioned request function can access REST resources that use the supported JSON, text, CSV, binary, or streaming request shapes.

The 1.x `salesforce.core` API remains available as a compatibility facade. Its default stays at v39.0 to avoid silently changing existing applications. New clients default to v67.0.

See [API coverage](docs/API_COVERAGE.md) for the exact scope and [migration](docs/MIGRATION.md) for 1.x upgrades.

## Install

Version 2 is not released yet. The current stable Clojars release is the legacy 1.x client:

Leiningen:

```clojure
[com.owainlewis/salesforce "1.0.2"]
```

Clojure CLI:

```clojure
com.owainlewis/salesforce {:mvn/version "1.0.2"}
```

To test version 2 from source, clone this repository, run `lein install`, and use `[com.owainlewis/salesforce "2.0.0-SNAPSHOT"]` locally. The examples below describe version 2.

The project supports Clojure 1.11 and 1.12 on Java 11, 17, and 21.

## Create a client

If your application already has an OAuth access token:

```clojure
(require '[salesforce.client :as sf])

(def client
  (sf/client {:instance-url "https://your-domain.my.salesforce.com"
              :access-token "access-token"
              :api-version "67.0"}))
```

Long-running applications can resolve tokens on demand:

```clojure
(def client
  (sf/client {:instance-url "https://your-domain.my.salesforce.com"
              :token-provider #(load-current-access-token)}))
```

The provider may return a token string or a map containing `:access-token` or `:access_token`.

## OAuth

New Salesforce integrations should use an External Client App and an appropriate OAuth flow. The library supports authorization code with PKCE, refresh token, JWT bearer, client credentials, and the legacy username-password flow.

```clojure
(require '[salesforce.auth :as auth])

(def token
  (auth/client-credentials-token!
   {:client-id "client-id"
    :client-secret "client-secret"
    :login-host "my-domain.my.salesforce.com"}))

(def client (sf/client token))
```

The password flow remains available through `auth/password-token!` and the legacy `salesforce.core/auth!`, but Salesforce recommends stronger flows for new integrations.

## sObjects

```clojure
(require '[salesforce.sobject :as sobject])

(sobject/objects client)
(sobject/describe client "Account")
(sobject/get-record client "Account" "001..." ["Id" "Name"])
(sobject/create-record! client "Account" {:Name "Acme"})
(sobject/update-record! client "Account" "001..." {:Name "New name"})
(sobject/delete-record! client "Account" "001...")

(sobject/upsert-by-external-id!
 client "Account" "External_Id__c" "customer-42" {:Name "Acme"})
```

Record, object, field, action, and external ID path components are percent-encoded.

## SOQL, SOSL, and pagination

```clojure
(require '[salesforce.query :as query])

(def first-page
  (query/query client "SELECT Id, Name FROM Account ORDER BY Name"))

;; Salesforce nextRecordsUrl values are followed verbatim.
(into [] (query/records client first-page))

(query/query-all client "SELECT Id FROM Account WHERE IsDeleted = TRUE")
(query/search client "FIND {Acme} IN NAME FIELDS RETURNING Account(Id, Name)")
```

Parameterized SOQL keeps values separate from query structure:

```clojure
(query/sqlvec->query
 ["SELECT Id FROM Account WHERE Name = ? AND Active__c = ?" "O'Brien" true])
```

It produces correctly escaped SOQL literals and ignores `?` characters inside quoted strings.

## Composite APIs

```clojure
(require '[salesforce.composite :as composite])

(composite/composite!
 client
 [{:method "POST"
   :url "/services/data/v67.0/sobjects/Account"
   :referenceId "account"
   :body {:Name "Acme"}}
  {:method "POST"
   :url "/services/data/v67.0/sobjects/Contact"
   :referenceId "contact"
   :body {:LastName "Smith" :AccountId "@{account.id}"}}]
 true)
```

The library supports Composite, Batch, Graph, sObject Tree, and sObject Collections. It validates Salesforce request count limits before sending calls.

## Bulk API 2.0

```clojure
(require '[salesforce.bulk2 :as bulk])

(def job
  (bulk/create-ingest-job!
   client {:object "Account"
           :operation "insert"
           :contentType "CSV"
           :lineEnding "LF"}))

(bulk/upload-csv! client (:id job) "Name\nAcme\n")
(bulk/close-ingest-job! client (:id job))

(bulk/wait-for-job! #(bulk/ingest-job client (:id job)))

;; Returns an InputStream by default.
(bulk/ingest-results client (:id job) :successful)
```

Bulk query results return a response envelope so callers can stream CSV and read the opaque `sforce-locator` header:

```clojure
(bulk/query-results client job-id {:max-records 10000})
```

For parallel downloads, use `bulk/parallel-result-pages`, follow its opaque `nextRecordsUrl` with `bulk/parallel-result-pages-more`, and download each `resultUrl` with `bulk/download-result-page`.

## Any REST resource

The generic API is the forward-compatibility layer:

```clojure
(require '[salesforce.api :as api])

;; /services/data/v67.0/limits
(api/request! client :get "/limits")

;; A new JSON endpoint can work without a library update.
(api/request! client :post "/newResource" {:json-body {:enabled true}})

;; Instance-relative Apex REST.
(api/raw-request! client :get "/services/apexrest/orders/42")
```

Absolute URLs are accepted only when they have the same origin as the configured instance. This prevents bearer tokens being sent to another host.

## Responses and errors

High-level functions return the decoded body. Use the transport when you also need status and headers:

```clojure
(require '[salesforce.client :as sf])

(sf/request-response! client :get "/services/data/v67.0/limits")
;; {:status 200
;;  :headers {...}
;;  :body {...}
;;  :request-id "..."
;;  :limit-info {:api-usage {:used 11 :available 15000}}}
```

JSON is keywordized. Empty 204 responses return `nil`. Text, byte arrays, and streams are available with `:as :text`, `:as :bytes`, and `:as :stream`.

Non-2xx responses throw `ExceptionInfo`. `ex-data` includes `:status`, `:error-code`, `:message`, `:fields`, `:request-id`, response headers, and the decoded body. Access tokens are never included.

The client has conservative connect and socket timeouts. Automatic retries are intentionally not applied to mutations because replaying a Salesforce create can duplicate data. Add bounded retry logic around operations that are safe for your application.

## Legacy API

Existing code can continue to use:

```clojure
(use 'salesforce.core)

(def token (auth! config))
(with-version "67.0"
  (so->get "Account" "001..." token))
```

All public 1.x vars and arities remain present. Error handling is now structured and empty successful responses correctly return `nil`. See [migration notes](docs/MIGRATION.md).

## Development

```bash
lein test
lein with-profile +clojure-1.12 test
lein cljfmt check
lein check
clj-kondo --lint src test
lein jar
```

The manual `Salesforce live smoke` GitHub workflow runs read-only discovery, limits, and SOQL checks against a configured test org.

See [CONTRIBUTING.md](CONTRIBUTING.md) for release and pull request guidance.

## License

Distributed under the Eclipse Public License, the same as Clojure.
