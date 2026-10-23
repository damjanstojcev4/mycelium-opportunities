# API

Base path: `/api/v1`. JSON fields currently use database snake_case for read projections and camelCase for request DTOs. Generated OpenAPI is available at `/api/v1/openapi` after administrator login; Swagger UI is `/api/v1/docs`.

## Authentication

GET `/auth/csrf` returns `{token,headerName}` and establishes a session. Send that header for every mutation, including registration and login. Login uses URL-encoded `email` and `password` at POST `/auth/login`; returns 204 and the HttpOnly session cookie. Successful login rotates the session and CSRF token, so obtain a new token before the next mutation.

POST `/auth/register`: `{email,password,fullName}`. POST `/auth/logout` logs out. GET `/me` returns user profile/role. Anonymous private requests return 401; unauthorized role or missing CSRF returns 403. The application does not implement password reset or email verification yet.

## Opportunities

- GET `/opportunities`: `q`, `type`, `country` (ISO alpha-2), `source` (code), `status`, `cpv`, `fundingProgram`, `industry`, `deadlineFrom`, `deadlineTo`, `publishedFrom`, `budgetMin`, `budgetMax`, `currency`, `sort`, `page`, `size`.
- Dates must be ISO 8601 instants; currency is required with budget bounds. Sorting: `newest`, `deadline`, `value` (grouped by currency; no currency conversions).
- Pagination is zero-based, defaults to 20 and is capped at 100. Response: `{items,total,page,size}`.
- GET `/opportunities/{id}`, `/opportunities/{id}/documents`, `/sources` are public. Only APPROVED opportunities are public.

## Workspace

- GET/POST `/companies`, PATCH `/companies/{id}`. Save DTO: `{name,description,countryCode,industry,city,preferences:{keywords,countries,types,cpv,certifications}}`.
- GET `/companies/{id}/matches?page=0` returns up to 20 ranked rows. Only the owning user can access them.
- GET/POST `/saved-opportunities`, DELETE `/saved-opportunities/{id}`, PATCH `/saved-opportunities/{id}/status`. Create body: `{opportunityId}`; state body: `{status}`.
- GET `/dashboard` derives metrics and type-specific recommendations from persisted records. `newMatches` counts distinct matched opportunities discovered in the last seven days.
- GET/PUT `/notifications/preferences`. Write: `{digest,reminders,changes,reminderDays:[7,3,1]}`.

## Administration

- GET `/admin/sources`; PATCH `/admin/sources/{id}` with `{enabled,accessStatus,intervalMinutes}`.
- POST `/admin/sources/{id}/sync` executes a bounded run and returns its actual SUCCESS/PARTIAL/FAILED report. HTTP success does not imply import success; inspect `status`.
- GET `/admin/ingestion-runs`, `/admin/review-queue` (latest 100).
- PATCH `/admin/opportunities/{id}/review`: `{status:"APPROVED"}` or REJECTED.
- POST multipart `/admin/opportunities/import/preview`: `file`, `type`, `mapping` (JSON field-to-header map). Returns parsed records and row-level errors.
- POST `/admin/opportunities/import`: `{sourceId,records:[SourceRecord...]}`. Server validates again and returns inserted/updated/duplicate/error counts. This also supports manual entry. All records require review.
- POST multipart `/admin/opportunities/{id}/documents`: `file` (PDF, up to 5 MB) and `sourceUrl` (official original). Returns extraction status/text; scanned files return OCR_REQUIRED. Stored files for approved opportunities are available at GET `/opportunities/{id}/documents/{documentId}/content` as PDF attachments.

Validation/domain errors use RFC Problem Details; authentication filter errors may use Spring's standard JSON error format. Do not rely on internal exception text for machine-readable control flow.
