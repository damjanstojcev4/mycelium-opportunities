package com.mycelium.opportunities.company;

import com.fasterxml.jackson.databind.JsonNode;
import com.mycelium.opportunities.common.Json;
import com.mycelium.opportunities.matching.MatchingService;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CompanyService {
  public record Profile(
      @NotBlank @Size(max = 200) String name,
      @Size(max = 10000) String description,
      @Pattern(regexp = "[A-Z]{2}") @NotBlank String countryCode,
      @Size(max = 200) String industry,
      @Size(max = 100) String city,
      JsonNode preferences) {}

  private final JdbcTemplate db;
  private final Json json;
  private final MatchingService matches;

  public CompanyService(JdbcTemplate db, Json json, MatchingService matches) {
    this.db = db;
    this.json = json;
    this.matches = matches;
  }

  public List<Map<String, Object>> list(UUID owner) {
    var rows =
        db.queryForList("select * from company where owner_user_id=? order by created_at", owner);
    rows.forEach(r -> r.put("profile", json.read(r.get("profile").toString())));
    return rows;
  }

  public void requireOwner(UUID company, UUID owner) {
    if (db.queryForObject(
            "select count(*) from company where id=? and owner_user_id=?",
            Long.class,
            company,
            owner)
        == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Company not found");
  }

  @Transactional
  public UUID save(UUID id, UUID owner, Profile body) {
    String preferences = json.write(body.preferences() == null ? Map.of() : body.preferences());
    if (preferences.length() > 20000) throw new IllegalArgumentException("Preferences too large");
    if (id == null) {
      id = UUID.randomUUID();
      db.update(
          "insert into company(id,owner_user_id,name,description,country_code,industry,city,profile) values(?,?,?,?,?,?,?,?::jsonb)",
          id,
          owner,
          body.name(),
          Objects.requireNonNullElse(body.description(), ""),
          body.countryCode(),
          body.industry(),
          body.city(),
          preferences);
    } else {
      requireOwner(id, owner);
      db.update(
          "update company set name=?,description=?,country_code=?,industry=?,city=?,profile=?::jsonb,updated_at=now() where id=?",
          body.name(),
          Objects.requireNonNullElse(body.description(), ""),
          body.countryCode(),
          body.industry(),
          body.city(),
          preferences,
          id);
    }
    matches.recalculate(id);
    return id;
  }
}
