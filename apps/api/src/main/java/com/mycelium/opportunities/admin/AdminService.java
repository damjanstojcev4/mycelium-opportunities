package com.mycelium.opportunities.admin;

import com.mycelium.opportunities.common.Json;
import com.mycelium.opportunities.matching.MatchingService;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class AdminService {
  private final JdbcTemplate db;
  private final MatchingService matching;
  private final Json json;

  public AdminService(JdbcTemplate db, MatchingService matching, Json json) {
    this.db = db;
    this.matching = matching;
    this.json = json;
  }

  public Object sources() {
    return db.queryForList("select * from source order by name");
  }

  public void configure(UUID id, boolean enabled, String access, int interval) {
    if (interval < 60
        || interval > 10080
        || !Set.of("VERIFIED", "ACCESS_UNVERIFIED", "RESTRICTED", "DISABLED").contains(access))
      throw new IllegalArgumentException("Invalid source configuration");
    if (enabled && !access.equals("VERIFIED"))
      throw new IllegalArgumentException("Enabled sources must have verified access");
    db.update(
        "update source set enabled=?,access_status=?,interval_minutes=? where id=?",
        enabled,
        access,
        interval,
        id);
  }

  public Object runs() {
    return db.queryForList(
        "select r.*,s.name source_name from ingestion_run r join source s on s.id=r.source_id order by started_at desc limit 100");
  }

  public Object queue() {
    var rows =
        db.queryForList(
            "select id,title,type,source_url,description,details,deadline_at from opportunity where review_status='PENDING' order by updated_at desc limit 100");
    rows.forEach(r -> r.put("details", json.read(r.get("details").toString())));
    return rows;
  }

  public void review(UUID id, String status) {
    if (!Set.of("APPROVED", "REJECTED").contains(status))
      throw new IllegalArgumentException("Invalid review decision");
    db.update("update opportunity set review_status=?,updated_at=now() where id=?", status, id);
    matching.recalculateAll();
  }
}
