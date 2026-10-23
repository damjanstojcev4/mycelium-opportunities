package com.mycelium.opportunities.matching;

import com.mycelium.opportunities.common.Json;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class MatchingService {
  private final JdbcTemplate db;
  private final Json json;
  private final MatchEngine engine;

  public MatchingService(JdbcTemplate db, Json json, MatchEngine engine) {
    this.db = db;
    this.json = json;
    this.engine = engine;
  }

  public void recalculate(UUID companyId) {
    var c = db.queryForMap("select * from company where id=?", companyId);
    int offset = 0;
    while (true) {
      var rows =
          db.queryForList(
              "select * from opportunity where review_status='APPROVED' order by id limit 250 offset ?",
              offset);
      for (var o : rows) {
        var details =
            (com.fasterxml.jackson.databind.node.ObjectNode) json.read(o.get("details").toString());
        if (o.get("estimated_value") != null)
          details.put("estimatedValue", (java.math.BigDecimal) o.get("estimated_value"));
        if (o.get("currency") != null) details.put("currency", (String) o.get("currency"));
        var result =
            engine.evaluate(
                (String) c.get("country_code"),
                (String) c.get("industry"),
                json.read(c.get("profile").toString()),
                (String) o.get("type"),
                (String) o.get("country_code"),
                o.get("title") + " " + o.get("description"),
                details);
        db.update(
            "insert into company_match(id,company_id,opportunity_id,relevance_score,eligibility_status,positive_reasons,missing_requirements,disqualifying_requirements) values(?,?,?,?,?,?::jsonb,?::jsonb,?::jsonb) on conflict(company_id,opportunity_id) do update set relevance_score=excluded.relevance_score,eligibility_status=excluded.eligibility_status,positive_reasons=excluded.positive_reasons,missing_requirements=excluded.missing_requirements,disqualifying_requirements=excluded.disqualifying_requirements,calculated_at=now()",
            UUID.randomUUID(),
            companyId,
            o.get("id"),
            result.score(),
            result.eligibility(),
            json.write(result.reasons()),
            json.write(result.missing()),
            json.write(result.disqualifiers()));
      }
      if (rows.size() < 250) break;
      offset += 250;
    }
  }

  public void recalculateAll() {
    db.queryForList("select id from company", UUID.class).forEach(this::recalculate);
  }

  public Object list(UUID company, int page) {
    if (page < 0 || page > 100000) throw new IllegalArgumentException("Invalid page");
    var rows =
        db.queryForList(
            "select m.*,o.title,o.type,o.deadline_at,o.source_url from company_match m join opportunity o on o.id=m.opportunity_id where m.company_id=? and o.review_status='APPROVED' and m.relevance_score>0 order by m.relevance_score desc,o.id limit 20 offset ?",
            company,
            page * 20);
    rows.forEach(
        r -> {
          for (String k :
              List.of("positive_reasons", "missing_requirements", "disqualifying_requirements"))
            r.put(k, json.read(r.get(k).toString()));
        });
    return rows;
  }
}
