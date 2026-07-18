# API coverage

This library targets Salesforce Platform REST API v67.0, Summer '26.

“Full Salesforce API” is not one protocol or specification. Salesforce publishes separate Platform REST, Connect REST, Bulk, Tooling, UI, GraphQL, Metadata SOAP, Pub/Sub gRPC, Data Cloud, Commerce, and Marketing Cloud APIs. Many resources also depend on org edition, licenses, permissions, and enabled features.

The support contract is:

1. `salesforce.api/request!` can call versioned REST resources that use supported JSON, text, CSV, binary, or streaming request shapes under `/services/data/vXX.X`.
2. `salesforce.api/raw-request!` can call instance-relative Apex REST and other REST paths.
3. High-value API families have focused, tested functions.
4. Separate non-REST protocols are explicit exclusions.

## First-class support

| Family | Namespace | Coverage |
| --- | --- | --- |
| OAuth | `salesforce.auth` | Authorization code, PKCE parameters, refresh token, JWT bearer, client credentials, legacy password, revoke |
| Discovery | `salesforce.api` | Versions, version resources, identity, org limits |
| sObjects | `salesforce.sobject` | Object list and describe, CRUD, fields, external ID get/upsert/delete, deleted/updated windows, relationships, blobs, named layouts |
| Query | `salesforce.query` | SOQL, QueryAll, query-more, lazy pages and records, explain |
| Search | `salesforce.query` | SOSL, parameterized search, scope order, suggestions |
| Composite | `salesforce.composite` | Composite, Batch, Graph, Tree, Collections create/update/upsert/retrieve/delete |
| Bulk API 2.0 | `salesforce.bulk2` | Ingest and query job lifecycle, CSV upload, status polling, serial and parallel result streams, query locators |
| Actions | `salesforce.actions` | Standard/custom actions, Flow, quick actions |
| Tooling | `salesforce.tooling` | Generic resources, SOQL/query-more, execute anonymous, record reads |
| GraphQL | `salesforce.graphql` | Query, variables, operation name, HTTP-200 GraphQL error handling |

Every focused wrapper has a wire-level request contract test. A manual GitHub workflow also runs read-only discovery, limits, and SOQL smoke checks against a configured Salesforce test org.

## Generic REST support

The generic layer covers REST resources that vary by org or are too broad for stable one-line wrappers. Examples include:

- Connect and Chatter
- UI API
- Analytics and Reports
- Knowledge
- Approvals and processes
- Duplicate management and match rules
- Tabs, themes, layouts, and compact layouts
- Record counts and recently viewed records
- Apex REST
- New versioned resources introduced after v67.0

Example:

```clojure
(api/request! client :get "/connect/communities")
(api/request! client :get "/ui-api/object-info/Account")
(api/request! client :post "/analytics/reports/00O..." {:json-body payload})
```

Availability is determined by Salesforce for the current org, API version, user permissions, and licenses.

## Separate protocols and products

These are not claimed as part of this REST client's typed surface:

- Metadata API SOAP operations
- Pub/Sub API gRPC streams and Avro payloads
- Legacy Streaming API CometD subscriptions
- Data Cloud APIs
- Marketing Cloud APIs
- Commerce APIs

They require different transports, schemas, authentication rules, generated clients, or product-specific release cycles. Adding them to this package would blur the support contract and increase dependency weight for every user.

## Version policy

Modern clients default to v67.0. Pin `:api-version` per client in production so Salesforce release changes are deliberate.

The legacy `salesforce.core/+version+` default remains v39.0 for compatibility. Use `set-version!`, `with-version`, or migrate to an immutable client.

Official references:

- [REST API resources](https://developer.salesforce.com/docs/atlas.en-us.api_rest.meta/api_rest/resources_list.htm)
- [Composite resource](https://developer.salesforce.com/docs/atlas.en-us.api_rest.meta/api_rest/resources_composite_composite_post.htm)
- [Bulk API 2.0](https://developer.salesforce.com/docs/atlas.en-us.api_asynch.meta/api_asynch/bulk_api_2_0.htm)
- [GraphQL API](https://developer.salesforce.com/docs/platform/graphql/references)
- [API end-of-life policy](https://developer.salesforce.com/docs/platform/connect-rest-api/guide/intro_api_eol.html)
