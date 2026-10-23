# Verification and delivery status

Date: 23 October 2026. This file distinguishes implemented/tested behavior from external prerequisites and remaining scope. No production database was provisioned and no deployment occurred.

## Executed checks

- Java 21 / Spring Boot 3.5.16 compilation and Maven packaging succeeded.
- 26 backend tests passed: 11 domain tests, 9 PostgreSQL integration tests, 3 real-source snapshot parser tests, 2 PDF/storage tests and 1 real local Mailpit SMTP test.
- PostgreSQL 17 Testcontainers executed Flyway, persistence, duplicate/revision handling, full-text Cyrillic search, public review gating, cross-user company/saved isolation, source failure/recovery and notification deduplication tests.
- Opt-in live TED and EU Funding smoke tests each passed separately. These are deliberately skipped in the ordinary offline suite. Live network verification does not assert commercial reuse permission or complete collection.
- Frontend TypeScript and ESLint passed. Three React component tests passed.
- Seven Playwright tests passed: four flows against a running API/disposable PostgreSQL (landing → explorer, registration validation, anonymous private-page handling, and registration → company profile → dashboard → logout), plus three explicitly mocked browser-fixture tests covering search/type filter requests, detail saving and saved-workflow update/removal.
- Next.js production build passed using Webpack. Turbopack failed to start its CSS worker on this Windows host both inside and outside the sandbox; scripts now use the supported Webpack option.
- `npm audit` reported zero vulnerabilities after updating Vitest to 5.0.5.
- Local frontend and backend were started and the landing page was visually inspected in the in-app browser. Test databases are disposable and separate from the database the user will provision.

## Phase coverage

| Phase | Delivered | Qualification |
|---|---|---|
| 0 Foundation | Monorepo, Spring/Next configuration, Compose, environment template, migrations, health | Runtime verified with disposable PostgreSQL; user's database connection still pending |
| 1 Data model | Users, companies, sources, opportunities, JSONB type metadata, documents, provenance, revisions, runs, matches, saves and notifications | Parameterized JDBC projections are used alongside Spring Data JPA; not an all-JPA model |
| 2 Ingestion | EU/TED connectors, conservative IPARD adapter, checkpoints, idempotency, revisions, safe HTTP and source health | EU APIs live-tested; IPARD snapshot-tested, access/reuse still gated; ESJN uses reviewed official export imports |
| 3 Explorer | Search/filter/sort/pagination, source links, detail/provenance/documents | Some metadata remains unknown where extraction is not reliable |
| 4 Workspace | Sessions/CSRF, registration, profiles, saves, workflow states | Tested end to end; sessions are in memory |
| 5 Matching | Configurable relevance factors, independent eligibility, explanations, company matches | Initial heuristic weights are not empirically validated; no eligibility guarantee |
| 6 Operations | Sources, sync history, review, CSV/XLSX mapping/preview/confirm, manual entry, preferences and email jobs | Review queue/run lists currently return latest 100; no full administrative history pagination |
| 7 Hardening | Ownership tests, constrained URLs, input limits, SMTP tests, CI and deployment guide | Controlled pilot only; deployment and source-policy review remain operator tasks |

## Remaining limitations from the full master brief

- IPARD broad reuse permission and ESJN downloadable export format/access were not established. No access-control bypass was attempted. The complete “one permitted Macedonian automatic source” acceptance criterion is **not yet met**.
- TED currently normalizes a conservative field subset. It preserves source metadata but does not reconcile amendments across publication identifiers, multiple lots, every deadline variant or funding amount ranges into dedicated structures.
- EU Funding preserves all published deadline dates and uses their last date to avoid showing an expired call as open. Date-only/midnight metadata is not treated as a confirmed submission time; reminders require a verified exact `deadline_at`. Program codes are retained when a display label is unavailable.
- Documents default to source links. Administrators can upload a permitted official PDF, extract its digital text, and store a copy for safe retrieval after publication review. Automatic bulk document downloading and OCR are not enabled. Scanned PDFs require manual review; extracted text does not automatically become a verified deadline or eligibility claim.
- No ETag/Last-Modified cache yet. The source client retries selected failures and rejects unapproved redirects; infrastructure egress controls are still recommended for DNS-rebinding defense.
- Matching recalculation is synchronous and batched, suitable for a small pilot; larger datasets need a persisted incremental recalculation queue. Complex turnover, consortium composition and past-performance rules still require human verification.
- Notification outbox keys prevent normal duplicate queueing. SMTP cannot guarantee exactly-once delivery across a crash after acceptance; SENDING records require operator inspection. Change alerts survive delayed approval and are bounded to 20 per preparation pass. Historical initial-import flooding is limited by publication/creation windows and capped digests; calls without a reliable recent publication timestamp are excluded from new-match digests.
- Dashboard metrics/recommendations, saved-item type filtering and reminder interval selection are implemented. Broad mobile/browser/accessibility regression coverage remains expansion work.
- Password recovery, email verification, persistent/distributed sessions and multi-instance operation are not included.
- CI configuration was written but has not run on GitHub. Docker Compose configuration validation passed. Dockerfiles and Nginx configuration are supplied; production image deployment and a TLS endpoint were not exercised.

## Before connecting a real database

Use a new dedicated PostgreSQL database/schema. Supply credentials through `.env` or a secret manager, not source files. Run migrations with appropriate permissions, configure secure cookies/HTTPS and bootstrap the first administrator. Then verify the actual source terms and review a small real import batch before opening the pilot to users.
