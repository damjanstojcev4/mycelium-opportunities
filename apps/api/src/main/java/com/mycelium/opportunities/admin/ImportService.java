package com.mycelium.opportunities.admin;

import com.mycelium.opportunities.ingestion.RecordWriter;
import com.mycelium.opportunities.source.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import org.apache.commons.csv.*;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ImportService {
  private final RecordWriter writer;

  public ImportService(RecordWriter writer) {
    this.writer = writer;
  }

  public Object preview(MultipartFile file, Map<String, String> mapping, String type)
      throws Exception {
    List<Map<String, String>> rows = new ArrayList<>();
    if (file.getOriginalFilename() != null
        && file.getOriginalFilename().toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
      try (var workbook = new XSSFWorkbook(file.getInputStream())) {
        var sheet = workbook.getSheetAt(0);
        if (sheet.getLastRowNum() > 1000)
          throw new IllegalArgumentException("Import at most 1000 rows");
        var headers = sheet.getRow(0);
        if (headers == null) throw new IllegalArgumentException("Missing header row");
        var formatter = new DataFormatter(Locale.ROOT);
        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
          var row = sheet.getRow(i);
          if (row == null) continue;
          Map<String, String> values = new HashMap<>();
          for (int j = 0; j < headers.getLastCellNum(); j++)
            values.put(
                formatter.formatCellValue(headers.getCell(j)),
                formatter.formatCellValue(row.getCell(j)));
          rows.add(values);
        }
      }
    } else {
      try (var reader = new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8);
          var csv =
              CSVFormat.DEFAULT
                  .builder()
                  .setHeader()
                  .setSkipHeaderRecord(true)
                  .get()
                  .parse(reader)) {
        for (var r : csv) {
          if (rows.size() >= 1000) throw new IllegalArgumentException("Import at most 1000 rows");
          rows.add(r.toMap());
        }
      }
    }
    List<Object> results = new ArrayList<>();
    int index = 2;
    for (var row : rows) {
      try {
        Map<String, String> values = new HashMap<>();
        mapping.forEach((field, column) -> values.put(field, row.getOrDefault(column, "")));
        var record = record(values, type);
        SafeSourceHttp.validate(record.sourceUrl());
        results.add(Map.of("row", index, "record", record));
      } catch (Exception e) {
        results.add(Map.of("row", index, "error", e.getMessage()));
      }
      index++;
    }
    return results;
  }

  private SourceRecord record(Map<String, String> m, String type) {
    String deadline = m.get("deadlineAt");
    Instant instant = deadline == null || deadline.isBlank() ? null : Instant.parse(deadline);
    return new SourceRecord(
        m.get("externalId"),
        type,
        m.get("title"),
        m.get("description"),
        m.get("issuer"),
        blank(m.get("country")),
        blank(m.get("publishedAt")) == null ? null : Instant.parse(m.get("publishedAt")),
        blank(m.get("opensAt")) == null ? null : Instant.parse(m.get("opensAt")),
        instant,
        blank(m.get("value")) == null ? null : new java.math.BigDecimal(m.get("value")),
        blank(m.get("currency")),
        blank(m.get("status")) == null ? "UNKNOWN" : m.get("status"),
        m.get("sourceUrl"),
        "mk",
        Map.of("importMethod", "official export"),
        List.of(),
        instant == null
            ? Map.of()
            : Map.of("deadlineAt", "Official export deadlineAt: " + deadline));
  }

  private String blank(String value) {
    return value == null || value.isBlank() ? null : value;
  }

  public Object confirm(UUID source, List<SourceRecord> records) {
    if (records.isEmpty() || records.size() > 1000)
      throw new IllegalArgumentException("Supply 1–1000 records");
    int inserted = 0, updated = 0, duplicates = 0;
    List<String> errors = new ArrayList<>();
    for (var r : records) {
      try {
        switch (writer.write(source, r)) {
          case "INSERTED" -> inserted++;
          case "UPDATED" -> updated++;
          default -> duplicates++;
        }
      } catch (Exception e) {
        errors.add(r.externalId() + ": " + e.getMessage());
      }
    }
    return Map.of(
        "inserted",
        inserted,
        "updated",
        updated,
        "duplicates",
        duplicates,
        "errors",
        errors,
        "reviewStatus",
        "PENDING");
  }
}
