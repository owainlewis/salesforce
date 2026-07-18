# Migrating from 1.x

Version 2 preserves the public `salesforce.core` vars, macro behavior, function arities, legacy token map keys, keywordized JSON bodies, and the v39.0 compatibility default.

## Recommended migration

Replace a process-wide mutable version and token map:

```clojure
(use 'salesforce.core)
(set-version! "67.0")
(so->get "Account" id token)
```

with an immutable client:

```clojure
(require '[salesforce.client :as sf]
         '[salesforce.sobject :as sobject])

(def client
  (sf/client {:instance-url (:instance_url token)
              :access-token (:access_token token)
              :api-version "67.0"}))

(sobject/get-record client "Account" id)
```

This isolates API version, token resolution, headers, timeouts, and limit tracking per client.

## Intentional behavior improvements

### HTTP errors

The old transport often returned a decoded Salesforce error body or `nil` after losing the HTTP status. Version 2 throws `ExceptionInfo` for non-2xx responses.

```clojure
(try
  (sobject/get-record client "Account" id)
  (catch clojure.lang.ExceptionInfo error
    (select-keys (ex-data error) [:status :error-code :message :fields :request-id])))
```

### Empty and non-JSON responses

HTTP 204 now returns `nil`. CSV, text, bytes, and streams no longer pass through JSON decoding.

### Request bodies and headers

Create requests now send valid JSON bodies. Caller headers merge with Authorization and defaults instead of being discarded.

### URL safety

Dynamic path segments are percent-encoded. Absolute URLs must match the Salesforce instance origin, which prevents bearer token disclosure.

### SOQL parameters

`salesforce.query/sqlvec->query` escapes strings, emits correct Boolean and null literals, ignores `?` inside quoted strings, and does not use `format`. The legacy `salesforce.core/sqlvec->query` retains historical Boolean, null, and temporal literal formatting for compatibility while fixing placeholder parsing and string escaping.

## Legacy auth

`salesforce.core/auth!` still uses the username-password flow. It delegates to `salesforce.auth/password-token!`.

New applications should select an External Client App flow suitable for the deployment:

- Authorization code with PKCE for interactive users
- Client credentials for approved server-to-server integrations
- JWT bearer where certificate-based delegation is appropriate
- Refresh token for continued access after an interactive grant

## Removed internals

No public 1.x vars were removed. The private `safe-request` helper was removed because it was unused, passed options incorrectly, and could attach tokens to metadata.
