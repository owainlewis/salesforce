# Changelog

## 2.0.0 - Unreleased

### Added

- Immutable per-org clients with API version, token provider, headers, timeouts, and limit tracking
- Structured response envelopes and Salesforce errors
- JSON, text, CSV, byte array, stream, and empty response handling
- OAuth authorization code, PKCE parameters, refresh token, JWT bearer, client credentials, and revocation helpers
- First-class sObject metadata, CRUD, external ID, relationship, blob, deleted, and updated operations
- SOQL, QueryAll, cursor pagination, query plans, SOSL, and parameterized searches
- Composite, Batch, Graph, Tree, and sObject Collections APIs
- Bulk API 2.0 ingest and query lifecycles with streamed CSV results
- Actions, Tooling, GraphQL, and generic future REST resource access
- Java 11, 17, and 21 plus Clojure 1.11 and 1.12 CI coverage

### Changed

- Updated Clojure, clj-http, Cheshire, Jackson, and build tooling
- Replaced CircleCI master deployment with GitHub Actions and protected tag releases
- Fixed JSON create bodies, caller header merging, HTTPS version discovery, URL encoding, and SOQL placeholder parsing
- Non-2xx responses now throw structured `ExceptionInfo`

### Compatibility

- Preserved all public `salesforce.core` 1.x vars and arities
- Preserved the legacy v39.0 default while new clients default to v67.0
