package com.mycelium.opportunities.source;

import java.util.*;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Component;

@Component
public class IpardConnector implements OpportunitySourceConnector {
  private final SafeSourceHttp http;

  public IpardConnector(SafeSourceHttp http) {
    this.http = http;
  }

  public String sourceCode() {
    return "IPARD";
  }

  public FetchResult fetch(String checkpoint) throws Exception {
    String listing = "https://www.ipardpa.gov.mk/mk/Home/JavniPovici/2";
    var document = Jsoup.parse(http.get(listing), listing);
    Map<String, String> links = new LinkedHashMap<>();
    for (var a : document.select("a[href*=/Home/RuralenPovici/]"))
      if (a.text().contains("ПОВИК"))
        links.put(a.absUrl("href"), a.text().replace("arrow_circle_right", "").strip());
    if (links.isEmpty())
      throw new IllegalArgumentException(
          "IPARD listing contains no recognized public calls; review selectors");
    List<SourceRecord> rows = new ArrayList<>();
    for (var entry : links.entrySet()) {
      SafeSourceHttp.validate(entry.getKey());
      var detail = Jsoup.parse(http.get(entry.getKey()), entry.getKey());
      List<String> docs =
          detail.select(".tabs-container a[href]").stream()
              .map(a -> a.absUrl("href").replace(" ", "%20"))
              .filter(u -> u.toLowerCase(Locale.ROOT).matches(".*\\.(pdf|docx?)$"))
              .distinct()
              .toList();
      rows.add(
          new SourceRecord(
              entry.getKey(),
              "GRANT",
              entry.getValue(),
              null,
              "АФПЗРР",
              "MK",
              null,
              null,
              null,
              null,
              null,
              "UNKNOWN",
              entry.getKey(),
              "mk",
              Map.of(
                  "reviewNote",
                  "Confirm business-grant classification, conditions and deadlines in the linked publication"),
              docs,
              Map.of()));
    }
    return new FetchResult(rows, null, true);
  }
}
