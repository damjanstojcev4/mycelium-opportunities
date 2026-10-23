package com.mycelium.opportunities;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mycelium.opportunities.common.Json;
import com.mycelium.opportunities.ingestion.RecordWriter;
import com.mycelium.opportunities.matching.MatchEngine;
import com.mycelium.opportunities.source.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;

class DomainTest {
  @Test
  void budgetsAreComparedOnlyWithinTheSameCurrency() {
    var engine = new MatchEngine();
    var profile = json.read("{\"budgetCurrency\":\"EUR\",\"budgetMin\":100,\"budgetMax\":500}");
    var eur =
        engine.evaluate(
            "MK",
            null,
            profile,
            "GRANT",
            null,
            "",
            json.read("{\"estimatedValue\":250,\"currency\":\"EUR\"}"));
    var mkd =
        engine.evaluate(
            "MK",
            null,
            profile,
            "GRANT",
            null,
            "",
            json.read("{\"estimatedValue\":250,\"currency\":\"MKD\"}"));
    assertEquals(10, eur.score());
    assertEquals(0, mkd.score());
  }

  private final Json json = new Json(new ObjectMapper());

  @Test
  void cyrillicMatchingPreservesUnknownEligibility() {
    var result =
        new MatchEngine()
            .evaluate(
                "MK",
                "Софтвер",
                json.read("{\"keywords\":[\"развој\"],\"types\":[\"TENDER\"]}"),
                "TENDER",
                "MK",
                "Развој на софтвер",
                json.read("{}"));
    assertEquals(50, result.score());
    assertEquals("UNKNOWN", result.eligibility());
    assertFalse(result.missing().isEmpty());
  }

  @Test
  void countryExclusionCannotBeHiddenByRelevance() {
    var result =
        new MatchEngine()
            .evaluate(
                "MK",
                "Java",
                json.read("{\"keywords\":[\"Java\"],\"types\":[\"GRANT\"]}"),
                "GRANT",
                null,
                "Java development",
                json.read("{\"eligibleCountries\":[\"DE\"]}"));
    assertTrue(result.score() > 0);
    assertEquals("NOT_ELIGIBLE", result.eligibility());
    assertEquals(1, result.disqualifiers().size());
  }

  @Test
  void missingMetadataDoesNotInventEligibility() {
    var result =
        new MatchEngine()
            .evaluate("MK", null, json.read("{}"), "GRANT", null, "Grant", json.read("{}"));
    assertEquals(0, result.score());
    assertEquals("UNKNOWN", result.eligibility());
  }

  @Test
  void unsafeUrlsAreRejected() {
    for (String url :
        List.of(
            "http://ted.europa.eu/a",
            "https://127.0.0.1/a",
            "https://ted.europa.eu.evil.test/a",
            "https://user@ted.europa.eu/a",
            "https://ted.europa.eu:8443/a"))
      assertThrows(IllegalArgumentException.class, () -> SafeSourceHttp.validate(url));
    assertDoesNotThrow(
        () -> SafeSourceHttp.validate("https://ted.europa.eu/en/notice/-/detail/123"));
  }

  @Test
  void hashesDetectChanges() {
    assertEquals(RecordWriter.hash("Јавен повик"), RecordWriter.hash("Јавен повик"));
    assertNotEquals(RecordWriter.hash("a"), RecordWriter.hash("b"));
  }

  @Test
  void deadlineWithoutEvidenceIsRejected() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new SourceRecord(
                "1",
                "GRANT",
                "Call",
                null,
                null,
                null,
                null,
                null,
                Instant.now(),
                null,
                null,
                "UNKNOWN",
                "https://ted.europa.eu/x",
                "en",
                null,
                null,
                null));
  }

  @Test
  void currencyIsNotGuessed() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new SourceRecord(
                "1",
                "GRANT",
                "Call",
                null,
                null,
                null,
                null,
                null,
                null,
                new BigDecimal("100.00"),
                null,
                "UNKNOWN",
                "https://ted.europa.eu/x",
                "en",
                null,
                null,
                null));
  }

  @Test
  void malformedTedResponseIsNotEmptySuccess() {
    var connector = new TedConnector(null, json, "");
    assertThrows(IllegalArgumentException.class, () -> connector.parse("{}"));
    assertThrows(
        IllegalArgumentException.class,
        () -> connector.parse("{\"notices\":[],\"timedOut\":true}"));
    assertTrue(connector.parse("{\"notices\":[]}").complete());
  }

  @Test
  void tedMultilingualTitleAndCountry() {
    var connector = new TedConnector(null, json, "");
    var result =
        connector.parse(
            "{\"notices\":[{\"publication-number\":\"test-1\",\"notice-title\":{\"eng\":\"Test fixture only\"},\"buyer-country\":[\"MKD\"]}],\"iterationNextToken\":\"next\"}");
    assertEquals("MK", result.records().getFirst().country());
    assertEquals("Test fixture only", result.records().getFirst().title());
    assertFalse(result.complete());
  }

  @Test
  void malformedFundingResponseFails() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new EuFundingConnector(null, json).parse("{\"error\":\"denied\"}", 1));
  }
}
