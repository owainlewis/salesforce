# Contributing

## Local checks

Install Java 11 or newer, Leiningen 2.11.2 or newer, and clj-kondo.

```bash
lein test
lein with-profile +clojure-1.12 test
lein cljfmt check
lein check
clj-kondo --lint src test
lein jar
```

Tests must not require Salesforce credentials. Bind `salesforce.client/*http-request*` and assert the complete outgoing request.

## Pull requests

- Preserve the `salesforce.core` compatibility surface unless the change is explicitly major-version work.
- Add exact request and response tests for new endpoints.
- Keep path locators opaque. Do not reconstruct Salesforce pagination URLs.
- Do not include access tokens, client secrets, assertions, or refresh tokens in exceptions or logs.
- Document API version and org feature assumptions.
- Update `docs/API_COVERAGE.md` when support boundaries change.

For server acceptance checks, configure the protected `salesforce-test` GitHub environment and run the manual `Salesforce live smoke` workflow. It uses client credentials and performs read-only resource, limit, and SOQL calls.

## Releases

Releases are tag-gated. They do not publish from every master build.

1. Set a non-snapshot project version.
2. Run all local checks.
3. Merge the release commit.
4. Create a matching `vX.Y.Z` tag.
5. The protected `clojars` GitHub environment publishes after checking that the tag and project version match.

The release environment requires `CLOJARS_USERNAME` and `CLOJARS_PASSWORD` secrets.
