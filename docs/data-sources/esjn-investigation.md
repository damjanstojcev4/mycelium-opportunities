# ESJN investigation

Date: 23 October 2026.

The master brief identifies the public procurement portal and its open-data announcements page. A read-only request to [the open-data entry](https://e-nabavki.gov.mk/opendata-announcements.aspx#/home) returned HTTP 403 in the research tool. We did not bypass the restriction, probe private endpoints, submit authentication, or solve CAPTCHA.

No stable documented unrestricted bulk API or currently downloadable export schema was established in this investigation. The existence of a browser portal is not evidence that an undocumented backend API is available for commercial collection. Exact export columns and reuse conditions remain unverified.

Implementation: the ESJN registry entry is FILE_IMPORT / RESTRICTED, disabled for scheduled fetching. An administrator can obtain an authorized official CSV/XLSX export through a supported portal workflow, select ESJN, map its columns, preview validation results and confirm the import into the review queue. Each row needs a stable external ID, title, type and official source URL. Repeated rows update by source/identifier rather than title similarity. Review is required before publication.

Next steps for the operator: obtain official export documentation and reuse terms from the responsible institution, inspect an authorized sample, map actual columns, verify source identifiers and date semantics, then test import with a small reviewed batch. Automatic collection should remain disabled until a supported method and its access conditions are documented.
