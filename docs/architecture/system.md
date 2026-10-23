# Architecture

The system is a modular monolith: one Spring API, one Next.js frontend and one PostgreSQL database. It targets a single API instance on a Hetzner VPS. No message broker or AI service is required.

## Responsibilities

- `auth`, `config`: account registration, BCrypt hashing, session login, CSRF, roles and first-admin bootstrap.
- `company`: ownership checks and profile persistence. One company is shown by the UI; the database permits multiple companies per owner.
- `opportunity`: paginated full-text queries, detail DTO projections, saved application workflows and dashboard aggregates.
- `source`: source-specific TED, Funding and IPARD connectors and an allowlisted outbound HTTP client.
- `ingestion`: source leases, run reports, checkpoints, per-record transactions, hashes and revisions.
- `matching`: deterministic relevance with independent eligibility assessment.
- `admin`: source configuration, CSV/XLSX preview/confirm and publication review.
- `notification`: preferences, daily notification preparation and SMTP outbox delivery.
- `extraction`, `storage`: bounded digital PDF extraction and replaceable filesystem storage.

Controllers do request binding; services implement domain actions. Read projections and PostgreSQL-specific writes use parameterized Spring JDBC. Spring Data JPA is configured and validates its entity mapping; it is deliberately not used to model every PostgreSQL JSONB/query projection. This is a pragmatic deviation from an all-JPA data layer.

## Ingestion

Scheduler selects due enabled/verified sources. A conditional database lease prevents overlapping normal runs. A run fetches at most five pages, validates and normalizes records, then writes each transactionally. The next checkpoint is saved only after a page persists without rejections. Failed pages can be retried safely. Unique `(source_id, external_id)` and advisory transaction locks serialize record writes.

Snapshots are SHA-256 hashed with sorted JSON map keys. Changes add a revision and reset review to PENDING. Failed runs preserve previous records. No source-removal heuristic deletes publications. Documents are linked, and only allowlisted official hosts are accepted. An administrator reviews provenance and publishes or rejects the record.

Tender/grant-specific optional metadata is JSONB (`details`) rather than many mostly-null columns. Monetary values use NUMERIC; known instants use timestamptz. Missing/ambiguous dates are not guessed. Full source metadata can remain in the revision snapshot. Public status is derived separately from stated status when a known deadline is past.

## Matching

Profile changes and publication review recalculate matches. Keywords, industry, country preference, type preference, same-currency budget and CPV contribute configurable initial weights (`app.matching.*-weight`, validated to total 100). No exchange rate is guessed. Eligibility remains UNKNOWN when country eligibility is unspecified. Published country, organization-type, size and consortium exclusions override relevance; incomplete conditions yield POSSIBLY_ELIGIBLE, never a guarantee. Explanations identify the fields that contributed.

## Security and deployment

The browser uses one origin and HttpOnly session cookies. CSRF tokens are obtained from `/auth/csrf` for each mutation. No browser-stored bearer tokens. Admin paths and documentation require ADMIN; company and saved-item writes enforce ownership in SQL/service checks. Upstream redirect following is disabled. URL allowlists are static code, not editable administrator input.

HTTPS terminates at Nginx; API and web ports bind to loopback. This first implementation keeps sessions and login rate-limit windows in memory. Run one API instance. Use database least privilege, TLS and infrastructure egress restrictions. A source domain resolving to private IPs is rejected; infrastructure egress rules provide defense against DNS rebinding.
