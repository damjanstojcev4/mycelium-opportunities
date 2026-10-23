package com.mycelium.opportunities.ingestion;

import com.mycelium.opportunities.source.*;
import java.util.*;
import org.slf4j.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class IngestionService {
  private static final Logger log = LoggerFactory.getLogger(IngestionService.class);
  private final JdbcTemplate db;
  private final List<OpportunitySourceConnector> connectors;
  private final RecordWriter writer;

  public IngestionService(
      JdbcTemplate db, List<OpportunitySourceConnector> connectors, RecordWriter writer) {
    this.db = db;
    this.connectors = connectors;
    this.writer = writer;
  }

  @Scheduled(fixedDelayString = "${app.scheduler-delay}")
  public void scheduled() {
    for (UUID id :
        db.queryForList(
            "select id from source where enabled and access_status='VERIFIED' and (last_attempt_at is null or last_attempt_at < now()-interval_minutes*interval '1 minute')",
            UUID.class)) {
      try {
        sync(id);
      } catch (Exception e) {
        log.warn("Source scheduling failed for {}: {}", id, e.getMessage());
      }
    }
  }

  public Map<String, Object> sync(UUID source) {
    var s = db.queryForMap("select * from source where id=?", source);
    if (!Boolean.TRUE.equals(s.get("enabled")) || !"VERIFIED".equals(s.get("access_status")))
      throw new IllegalArgumentException(
          "Enable this source only after verifying access and reuse conditions");
    var connector =
        connectors.stream()
            .filter(c -> c.sourceCode().equals(s.get("code")))
            .findFirst()
            .orElseThrow(
                () -> new IllegalArgumentException("This source requires an official-file import"));
    if (db.update(
            "update source set lease_until=now()+interval '10 minutes',last_attempt_at=now() where id=? and (lease_until is null or lease_until < now())",
            source)
        == 0) throw new IllegalArgumentException("Source sync is already running");
    UUID run = UUID.randomUUID();
    db.update("insert into ingestion_run(id,source_id,status) values(?,?,'RUNNING')", run, source);
    int fetched = 0, inserted = 0, updated = 0, rejected = 0;
    boolean complete = false;
    try {
      String checkpoint = (String) s.get("checkpoint");
      for (int page = 0; page < 5; page++) {
        var batch = connector.fetch(checkpoint);
        fetched += batch.records().size();
        for (var record : batch.records()) {
          try {
            String result = writer.write(source, record);
            if (result.equals("INSERTED")) inserted++;
            if (result.equals("UPDATED")) updated++;
          } catch (Exception e) {
            rejected++;
            db.update(
                "insert into ingestion_error(id,run_id,external_id,message) values(?,?,?,?)",
                UUID.randomUUID(),
                run,
                record.externalId(),
                "Record validation/persistence failed; inspect server log");
            log.warn("Rejected source record {}", record.externalId(), e);
          }
        }
        if (rejected > 0)
          throw new IllegalStateException("Records rejected; checkpoint retained for retry");
        complete = batch.complete();
        String next = complete ? null : batch.nextCheckpoint();
        if (!complete && Objects.equals(checkpoint, next))
          throw new IllegalArgumentException("Source pagination did not advance");
        checkpoint = next;
        db.update(
            "update source set checkpoint=?,lease_until=now()+interval '10 minutes' where id=?",
            checkpoint,
            source);
        if (complete) break;
      }
      db.update(
          "update ingestion_run set status=?,fetched=?,inserted=?,updated=?,rejected=?,finished_at=now() where id=?",
          complete ? "SUCCESS" : "PARTIAL",
          fetched,
          inserted,
          updated,
          rejected,
          run);
      db.update(
          "update source set last_success_at=CASE WHEN ? THEN now() ELSE last_success_at END,last_error=null where id=?",
          complete,
          source);
      if (!complete)
        db.update(
            "update source set last_attempt_at=now()-interval_minutes*interval '1 minute' where id=?",
            source);
    } catch (Exception e) {
      log.warn("Ingestion failed for {}", s.get("code"), e);
      db.update(
          "update ingestion_run set status='FAILED',error=?,fetched=?,inserted=?,updated=?,rejected=?,finished_at=now() where id=?",
          e.getMessage(),
          fetched,
          inserted,
          updated,
          rejected,
          run);
      db.update("update source set last_error=? where id=?", e.getMessage(), source);
    } finally {
      db.update("update source set lease_until=null where id=?", source);
    }
    return db.queryForMap("select * from ingestion_run where id=?", run);
  }
}
