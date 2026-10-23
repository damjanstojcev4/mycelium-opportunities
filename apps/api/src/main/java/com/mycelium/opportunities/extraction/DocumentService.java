package com.mycelium.opportunities.extraction;

import com.mycelium.opportunities.opportunity.OpportunityQuery;
import com.mycelium.opportunities.source.SafeSourceHttp;
import com.mycelium.opportunities.storage.DocumentStorage;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class DocumentService {
  private final DocumentStorage storage;
  private final PdfExtractor extractor;
  private final JdbcTemplate db;
  private final OpportunityQuery opportunities;

  public DocumentService(
      DocumentStorage storage,
      PdfExtractor extractor,
      JdbcTemplate db,
      OpportunityQuery opportunities) {
    this.storage = storage;
    this.extractor = extractor;
    this.db = db;
    this.opportunities = opportunities;
  }

  public Object upload(UUID opportunity, String sourceUrl, MultipartFile file) throws Exception {
    SafeSourceHttp.validate(sourceUrl);
    if (file.getSize() > 5_000_000) throw new IllegalArgumentException("PDF exceeds 5 MB");
    if (db.queryForObject("select count(*) from opportunity where id=?", Integer.class, opportunity)
        == 0)
      throw new org.springframework.web.server.ResponseStatusException(
          org.springframework.http.HttpStatus.NOT_FOUND);
    byte[] bytes = file.getBytes();
    if (bytes.length < 5
        || !new String(bytes, 0, 5, java.nio.charset.StandardCharsets.US_ASCII).equals("%PDF-"))
      throw new IllegalArgumentException("Upload a digital PDF document");
    var extraction = extractor.extract(bytes);
    String key = storage.put(bytes);
    String hash =
        HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
    db.update(
        "insert into opportunity_document(id,opportunity_id,source_document_url,filename,mime_type,storage_path,content_hash,extraction_status,extracted_text,downloaded_at) values(?,?,?,'source-document.pdf','application/pdf',?,?,?,?,now()) on conflict(opportunity_id,source_document_url) do update set filename=excluded.filename,mime_type=excluded.mime_type,storage_path=excluded.storage_path,content_hash=excluded.content_hash,extraction_status=excluded.extraction_status,extracted_text=excluded.extracted_text,downloaded_at=excluded.downloaded_at",
        UUID.randomUUID(),
        opportunity,
        sourceUrl,
        key,
        hash,
        extraction.status(),
        extraction.text());
    return Map.of(
        "status",
        extraction.status(),
        "text",
        extraction.text(),
        "note",
        "Extracted text is not a verified deadline or eligibility claim. Review the official document before updating fields.");
  }

  public byte[] download(UUID opportunity, UUID document) throws Exception {
    opportunities.detail(opportunity);
    var rows =
        db.queryForList(
            "select storage_path from opportunity_document where id=? and opportunity_id=? and storage_path is not null",
            document,
            opportunity);
    if (rows.isEmpty())
      throw new org.springframework.web.server.ResponseStatusException(
          org.springframework.http.HttpStatus.NOT_FOUND);
    return storage.get((String) rows.getFirst().get("storage_path"));
  }
}
