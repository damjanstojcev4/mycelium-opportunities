# Mycelium Opportunities

A Java 21 / Spring Boot and Next.js workspace for finding and evaluating official tender and grant publications. Built for companies in North Macedonia, with European source adapters.

## Connect your database

You provision PostgreSQL; the application creates its tables with Flyway. No production database or credentials are included.

1. Create an empty PostgreSQL 17 database and a dedicated application user with migration rights on its schema.
2. Copy `.env.example` to `.env` and set `DATABASE_URL`, `DATABASE_USER`, and `DATABASE_PASSWORD`. The URL must be JDBC format, for example `jdbc:postgresql://your-host:5432/mycelium?sslmode=verify-full` (configure trusted certificates for your provider).
3. Set `ADMIN_EMAIL` and a unique `ADMIN_PASSWORD` of at least 12 characters for the first start. Bootstrap creates an administrator only when none exists. Remove those two settings after successful bootstrap.
4. Run `docker compose up --build -d api web` and open http://localhost:3000.
5. Sign in, open Administration, review source permissions, enable verified sources, synchronize, then review imported records before publication.

For a database running directly on your Windows host, use `host.docker.internal` in the Compose API connection string. On a VPS use your actual database hostname. The app does not create the database itself.

## What is implemented

- Public opportunity explorer with PostgreSQL full-text search, filters, sorting, pagination, official links and uncertainty labels.
- Session login, registration, CSRF protection, BCrypt passwords, request rate limiting, administrator authorization and owner-scoped company data.
- Company profiles, deterministic relevance scores, separate eligibility concerns, saved opportunities and application workflow states.
- Source registry, scheduled bounded collection, resumable checkpoints, idempotent writes, revisions, provenance and review queues.
- TED and EU Funding source adapters; conservative IPARD listing adapter gated on access verification; ESJN official export fallback.
- CSV/XLSX preview, column mapping, validation, import reports and manual entry. Imports never publish automatically.
- Opt-in email digest, deadline reminders and confirmed changes, queued delivery and retries. SMTP is disabled by default.
- Safe filesystem storage, administrator PDF upload/text extraction and controlled retrieval; source documents are linked by default.
- Docker Compose, Nginx example, CI and automated tests.

This is a pilot implementation, not a claim that every acceptance item in the original master brief is complete. See [verification and remaining limitations](docs/verification.md) before deployment. No opportunities or analytics are fabricated or seeded in production.

## Development

Requirements: Java 21, Maven 3.9+, Node 22+, PostgreSQL 17. Docker is needed for disposable PostgreSQL tests.

Backend (set environment variables in your shell; Maven does not automatically read `.env`):

```powershell
$env:DATABASE_URL = 'jdbc:postgresql://localhost:5432/mycelium'
$env:DATABASE_USER = 'mycelium'
# Set DATABASE_PASSWORD through your local environment or secret manager.
mvn -f apps/api/pom.xml spring-boot:run
```

Frontend:

```text
cd apps/web
npm ci
npm run dev
```

Next.js proxies `/api/*` to `http://localhost:8080`, keeping session cookies same-origin. Override `API_INTERNAL_URL` before building if needed. The production reverse proxy sends `/api/` directly to Spring.

For an optional local development database, set `POSTGRES_PASSWORD`, set `DATABASE_URL=jdbc:postgresql://postgres:5432/mycelium`, then run `docker compose --profile local-db --profile mail up --build -d`. This is optional; the default stack uses your separately provisioned database.

Mailpit is available with `docker compose --profile mail up -d mailpit`; its inbox is at http://localhost:8025. Set `NOTIFICATIONS_ENABLED=true` only after configuring SMTP and opt into notifications in Company Profile.

## Checks

```text
mvn -f apps/api/pom.xml verify
cd apps/web
npm run typecheck
npm run lint
npm test
npm run build
```

PostgreSQL integration tests use Testcontainers. Without Docker they are explicitly skipped; unit-test success alone does not establish database compatibility.

Opt-in official API smoke tests (network required, no database persistence):

```text
mvn -f apps/api/pom.xml -Dtest=LiveSourceTest -DliveSources=true test
```

For full browser tests, run the API against a disposable database:

```text
mvn -f apps/api/pom.xml spring-boot:test-run -Dspring-boot.run.main-class=com.mycelium.opportunities.TestApplication
cd apps/web
npx playwright install chromium
npm run test:e2e
```

Never point automated acceptance tests at a production database.

## Configuration

See `.env.example` for database, SMTP, secure cookies, administrator bootstrap, polling and TED query settings. Polling cadence is configured per source in minutes (minimum 60); the scheduler checks which sources are due every minute. Production must use HTTPS and `COOKIE_SECURE=true`. Documents persist in the `documents` volume. Sessions are currently in-memory and expire on API restarts.

## Troubleshooting

- **API cannot start:** verify JDBC URL, database availability, user permissions and Flyway logs. Health is `/actuator/health`.
- **Empty explorer:** this is expected before real records are imported and approved. Check the admin review queue and ingestion history.
- **Source failed:** inspect its diagnostic. Existing records are preserved. Correct access or parser problems before retrying; do not bypass upstream restrictions.
- **Sign-in or CSRF errors:** use the same site origin for browser and API, and use HTTPS when secure cookies are enabled.
- **Turbopack worker failure on Windows:** this repository uses Next.js's supported Webpack build/development option for compatibility with the tested host.
- **Notifications not arriving:** check global enablement, user opt-in, SMTP settings and `notification` delivery status. Daily preparation runs at 07:00 Europe/Skopje.

## Project map

`apps/api` holds the modular Spring application and Flyway SQL. `apps/web` holds App Router screens and accessible shared components. `infrastructure` contains deployment examples; `docs` covers architecture, source evidence, API and operations. No AI key is required.
