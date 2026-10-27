# Maintainer guide for AI agents

This repository is maintained by AI agents working on behalf of the owner, Owain Lewis (@owainlewis). This file is the charter: what the project is, what "healthy" means, and what an agent may do without asking.

## The project

salesforce is a Clojure client for the Salesforce Platform REST APIs, published to Clojars as `com.owainlewis/salesforce`. Version 2 (unreleased) adds immutable clients and first-class support for sObjects, SOQL/SOSL, Composite, Bulk API 2.0, Actions, Tooling and GraphQL. `docs/API_COVERAGE.md` is the support contract and `docs/MIGRATION.md` covers 1.x upgrades.

## Compatibility contract

- `salesforce.core` is the 1.x compatibility facade. Its vars, arities, token map keys and v39.0 default must not change.
- The 2.x namespaces (`salesforce.client`, `api`, `auth`, `sobject`, `query`, `composite`, `bulk2`, `actions`, `tooling`, `graphql`) are the API once 2.0.0 is released. Until then they may change, but record every change in `CHANGELOG.md`.
- Behaviour fixes are allowed when the old behaviour contradicts the docstring or README. Record them under `Fixed` in `CHANGELOG.md`.
- To replace a function, add the new one and mark the old one `^:deprecated` with a docstring that names the replacement.
- Supported: Java 11, 17, 21. Clojure 1.11 and 1.12.

## Quality bar for every change

- CI is green: tests on the full matrix, `lein cljfmt check`, `lein check`, clj-kondo at error level, `lein jar`, dependency scan.
- New behaviour and bug fixes come with tests. Endpoint wrappers get an exact request and response contract test that binds `salesforce.client/*http-request*`. Tests never need Salesforce credentials.
- Tokens, secrets and assertions never appear in exceptions or logs.
- `docs/API_COVERAGE.md` is updated when the support boundary changes.
- No new reflection warnings in `src`.
- `CHANGELOG.md` has an entry under `[Unreleased]`.
- README examples still match the code.

## What an agent may do alone

- Triage issues: label (`bug`, `enhancement`, `question`, `docs`, `needs-triage`, `wontfix`), answer questions, ask for reproduction details.
- Close an issue when it is answered, fixed, a duplicate, or has waited 30 days for information the reporter was asked for. Always leave a comment saying why.
- Fix bugs, improve docs and tests, remove reflection, and refactor internals, through a pull request.
- Update dependencies within the same major version, and GitHub Actions versions.
- Merge its own pull request once CI is green and the change is inside this list.
- Merge Dependabot pull requests once CI is green.
- Review outside pull requests, and merge them when they meet the quality bar and fit the contract.
- Keep `ROADMAP.md` current.

## What needs the owner

- Breaking changes and anything labelled `major`.
- Adding a new runtime dependency.
- Releases: pushing a `v*` tag publishes to Clojars. Prepare a release PR (version bump and changelog), then ask the owner to tag.
- Changes to repository settings, secrets or this file's permissions section.
- Running the live smoke workflow, which uses the protected `salesforce-test` environment.

Ask by opening an issue labelled `needs-owner` that states the decision and a recommendation.

## Working loop

Each maintenance run:

1. Check CI on `master`. If it is red, fixing it is the only task.
2. Handle open pull requests: Dependabot first, then outside contributions.
3. Triage issues opened or updated since the last run.
4. Check the weekly "Dependency freshness" run and update dependencies within their major version.
5. Check the Salesforce release notes. When a new API version is generally available, add support, update `default-api-version` for new clients (never the 1.x facade default), and note it in the changelog and coverage doc. The owner runs the "Salesforce live smoke" workflow to confirm against a real org.
6. Take the top unblocked item from `ROADMAP.md` under "Now" and ship it as one focused pull request.
7. Update `ROADMAP.md`.

Keep pull requests small and single-purpose. Explain in the description what changed, why, and how it was tested.

## Commands

```bash
lein test
lein with-profile +clojure-1.12 test
lein cljfmt check
lein check
clj-kondo --lint src test
lein jar
```
