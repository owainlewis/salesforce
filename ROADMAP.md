# Roadmap

Maintained by the project's agents and the owner. See `AGENTS.md` for how items move.

## Now

- Release 2.0.0 (needs the owner): run the live smoke workflow against a test org, set the version, tag `v2.0.0`.
- Track Salesforce's Winter '27 release and move new clients to its API version once it is generally available.

## Next

- Retry with backoff for `REQUEST_LIMIT_EXCEEDED` and 503 responses, opt-in per client.
- Automatic token refresh on 401 when the client has a refresh token or JWT credentials.
- UI API helpers (`ui-api/object-info`, record layouts), which are now common in integrations.
- Lazy Bulk API 2.0 query results as Clojure maps, not only CSV streams.

## Later

- Pub/Sub API (gRPC) as a separate artifact, so the core client stays light.
- Metadata API deploy and retrieve as a separate artifact.

## Done

- 2026-10: agent maintenance charter, dependency freshness report fixed.
- 2026-07: version 2 client: immutable clients, OAuth flows, Composite, Bulk 2.0, Tooling, GraphQL, CI, tag-gated releases.
