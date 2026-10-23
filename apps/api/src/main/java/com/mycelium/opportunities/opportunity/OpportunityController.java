package com.mycelium.opportunities.opportunity;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class OpportunityController {
  private final OpportunityQuery query;
  private final JdbcTemplate db;

  public OpportunityController(OpportunityQuery query, JdbcTemplate db) {
    this.query = query;
    this.db = db;
  }

  @GetMapping("/opportunities")
  public OpportunityQuery.Page search(@RequestParam Map<String, String> filters) {
    return query.search(filters);
  }

  @GetMapping("/opportunities/{id}")
  public Map<String, Object> detail(@PathVariable UUID id) {
    return query.detail(id);
  }

  @GetMapping("/opportunities/{id}/documents")
  public Object documents(@PathVariable UUID id) {
    return query.detail(id).get("documents");
  }

  @GetMapping("/sources")
  public Object sources() {
    return db.queryForList(
        "select code,name,base_url,source_type,access_status,last_success_at from source order by name");
  }
}
