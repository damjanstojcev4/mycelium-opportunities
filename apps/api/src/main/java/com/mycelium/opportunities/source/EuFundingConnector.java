package com.mycelium.opportunities.source;

import com.fasterxml.jackson.databind.JsonNode;
import com.mycelium.opportunities.common.Json;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class EuFundingConnector implements OpportunitySourceConnector {
  private final SafeSourceHttp http;
  private final Json json;

  public EuFundingConnector(SafeSourceHttp http, Json json) {
    this.http = http;
    this.json = json;
  }

  public String sourceCode() {
    return "EU_FUNDING";
  }

  public FetchResult fetch(String checkpoint) throws Exception {
    int page = checkpoint == null || checkpoint.isBlank() ? 1 : Integer.parseInt(checkpoint);
    String query =
        json.write(
            Map.of("bool", Map.of("must", List.of(Map.of("terms", Map.of("type", List.of("1")))))));
    String boundary = "MyceliumQueryBoundary";
    String form =
        "--"
            + boundary
            + "\r\nContent-Disposition: form-data; name=\"query\"\r\nContent-Type: application/json\r\n\r\n"
            + query
            + "\r\n--"
            + boundary
            + "\r\nContent-Disposition: form-data; name=\"languages\"\r\nContent-Type: application/json\r\n\r\n[\"en\"]\r\n--"
            + boundary
            + "\r\nContent-Disposition: form-data; name=\"sort\"\r\nContent-Type: application/json\r\n\r\n{\"field\":\"es_SortDate\",\"order\":\"DESC\"}\r\n--"
            + boundary
            + "--\r\n";
    return parse(
        http.post(
            "https://api.tech.ec.europa.eu/search-api/prod/rest/search?apiKey=SEDIA&text="
                + URLEncoder.encode("***", StandardCharsets.UTF_8)
                + "&pageSize=10&pageNumber="
                + page,
            form,
            "multipart/form-data; boundary=" + boundary),
        page);
  }

  public FetchResult parse(String body, int page) {
    var root = json.read(body);
    if (!root.path("results").isArray())
      throw new IllegalArgumentException("Funding API results are missing");
    List<SourceRecord> rows = new ArrayList<>();
    for (JsonNode r : root.path("results")) {
      JsonNode m = r.path("metadata");
      String id = TedConnector.text(m.path("identifier"));
      String title = TedConnector.text(m.path("title"));
      if (title.isBlank()) title = r.path("title").asText();
      String url = r.path("url").asText();
      SafeSourceHttp.validate(url);
      String status =
          switch (TedConnector.text(m.path("status"))) {
            case "31094501" -> "UPCOMING";
            case "31094502" -> "OPEN";
            case "31094503" -> "CLOSED";
            default -> "UNKNOWN";
          };
      if (!"1".equals(TedConnector.text(m.path("type"))))
        throw new IllegalArgumentException(
            "Funding API returned a non-grant topic despite the filter");
      var details = new LinkedHashMap<String, Object>();
      details.put("raw", r);
      details.put("fundingProgram", TedConnector.text(m.path("frameworkProgramme")));
      details.put("deadlineDates", TedConnector.strings(m.path("deadlineDate")));
      var dates =
          TedConnector.strings(m.path("deadlineDate")).stream()
              .map(this::parseInstant)
              .filter(Objects::nonNull)
              .sorted()
              .toList();
      if (!dates.isEmpty())
        details.put(
            "latestDeadlineDate",
            dates
                .getLast()
                .atZone(java.time.ZoneId.of("Europe/Brussels"))
                .toLocalDate()
                .toString());
      String conditions = TedConnector.text(m.path("topicConditions"));
      if (!conditions.isBlank())
        details.put("eligibilityRequirements", org.jsoup.Jsoup.parse(conditions).text());
      List<String> documents = new ArrayList<>();
      for (var link : org.jsoup.Jsoup.parse(conditions, url).select("a[href]")) {
        String href = link.absUrl("href");
        try {
          SafeSourceHttp.validate(href);
          if (href.toLowerCase(Locale.ROOT).contains(".pdf")) documents.add(href);
        } catch (IllegalArgumentException ignored) {
          /* External policy references are retained in raw source metadata, never fetched. */
        }
      }
      String description = TedConnector.text(m.path("descriptionByte"));
      if (description.isBlank()) description = TedConnector.text(m.path("description"));
      // Portal midnight deadline metadata may represent only a date, not a submission time.
      // Preserve all dates as evidence; do not turn date-only semantics into an exact cutoff.
      rows.add(
          new SourceRecord(
              id,
              "GRANT",
              title,
              org.jsoup.Jsoup.parse(description).text(),
              "European Commission",
              null,
              null,
              parseInstant(TedConnector.text(m.path("startDate"))),
              null,
              null,
              null,
              status,
              url,
              r.path("language").asText("en"),
              details,
              documents.stream().distinct().toList(),
              Map.of("deadlineDates", m.path("deadlineDate").toString())));
    }
    boolean complete = rows.size() < 10;
    return new FetchResult(rows, complete ? null : Integer.toString(page + 1), complete);
  }

  private java.time.Instant parseInstant(String value) {
    try {
      return java.time.OffsetDateTime.parse(value).toInstant();
    } catch (java.time.format.DateTimeParseException ignored) {
      return null;
    }
  }
}
