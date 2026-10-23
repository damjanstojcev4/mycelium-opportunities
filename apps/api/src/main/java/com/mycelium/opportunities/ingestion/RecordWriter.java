package com.mycelium.opportunities.ingestion;

import com.mycelium.opportunities.common.Json;
import com.mycelium.opportunities.source.*;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RecordWriter {
  private final JdbcTemplate db;
  private final Json json;

  public RecordWriter(JdbcTemplate db, Json json) {
    this.db = db;
    this.json = json;
  }

  @Transactional
  public String write(UUID source, SourceRecord record) {
    var publicationUrl = SafeSourceHttp.validate(record.sourceUrl());
    String base = db.queryForObject("select base_url from source where id=?", String.class, source);
    String sourceHost = java.net.URI.create(base).getHost().replaceFirst("^www\\.", "");
    if (!publicationUrl.getHost().replaceFirst("^www\\.", "").equals(sourceHost))
      throw new IllegalArgumentException("Publication URL does not belong to the selected source");
    String snapshot = json.write(record), hash = hash(snapshot);
    db.queryForObject(
        "select pg_advisory_xact_lock(hashtextextended(?,0))",
        Object.class,
        source + ":" + record.externalId());
    var existing =
        db.queryForList(
            "select id,content_hash,deadline_at,status from opportunity where source_id=? and external_id=?",
            source,
            record.externalId());
    UUID id = existing.isEmpty() ? UUID.randomUUID() : (UUID) existing.getFirst().get("id");
    if (!existing.isEmpty() && hash.equals(existing.getFirst().get("content_hash"))) {
      db.update("update opportunity set last_verified_at=now() where id=?", id);
      return "UNCHANGED";
    }
    db.update(
        "insert into opportunity(id,type,source_id,external_id,title,description,issuer_name,country_code,published_at,opens_at,deadline_at,estimated_value,currency,status,source_url,original_language,details,content_hash,last_verified_at) values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?::jsonb,?,now()) on conflict(source_id,external_id) do update set title=excluded.title,description=excluded.description,issuer_name=excluded.issuer_name,country_code=excluded.country_code,published_at=excluded.published_at,opens_at=excluded.opens_at,deadline_at=excluded.deadline_at,estimated_value=excluded.estimated_value,currency=excluded.currency,status=excluded.status,details=excluded.details,content_hash=excluded.content_hash,source_url=excluded.source_url,review_status='PENDING',updated_at=now(),last_verified_at=now()",
        id,
        record.type(),
        source,
        record.externalId(),
        record.title(),
        record.description(),
        record.issuer(),
        record.country(),
        ts(record.publishedAt()),
        ts(record.opensAt()),
        ts(record.deadlineAt()),
        record.value(),
        record.currency(),
        record.status(),
        record.sourceUrl(),
        record.language(),
        json.write(record.details()),
        hash);
    var changed = new ArrayList<String>();
    var old =
        db.queryForList(
            "select snapshot from opportunity_revision where opportunity_id=? order by created_at desc limit 1",
            id);
    var newNode = json.read(snapshot);
    if (!old.isEmpty()) {
      var oldNode = json.read(old.getFirst().get("snapshot").toString());
      newNode
          .fieldNames()
          .forEachRemaining(
              key -> {
                if (!Objects.equals(oldNode.get(key), newNode.get(key))) changed.add(key);
              });
    } else changed.add("created");
    db.update(
        "insert into opportunity_revision(id,opportunity_id,snapshot,content_hash,changed_fields) values(?,?,?::jsonb,?,?::jsonb)",
        UUID.randomUUID(),
        id,
        snapshot,
        hash,
        json.write(changed));
    for (var evidence : record.evidence().entrySet())
      db.update(
          "insert into field_provenance(id,opportunity_id,field_name,source_url,evidence) values(?,?,?,?,?)",
          UUID.randomUUID(),
          id,
          evidence.getKey(),
          record.sourceUrl(),
          evidence.getValue());
    for (String url : record.documents()) {
      SafeSourceHttp.validate(url);
      db.update(
          "insert into opportunity_document(id,opportunity_id,source_document_url) values(?,?,?) on conflict do nothing",
          UUID.randomUUID(),
          id,
          url);
    }
    return existing.isEmpty() ? "INSERTED" : "UPDATED";
  }

  public static String hash(String s) {
    try {
      return HexFormat.of()
          .formatHex(
              java.security.MessageDigest.getInstance("SHA-256")
                  .digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private Timestamp ts(Instant i) {
    return i == null ? null : Timestamp.from(i);
  }
}
