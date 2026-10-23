# Source registry

Verification date: 23 October 2026. Reachability and parsing checks are not a legal determination of reuse rights. All scheduled sources start disabled; no production opportunities are seeded.

| Source | Official URL / documentation | Method | Implementation and access |
|---|---|---|---|
| EU Funding & Tenders | [Official API guide](https://ec.europa.eu/info/funding-tenders/opportunities/portal/screen/support/apis) | Multipart POST to `api.tech.ec.europa.eu/search-api/prod/rest/search?apiKey=SEDIA&text=***` | Live fetch and parser smoke test passed. Uses type 1 grant topics, 10 records/page, English language request and date sorting. Metadata and document links retained; status codes 31094501/2/3 normalized. Admin must verify current reuse terms before enabling. |
| TED | [Search API](https://docs.ted.europa.eu/api/latest/search.html), [reuse guide](https://docs.ted.europa.eu/ODS/latest/reuse/search-api.html), [OpenAPI](https://api.ted.europa.eu/api-v3.yaml) | POST `https://api.ted.europa.eu/v3/notices/search` | Live fetch and parser smoke test passed. Anonymous published-notice search documented for reuse. Iteration cursor, 100 notices/page, configurable expert query. Official YAML saved alongside this registry for the inspected schema. |
| Agricultural and Rural Development Agency | [National calls](https://www.ipardpa.gov.mk/mk/Home/JavniPovici/2) | Jsoup listing/detail parsing | Public listing and one linked detail page retrieved successfully. `robots.txt` returned 404. No affirmative blanket commercial reuse permission established. Disabled / ACCESS_UNVERIFIED; explicit administrator access review required. |
| Macedonian ESJN | [Public portal](https://e-nabavki.gov.mk/PublicAccess/Home.aspx), [open-data portal](https://e-nabavki.gov.mk/opendata-announcements.aspx#/home) | Reviewed official CSV/XLSX import | Open-data request returned 403 during investigation. No invented API or bypass. RESTRICTED; export import available once the operator obtains a permitted official dataset. |

## Normalization decisions

TED title/buyer fields can be multilingual objects; country fields can be arrays using ISO alpha-3. Normalization selects English if present and converts a single buyer country to alpha-2. Raw fields remain available. A contract award notice is not presented as an open tender; other status remains UNKNOWN until supported by source data. The initial connector does not infer lot-level deadlines or aggregate lot values.

Funding fields are commonly arrays. Grant topics are selected explicitly; a different returned type fails validation. Source URLs can point to the official topic JSON document and are retained exactly. Midnight deadline metadata may encode only a date; these values are preserved in `details.deadlineDates` and provenance, not silently converted into a precise application cutoff. Multiple deadlines remain preserved rather than collapsed. Eligibility text is stripped of HTML; eligible countries are not inferred from public availability or framework participation.

The IPARD listing currently links to `/mk/Home/RuralenPovici/11/75` (ЈАВЕН ПОВИК бр.04/2026). The detail page links a PDF call and Word application documents. The adapter preserves these references and Cyrillic titles, leaves unverified deadlines/conditions unknown, and requires publication review. Public HTML alone does not prove an individual call's business eligibility. No bulk PDF downloading is enabled.

The outbound client accepts only HTTPS on fixed official hosts, rejects private-address resolution, refuses redirects, paces requests, bounds response size and attempts, handles Retry-After and checks content types. It does not implement conditional ETag/Last-Modified caching yet.

## Evidence files

`ted-api-v3.yaml`, `funding-sample.json`, `ipard-listing.html`, and `ipard-detail.html` are public-source investigation snapshots, not application seed data. They are not imported automatically. Tests that construct synthetic records identify them explicitly as fixtures.
