package com.mycelium.opportunities.opportunity;

import com.mycelium.opportunities.common.Json;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.web.server.ResponseStatusException;

@Repository
public class OpportunityQuery {
  private final NamedParameterJdbcTemplate db;
  private final Json json;

  public OpportunityQuery(NamedParameterJdbcTemplate db, Json json) {
    this.db = db;
    this.json = json;
  }

  public record Page(List<Map<String, Object>> items, long total, int page, int size) {}

  static final String STATUS =
      "CASE WHEN o.status IN ('OPEN','UPCOMING') AND (o.deadline_at < now() OR (o.details->>'latestDeadlineDate')::date < (now() at time zone 'Europe/Brussels')::date) THEN 'CLOSED' ELSE o.status END";
  static final String SELECT =
      "select o.*,s.name source_name,s.code source_code,"
          + STATUS
          + " effective_status from opportunity o join source s on s.id=o.source_id";

  public Page search(Map<String, String> filters) {
    int page = integer(filters.getOrDefault("page", "0"), 0, 100000),
        size = integer(filters.getOrDefault("size", "20"), 1, 100);
    StringBuilder where = new StringBuilder(" where o.review_status='APPROVED'");
    Map<String, Object> args = new HashMap<>();
    for (var entry :
        Map.of("type", "o.type", "country", "o.country_code", "source", "s.code", "status", STATUS)
            .entrySet()) {
      String value = filters.get(entry.getKey());
      if (value != null && !value.isBlank()) {
        where.append(" and ").append(entry.getValue()).append("=:").append(entry.getKey());
        args.put(entry.getKey(), value);
      }
    }
    if (present(filters, "q")) {
      where.append(" and o.search_vector @@ websearch_to_tsquery('simple',:q)");
      args.put("q", filters.get("q"));
    }
    for (String key : List.of("cpv", "fundingProgram", "industry"))
      if (present(filters, key)) {
        where.append(" and o.details ->> '").append(key).append("' ilike :").append(key);
        args.put(key, "%" + filters.get(key) + "%");
      }
    for (String key : List.of("deadlineFrom", "deadlineTo", "publishedFrom"))
      if (present(filters, key)) {
        where.append(
            key.equals("publishedFrom")
                ? " and o.published_at >= CAST(:publishedFrom as timestamptz)"
                : key.equals("deadlineFrom")
                    ? " and o.deadline_at >= CAST(:deadlineFrom as timestamptz)"
                    : " and o.deadline_at <= CAST(:deadlineTo as timestamptz)");
        args.put(key, java.time.Instant.parse(filters.get(key)));
      }
    for (String key : List.of("budgetMin", "budgetMax"))
      if (present(filters, key)) {
        if (!present(filters, "currency"))
          throw new IllegalArgumentException("Currency is required for budget filtering");
        where
            .append(" and o.estimated_value ")
            .append(key.equals("budgetMin") ? ">=" : "<=")
            .append(":")
            .append(key);
        args.put(key, new java.math.BigDecimal(filters.get(key)));
      }
    if (present(filters, "currency")) {
      where.append(" and o.currency=:currency");
      args.put("currency", filters.get("currency"));
    }
    String sort =
        switch (filters.getOrDefault("sort", "newest")) {
          case "deadline" -> "o.deadline_at asc nulls last";
          case "value" -> "o.currency asc,o.estimated_value desc nulls last";
          default -> "o.published_at desc nulls last";
        };
    long total =
        db.queryForObject(
            "select count(*) from opportunity o join source s on s.id=o.source_id" + where,
            args,
            Long.class);
    args.put("limit", size);
    args.put("offset", (long) page * size);
    var rows =
        db.queryForList(
            SELECT + where + " order by " + sort + ",o.id limit :limit offset :offset", args);
    rows.forEach(this::decode);
    return new Page(rows, total, page, size);
  }

  public Map<String, Object> detail(UUID id) {
    var rows =
        db.queryForList(
            SELECT + " where o.id=:id and o.review_status='APPROVED'", Map.of("id", id));
    if (rows.isEmpty())
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Opportunity not found");
    var row = rows.getFirst();
    decode(row);
    row.put(
        "documents",
        db.queryForList(
            "select id,source_document_url,filename,mime_type,extraction_status,(storage_path is not null) stored from opportunity_document where opportunity_id=:id",
            Map.of("id", id)));
    row.put(
        "provenance",
        db.queryForList(
            "select field_name,source_url,evidence,observed_at from field_provenance where opportunity_id=:id order by observed_at desc limit 50",
            Map.of("id", id)));
    return row;
  }

  public void decode(Map<String, Object> row) {
    row.remove("search_vector");
    if (row.get("details") != null) row.put("details", json.read(row.get("details").toString()));
  }

  private boolean present(Map<String, String> f, String k) {
    return f.containsKey(k) && !f.get(k).isBlank();
  }

  private int integer(String s, int min, int max) {
    int n = Integer.parseInt(s);
    if (n < min || n > max) throw new IllegalArgumentException("Pagination outside allowed range");
    return n;
  }
}
