CREATE TABLE app_user (
 id uuid PRIMARY KEY, email varchar(254) NOT NULL UNIQUE, password_hash text NOT NULL,
 full_name varchar(160) NOT NULL, role varchar(16) NOT NULL CHECK(role IN ('USER','ADMIN')),
 preferred_language varchar(8) NOT NULL DEFAULT 'en', timezone text NOT NULL DEFAULT 'Europe/Skopje',
 created_at timestamptz NOT NULL DEFAULT now(), updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE company (
 id uuid PRIMARY KEY, owner_user_id uuid NOT NULL REFERENCES app_user(id), name varchar(200) NOT NULL,
 description text NOT NULL DEFAULT '', country_code varchar(3) NOT NULL, city text,
 industry text, business_size text, employee_count integer CHECK(employee_count >= 0), founding_year integer,
 website text, profile jsonb NOT NULL DEFAULT '{}', created_at timestamptz NOT NULL DEFAULT now(), updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX company_owner ON company(owner_user_id);
CREATE TABLE source (
 id uuid PRIMARY KEY, code text NOT NULL UNIQUE, name text NOT NULL, base_url text NOT NULL,
 source_type text NOT NULL, country_code varchar(3), enabled boolean NOT NULL DEFAULT false,
 access_status text NOT NULL DEFAULT 'ACCESS_UNVERIFIED', interval_minutes integer NOT NULL DEFAULT 1440 CHECK(interval_minutes >= 60),
 last_attempt_at timestamptz, last_success_at timestamptz, last_error text, checkpoint text,
 lease_until timestamptz, created_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE opportunity (
 id uuid PRIMARY KEY, type varchar(8) NOT NULL CHECK(type IN ('TENDER','GRANT')), source_id uuid NOT NULL REFERENCES source(id),
 external_id text NOT NULL, title text NOT NULL, summary text, description text, issuer_name text,
 country_code varchar(3), region text, published_at timestamptz, opens_at timestamptz, deadline_at timestamptz,
 deadline_timezone text, estimated_value numeric(20,2) CHECK(estimated_value >= 0), currency varchar(3),
 status text NOT NULL CHECK(status IN ('UPCOMING','OPEN','CLOSED','CANCELLED','AWARDED','UNKNOWN')),
 source_url text NOT NULL, original_language varchar(8) NOT NULL DEFAULT 'en',
 review_status text NOT NULL DEFAULT 'PENDING' CHECK(review_status IN ('PENDING','APPROVED','REJECTED')),
 details jsonb NOT NULL DEFAULT '{}', content_hash text NOT NULL, last_verified_at timestamptz,
 created_at timestamptz NOT NULL DEFAULT now(), updated_at timestamptz NOT NULL DEFAULT now(),
 search_vector tsvector GENERATED ALWAYS AS (to_tsvector('simple',coalesce(title,'') || ' ' || coalesce(description,''))) STORED,
 UNIQUE(source_id,external_id)
);
CREATE INDEX opportunity_search ON opportunity USING gin(search_vector);
CREATE INDEX opportunity_public ON opportunity(review_status,type,country_code,deadline_at);
CREATE INDEX opportunity_published ON opportunity(published_at DESC);
CREATE INDEX opportunity_details ON opportunity USING gin(details);
CREATE TABLE opportunity_revision (
 id uuid PRIMARY KEY, opportunity_id uuid NOT NULL REFERENCES opportunity(id), snapshot jsonb NOT NULL,
 content_hash text NOT NULL, changed_fields jsonb NOT NULL DEFAULT '[]', created_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE opportunity_document (
 id uuid PRIMARY KEY, opportunity_id uuid NOT NULL REFERENCES opportunity(id), source_document_url text NOT NULL,
 filename text, mime_type text, storage_path text, content_hash text, extraction_status text NOT NULL DEFAULT 'LINK_ONLY',
 extracted_text text, downloaded_at timestamptz, UNIQUE(opportunity_id,source_document_url)
);
CREATE TABLE field_provenance (
 id uuid PRIMARY KEY, opportunity_id uuid NOT NULL REFERENCES opportunity(id), field_name text NOT NULL,
 source_url text NOT NULL, evidence text NOT NULL, observed_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE ingestion_run (
 id uuid PRIMARY KEY, source_id uuid NOT NULL REFERENCES source(id), status text NOT NULL,
 fetched integer NOT NULL DEFAULT 0, inserted integer NOT NULL DEFAULT 0, updated integer NOT NULL DEFAULT 0,
 rejected integer NOT NULL DEFAULT 0, error text, started_at timestamptz NOT NULL DEFAULT now(), finished_at timestamptz
);
CREATE TABLE ingestion_error (
 id uuid PRIMARY KEY, run_id uuid NOT NULL REFERENCES ingestion_run(id), external_id text, message text NOT NULL, created_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE company_match (
 id uuid PRIMARY KEY, company_id uuid NOT NULL REFERENCES company(id), opportunity_id uuid NOT NULL REFERENCES opportunity(id),
 relevance_score integer NOT NULL CHECK(relevance_score BETWEEN 0 AND 100), eligibility_status text NOT NULL,
 positive_reasons jsonb NOT NULL, missing_requirements jsonb NOT NULL, disqualifying_requirements jsonb NOT NULL,
 calculated_at timestamptz NOT NULL DEFAULT now(), UNIQUE(company_id,opportunity_id)
);
CREATE TABLE saved_opportunity (
 id uuid PRIMARY KEY, user_id uuid NOT NULL REFERENCES app_user(id), opportunity_id uuid NOT NULL REFERENCES opportunity(id),
 status text NOT NULL DEFAULT 'SAVED' CHECK(status IN ('SAVED','REVIEWING','PLANNING_TO_APPLY','APPLIED','WON','LOST','DISMISSED')),
 created_at timestamptz NOT NULL DEFAULT now(), UNIQUE(user_id,opportunity_id)
);
CREATE TABLE notification_preference (
 user_id uuid PRIMARY KEY REFERENCES app_user(id), digest boolean NOT NULL DEFAULT false,
 reminders boolean NOT NULL DEFAULT false, changes boolean NOT NULL DEFAULT false, reminder_days jsonb NOT NULL DEFAULT '[7,3,1]'
);
CREATE TABLE notification (
 id uuid PRIMARY KEY, user_id uuid NOT NULL REFERENCES app_user(id), opportunity_id uuid REFERENCES opportunity(id),
 type text NOT NULL, channel text NOT NULL DEFAULT 'EMAIL', status text NOT NULL DEFAULT 'QUEUED',
 deduplication_key text NOT NULL UNIQUE, subject text NOT NULL, body text NOT NULL,
 attempts integer NOT NULL DEFAULT 0, next_attempt_at timestamptz NOT NULL DEFAULT now(), last_error text,
 created_at timestamptz NOT NULL DEFAULT now(), sent_at timestamptz
);
INSERT INTO source(id,code,name,base_url,source_type,country_code,access_status) VALUES
 ('00000000-0000-0000-0000-000000000001','EU_FUNDING','EU Funding & Tenders','https://ec.europa.eu','REST_API',NULL,'ACCESS_UNVERIFIED'),
 ('00000000-0000-0000-0000-000000000002','TED','Tenders Electronic Daily','https://ted.europa.eu','REST_API',NULL,'VERIFIED'),
 ('00000000-0000-0000-0000-000000000003','IPARD','Agricultural and Rural Development Agency','https://www.ipardpa.gov.mk','HTML_SCRAPER','MK','ACCESS_UNVERIFIED'),
 ('00000000-0000-0000-0000-000000000004','ESJN','Macedonian Public Procurement','https://e-nabavki.gov.mk','FILE_IMPORT','MK','RESTRICTED');
