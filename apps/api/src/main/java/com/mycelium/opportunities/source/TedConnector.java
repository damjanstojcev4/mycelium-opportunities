package com.mycelium.opportunities.source;

import com.fasterxml.jackson.databind.JsonNode;
import com.mycelium.opportunities.common.Json;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class TedConnector implements OpportunitySourceConnector {
  private final SafeSourceHttp http;
  private final Json json;
  private final String query;

  public TedConnector(SafeSourceHttp http, Json json, @Value("${app.ted-query}") String query) {
    this.http = http;
    this.json = json;
    this.query = query;
  }

  public String sourceCode() {
    return "TED";
  }

  public FetchResult fetch(String checkpoint) throws Exception {
    Map<String, Object> request = new LinkedHashMap<>();
    request.put("query", query);
    request.put(
        "fields",
        List.of(
            "publication-number",
            "notice-title",
            "buyer-name",
            "buyer-country",
            "publication-date",
            "classification-cpv",
            "notice-type"));
    request.put("limit", 100);
    request.put("scope", "ALL");
    request.put("paginationMode", "ITERATION");
    if (checkpoint != null && !checkpoint.isBlank()) request.put("iterationNextToken", checkpoint);
    return parse(
        http.post(
            "https://api.ted.europa.eu/v3/notices/search",
            json.write(request),
            "application/json"));
  }

  public FetchResult parse(String body) {
    JsonNode root = json.read(body);
    if (!root.path("notices").isArray() || root.path("timedOut").asBoolean())
      throw new IllegalArgumentException("TED returned an invalid or incomplete search response");
    List<SourceRecord> records = new ArrayList<>();
    for (JsonNode n : root.path("notices")) {
      String id = text(n.path("publication-number"));
      String country = country(text(n.path("buyer-country")));
      String noticeType = text(n.path("notice-type"));
      Map<String, Object> details = new LinkedHashMap<>();
      details.put("cpv", strings(n.path("classification-cpv")));
      details.put("raw", n);
      details.put("noticeType", noticeType);
      records.add(
          new SourceRecord(
              id,
              "TENDER",
              text(n.path("notice-title")),
              null,
              text(n.path("buyer-name")),
              country,
              null,
              null,
              null,
              null,
              null,
              noticeType.startsWith("can-") ? "AWARDED" : "UNKNOWN",
              "https://ted.europa.eu/en/notice/-/detail/" + id,
              "en",
              details,
              List.of(),
              Map.of()));
    }
    String next = root.path("iterationNextToken").asText("");
    return new FetchResult(records, next, records.isEmpty() || next.isBlank());
  }

  static String text(JsonNode node) {
    if (node.isTextual()) return node.asText();
    if (node.isArray()) return node.isEmpty() ? "" : text(node.get(0));
    if (node.isObject()) {
      if (node.has("eng")) return text(node.get("eng"));
      var it = node.elements();
      return it.hasNext() ? text(it.next()) : "";
    }
    return "";
  }

  static List<String> strings(JsonNode node) {
    List<String> result = new ArrayList<>();
    if (node.isArray()) node.forEach(n -> result.add(text(n)));
    else if (!text(node).isBlank()) result.add(text(node));
    return result;
  }

  static String country(String alpha3) {
    for (String c : Locale.getISOCountries())
      if (new Locale.Builder().setRegion(c).build().getISO3Country().equals(alpha3)) return c;
    return alpha3.matches("[A-Z]{2}") ? alpha3 : null;
  }
}
