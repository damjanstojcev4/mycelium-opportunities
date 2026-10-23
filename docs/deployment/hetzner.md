# Single-VPS deployment

Provision a supported Linux server with Docker Compose, Nginx and a DNS name. This repository has not been deployed to Hetzner.

1. Provision PostgreSQL separately (or use the explicitly optional local-db profile for a controlled pilot). Create the database and dedicated migration/application user. Do not expose PostgreSQL publicly.
2. Copy the repository to the VPS and create `.env` with restrictive filesystem permissions. Set JDBC URL/user/password, real `PUBLIC_URL`, `COOKIE_SECURE=true`, SMTP settings and initial admin bootstrap values.
3. Start `docker compose up --build -d api web`. Confirm `/actuator/health` on loopback returns healthy, inspect Flyway logs and load the web frontend.
4. Obtain a trusted certificate with your preferred ACME client. Adapt `infrastructure/nginx/mycelium.conf` for the actual domain and certificate paths. Test Nginx configuration, then enable it. Permit only SSH, HTTP and HTTPS at the firewall.
5. Log in as administrator, remove bootstrap credentials from the environment and restart. Review source access/reuse, sync a bounded batch, review imported opportunities and approve only supported claims.
6. Test registration, profile ownership, saved workflows and SMTP with pilot users. Notification preparation runs at 07:00 Europe/Skopje. Opt-ins default off.

## Persistence and backups

The document volume is persistent; source-linked files are not automatically downloaded. The externally supplied database contains all domain records. Use daily `pg_dump --format=custom` backups through a protected credential mechanism, encrypted off-host copies and retention appropriate to the pilot. Test restoration into a separate empty database before launch. Back up the document volume consistently with database backups when stored documents are used. Do not delete volumes during upgrades.

## Upgrades and recovery

Back up first, deploy immutable image versions, inspect Flyway changes, then recreate API/web services. Never edit an applied migration. Roll forward with a new migration or restore the database and matching application version together. API restarts end in-memory sessions. Docker image tags in these examples should be pinned to reviewed digests for a production release.

Monitor health, ingestion failures, last successful source runs, disk usage, PostgreSQL connections, SMTP errors and certificate expiration. A PARTIAL run is not a complete synchronization. Source records remain available during upstream failures, but last verification timestamps must remain visible.

## Pilot limitations

Run a single API instance: sessions, authentication throttling and notification scheduling are designed for that topology. SMTP delivery cannot be exactly once across a crash after provider acceptance; inspect SENDING records manually before retrying. Apply egress firewall rules blocking private and metadata ranges for outbound source fetching. Complete the remaining items in `docs/verification.md` before expanding beyond a controlled pilot.

Forwarded headers are enabled for the loopback/trusted-proxy topology. Keep the API port private and retain Nginx's overwrite of `X-Forwarded-For`; do not expose an API listener that trusts client-supplied proxy headers directly to the Internet.
