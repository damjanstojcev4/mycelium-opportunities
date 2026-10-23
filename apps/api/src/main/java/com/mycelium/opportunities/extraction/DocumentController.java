package com.mycelium.opportunities.extraction;

import java.util.UUID;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1")
public class DocumentController {
  private final DocumentService documents;

  public DocumentController(DocumentService documents) {
    this.documents = documents;
  }

  @PostMapping("/admin/opportunities/{id}/documents")
  public Object upload(
      @PathVariable UUID id, @RequestParam String sourceUrl, @RequestParam MultipartFile file)
      throws Exception {
    return documents.upload(id, sourceUrl, file);
  }

  @GetMapping("/opportunities/{id}/documents/{document}/content")
  public ResponseEntity<byte[]> content(@PathVariable UUID id, @PathVariable UUID document)
      throws Exception {
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_PDF)
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=source-document.pdf")
        .header("X-Content-Type-Options", "nosniff")
        .body(documents.download(id, document));
  }
}
