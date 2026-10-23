package com.mycelium.opportunities.extraction;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

@Component
public class PdfExtractor {
  public record Extraction(String status, String text) {}

  public Extraction extract(byte[] pdf) throws java.io.IOException {
    if (pdf.length > 5_000_000) throw new java.io.IOException("PDF exceeds 5 MB");
    try (var document = Loader.loadPDF(pdf)) {
      if (document.getNumberOfPages() > 200) throw new java.io.IOException("PDF exceeds 200 pages");
      String text = new PDFTextStripper().getText(document);
      return new Extraction(text.strip().length() < 30 ? "OCR_REQUIRED" : "EXTRACTED", text);
    }
  }
}
