package com.mycelium.opportunities.admin;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mycelium.opportunities.ingestion.IngestionService;
import com.mycelium.opportunities.source.SourceRecord;
import java.util.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {
  private final AdminService admin;
  private final IngestionService ingestion;
  private final ImportService imports;
  private final ObjectMapper mapper;

  public AdminController(
      AdminService admin, IngestionService ingestion, ImportService imports, ObjectMapper mapper) {
    this.admin = admin;
    this.ingestion = ingestion;
    this.imports = imports;
    this.mapper = mapper;
  }

  public record Config(boolean enabled, String accessStatus, int intervalMinutes) {}

  public record Review(String status) {}

  public record Import(UUID sourceId, List<SourceRecord> records) {}

  @GetMapping("/sources")
  public Object sources() {
    return admin.sources();
  }

  @PatchMapping("/sources/{id}")
  public void configure(@PathVariable UUID id, @RequestBody Config c) {
    admin.configure(id, c.enabled(), c.accessStatus(), c.intervalMinutes());
  }

  @PostMapping("/sources/{id}/sync")
  public Object sync(@PathVariable UUID id) {
    return ingestion.sync(id);
  }

  @GetMapping("/ingestion-runs")
  public Object runs() {
    return admin.runs();
  }

  @GetMapping("/review-queue")
  public Object queue() {
    return admin.queue();
  }

  @PatchMapping("/opportunities/{id}/review")
  public void review(@PathVariable UUID id, @RequestBody Review r) {
    admin.review(id, r.status());
  }

  @PostMapping("/opportunities/import/preview")
  public Object preview(
      @RequestParam MultipartFile file, @RequestParam String mapping, @RequestParam String type)
      throws Exception {
    return imports.preview(
        file, mapper.readValue(mapping, new TypeReference<Map<String, String>>() {}), type);
  }

  @PostMapping("/opportunities/import")
  public Object confirm(@RequestBody Import body) {
    return imports.confirm(body.sourceId(), body.records());
  }
}
