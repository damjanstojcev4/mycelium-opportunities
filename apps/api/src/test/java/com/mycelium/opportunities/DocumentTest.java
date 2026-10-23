package com.mycelium.opportunities;

import static org.junit.jupiter.api.Assertions.*;

import com.mycelium.opportunities.extraction.PdfExtractor;
import com.mycelium.opportunities.storage.FileDocumentStorage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import org.apache.pdfbox.pdmodel.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DocumentTest {
  @TempDir Path directory;

  @Test
  void storageKeysCannotEscapeDirectory() throws Exception {
    var storage = new FileDocumentStorage(directory.toString());
    byte[] content = {1, 2, 3};
    String key = storage.put(content);
    assertArrayEquals(content, storage.get(key));
    assertThrows(IllegalArgumentException.class, () -> storage.get("../secrets"));
  }

  @Test
  void scannedOrEmptyPdfRequiresReview() throws Exception {
    try (var document = new PDDocument();
        var bytes = new ByteArrayOutputStream()) {
      document.addPage(new PDPage());
      document.save(bytes);
      assertEquals("OCR_REQUIRED", new PdfExtractor().extract(bytes.toByteArray()).status());
    }
  }
}
