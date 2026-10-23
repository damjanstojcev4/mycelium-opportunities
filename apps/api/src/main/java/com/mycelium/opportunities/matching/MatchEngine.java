package com.mycelium.opportunities.matching;

import com.fasterxml.jackson.databind.JsonNode;
import java.text.Normalizer;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class MatchEngine {
  @org.springframework.beans.factory.annotation.Value("${app.matching.industry-weight:25}")
  private int industryWeight = 25;

  @org.springframework.beans.factory.annotation.Value("${app.matching.keyword-weight:30}")
  private int keywordWeight = 30;

  @org.springframework.beans.factory.annotation.Value("${app.matching.geography-weight:15}")
  private int geographyWeight = 15;

  @org.springframework.beans.factory.annotation.Value("${app.matching.type-weight:15}")
  private int typeWeight = 15;

  @org.springframework.beans.factory.annotation.Value("${app.matching.budget-weight:10}")
  private int budgetWeight = 10;

  @org.springframework.beans.factory.annotation.Value("${app.matching.cpv-weight:5}")
  private int cpvWeight = 5;

  @jakarta.annotation.PostConstruct
  void validateWeights() {
    if (java.util.stream.IntStream.of(
                industryWeight, keywordWeight, geographyWeight, typeWeight, budgetWeight, cpvWeight)
            .anyMatch(w -> w < 0)
        || industryWeight + keywordWeight + geographyWeight + typeWeight + budgetWeight + cpvWeight
            != 100)
      throw new IllegalArgumentException("Matching weights must be nonnegative and total 100");
  }

  public record Result(
      int score,
      String eligibility,
      List<String> reasons,
      List<String> missing,
      List<String> disqualifiers) {}

  public Result evaluate(
      String country,
      String industry,
      JsonNode profile,
      String type,
      String opportunityCountry,
      String text,
      JsonNode details) {
    List<String> reasons = new ArrayList<>(),
        missing = new ArrayList<>(),
        disqualified = new ArrayList<>();
    int score = 0;
    String hay = normalize(text);
    if (industry != null && !industry.isBlank() && hay.contains(normalize(industry))) {
      score += industryWeight;
      reasons.add("Industry mentioned: " + industry);
    }
    var words = values(profile, "keywords");
    var found = words.stream().filter(w -> hay.contains(normalize(w))).toList();
    if (!found.isEmpty()) {
      score += Math.min(keywordWeight, keywordWeight * found.size() / 3);
      reasons.add("Capabilities mentioned: " + String.join(", ", found));
    }
    if (values(profile, "countries").contains(opportunityCountry)) {
      score += geographyWeight;
      reasons.add("Within your preferred markets");
    }
    if (values(profile, "types").contains(type)) {
      score += typeWeight;
      reasons.add("Matches your preferred opportunity type");
    }
    var cpv = values(profile, "cpv");
    if (cpv.stream().anyMatch(c -> values(details, "cpv").contains(c))) {
      score += cpvWeight;
      reasons.add("CPV classification matches");
    }
    var eligible = values(details, "eligibleCountries");
    if (profile.path("budgetCurrency").isTextual()
        && details.path("currency").isTextual()
        && profile.path("budgetCurrency").asText().equals(details.path("currency").asText())
        && details.path("estimatedValue").isNumber()) {
      var value = details.path("estimatedValue").decimalValue();
      boolean hasPreference =
          profile.path("budgetMin").isNumber() || profile.path("budgetMax").isNumber();
      if (hasPreference
          && (!profile.path("budgetMin").isNumber()
              || value.compareTo(profile.path("budgetMin").decimalValue()) >= 0)
          && (!profile.path("budgetMax").isNumber()
              || value.compareTo(profile.path("budgetMax").decimalValue()) <= 0)) {
        score += budgetWeight;
        reasons.add("Within your preferred budget in " + details.path("currency").asText());
      }
    }
    if (eligible.isEmpty()) missing.add("Eligible countries have not been confirmed");
    else if (!eligible.contains(country))
      disqualified.add("Your country is excluded by the published eligible-country list");
    for (String certification : values(details, "requiredCertifications"))
      if (!values(profile, "certifications").contains(certification))
        missing.add("Verify required certification: " + certification);
    assessCategory(
        profile,
        details,
        "businessSize",
        "eligibleBusinessSizes",
        "Business size",
        missing,
        disqualified);
    assessCategory(
        profile,
        details,
        "organizationType",
        "eligibleOrganizationTypes",
        "Organization type",
        missing,
        disqualified);
    if (details.path("consortiumRequired").asBoolean(false)) {
      if (!profile.has("consortiumAvailable"))
        missing.add("Confirm a consortium is available as required by the call");
      else if (!profile.path("consortiumAvailable").asBoolean())
        disqualified.add("The call requires a consortium, but your profile says none is available");
    }
    if (details.path("eligibilityRequirements").asText("").isBlank())
      missing.add("Full applicant requirements are not specified");
    else
      missing.add(
          "Review published applicant requirements: "
              + details.path("eligibilityRequirements").asText());
    String eligibility =
        !disqualified.isEmpty()
            ? "NOT_ELIGIBLE"
            : eligible.isEmpty() ? "UNKNOWN" : "POSSIBLY_ELIGIBLE";
    return new Result(score, eligibility, reasons, missing, disqualified);
  }

  public static List<String> values(JsonNode node, String key) {
    var v = node.path(key);
    List<String> out = new ArrayList<>();
    if (v.isArray())
      v.forEach(
          n -> {
            if (n.isTextual() && !n.asText().isBlank()) out.add(n.asText().strip());
          });
    return out;
  }

  private void assessCategory(
      JsonNode profile,
      JsonNode details,
      String profileKey,
      String requirementKey,
      String label,
      List<String> missing,
      List<String> disqualified) {
    var allowed = values(details, requirementKey);
    if (allowed.isEmpty()) return;
    String actual = profile.path(profileKey).asText("");
    if (actual.isBlank())
      missing.add(label + " must be checked against the published eligible categories");
    else if (!allowed.contains(actual))
      disqualified.add(label + " is excluded by the published categories");
  }

  private String normalize(String text) {
    return Normalizer.normalize(Objects.requireNonNullElse(text, ""), Normalizer.Form.NFC)
        .toLowerCase(Locale.ROOT);
  }
}
