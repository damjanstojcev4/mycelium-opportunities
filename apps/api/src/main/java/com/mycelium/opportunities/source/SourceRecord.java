package com.mycelium.opportunities.source;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

public record SourceRecord(
    String externalId,
    String type,
    String title,
    String description,
    String issuer,
    String country,
    Instant publishedAt,
    Instant opensAt,
    Instant deadlineAt,
    BigDecimal value,
    String currency,
    String status,
    String sourceUrl,
    String language,
    Map<String, Object> details,
    List<String> documents,
    Map<String, String> evidence) {
  public SourceRecord {
    if (externalId == null || externalId.isBlank() || title == null || title.isBlank())
      throw new IllegalArgumentException("Source identifier and title are required");
    if (!Set.of("TENDER", "GRANT").contains(type))
      throw new IllegalArgumentException("Invalid opportunity type");
    if (!Set.of("OPEN", "UPCOMING", "CLOSED", "CANCELLED", "AWARDED", "UNKNOWN").contains(status))
      throw new IllegalArgumentException("Invalid source status");
    if (value != null && (value.signum() < 0 || currency == null || !currency.matches("[A-Z]{3}")))
      throw new IllegalArgumentException(
          "A nonnegative amount and ISO currency are required together");
    if (country != null && !country.matches("[A-Z]{2}"))
      throw new IllegalArgumentException("Country must use ISO alpha-2");
    SafeSourceHttp.validate(sourceUrl);
    if (title.length() > 2000 || (description != null && description.length() > 500000))
      throw new IllegalArgumentException("Source record text is too large");
    details = details == null ? Map.of() : details;
    documents = documents == null ? List.of() : documents;
    evidence = evidence == null ? Map.of() : evidence;
    if (deadlineAt != null && !evidence.containsKey("deadlineAt"))
      throw new IllegalArgumentException("Deadline requires source evidence");
    if (details.containsKey("latestDeadlineDate"))
      java.time.LocalDate.parse(details.get("latestDeadlineDate").toString());
  }
}
